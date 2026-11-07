package com.v2ray.ang.handler

import android.content.Context
import android.net.TrafficStats
import android.os.SystemClock
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.AetherCoreManager
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.dto.ConnectionTestResult
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.AetherIpVersion
import com.v2ray.ang.enums.AetherObfuscation
import com.v2ray.ang.enums.AetherProtocol
import com.v2ray.ang.enums.AetherPsiphon
import com.v2ray.ang.enums.AetherTor
import com.v2ray.ang.enums.AetherTransport
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * State of real-time network traffic
 */
data class HamedTrafficState(
    val downloadSpeedStr: String = "0 B/s",
    val uploadSpeedStr: String = "0 B/s",
    val downloadSpeedBytes: Long = 0L,
    val uploadSpeedBytes: Long = 0L,
    val totalDownloadStr: String = "0 MB",
    val totalUploadStr: String = "0 MB",
)

/**
 * State of connection info (IP & Flag & Ping)
 */
data class HamedConnectionInfo(
    val ip: String = "---",
    val country: String = "---",
    val flagEmoji: String = "🌐",
    val pingMs: Long = -1L,
    val isTesting: Boolean = false,
    val testError: String? = null,
)

object HamedAetherManager {

    private const val TAG = "HamedAetherManager"
    const val DEFAULT_PROFILE_REMARKS = "Hamed Aether"

    private val scope = CoroutineScope(Dispatchers.IO)
    private var trafficJob: Job? = null

    private val _trafficState = MutableStateFlow(HamedTrafficState())
    val trafficState: StateFlow<HamedTrafficState> = _trafficState.asStateFlow()

    private val _connectionInfo = MutableStateFlow(HamedConnectionInfo())
    val connectionInfo: StateFlow<HamedConnectionInfo> = _connectionInfo.asStateFlow()

    private var sessionStartRx = 0L
    private var sessionStartTx = 0L
    private var lastRxBytes = 0L
    private var lastTxBytes = 0L
    private var lastTimeMs = 0L

    /**
     * Ensures an Aether configuration exists in MMKV and returns its GUID.
     */
    fun ensureAetherProfile(): String {
        val selectedGuid = MmkvManager.getSelectServer().orEmpty()
        if (selectedGuid.isNotEmpty()) {
            val currentProfile = MmkvManager.decodeServerConfig(selectedGuid)
            if (currentProfile?.configType == EConfigType.AETHER) {
                return selectedGuid
            }
        }

        // Search through all existing profiles for any Aether profile
        val allGuids = MmkvManager.decodeServerList("")
        for (guid in allGuids) {
            val profile = MmkvManager.decodeServerConfig(guid)
            if (profile?.configType == EConfigType.AETHER) {
                MmkvManager.setSelectServer(guid)
                return guid
            }
        }

        // Create a new Hamed Aether profile
        val profile = ProfileItem.create(EConfigType.AETHER).apply {
            remarks = DEFAULT_PROFILE_REMARKS
            server = "162.159.192.1"
            serverPort = "2408"
            aetherProtocol = AetherProtocol.MASQUE.type
            aetherTransport = AetherTransport.HTTP3.type
            aetherMasqueSni = AppConfig.AETHER_MASQUE_SNI
            aetherObfuscation = AetherObfuscation.AUTO.type
            aetherIpVersion = AetherIpVersion.V4.type
            aetherPsiphon = AetherPsiphon.OFF.type
            aetherTor = AetherTor.OFF.type
            aetherFingerprint = "chrome"
        }

        val guid = MmkvManager.encodeServerConfig("", profile)
        MmkvManager.setSelectServer(guid)
        LogUtil.d(TAG, "Created default Hamed Aether profile: $guid")
        return guid
    }

    /**
     * Returns the currently active Aether profile and its GUID.
     */
    fun getActiveAetherProfile(): Pair<String, ProfileItem> {
        val guid = ensureAetherProfile()
        var profile = MmkvManager.decodeServerConfig(guid)
        if (profile == null || profile.configType != EConfigType.AETHER) {
            profile = ProfileItem.create(EConfigType.AETHER).apply {
                remarks = DEFAULT_PROFILE_REMARKS
                server = "162.159.192.1"
                serverPort = "2408"
                aetherProtocol = AetherProtocol.MASQUE.type
                aetherTransport = AetherTransport.HTTP3.type
                aetherMasqueSni = AppConfig.AETHER_MASQUE_SNI
                aetherObfuscation = AetherObfuscation.AUTO.type
                aetherIpVersion = AetherIpVersion.V4.type
                aetherPsiphon = AetherPsiphon.OFF.type
                aetherTor = AetherTor.OFF.type
            }
            MmkvManager.encodeServerConfig(guid, profile)
        }
        return Pair(guid, profile)
    }

    /**
     * Updates the protocol of the active Aether profile.
     */
    fun updateProtocol(protocol: AetherProtocol): ProfileItem {
        val (guid, profile) = getActiveAetherProfile()
        profile.aetherProtocol = protocol.type
        MmkvManager.encodeServerConfig(guid, profile)
        return profile
    }

    /**
     * Updates the chain carrier (Psiphon or Tor) of the active Aether profile.
     */
    fun updateChain(psiphon: AetherPsiphon, tor: AetherTor): ProfileItem {
        val (guid, profile) = getActiveAetherProfile()
        profile.aetherPsiphon = psiphon.type
        profile.aetherTor = tor.type
        MmkvManager.encodeServerConfig(guid, profile)
        return profile
    }

    /**
     * Updates obfuscation mode.
     */
    fun updateObfuscation(obf: AetherObfuscation): ProfileItem {
        val (guid, profile) = getActiveAetherProfile()
        profile.aetherObfuscation = obf.type
        MmkvManager.encodeServerConfig(guid, profile)
        return profile
    }

    /**
     * Starts monitoring traffic speed and total transferred bytes.
     */
    fun startTrafficMonitoring() {
        trafficJob?.cancel()
        sessionStartRx = TrafficStats.getTotalRxBytes()
        sessionStartTx = TrafficStats.getTotalTxBytes()
        lastRxBytes = sessionStartRx
        lastTxBytes = sessionStartTx
        lastTimeMs = SystemClock.elapsedRealtime()

        trafficJob = scope.launch {
            while (isActive) {
                delay(1000)
                val nowTime = SystemClock.elapsedRealtime()
                val currentRx = TrafficStats.getTotalRxBytes()
                val currentTx = TrafficStats.getTotalTxBytes()

                val timeDeltaSec = ((nowTime - lastTimeMs).coerceAtLeast(1L)) / 1000.0
                if (timeDeltaSec > 0) {
                    val rxSpeed = ((currentRx - lastRxBytes).coerceAtLeast(0L) / timeDeltaSec).toLong()
                    val txSpeed = ((currentTx - lastTxBytes).coerceAtLeast(0L) / timeDeltaSec).toLong()

                    val totalRx = (currentRx - sessionStartRx).coerceAtLeast(0L)
                    val totalTx = (currentTx - sessionStartTx).coerceAtLeast(0L)

                    _trafficState.value = HamedTrafficState(
                        downloadSpeedStr = formatSpeed(rxSpeed),
                        uploadSpeedStr = formatSpeed(txSpeed),
                        downloadSpeedBytes = rxSpeed,
                        uploadSpeedBytes = txSpeed,
                        totalDownloadStr = formatDataSize(totalRx),
                        totalUploadStr = formatDataSize(totalTx)
                    )
                }

                lastRxBytes = currentRx
                lastTxBytes = currentTx
                lastTimeMs = nowTime
            }
        }
    }

    /**
     * Stops traffic monitoring.
     */
    fun stopTrafficMonitoring() {
        trafficJob?.cancel()
        trafficJob = null
        _trafficState.value = HamedTrafficState()
    }

    /**
     * Converts a 2-letter ISO country code (e.g. "US", "DE", "IR") to an emoji flag.
     */
    fun countryCodeToEmojiFlag(countryCode: String?): String {
        if (countryCode == null || countryCode.length != 2) return "🌐"
        val code = countryCode.uppercase()
        val first = Character.codePointAt(code, 0) - 0x41 + 0x1F1E6
        val second = Character.codePointAt(code, 1) - 0x41 + 0x1F1E6
        return try {
            String(Character.toChars(first)) + String(Character.toChars(second))
        } catch (_: Exception) {
            "🌐"
        }
    }

    /**
     * Updates the connection info based on a ConnectionTestResult.
     */
    fun updateFromTestResult(result: ConnectionTestResult) {
        val flag = countryCodeToEmojiFlag(result.country)
        val country = result.country ?: "---"
        val ip = result.ipAddress ?: "---"
        _connectionInfo.value = _connectionInfo.value.copy(
            ip = ip,
            country = country,
            flagEmoji = flag,
            pingMs = result.delayMillis,
            isTesting = false,
            testError = if (result.delayMillis < 0) result.errorMessage else null
        )
    }

    /**
     * Triggers an HTTPS ping and IP lookup.
     */
    fun runRealHttpsTest(onComplete: ((ConnectionTestResult) -> Unit)? = null) {
        _connectionInfo.value = _connectionInfo.value.copy(isTesting = true, testError = null)
        scope.launch {
            val start = System.currentTimeMillis()
            var delayMs = -1L
            var errorMsg: String? = null

            try {
                // Test real connection to Google or Cloudflare
                val latency = SpeedtestManager.socketConnectTime("1.1.1.1", 443, 3000)
                delayMs = if (latency >= 0) latency else (System.currentTimeMillis() - start)
            } catch (e: Exception) {
                errorMsg = e.message ?: "Connection error"
            }

            val remoteInfo = try {
                SpeedtestManager.getRemoteIPInfo()
            } catch (_: Exception) {
                null
            }

            val result = ConnectionTestResult(
                delayMillis = delayMs,
                errorMessage = errorMsg.orEmpty(),
                country = remoteInfo?.country,
                ipAddress = remoteInfo?.ipAddress
            )

            withContext(Dispatchers.Main) {
                updateFromTestResult(result)
                onComplete?.invoke(result)
            }
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec < 1024 -> "$bytesPerSec B/s"
            bytesPerSec < 1024 * 1024 -> "${bytesPerSec / 1024} KB/s"
            else -> String.format(java.util.Locale.US, "%.1f MB/s", bytesPerSec / (1024.0 * 1024.0))
        }
    }

    private fun formatDataSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}

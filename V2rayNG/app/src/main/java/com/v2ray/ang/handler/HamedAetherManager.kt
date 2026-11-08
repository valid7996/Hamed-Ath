package com.v2ray.ang.handler

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.AetherCoreManager
import com.v2ray.ang.core.AetherExit
import com.v2ray.ang.core.AetherIdentityManager
import com.v2ray.ang.core.AetherKeys
import com.v2ray.ang.core.AetherScanner
import com.v2ray.ang.dto.ConnectionTestResult
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.AetherFingerprint
import com.v2ray.ang.enums.AetherIpVersion
import com.v2ray.ang.enums.AetherKeyKind
import com.v2ray.ang.enums.AetherObfuscation
import com.v2ray.ang.enums.AetherProtocol
import com.v2ray.ang.enums.AetherPsiphon
import com.v2ray.ang.enums.AetherTor
import com.v2ray.ang.enums.AetherTransport
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.ui.server.AetherKeysRepository
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * State of real-time network traffic through the VPN tunnel
 */
data class HamedTrafficState(
    val downloadSpeedStr: String = "0 B/s",
    val uploadSpeedStr: String = "0 B/s",
    val downloadSpeedBytes: Long = 0L,
    val uploadSpeedBytes: Long = 0L,
    val totalDownloadStr: String = "0 B",
    val totalUploadStr: String = "0 B",
)

/**
 * State of connection info (IP & Flag & Ping)
 */
data class HamedConnectionInfo(
    val ip: String = "---",
    val country: String = "---",
    val flagEmoji: String = "🌐",
    val carrierFlagEmoji: String = "🇺🇸",
    val carrierName: String = "Cloudflare WARP",
    val isChained: Boolean = false,
    val pingMs: Long = -1L,
    val isTesting: Boolean = false,
    val testError: String? = null,
)

object HamedAetherManager {

    private const val TAG = "HamedAetherManager"
    const val DEFAULT_PROFILE_REMARKS = "Hamed Aether"

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _preparationStatus = MutableStateFlow<String?>(null)
    val preparationStatus: StateFlow<String?> = _preparationStatus.asStateFlow()

    private val _trafficState = MutableStateFlow(HamedTrafficState())
    val trafficState: StateFlow<HamedTrafficState> = _trafficState.asStateFlow()

    private val _connectionInfo = MutableStateFlow(HamedConnectionInfo())
    val connectionInfo: StateFlow<HamedConnectionInfo> = _connectionInfo.asStateFlow()

    private var accumulatedUpBytes = 0L
    private var accumulatedDownBytes = 0L

    fun setPreparationStatus(status: String?) {
        _preparationStatus.value = status
    }

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

        // Create a new Hamed Aether profile with optimal defaults
        val profile = ProfileItem.create(EConfigType.AETHER).apply {
            remarks = DEFAULT_PROFILE_REMARKS
            server = ""
            serverPort = "2408"
            aetherProtocol = AetherProtocol.MASQUE.type
            aetherTransport = AetherTransport.HTTP2.type
            aetherMasqueSni = AppConfig.AETHER_MASQUE_SNI
            aetherObfuscation = AetherObfuscation.AUTO.type
            aetherIpVersion = AetherIpVersion.V4.type
            aetherPsiphon = AetherPsiphon.OFF.type
            aetherTor = AetherTor.OFF.type
            aetherFingerprint = "chrome"
            aetherFragment = true
            aetherFragmentSize = "10-30"
            aetherFragmentDelay = "10-20"
            aetherScanMode = "balanced"
        }
        applySettingsToProfile(profile)

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
                server = ""
                serverPort = "2408"
                aetherProtocol = AetherProtocol.MASQUE.type
                aetherTransport = AetherTransport.HTTP2.type
                aetherMasqueSni = AppConfig.AETHER_MASQUE_SNI
                aetherObfuscation = AetherObfuscation.AUTO.type
                aetherIpVersion = AetherIpVersion.V4.type
                aetherPsiphon = AetherPsiphon.OFF.type
                aetherTor = AetherTor.OFF.type
                aetherFingerprint = "chrome"
                aetherFragment = true
                aetherFragmentSize = "10-30"
                aetherFragmentDelay = "10-20"
                aetherScanMode = "balanced"
            }
            applySettingsToProfile(profile)
            MmkvManager.encodeServerConfig(guid, profile)
        }
        return Pair(guid, profile)
    }

    /**
     * Applies global Masque and WireGuard settings configured in SettingsActivity to the given profile.
     */
    fun applySettingsToProfile(profile: ProfileItem) {
        val transport = MmkvManager.decodeSettingsString(AppConfig.PREF_AETHER_TRANSPORT, AetherTransport.HTTP2.type) ?: AetherTransport.HTTP2.type
        val scanMode = MmkvManager.decodeSettingsString(AppConfig.PREF_AETHER_SCAN_MODE, "balanced")
            ?: MmkvManager.decodeSettingsString(AppConfig.PREF_AETHER_MASQUE_SCAN_MODE, "balanced") ?: "balanced"
        val obfs = MmkvManager.decodeSettingsString(AppConfig.PREF_AETHER_OBFUSCATION_OVERRIDE, AetherObfuscation.AUTO.type) ?: AetherObfuscation.AUTO.type
        val finalMask = MmkvManager.decodeSettingsString(AppConfig.PREF_AETHER_FINAL_MASK, "")
        val dialMode = MmkvManager.decodeSettingsString(AppConfig.PREF_AETHER_DIAL_MODE, "")

        val sni = MmkvManager.decodeSettingsString(AppConfig.PREF_AETHER_MASQUE_SNI_OVERRIDE, AppConfig.AETHER_MASQUE_SNI) ?: AppConfig.AETHER_MASQUE_SNI
        val fragment = MmkvManager.decodeSettingsBool(AppConfig.PREF_AETHER_FRAGMENT_ENABLED, true)
        val fragmentSize = MmkvManager.decodeSettingsString(AppConfig.PREF_AETHER_FRAGMENT_SIZE_OVERRIDE, "10-30") ?: "10-30"
        val fragmentDelay = MmkvManager.decodeSettingsString(AppConfig.PREF_AETHER_FRAGMENT_DELAY_OVERRIDE, "10-20") ?: "10-20"
        val ech = MmkvManager.decodeSettingsBool(AppConfig.PREF_AETHER_ECH_ENABLED, false)

        profile.aetherTransport = transport
        profile.aetherScanMode = scanMode
        profile.aetherObfuscation = obfs
        profile.aetherMasqueSni = sni
        profile.aetherFragment = fragment
        profile.aetherFragmentSize = fragmentSize
        profile.aetherFragmentDelay = fragmentDelay
        profile.aetherEch = ech
        if (!finalMask.isNullOrBlank()) {
            profile.finalMask = finalMask.trim()
        }
        if (!dialMode.isNullOrBlank()) {
            profile.dialMode = dialMode.trim()
        }
    }

    /**
     * Updates the protocol of the active Aether profile.
     */
    fun updateProtocol(protocol: AetherProtocol): ProfileItem {
        val (guid, profile) = getActiveAetherProfile()
        profile.aetherProtocol = protocol.type
        // Reset endpoints when switching to two-hop or single-hop to ensure fresh scanning
        if (protocol.twoHops) {
            profile.aetherWiwOuter = null
            profile.aetherWiwInner = null
        } else {
            profile.server = ""
        }
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
     * Updates the Psiphon exit country/region code (e.g. "US", "DE", "GB", or empty for auto).
     */
    fun updatePsiphonRegion(region: String): ProfileItem {
        val (guid, profile) = getActiveAetherProfile()
        profile.aetherPsiphonRegion = region.trim().uppercase().ifEmpty { null }
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
     * Prepares keys and endpoints before starting VPN service.
     * Checks if WARP keys exist, registers them if missing, and scans for working endpoints.
     */
    fun prepareAndConnect(
        context: Context,
        onStatusChange: (String) -> Unit,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        scope.launch {
            try {
                val (guid, profile) = getActiveAetherProfile()
                applySettingsToProfile(profile)
                MmkvManager.encodeServerConfig(guid, profile)
                val protocol = AetherProtocol.fromString(profile.aetherProtocol)

                // 1. Verify and obtain keys if missing
                val neededFiles = AetherIdentityManager.filesOf(protocol)
                val missingFiles = AetherIdentityManager.missing(context, neededFiles)
                if (missingFiles.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        onStatusChange("در حال ثبت و دریافت کلیدهای WARP...")
                    }
                    val repo = AetherKeysRepository(context)
                    val settings = repo.loadSettings()
                    val keyKind = when (protocol) {
                        AetherProtocol.WIREGUARD -> AetherKeyKind.WIREGUARD
                        AetherProtocol.MASQUE -> AetherKeyKind.MASQUE
                        AetherProtocol.GOOL -> AetherKeyKind.GOOL
                        AetherProtocol.MIM -> AetherKeyKind.MIM
                        AetherProtocol.WG_OVER_MASQUE -> AetherKeyKind.WG_OVER_MASQUE
                    }
                    val updatedSettings = settings.copy(
                        kind = keyKind,
                        fragment = true,
                        fragmentSize = if (settings.fragmentSize.isBlank()) "10-30" else settings.fragmentSize,
                        fragmentDelay = if (settings.fragmentDelay.isBlank()) "10-20" else settings.fragmentDelay,
                        fingerprint = AetherFingerprint.CHROME
                    )
                    repo.saveSettings(updatedSettings)
                    val renewArgs = AetherKeys.arguments(updatedSettings)

                    LogUtil.i(TAG, "Renewing keys for $keyKind...")
                    val keys = AetherIdentityManager.renew(context, keyKind, renewArgs, updatedSettings.exit) { line ->
                        LogUtil.d(TAG, "Key renewal: $line")
                    }

                    val stillMissing = AetherIdentityManager.missing(context, neededFiles)
                    if (stillMissing.isNotEmpty() && keys == null) {
                        LogUtil.w(TAG, "Key registration could not complete, missing: $stillMissing")
                    }
                }

                // 2. Check if clean endpoint scan is needed
                val needsScan = if (protocol.twoHops) {
                    profile.aetherWiwOuter.isNullOrBlank()
                } else {
                    profile.server.isNullOrBlank() || profile.server == "162.159.192.1"
                }

                if (needsScan) {
                    withContext(Dispatchers.Main) {
                        onStatusChange("در حال اسکن و یافتن آی‌پی تمیز...")
                    }
                    LogUtil.i(TAG, "Scanning clean endpoint for ${profile.aetherProtocol}...")
                    val scanResult = AetherScanner.scan(context, profile) { line ->
                        LogUtil.d(TAG, "AetherScan: $line")
                    }
                    if (scanResult != null) {
                        if (protocol.twoHops) {
                            profile.aetherWiwOuter = scanResult.endpoint.toString()
                            scanResult.innerHop?.let { profile.aetherWiwInner = it.toString() }
                        } else {
                            profile.server = scanResult.endpoint.host
                            profile.serverPort = scanResult.endpoint.port.toString()
                        }
                        MmkvManager.encodeServerConfig(guid, profile)
                        LogUtil.i(TAG, "Successfully scanned endpoint: ${scanResult.endpoint}")
                    } else {
                        LogUtil.w(TAG, "Scan returned null, relying on core auto-discovery")
                    }
                }

                withContext(Dispatchers.Main) {
                    onStatusChange("در حال اتصال...")
                    onSuccess(guid)
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "prepareAndConnect error", e)
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "خطا در برقراری ارتباط")
                }
            }
        }
    }

    /**
     * Updates real traffic speeds directly from the VPN daemon's outbound statistics.
     */
    fun updateTrafficFromCore(upSpeed: Long, downSpeed: Long, upBytes: Long, downBytes: Long) {
        accumulatedUpBytes += upBytes
        accumulatedDownBytes += downBytes

        _trafficState.value = HamedTrafficState(
            downloadSpeedStr = formatSpeed(downSpeed),
            uploadSpeedStr = formatSpeed(upSpeed),
            downloadSpeedBytes = downSpeed,
            uploadSpeedBytes = upSpeed,
            totalDownloadStr = formatDataSize(accumulatedDownBytes),
            totalUploadStr = formatDataSize(accumulatedUpBytes)
        )
    }

    /**
     * Resets speeds to zero when VPN is disconnected or stopped.
     */
    fun resetTraffic() {
        _trafficState.value = _trafficState.value.copy(
            downloadSpeedStr = "0 B/s",
            uploadSpeedStr = "0 B/s",
            downloadSpeedBytes = 0L,
            uploadSpeedBytes = 0L
        )
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
     * Fast and smart IP detection via Cloudflare trace endpoint.
     * Detects public IP, country code, emoji flag, and ping latency instantly.
     */
    fun fetchFastPublicIp(onComplete: ((HamedConnectionInfo) -> Unit)? = null) {
        scope.launch {
            _connectionInfo.value = _connectionInfo.value.copy(isTesting = true)
            val startTime = System.currentTimeMillis()
            var detected = false

            val endpoints = listOf(
                "https://1.1.1.1/cdn-cgi/trace",
                "https://1.0.0.1/cdn-cgi/trace",
                "https://cloudflare.com/cdn-cgi/trace",
                "http://ip-api.com/line?fields=query,countryCode"
            )

            for (endpoint in endpoints) {
                if (detected) break
                try {
                    val url = java.net.URL(endpoint)
                    val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                        connectTimeout = 2000
                        readTimeout = 2000
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "Mozilla/5.0 HamedAether")
                    }
                    val code = conn.responseCode
                    val latency = System.currentTimeMillis() - startTime
                    if (code == 200) {
                        val body = conn.inputStream.bufferedReader().readText()
                        var ip = ""
                        var loc = ""
                        if (endpoint.contains("ip-api")) {
                            val lines = body.lines().filter { it.isNotBlank() }
                            ip = lines.getOrNull(0)?.trim().orEmpty()
                            loc = lines.getOrNull(1)?.trim().orEmpty()
                        } else {
                            for (line in body.lines()) {
                                if (line.startsWith("ip=")) ip = line.substring(3).trim()
                                if (line.startsWith("loc=")) loc = line.substring(4).trim()
                            }
                        }
                        if (ip.isNotBlank() && ip.contains(".")) {
                            val (_, profile) = getActiveAetherProfile()
                            val isChained = AetherPsiphon.fromString(profile.aetherPsiphon) != AetherPsiphon.OFF ||
                                            AetherTor.fromString(profile.aetherTor) != AetherTor.OFF

                            val exitFlag = countryCodeToEmojiFlag(loc)
                            val info = HamedConnectionInfo(
                                ip = ip,
                                country = loc.ifBlank { "OK" },
                                flagEmoji = exitFlag,
                                carrierFlagEmoji = "🇺🇸",
                                carrierName = "Cloudflare WARP",
                                isChained = isChained,
                                pingMs = latency,
                                isTesting = false,
                                testError = null
                            )
                            _connectionInfo.value = info
                            onComplete?.invoke(info)
                            detected = true
                            break
                        }
                    }
                } catch (e: Exception) {
                    LogUtil.d(TAG, "Fast probe $endpoint error: ${e.message}")
                }
            }

            if (!detected && _connectionInfo.value.ip == "---") {
                _connectionInfo.value = _connectionInfo.value.copy(
                    isTesting = false,
                    testError = "در حال شناسایی..."
                )
            }
        }
    }

    /**
     * Updates the connection info based on a real ConnectionTestResult from CoreServiceManager.
     */
    fun updateFromTestResult(result: ConnectionTestResult) {
        val isSuccessful = result.delayMillis >= 0
        val (_, profile) = getActiveAetherProfile()
        val isChained = AetherPsiphon.fromString(profile.aetherPsiphon) != AetherPsiphon.OFF ||
                        AetherTor.fromString(profile.aetherTor) != AetherTor.OFF
        val flag = if (isSuccessful) countryCodeToEmojiFlag(result.country) else "🌐"
        val country = if (isSuccessful) (result.country ?: "---") else "---"
        val ip = if (isSuccessful) (result.ipAddress ?: "---") else "---"

        _connectionInfo.value = HamedConnectionInfo(
            ip = ip,
            country = country,
            flagEmoji = flag,
            carrierFlagEmoji = "🇺🇸",
            carrierName = "Cloudflare WARP",
            isChained = isChained,
            pingMs = result.delayMillis,
            isTesting = false,
            testError = if (!isSuccessful) result.errorMessage.ifBlank { "قطع اتصال" } else null
        )
    }

    fun resetConnectionInfo() {
        _connectionInfo.value = HamedConnectionInfo()
    }

    fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec < 1024 -> "$bytesPerSec B/s"
            bytesPerSec < 1024 * 1024 -> "${bytesPerSec / 1024} KB/s"
            else -> String.format(java.util.Locale.US, "%.1f MB/s", bytesPerSec / (1024.0 * 1024.0))
        }
    }

    fun formatDataSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}

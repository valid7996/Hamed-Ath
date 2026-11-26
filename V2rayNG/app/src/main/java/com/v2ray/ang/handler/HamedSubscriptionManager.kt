package com.v2ray.ang.handler

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.HamedSubscriptionChain
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.enums.AetherPsiphon
import com.v2ray.ang.enums.AetherTor
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.ui.main.HamedChainType
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The four connection sources available on the Home Connection Dashboard.
 */
enum class HamedDashboardSource(
    val storageId: String,
    val subRemark: String,
    val subUrl: String,
) {
    AETHER(
        storageId = "aether",
        subRemark = "Hamed Aether",
        subUrl = "",
    ),
    SERVERLESS(
        storageId = "serverless",
        subRemark = "Serverless",
        subUrl = "https://raw.githubusercontent.com/patterniha/Serverless-for-Iran/refs/heads/main/Subscription/Serverless-for-Iran.json#Patterniha-S",
    ),
    MITM(
        storageId = "mitm",
        subRemark = "MITM-DomainFronting",
        subUrl = "https://raw.githubusercontent.com/patterniha/MITM-DomainFronting/refs/heads/main/Subscription/MITM-DomainFronting.json#Patterniha-M",
    ),
    PATTERNIHA(
        storageId = "patterniha",
        subRemark = "RobinHood",
        subUrl = "https://raw.githubusercontent.com/patterniha/Free-Configs/main/configs.txt",
    );

    companion object {
        val subscriptionSources: List<HamedDashboardSource> = listOf(SERVERLESS, MITM, PATTERNIHA)

        fun fromStorageId(id: String?): HamedDashboardSource =
            entries.firstOrNull { it.storageId.equals(id, ignoreCase = true) } ?: AETHER
    }
}

/**
 * Lightweight summary of a profile belonging to one of Hamed's synced dashboard subscriptions.
 */
data class HamedSubServerSummary(
    val guid: String,
    val remarks: String,
    val subtitle: String,
    val configType: EConfigType,
    val pingMillis: Long,
)

/**
 * Manages the 3 built-in dashboard subscriptions (`Serverless`, `MITM-DomainFronting`, `Patterniha`),
 * keeps them synced inside MMKV's subscription & server stores, persists per-source selection and
 * Psiphon / Tor chaining settings, and coordinates background synchronization with the UI.
 */
object HamedSubscriptionManager {

    private const val TAG = AppConfig.TAG

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private var syncJob: Job? = null

    private val subIdCache = HashMap<HamedDashboardSource, String>()

    private val _activeSource = MutableStateFlow(HamedDashboardSource.AETHER)
    val activeSource: StateFlow<HamedDashboardSource> = _activeSource.asStateFlow()

    private val _sourceServers = MutableStateFlow<Map<HamedDashboardSource, List<HamedSubServerSummary>>>(emptyMap())
    val sourceServers: StateFlow<Map<HamedDashboardSource, List<HamedSubServerSummary>>> = _sourceServers.asStateFlow()

    private val _selectedGuidBySource = MutableStateFlow<Map<HamedDashboardSource, String?>>(emptyMap())
    val selectedGuidBySource: StateFlow<Map<HamedDashboardSource, String?>> = _selectedGuidBySource.asStateFlow()

    private val _chainBySource = MutableStateFlow<Map<HamedDashboardSource, HamedChainType>>(emptyMap())
    val chainBySource: StateFlow<Map<HamedDashboardSource, HamedChainType>> = _chainBySource.asStateFlow()

    private val _autoModeBySource = MutableStateFlow<Map<HamedDashboardSource, Boolean>>(emptyMap())
    val autoModeBySource: StateFlow<Map<HamedDashboardSource, Boolean>> = _autoModeBySource.asStateFlow()

    private val _psiphonRegion = MutableStateFlow("")
    val psiphonRegion: StateFlow<String> = _psiphonRegion.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _syncStatusError = MutableStateFlow<String?>(null)
    val syncStatusError: StateFlow<String?> = _syncStatusError.asStateFlow()

    /** Ensures built-in subscriptions exist in MMKV and refreshes in-memory dashboard state. */
    fun initialize(context: Context? = null, syncEmptyInBackground: Boolean = true): HamedDashboardSource {
        context?.let {
            HamedMitmCertGenerator.ensureCertFiles(it)
        }
        ensureBuiltInSubscriptions()
        loadPersistedState()
        refreshServerLists()
        if (syncEmptyInBackground) {
            val emptySources = HamedDashboardSource.subscriptionSources.filter { source ->
                _sourceServers.value[source].orEmpty().isEmpty()
            }
            if (emptySources.isNotEmpty()) {
                launchSync(sources = emptySources, force = false)
            }
        }
        return _activeSource.value
    }

    /** Returns the persisted active dashboard source. */
    fun getActiveSource(): HamedDashboardSource {
        val stored = runCatching {
            MmkvManager.decodeSettingsString(AppConfig.PREF_HAMED_DASHBOARD_SOURCE, HamedDashboardSource.AETHER.storageId)
        }.getOrNull()
        return HamedDashboardSource.fromStorageId(stored)
    }

    /** Creates or reconciles the 3 built-in subscriptions inside [MmkvManager]. */
    @Synchronized
    fun ensureBuiltInSubscriptions(): Map<HamedDashboardSource, String> {
        val existing = MmkvManager.decodeSubscriptions()
        val mapping = HashMap<HamedDashboardSource, String>()

        for (source in HamedDashboardSource.subscriptionSources) {
            val baseUrl = source.subUrl.substringBefore('#')
            val found = existing.firstOrNull { cache ->
                val url = cache.subscription.url.trim()
                val remarks = cache.subscription.remarks.trim()
                url.equals(source.subUrl, ignoreCase = true) ||
                    url.substringBefore('#').equals(baseUrl, ignoreCase = true) ||
                    remarks.equals(source.subRemark, ignoreCase = true) ||
                    remarks.equals("[${source.subRemark}", ignoreCase = true) ||
                    (source == HamedDashboardSource.PATTERNIHA && remarks.equals("Patterniha", ignoreCase = true))
            }
            if (found != null) {
                var changed = false
                if (found.subscription.url != source.subUrl) {
                    found.subscription.url = source.subUrl
                    changed = true
                }
                if (found.subscription.remarks != source.subRemark) {
                    found.subscription.remarks = source.subRemark
                    changed = true
                }
                if (!found.subscription.enabled) {
                    found.subscription.enabled = true
                    changed = true
                }
                if (!found.subscription.autoUpdate) {
                    found.subscription.autoUpdate = true
                    changed = true
                }
                if (changed) {
                    MmkvManager.tryEncodeSubscription(found.guid, found.subscription)
                }
                mapping[source] = found.guid
            } else {
                val item = SubscriptionItem(
                    remarks = source.subRemark,
                    url = source.subUrl,
                    enabled = true,
                    autoUpdate = true,
                )
                val createdGuid = MmkvManager.tryEncodeSubscription("", item)
                if (!createdGuid.isNullOrBlank()) {
                    mapping[source] = createdGuid
                }
            }
        }

        subIdCache.clear()
        subIdCache.putAll(mapping)
        return mapping
    }

    /** Finds which built-in subscription [profile] belongs to, if any. */
    fun resolveSourceForProfile(profile: ProfileItem?): HamedDashboardSource? {
        if (profile == null) return null
        if (profile.configType == EConfigType.AETHER) return HamedDashboardSource.AETHER
        val subId = profile.subscriptionId
        if (subId.isBlank()) return null
        if (subIdCache.isEmpty()) {
            runCatching { ensureBuiltInSubscriptions() }
        }
        return subIdCache.entries.firstOrNull { it.value == subId }?.key
    }

    /** Returns the [HamedChainType] stored for [source]. */
    fun getChain(source: HamedDashboardSource): HamedChainType {
        if (source == HamedDashboardSource.AETHER) {
            val (_, profile) = HamedAetherManager.getActiveAetherProfile()
            return when {
                AetherTor.fromString(profile.aetherTor) != AetherTor.OFF -> HamedChainType.TOR
                AetherPsiphon.fromString(profile.aetherPsiphon) != AetherPsiphon.OFF -> HamedChainType.PSIPHON
                else -> HamedChainType.DEFAULT
            }
        }
        val defaultChain = defaultChainFor(source)
        val raw = runCatching {
            MmkvManager.decodeSettingsString(AppConfig.PREF_HAMED_SUB_CHAIN_PREFIX + source.storageId, defaultChain.name)
        }.getOrNull()
        return parseChainType(raw, fallback = defaultChain)
    }

    /** Persists [chain] for [source] and updates observables. */
    fun setChain(source: HamedDashboardSource, chain: HamedChainType) {
        if (source == HamedDashboardSource.AETHER) {
            val psiphon = if (chain == HamedChainType.PSIPHON) AetherPsiphon.CHAIN else AetherPsiphon.OFF
            val tor = if (chain == HamedChainType.TOR) AetherTor.CHAIN else AetherTor.OFF
            HamedAetherManager.updateChain(psiphon, tor)
        } else {
            MmkvManager.encodeSettings(AppConfig.PREF_HAMED_SUB_CHAIN_PREFIX + source.storageId, chain.name)
        }
        _chainBySource.value = _chainBySource.value.toMutableMap().apply { put(source, chain) }
    }

    /** Returns the shared Psiphon exit country/region code (empty = Auto). */
    fun getPsiphonRegion(): String {
        val stored = runCatching {
            MmkvManager.decodeSettingsString(AppConfig.PREF_HAMED_SUB_PSIPHON_REGION, null)
        }.getOrNull()
        if (stored != null) return stored
        return runCatching {
            HamedAetherManager.getActiveAetherProfile().second.aetherPsiphonRegion.orEmpty()
        }.getOrDefault("")
    }

    /** Updates the Psiphon exit country/region for both Aether and synced subscriptions. */
    fun setPsiphonRegion(region: String) {
        val clean = region.trim().uppercase()
        MmkvManager.encodeSettings(AppConfig.PREF_HAMED_SUB_PSIPHON_REGION, clean)
        HamedAetherManager.updatePsiphonRegion(clean)
        _psiphonRegion.value = clean
    }

    /**
     * Resolves chain & Psiphon region for [profile] if it belongs to one of the 3 synced
     * dashboard subscriptions; `null` for Aether or unrelated profiles.
     */
    fun getChainSelectionForProfile(profile: ProfileItem?): HamedSubscriptionChain.ChainSelection? {
        val source = resolveSourceForProfile(profile) ?: return null
        if (source == HamedDashboardSource.AETHER) return null
        return HamedSubscriptionChain.ChainSelection(
            chain = getChain(source),
            psiphonRegion = getPsiphonRegion(),
        )
    }

    /** Reloads cached server lists and selections for all 3 synced subscriptions from MMKV. */
    fun refreshServerLists() {
        val mapping = if (subIdCache.isEmpty()) ensureBuiltInSubscriptions() else subIdCache.toMap()
        val serversMap = HashMap<HamedDashboardSource, List<HamedSubServerSummary>>()
        val selectedMap = HashMap<HamedDashboardSource, String?>()

        for (source in HamedDashboardSource.subscriptionSources) {
            val subId = mapping[source]
            val items = if (!subId.isNullOrBlank()) {
                MmkvManager.decodeServerList(subId).mapNotNull { guid ->
                    val cfg = MmkvManager.decodeServerConfig(guid) ?: return@mapNotNull null
                    val ping = MmkvManager.decodeServerAffiliationInfo(guid)?.testDelayMillis ?: 0L
                    HamedSubServerSummary(
                        guid = guid,
                        remarks = cfg.remarks.ifBlank { cfg.server.orEmpty().ifBlank { source.subRemark } },
                        subtitle = formatServerSubtitle(source, cfg),
                        configType = cfg.configType,
                        pingMillis = ping,
                    )
                }
            } else {
                emptyList()
            }
            serversMap[source] = items

            val storedGuid = MmkvManager.decodeSettingsString(AppConfig.PREF_HAMED_SUB_SELECTED_GUID_PREFIX + source.storageId)
            val resolvedGuid = items.firstOrNull { it.guid == storedGuid }?.guid ?: items.firstOrNull()?.guid
            selectedMap[source] = resolvedGuid
            if (resolvedGuid != null && resolvedGuid != storedGuid) {
                MmkvManager.encodeSettings(AppConfig.PREF_HAMED_SUB_SELECTED_GUID_PREFIX + source.storageId, resolvedGuid)
            }
        }

        _sourceServers.value = serversMap
        _selectedGuidBySource.value = selectedMap
        refreshChainState()
        refreshAutoModeState()
    }

    /** Whether [source] automatically tests configs in batches of 5 on connect. */
    fun defaultAutoFor(source: HamedDashboardSource): Boolean = source == HamedDashboardSource.PATTERNIHA

    /** Returns `true` when [source] should auto-select the best responding server in batches of 5. */
    fun isAutoMode(source: HamedDashboardSource): Boolean {
        if (source == HamedDashboardSource.AETHER) return false
        val defaultAuto = defaultAutoFor(source)
        return runCatching {
            MmkvManager.decodeSettingsBool(AppConfig.PREF_HAMED_SUB_AUTO_PREFIX + source.storageId, defaultAuto)
        }.getOrDefault(defaultAuto)
    }

    /** Enables or disables automatic 5-batch real-ping selection for [source]. */
    fun setAutoMode(source: HamedDashboardSource, enabled: Boolean) {
        if (source == HamedDashboardSource.AETHER) return
        MmkvManager.encodeSettings(AppConfig.PREF_HAMED_SUB_AUTO_PREFIX + source.storageId, enabled)
        _autoModeBySource.value = _autoModeBySource.value.toMutableMap().apply { put(source, enabled) }
    }

    /** Orders subscription servers for batch-of-5 testing: previously responding fastest first, then untested, then failed. */
    fun orderedAutoCandidates(servers: List<HamedSubServerSummary>): List<HamedSubServerSummary> {
        val positive = servers.filter { it.pingMillis > 0L }.sortedBy { it.pingMillis }
        val untested = servers.filter { it.pingMillis == 0L }
        val failed = servers.filter { it.pingMillis < 0L }
        return positive + untested + failed
    }

    /** Records the winning [guid] chosen by automatic batch probing without leaving auto mode. */
    fun applyAutoSelectedServer(source: HamedDashboardSource, guid: String) {
        if (source == HamedDashboardSource.AETHER) return
        MmkvManager.encodeSettings(AppConfig.PREF_HAMED_SUB_SELECTED_GUID_PREFIX + source.storageId, guid)
        MmkvManager.setSelectServer(guid)
        _selectedGuidBySource.value = _selectedGuidBySource.value.toMutableMap().apply { put(source, guid) }
        refreshServerLists()
    }

    /** Switches dashboard mode to [source] and returns the active profile GUID if available. */
    fun selectSource(source: HamedDashboardSource): String? {
        MmkvManager.encodeSettings(AppConfig.PREF_HAMED_DASHBOARD_SOURCE, source.storageId)
        _activeSource.value = source

        if (source == HamedDashboardSource.AETHER) {
            val aetherGuid = HamedAetherManager.ensureAetherProfile()
            MmkvManager.setSelectServer(aetherGuid)
            return aetherGuid
        }

        refreshServerLists()
        val chosenGuid = _selectedGuidBySource.value[source]
        if (!chosenGuid.isNullOrBlank()) {
            MmkvManager.setSelectServer(chosenGuid)
        } else if (syncJob?.isActive != true) {
            // If this source hasn't been fetched yet, sync it in background immediately.
            launchSync(sources = listOf(source), force = false)
        }
        return chosenGuid
    }

    /** Selects a specific profile [guid] inside subscription [source], switching that source to manual choice. */
    fun selectServer(source: HamedDashboardSource, guid: String) {
        if (source == HamedDashboardSource.AETHER) {
            selectSource(HamedDashboardSource.AETHER)
            return
        }
        setAutoMode(source, false)
        MmkvManager.encodeSettings(AppConfig.PREF_HAMED_DASHBOARD_SOURCE, source.storageId)
        MmkvManager.encodeSettings(AppConfig.PREF_HAMED_SUB_SELECTED_GUID_PREFIX + source.storageId, guid)
        _activeSource.value = source
        _selectedGuidBySource.value = _selectedGuidBySource.value.toMutableMap().apply { put(source, guid) }
        MmkvManager.setSelectServer(guid)
    }

    /**
     * Synchronizes [sources] in background; invokes [onFinished] on the Main thread when complete.
     */
    fun launchSync(
        sources: List<HamedDashboardSource> = HamedDashboardSource.subscriptionSources,
        force: Boolean = true,
        onFinished: ((String?) -> Unit)? = null,
    ) {
        if (syncJob?.isActive == true && !force) return
        if (force) syncJob?.cancel()
        syncJob = scope.launch {
            val activeSelectedGuid = syncBlocking(sources = sources, force = force)
            onFinished?.let { cb ->
                withContext(Dispatchers.Main) { cb(activeSelectedGuid) }
            }
        }
    }

    /**
     * Synchronizes [sources] synchronously on a background thread. Preserves the user's selected
     * server by remarks across subscription re-imports and returns the active profile GUID if any.
     */
    suspend fun syncBlocking(
        sources: List<HamedDashboardSource> = HamedDashboardSource.subscriptionSources,
        force: Boolean = true,
    ): String? = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            _syncing.value = true
            _syncStatusError.value = null
            try {
                val mapping = ensureBuiltInSubscriptions()
                val allSubs = MmkvManager.decodeSubscriptions().associateBy { it.guid }
                var anyFailed = false

                for (source in sources) {
                    if (source == HamedDashboardSource.AETHER) continue
                    val subId = mapping[source] ?: continue
                    val cache: SubscriptionCache = allSubs[subId] ?: continue

                    val currentList = MmkvManager.decodeServerList(subId)
                    if (!force && currentList.isNotEmpty()) continue

                    val prevGuid = MmkvManager.decodeSettingsString(AppConfig.PREF_HAMED_SUB_SELECTED_GUID_PREFIX + source.storageId)
                    val prevRemarks = prevGuid?.let { MmkvManager.decodeServerConfig(it)?.remarks }

                    val result = runCatching { AngConfigManager.updateConfigViaSub(cache) }
                        .onFailure { LogUtil.w(TAG, "Subscription sync exception for ${source.subRemark}", it) }
                        .getOrNull()

                    if (result == null || result.failureCount > 0 || result.configCount <= 0) {
                        anyFailed = true
                    } else {
                        val updatedList = MmkvManager.decodeServerList(subId)
                        val preserved = prevRemarks?.let { targetRemark ->
                            updatedList.firstOrNull { g ->
                                MmkvManager.decodeServerConfig(g)?.remarks == targetRemark
                            }
                        } ?: updatedList.firstOrNull()
                        if (!preserved.isNullOrBlank()) {
                            MmkvManager.encodeSettings(AppConfig.PREF_HAMED_SUB_SELECTED_GUID_PREFIX + source.storageId, preserved)
                        }
                    }
                }

                refreshServerLists()
                val currentActive = getActiveSource()
                _activeSource.value = currentActive
                val activeGuid = if (currentActive == HamedDashboardSource.AETHER) {
                    null
                } else {
                    _selectedGuidBySource.value[currentActive]?.also { g ->
                        MmkvManager.setSelectServer(g)
                    }
                }
                if (anyFailed) {
                    _syncStatusError.value = "sync_partial"
                }
                activeGuid
            } finally {
                _syncing.value = false
            }
        }
    }

    /** Ensures the currently selected server GUID matches MMKV's selected dashboard source. */
    fun syncSelectionFromExternalGuid(guid: String?) {
        if (guid.isNullOrBlank()) return
        val profile = MmkvManager.decodeServerConfig(guid) ?: return
        val source = resolveSourceForProfile(profile) ?: return
        MmkvManager.encodeSettings(AppConfig.PREF_HAMED_DASHBOARD_SOURCE, source.storageId)
        _activeSource.value = source
        if (source != HamedDashboardSource.AETHER) {
            MmkvManager.encodeSettings(AppConfig.PREF_HAMED_SUB_SELECTED_GUID_PREFIX + source.storageId, guid)
            _selectedGuidBySource.value = _selectedGuidBySource.value.toMutableMap().apply { put(source, guid) }
        }
        refreshServerLists()
    }

    private fun loadPersistedState() {
        _activeSource.value = getActiveSource()
        _psiphonRegion.value = getPsiphonRegion()
        refreshChainState()
        refreshAutoModeState()
    }

    private fun refreshChainState() {
        val chains = HashMap<HamedDashboardSource, HamedChainType>()
        HamedDashboardSource.entries.forEach { source ->
            chains[source] = getChain(source)
        }
        _chainBySource.value = chains
    }

    private fun refreshAutoModeState() {
        val autos = HashMap<HamedDashboardSource, Boolean>()
        HamedDashboardSource.entries.forEach { source ->
            autos[source] = isAutoMode(source)
        }
        _autoModeBySource.value = autos
    }

    private fun defaultChainFor(source: HamedDashboardSource): HamedChainType = when (source) {
        HamedDashboardSource.AETHER,
        HamedDashboardSource.SERVERLESS,
        HamedDashboardSource.MITM, -> HamedChainType.PSIPHON
        HamedDashboardSource.PATTERNIHA -> HamedChainType.DEFAULT
    }

    private fun parseChainType(raw: String?, fallback: HamedChainType): HamedChainType =
        HamedChainType.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: fallback

    private fun formatServerSubtitle(source: HamedDashboardSource, profile: ProfileItem): String {
        return when (source) {
            HamedDashboardSource.SERVERLESS -> "Serverless • Xray Custom"
            HamedDashboardSource.MITM -> "MITM • Domain Fronting"
            HamedDashboardSource.PATTERNIHA -> {
                val proto = profile.configType.name
                val network = profile.network?.uppercase().orEmpty()
                if (network.isNotBlank()) "$proto • $network" else proto
            }
            HamedDashboardSource.AETHER -> profile.configType.name
        }
    }
}

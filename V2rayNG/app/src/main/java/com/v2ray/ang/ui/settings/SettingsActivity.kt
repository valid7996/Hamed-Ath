package com.v2ray.ang.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.VPN
import com.v2ray.ang.R
import com.v2ray.ang.enums.AetherProtocol
import com.v2ray.ang.handler.HamedAetherManager
import com.v2ray.ang.ui.AboutActivity
import com.v2ray.ang.ui.perappproxy.PerAppProxyActivity
import com.v2ray.ang.ui.server.ServerAetherActivity
import com.v2ray.ang.ui.server.ServerAetherKeysActivity
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.MmkvManager.rememberMmkvBool
import com.v2ray.ang.handler.MmkvManager.rememberMmkvString
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.CollapsiblePreferenceGroupHeader
import com.v2ray.ang.ui.compose.HamedThemeColors
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.ui.compose.SettingsEditItem
import com.v2ray.ang.ui.compose.SettingsListItem
import com.v2ray.ang.ui.compose.SettingsMenuItem
import com.v2ray.ang.ui.compose.SettingsSwitchItem
import com.v2ray.ang.ui.compose.ThemeManager
import com.v2ray.ang.ui.compose.verticalScrollbar
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.launch

class SettingsActivity : BaseComponentActivity() {

    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.refreshSystemVpnSettingsAvailability()
            }
        }
    }

    private fun openSystemVpnSettings() {
        try {
            startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
        } catch (error: ActivityNotFoundException) {
            reportSystemVpnSettingsFailure(error)
        } catch (error: SecurityException) {
            reportSystemVpnSettingsFailure(error)
        }
    }

    private fun reportSystemVpnSettingsFailure(error: RuntimeException) {
        LogUtil.e(AppConfig.TAG, "Cannot open system VPN settings", error)
        toastError(R.string.toast_system_vpn_settings_unavailable)
    }

    @Composable
    override fun ScreenContent() {
        SettingsScreen(
            viewModel = viewModel,
            onBackClick = { finish() },
            onModeHelpClicked = { Utils.openUri(this, AppConfig.APP_WIKI_MODE) },
            onSystemVpnSettingsClicked = ::openSystemVpnSettings
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit,
    onModeHelpClicked: () -> Unit,
    onSystemVpnSettingsClicked: () -> Unit
) {
    val scrollState = rememberScrollState()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val systemVpnSettingsAvailable by viewModel.systemVpnSettingsAvailable.collectAsStateWithLifecycle()
    val aetherListenPort by viewModel.aetherListenPort.collectAsStateWithLifecycle()
    var masqueSettingsExpanded by rememberSaveable { mutableStateOf(true) }
    var wireguardSettingsExpanded by rememberSaveable { mutableStateOf(true) }
    var generalSettingsExpanded by rememberSaveable { mutableStateOf(true) }

    var aetherTransport by rememberMmkvString(AppConfig.PREF_AETHER_TRANSPORT, "h2")
    var masqueScanMode by rememberMmkvString(AppConfig.PREF_AETHER_MASQUE_SCAN_MODE, "balanced")
    var wgScanMode by rememberMmkvString(AppConfig.PREF_AETHER_WG_SCAN_MODE, "balanced")
    var masqueSni by rememberMmkvString(AppConfig.PREF_AETHER_MASQUE_SNI_OVERRIDE, AppConfig.AETHER_MASQUE_SNI)
    var aetherFragment by rememberMmkvBool(AppConfig.PREF_AETHER_FRAGMENT_ENABLED, true)
    var aetherFragmentSize by rememberMmkvString(AppConfig.PREF_AETHER_FRAGMENT_SIZE_OVERRIDE, "10-30")
    var aetherFragmentDelay by rememberMmkvString(AppConfig.PREF_AETHER_FRAGMENT_DELAY_OVERRIDE, "10-20")
    var aetherEch by rememberMmkvBool(AppConfig.PREF_AETHER_ECH_ENABLED, false)
    var aetherObfuscation by rememberMmkvString(AppConfig.PREF_AETHER_OBFUSCATION_OVERRIDE, "auto")

    fun syncAetherProfile() {
        val (guid, profile) = HamedAetherManager.getActiveAetherProfile()
        HamedAetherManager.applySettingsToProfile(profile)
        MmkvManager.encodeServerConfig(guid, profile)
    }

    val (_, initialActiveProf) = remember { HamedAetherManager.getActiveAetherProfile() }
    var masqueVariant by rememberSaveable {
        mutableStateOf(
            when (initialActiveProf.aetherProtocol) {
                AetherProtocol.MIM.type -> "mim"
                AetherProtocol.WG_OVER_MASQUE.type -> "wireguard-over-masque"
                else -> "masque"
            }
        )
    }

    var mode by rememberMmkvString(AppConfig.PREF_MODE, VPN)
    var buttonPosition by rememberMmkvString(AppConfig.PREF_HAMED_BUTTON_POSITION, "middle")
    var speedEnabled by rememberMmkvBool(AppConfig.PREF_SPEED_ENABLED, true)
    var language by remember {
        mutableStateOf(
            MmkvManager.decodeSettingsString(AppConfig.PREF_LANGUAGE, "auto") ?: "auto"
        )
    }
    var uiModeNight by rememberMmkvString(AppConfig.PREF_UI_MODE_NIGHT, "0")
    var dynamicColor by rememberMmkvBool(AppConfig.PREF_DYNAMIC_COLOR, true)

    val dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    LaunchedEffect(dynamicColorSupported) {
        if (!dynamicColorSupported && dynamicColor) {
            dynamicColor = false
            ThemeManager.setDynamicColorEnabled(false)
        }
    }

    val languageEntries = stringArrayResource(R.array.language_select).toList()
    val languageValues = stringArrayResource(R.array.language_select_value).toList()
    val uiModeNightEntries = stringArrayResource(R.array.ui_mode_night).toList()
    val uiModeNightValues = stringArrayResource(R.array.ui_mode_night_value).toList()
    val modeEntries = stringArrayResource(R.array.mode_entries).toList()
    val modeValues = stringArrayResource(R.array.mode_value).toList()
    val buttonPositionEntries = stringArrayResource(R.array.hamed_button_position_entries).toList()
    val buttonPositionValues = stringArrayResource(R.array.hamed_button_position_values).toList()
    val transportEntries = stringArrayResource(R.array.aether_transport_entries).toList()
    val transportValues = stringArrayResource(R.array.aether_transport_values).toList()
    val scanEntries = stringArrayResource(R.array.aether_scan_entries).toList()
    val scanValues = stringArrayResource(R.array.aether_scan_values).toList()
    val obfuscationEntries = stringArrayResource(R.array.aether_obfuscation_entries).toList()
    val obfuscationValues = stringArrayResource(R.array.aether_obfuscation_values).toList()
    val masqueVariantEntries = stringArrayResource(R.array.hamed_masque_protocol_entries).toList()
    val masqueVariantValues = stringArrayResource(R.array.hamed_masque_protocol_values).toList()

    Scaffold(
        containerColor = HamedThemeColors.Background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_settings),
                onBackClick = onBackClick,
                isLoading = isLoading
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScrollbar(scrollState)
                .verticalScroll(scrollState)
        ) {
            val context = LocalContext.current

            // ==========================================
            // بخش اول: تنظیمات Masque
            // ==========================================
            CollapsiblePreferenceGroupHeader(
                title = stringResource(R.string.hamed_section_masque),
                expanded = masqueSettingsExpanded,
                onExpandedChange = { masqueSettingsExpanded = it }
            )
            if (masqueSettingsExpanded) {
                SettingsListItem(
                    title = stringResource(R.string.hamed_masque_transport),
                    entries = transportEntries,
                    values = transportValues,
                    selectedValue = aetherTransport,
                    onSelected = {
                        aetherTransport = it
                        syncAetherProfile()
                    }
                )
                SettingsListItem(
                    title = stringResource(R.string.hamed_masque_scan_mode),
                    entries = scanEntries,
                    values = scanValues,
                    selectedValue = masqueScanMode,
                    onSelected = {
                        masqueScanMode = it
                        syncAetherProfile()
                    }
                )
                SettingsListItem(
                    title = stringResource(R.string.hamed_masque_protocol_variant),
                    entries = masqueVariantEntries,
                    values = masqueVariantValues,
                    selectedValue = masqueVariant,
                    onSelected = {
                        masqueVariant = it
                        val proto = when (it) {
                            "mim" -> AetherProtocol.MIM
                            "wireguard-over-masque" -> AetherProtocol.WG_OVER_MASQUE
                            else -> AetherProtocol.MASQUE
                        }
                        HamedAetherManager.updateProtocol(proto)
                    }
                )
                SettingsEditItem(
                    title = stringResource(R.string.hamed_masque_sni),
                    value = masqueSni,
                    onValueChanged = {
                        masqueSni = it
                        syncAetherProfile()
                    }
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.hamed_masque_fragment),
                    summary = stringResource(R.string.hamed_masque_fragment_summary),
                    checked = aetherFragment,
                    onCheckedChange = {
                        aetherFragment = it
                        syncAetherProfile()
                    }
                )
                if (aetherFragment) {
                    SettingsEditItem(
                        title = stringResource(R.string.hamed_masque_fragment_size),
                        value = aetherFragmentSize,
                        onValueChanged = {
                            aetherFragmentSize = it
                            syncAetherProfile()
                        }
                    )
                    SettingsEditItem(
                        title = stringResource(R.string.hamed_masque_fragment_delay),
                        value = aetherFragmentDelay,
                        keyboardNumber = true,
                        onValueChanged = {
                            aetherFragmentDelay = it
                            syncAetherProfile()
                        }
                    )
                }
                SettingsSwitchItem(
                    title = stringResource(R.string.hamed_masque_ech),
                    summary = stringResource(R.string.hamed_masque_ech_summary),
                    checked = aetherEch,
                    onCheckedChange = {
                        aetherEch = it
                        syncAetherProfile()
                    }
                )
            }

            // ==========================================
            // بخش دوم: تنظیمات وایرگارد (WireGuard)
            // ==========================================
            CollapsiblePreferenceGroupHeader(
                title = stringResource(R.string.hamed_section_wireguard),
                expanded = wireguardSettingsExpanded,
                onExpandedChange = { wireguardSettingsExpanded = it }
            )
            if (wireguardSettingsExpanded) {
                SettingsListItem(
                    title = stringResource(R.string.hamed_wg_scan_mode),
                    entries = scanEntries,
                    values = scanValues,
                    selectedValue = wgScanMode,
                    onSelected = {
                        wgScanMode = it
                        syncAetherProfile()
                    }
                )
                SettingsListItem(
                    title = stringResource(R.string.hamed_wg_obfuscation),
                    entries = obfuscationEntries,
                    values = obfuscationValues,
                    selectedValue = aetherObfuscation,
                    onSelected = {
                        aetherObfuscation = it
                        syncAetherProfile()
                    }
                )
                SettingsMenuItem(
                    icon = painterResource(R.drawable.ic_lock_24dp),
                    title = stringResource(R.string.hamed_wg_renew_keys),
                    subtitle = stringResource(R.string.hamed_wg_renew_keys_summary),
                    onClick = { context.startActivity(Intent(context, ServerAetherKeysActivity::class.java)) }
                )
            }

            // ==========================================
            // بخش سوم: تنظیمات عمومی و کاربری
            // ==========================================
            CollapsiblePreferenceGroupHeader(
                title = stringResource(R.string.hamed_section_general),
                expanded = generalSettingsExpanded,
                onExpandedChange = { generalSettingsExpanded = it }
            )
            if (generalSettingsExpanded) {
                SettingsListItem(
                    title = stringResource(R.string.hamed_button_position_title),
                    entries = buttonPositionEntries,
                    values = buttonPositionValues,
                    selectedValue = buttonPosition,
                    onSelected = { buttonPosition = it }
                )
                SettingsListItem(
                    title = stringResource(R.string.hamed_settings_mode_title),
                    entries = modeEntries,
                    values = modeValues,
                    selectedValue = mode,
                    onSelected = { mode = it }
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.hamed_general_speed),
                    summary = stringResource(R.string.hamed_general_speed_summary),
                    checked = speedEnabled,
                    onCheckedChange = { speedEnabled = it }
                )
                SettingsMenuItem(
                    icon = painterResource(R.drawable.ic_per_apps_24dp),
                    title = stringResource(R.string.hamed_settings_per_app),
                    subtitle = stringResource(R.string.title_pref_per_app_proxy),
                    onClick = { context.startActivity(Intent(context, PerAppProxyActivity::class.java)) }
                )
                SettingsListItem(
                    title = stringResource(R.string.title_language),
                    entries = languageEntries,
                    values = languageValues,
                    selectedValue = language,
                    onSelected = {
                        language = it
                        AppLocaleManager.setApplicationLanguage(it)
                    }
                )
                SettingsListItem(
                    title = stringResource(R.string.title_pref_ui_mode_night),
                    entries = uiModeNightEntries,
                    values = uiModeNightValues,
                    selectedValue = uiModeNight,
                    onSelected = {
                        uiModeNight = it
                        ThemeManager.setThemeMode(it)
                    }
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.title_pref_dynamic_color),
                    summary = stringResource(R.string.summary_pref_dynamic_color),
                    checked = dynamicColor,
                    enabled = dynamicColorSupported,
                    onCheckedChange = {
                        dynamicColor = it
                        ThemeManager.setDynamicColorEnabled(it)
                    }
                )
                SettingsMenuItem(
                    icon = painterResource(R.drawable.ic_routing_24dp),
                    title = stringResource(R.string.title_aether_core),
                    subtitle = stringResource(R.string.hamed_settings_aether_summary),
                    onClick = { context.startActivity(Intent(context, ServerAetherActivity::class.java)) }
                )
                SettingsMenuItem(
                    icon = painterResource(R.drawable.ic_about_24dp),
                    title = stringResource(R.string.title_about),
                    subtitle = stringResource(R.string.hamed_aether_title),
                    onClick = { context.startActivity(Intent(context, AboutActivity::class.java)) }
                )
                SettingsMenuItem(
                    icon = painterResource(R.drawable.ic_source_code_24dp),
                    title = stringResource(R.string.title_source_code),
                    subtitle = AppConfig.APP_URL,
                    onClick = { Utils.openUri(context, AppConfig.APP_URL) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            NavigationBarsSpacer()
        }
    }
}

@file:OptIn(ExperimentalFoundationApi::class)

package com.v2ray.ang.ui.main

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.enums.AetherIpVersion
import com.v2ray.ang.enums.AetherObfuscation
import com.v2ray.ang.enums.AetherProtocol
import com.v2ray.ang.enums.AetherPsiphon
import com.v2ray.ang.enums.AetherTor
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.HamedAetherManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.MmkvManager.rememberMmkvBool
import com.v2ray.ang.handler.MmkvManager.rememberMmkvString
import com.v2ray.ang.ui.compose.HamedPaletteId
import com.v2ray.ang.ui.compose.HamedPalettes
import com.v2ray.ang.ui.compose.HamedThemeColors
import com.v2ray.ang.ui.compose.ThemeManager
import com.v2ray.ang.ui.compose.hamedPalette
import kotlinx.coroutines.delay
import kotlin.math.sin

/** Width at which the dashboard switches to the side-by-side wide layout. */
private val LANDSCAPE_BREAKPOINT = 720.dp

@Composable
fun HamedAetherScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
) {
    val context = LocalContext.current
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val isRunning = uiState.isRunning
    val status = uiState.status

    val trafficState by HamedAetherManager.trafficState.collectAsStateWithLifecycle()
    val connectionInfo by HamedAetherManager.connectionInfo.collectAsStateWithLifecycle()
    val preparationStatus by HamedAetherManager.preparationStatus.collectAsStateWithLifecycle()

    // Ensure Aether profile is present
    LaunchedEffect(Unit) {
        val guid = HamedAetherManager.ensureAetherProfile()
        if (uiState.selectedGuid != guid) {
            onAction(MainAction.SelectServer(guid))
        }
    }

    val isConnecting = status is MainStatus.Connecting || preparationStatus != null
    val isActuallyConnected = isRunning && !isConnecting

    // Auto fast IP detection and ping test on real connection, reset on disconnect
    LaunchedEffect(isActuallyConnected) {
        if (isActuallyConnected) {
            HamedAetherManager.fetchFastPublicIp()
            var retries = 0
            while (retries < 6 && HamedAetherManager.connectionInfo.value.ip == "---") {
                delay(800)
                HamedAetherManager.fetchFastPublicIp()
                retries++
            }
            delay(400)
            onAction(MainAction.TestCurrentServer)
        } else {
            HamedAetherManager.resetTraffic()
            HamedAetherManager.resetConnectionInfo()
        }
    }

    // Sync real test result from Xray/CoreServiceManager into connectionInfo
    LaunchedEffect(status) {
        if (status is MainStatus.ConnectionTest) {
            HamedAetherManager.updateFromTestResult(status.result)
        }
    }

    // Connection timer (only counts when actually connected, not warming up)
    var connectionStartTime by rememberSaveable { mutableLongStateOf(0L) }
    var elapsedSeconds by rememberSaveable { mutableLongStateOf(0L) }
    LaunchedEffect(isActuallyConnected) {
        if (isActuallyConnected) {
            if (connectionStartTime == 0L) {
                connectionStartTime = SystemClock.elapsedRealtime()
            }
            while (true) {
                elapsedSeconds = (SystemClock.elapsedRealtime() - connectionStartTime) / 1000L
                delay(1000)
            }
        } else {
            connectionStartTime = 0L
            elapsedSeconds = 0L
        }
    }

    // Active Profile Configuration State
    var activeProtocol by remember {
        val (_, prof) = HamedAetherManager.getActiveAetherProfile()
        mutableStateOf(AetherProtocol.fromString(prof.aetherProtocol))
    }
    var activeChain by remember {
        val (_, prof) = HamedAetherManager.getActiveAetherProfile()
        val isPsiphon = AetherPsiphon.fromString(prof.aetherPsiphon) != AetherPsiphon.OFF
        val isTor = AetherTor.fromString(prof.aetherTor) != AetherTor.OFF
        val chain = when {
            isPsiphon -> HamedChainType.PSIPHON
            isTor -> HamedChainType.TOR
            else -> HamedChainType.DEFAULT
        }
        mutableStateOf(chain)
    }
    var activePsiphonRegion by remember {
        val (_, prof) = HamedAetherManager.getActiveAetherProfile()
        mutableStateOf(prof.aetherPsiphonRegion.orEmpty())
    }
    var activeObfs by remember {
        val (_, prof) = HamedAetherManager.getActiveAetherProfile()
        mutableStateOf(AetherObfuscation.fromString(prof.aetherObfuscation))
    }
    var activeIpVersion by remember {
        val (_, prof) = HamedAetherManager.getActiveAetherProfile()
        mutableStateOf(AetherIpVersion.fromString(prof.aetherIpVersion))
    }

    // The editable dashboard layout, kept as JSON in MMKV
    var tileLayoutRaw by rememberMmkvString(AppConfig.PREF_HAMED_TILE_LAYOUT, "")

    // Dashboard edit mode: long-press any tile to jiggle, drag to re-order, cross to hide
    var editMode by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = editMode) { editMode = false }

    // A layout reset from Settings must also reach a live dashboard when the process resumes
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val stored = MmkvManager.decodeSettingsString(AppConfig.PREF_HAMED_TILE_LAYOUT, "") ?: ""
                if (stored != tileLayoutRaw) tileLayoutRaw = stored
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val tileLayout = remember(tileLayoutRaw) { HamedTileLayout.decode(tileLayoutRaw) }
    val visibleTiles = tileLayout.visibleTiles()
    val hiddenTiles = tileLayout.hiddenTiles()

    // Ultra premium connect mode
    var premiumEnabled by rememberMmkvBool(AppConfig.PREF_HAMED_ULTRA_PREMIUM, false)

    // Color template for the quick switcher
    val paletteId by ThemeManager.palette.collectAsState()

    val buttonPosition by rememberMmkvString(AppConfig.PREF_HAMED_BUTTON_POSITION, "middle")
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    fun onTileMoved(from: Int, to: Int) {
        tileLayoutRaw = tileLayout.swapped(from, to).encode()
    }
    fun onTileHidden(tile: HamedTile) {
        tileLayoutRaw = tileLayout.hiding(tile).encode()
    }
    fun onTileShown(tile: HamedTile) {
        tileLayoutRaw = tileLayout.showing(tile).encode()
    }
    fun onLayoutReset() {
        tileLayoutRaw = HamedTileLayout.default().encode()
    }

    val tileContext = HamedTileContext(
        isRunning = isRunning,
        isConnecting = isConnecting,
        trafficState = trafficState,
        connectionInfo = connectionInfo,
        isActuallyConnected = isActuallyConnected,
        elapsedSeconds = elapsedSeconds,
        premiumEnabled = premiumEnabled,
        onTogglePremium = { premiumEnabled = !premiumEnabled },
        activeProtocol = activeProtocol,
        activeChain = activeChain,
        activePsiphonRegion = activePsiphonRegion,
        activeObfs = activeObfs,
        activeIpVersion = activeIpVersion,
        onProtocol = { selected ->
            activeProtocol = selected
            HamedAetherManager.updateProtocol(selected)
            if (isRunning) onAction(MainAction.RestartService)
        },
        onChain = { selected ->
            activeChain = selected
            val psiphon = if (selected == HamedChainType.PSIPHON) AetherPsiphon.CHAIN else AetherPsiphon.OFF
            val tor = if (selected == HamedChainType.TOR) AetherTor.CHAIN else AetherTor.OFF
            HamedAetherManager.updateChain(psiphon, tor)
            if (isRunning) onAction(MainAction.RestartService)
        },
        onPsiphonRegion = { selectedRegion ->
            activePsiphonRegion = selectedRegion
            HamedAetherManager.updatePsiphonRegion(selectedRegion)
            if (isRunning) onAction(MainAction.RestartService)
        },
        onObfs = { selected ->
            activeObfs = selected
            HamedAetherManager.updateObfuscation(selected)
            if (isRunning) onAction(MainAction.RestartService)
        },
        onIpVersion = { selected ->
            activeIpVersion = selected
            HamedAetherManager.updateIpVersion(selected)
            if (isRunning) onAction(MainAction.RestartService)
        },
        onRefreshIp = {
            if (isRunning) {
                HamedAetherManager.fetchFastPublicIp()
                onAction(MainAction.TestCurrentServer)
            } else {
                context.toast(R.string.hamed_please_connect_first)
            }
        },
        onToggle = { onAction(MainAction.ToggleService) },
    )

    Scaffold(
        containerColor = HamedThemeColors.Background,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            Surface(
                color = HamedThemeColors.Background,
                shadowElevation = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = navBarPadding + 6.dp, top = 0.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.6.dp)
                            .background(HamedThemeColors.Border.copy(alpha = 0.5f))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        HamedBreadcrumbBar(
                            activeProtocol = activeProtocol,
                            activeChain = activeChain,
                            activePsiphonRegion = activePsiphonRegion,
                            activeObfs = activeObfs
                        )
                    }
                }
            }
        },
        topBar = {
            HamedTopBar(
                isConnecting = isConnecting,
                isActuallyConnected = isActuallyConnected,
                preparationStatus = preparationStatus,
                status = status,
                elapsedSeconds = elapsedSeconds,
                selectedPalette = paletteId,
                onSelectPalette = { ThemeManager.setPalette(it) },
                onOpenLogcat = { onNavigate(MainDestination.Logcat) },
                onOpenSettings = { onNavigate(MainDestination.Settings) },
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isWide = maxWidth >= LANDSCAPE_BREAKPOINT

            if (isWide) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(0.44f)
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        HamedServerCard(
                            tileContext = tileContext,
                            onTestPing = { onAction(MainAction.TestCurrentServer) }
                        )
                        HamedHeroConnect(
                            isActuallyConnected = isActuallyConnected,
                            isConnecting = isConnecting,
                            preparationStatus = preparationStatus,
                            elapsedSeconds = elapsedSeconds,
                            premiumEnabled = premiumEnabled,
                            shakeTick = uiState.connectionErrorCount,
                            tileContext = tileContext,
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(0.56f)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        HamedConnectionDashboardBottom(
                            tileContext = tileContext,
                            onTestPing = { onAction(MainAction.TestCurrentServer) }
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    HamedTileEditBar(
                        editMode = editMode,
                        hiddenTiles = hiddenTiles,
                        onExitEdit = { editMode = false },
                        onReset = ::onLayoutReset,
                        onShowTile = ::onTileShown,
                    )

                    if (editMode) {
                        // In edit mode the whole order is dragged through a single grid
                        HamedHeroConnect(
                            isActuallyConnected = isActuallyConnected,
                            isConnecting = isConnecting,
                            preparationStatus = preparationStatus,
                            elapsedSeconds = elapsedSeconds,
                            premiumEnabled = premiumEnabled,
                            shakeTick = uiState.connectionErrorCount,
                            tileContext = tileContext,
                        )
                        HamedTileGrid(
                            visibleTiles = visibleTiles,
                            editMode = true,
                            onEnterEdit = {},
                            onMove = ::onTileMoved,
                            onHide = ::onTileHidden,
                            tileContext = tileContext,
                        )
                    } else {
                        // 1. TOP: Protocol & Server selection card (with expandable settings)
                        HamedServerCard(
                            tileContext = tileContext,
                            onTestPing = { onAction(MainAction.TestCurrentServer) }
                        )

                        // 2. MIDDLE: Connect button hero banner
                        HamedHeroConnect(
                            isActuallyConnected = isActuallyConnected,
                            isConnecting = isConnecting,
                            preparationStatus = preparationStatus,
                            elapsedSeconds = elapsedSeconds,
                            premiumEnabled = premiumEnabled,
                            shakeTick = uiState.connectionErrorCount,
                            tileContext = tileContext,
                        )

                        // 3. BOTTOM: IP & Connection dashboard
                        HamedConnectionDashboardBottom(
                            tileContext = tileContext,
                            onTestPing = { onAction(MainAction.TestCurrentServer) }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

/**
 * The top bar: logcat, name and live status, the palette quick switcher and settings.
 */
@Composable
fun HamedTopBar(
    isConnecting: Boolean,
    isActuallyConnected: Boolean,
    preparationStatus: String?,
    status: MainStatus,
    elapsedSeconds: Long,
    selectedPalette: HamedPaletteId,
    onSelectPalette: (HamedPaletteId) -> Unit,
    onOpenLogcat: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Surface(
        color = HamedThemeColors.CardBackground,
        border = BorderStroke(0.5.dp, HamedThemeColors.Border),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = statusBarPadding + 4.dp, bottom = 10.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Grid Menu Button (Left)
            Surface(
                modifier = Modifier
                    .size(42.dp)
                    .clickable { onOpenLogcat() },
                shape = RoundedCornerShape(12.dp),
                color = HamedThemeColors.InnerCard,
                border = BorderStroke(1.dp, HamedThemeColors.Border)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_grid_24dp),
                        contentDescription = stringResource(R.string.title_logcat),
                        tint = HamedThemeColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Logo & App Name (Center)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_hamed_logo),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = stringResource(R.string.hamed_aether_title),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = HamedThemeColors.TextPrimary
                    )
                }
                val statusSubText = when {
                    preparationStatus != null -> preparationStatus
                    isConnecting -> {
                        val connectingMessage = (status as? MainStatus.Connecting)?.message
                        if (!connectingMessage.isNullOrBlank()) connectingMessage
                        else stringResource(R.string.hamed_btn_connecting)
                    }
                    isActuallyConnected -> stringResource(R.string.connection_connected) +
                            "  ·  " + formatHamedElapsed(elapsedSeconds)
                    else -> stringResource(R.string.hamed_status_subtitle_secure)
                }
                val statusSubColor = when {
                    isConnecting -> HamedThemeColors.Warning
                    isActuallyConnected -> HamedThemeColors.Success
                    else -> HamedThemeColors.TextSecondary
                }
                Text(
                    text = statusSubText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (isActuallyConnected) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = statusSubColor
                )
            }

            // Right Buttons: Palette Quick Switcher & Settings
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HamedPalettePicker(
                    selectedPalette = selectedPalette,
                    onSelectPalette = onSelectPalette,
                )
                Surface(
                    modifier = Modifier
                        .size(42.dp)
                        .clickable { onOpenSettings() },
                    shape = RoundedCornerShape(12.dp),
                    color = HamedThemeColors.InnerCard,
                    border = BorderStroke(1.dp, HamedThemeColors.Border)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings_24dp),
                            contentDescription = stringResource(R.string.title_settings),
                            tint = HamedThemeColors.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Quick five-template switcher dot-menu in the top bar. */
@Composable
fun HamedPalettePicker(
    selectedPalette: HamedPaletteId,
    onSelectPalette: (HamedPaletteId) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            modifier = Modifier
                .size(42.dp)
                .clickable { expanded = true },
            shape = RoundedCornerShape(12.dp),
            color = HamedThemeColors.InnerCard,
            border = BorderStroke(1.dp, HamedThemeColors.Border)
        ) {
            Box(contentAlignment = Alignment.Center) {
                val current = hamedPalette(selectedPalette)
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(current.primary, current.secondary)))
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .heightIn(max = 340.dp)
                .background(HamedThemeColors.CardBackground)
        ) {
            HamedPalettes.forEach { palette ->
                val selected = palette.id == selectedPalette
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(hamedPaletteNameResource(palette.id)),
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) HamedThemeColors.AccentCyan else HamedThemeColors.TextPrimary
                        )
                    },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(palette.primary, palette.secondary))
                                )
                                .border(
                                    width = if (selected) 1.5.dp else 0.dp,
                                    color = if (selected) HamedThemeColors.TextPrimary else Color.Transparent,
                                    shape = CircleShape
                                )
                        )
                    },
                    trailingIcon = {
                        if (selected) {
                            Icon(
                                painter = painterResource(R.drawable.ic_action_done),
                                contentDescription = null,
                                tint = HamedThemeColors.AccentCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    },
                    onClick = {
                        onSelectPalette(palette.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** The edit-mode toolbar: a done chip, reset, and every hidden tile as a restore chip. */
@Composable
fun HamedTileEditBar(
    editMode: Boolean,
    hiddenTiles: List<HamedTile>,
    onExitEdit: () -> Unit,
    onReset: () -> Unit,
    onShowTile: (HamedTile) -> Unit,
) {
    AnimatedVisibility(visible = editMode) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.hamed_tile_edit_hint),
                style = MaterialTheme.typography.labelSmall,
                color = HamedThemeColors.TextSecondary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = HamedThemeColors.InnerCard,
                    border = BorderStroke(1.dp, HamedThemeColors.Border),
                    modifier = Modifier.clickable { onExitEdit() }
                ) {
                    Text(
                        text = stringResource(R.string.hamed_tile_layout_done),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = HamedThemeColors.AccentCyan,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = HamedThemeColors.InnerCard,
                    border = BorderStroke(1.dp, HamedThemeColors.Border),
                    modifier = Modifier.clickable { onReset() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_restore_24dp),
                            contentDescription = null,
                            tint = HamedThemeColors.Warning,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(R.string.hamed_tile_layout_reset),
                            style = MaterialTheme.typography.labelMedium,
                            color = HamedThemeColors.TextPrimary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = {},
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_edit_24dp),
                        contentDescription = null,
                        tint = HamedThemeColors.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (hiddenTiles.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.hamed_tile_hidden_title),
                        style = MaterialTheme.typography.labelSmall,
                        color = HamedThemeColors.TextSecondary
                    )
                    hiddenTiles.forEach { tile ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = HamedThemeColors.CardBackground,
                            border = BorderStroke(
                                1.dp, HamedThemeColors.Border
                            ),
                            modifier = Modifier.clickable { onShowTile(tile) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_add_24dp),
                                    contentDescription = null,
                                    tint = HamedThemeColors.AccentCyan,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = stringResource(hamedTileTitleResource(tile)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = HamedThemeColors.TextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The editable dashboard: tiles flow in pairs of half tiles and full-width tiles. Known tiles
 * all fit; every tile is visible unless hidden in the edit bar.
 */
@Composable
fun HamedTileGrid(
    visibleTiles: List<HamedTile>,
    editMode: Boolean,
    onEnterEdit: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onHide: (HamedTile) -> Unit,
    tileContext: HamedTileContext,
) {
    if (visibleTiles.isEmpty()) return
    val haptics = LocalHapticFeedback.current

    var dragIndex by remember { mutableIntStateOf(-1) }

    val rows = remember(visibleTiles) { chunkHamedTiles(visibleTiles) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEachIndexed { rowIndex, rowTiles ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowTiles.forEach { tile ->
                    val globalIndex = visibleTiles.indexOf(tile)
                    val isDragging = editMode && dragIndex == globalIndex

                    // Jiggle animation state shared by every tile in edit mode
                    val jiggle = rememberInfiniteTransition(label = "hamedJiggle")
                    val phase by jiggle.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 280, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "hamedJigglePhase"
                    )

                    val pressScale = remember { Animatable(1f) }
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    LaunchedEffect(isPressed, editMode) {
                        pressScale.animateTo(
                            if (isPressed && !editMode) 0.96f else 1f,
                            tween(durationMillis = 160, easing = FastOutSlowInEasing)
                        )
                    }

                    val elevation by animateFloatAsState(
                        targetValue = if (isDragging) 18f else 4f,
                        label = "hamedTileElevation"
                    )

                    val tileSize = remember { mutableStateOf(IntSize.Zero) }
                    val draggingModifier = if (editMode) {
                        Modifier.hamedTileDrag(
                            key = tile.name,
                            tileSpan = tile.span,
                            tileSize = tileSize,
                            onDragStart = {
                                dragIndex = globalIndex
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragEnd = { dragIndex = -1 },
                            onSwap = { direction ->
                                val target = globalIndex + direction
                                if (target in visibleTiles.indices && target != globalIndex) {
                                    onMove(globalIndex, target)
                                    dragIndex = target
                                }
                            },
                        )
                    } else {
                        Modifier
                    }

                    val jiggleModifier = if (editMode) {
                        val angle = sin(phase * 2f * Math.PI).toFloat()
                        Modifier.graphicsLayer {
                            rotationZ = angle * 1.6f
                            translationX = angle * 2.dp.toPx() * 0.6f
                        }
                    } else {
                        Modifier
                    }

                    val shape = RoundedCornerShape(20.dp)
                    Box(
                        modifier = Modifier
                            .then(
                                if (rowTiles.size > 1) Modifier.weight(1f) else Modifier.fillMaxWidth()
                            )
                            .onSizeChanged { tileSize.value = it }
                            .graphicsLayer {
                                scaleX = pressScale.value * (if (isDragging) 1.05f else 1f)
                                scaleY = pressScale.value * (if (isDragging) 1.05f else 1f)
                            }
                            .then(jiggleModifier)
                            .hamedTileLongPress(onLongPress = {
                                if (!editMode) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onEnterEdit()
                                }
                            })
                            .then(draggingModifier)
                    ) {
                        Surface(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                            shape = shape,
                            color = HamedThemeColors.CardBackground,
                            shadowElevation = elevation.dp,
                            border = BorderStroke(
                                1.dp,
                                if (isDragging) HamedThemeColors.AccentCyan else HamedThemeColors.Border,
                            ),
                            interactionSource = interactionSource,
                        ) {
                            HamedTileContent(
                                tile = tile,
                                tileContext = tileContext,
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                        if (editMode) {
                            Surface(
                                onClick = { onHide(tile) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .size(22.dp),
                                shape = CircleShape,
                                color = HamedThemeColors.Error,
                                shadowElevation = 4.dp,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_delete_24dp),
                                        contentDescription = stringResource(R.string.hamed_tile_hide),
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // The dragged tile's step past the row end settles during the next recomposition.
            if (rowTiles.size == 1 && editMode && dragIndex >= 0) {
                // keep min-height for spacing stability while a tile is dragged over the gaps
                Spacer(modifier = Modifier.height(2.dp))
            }
        }
    }
}

/** Groups tiles into rows: full-width tiles alone, half-width tiles paired two per row. */
private fun chunkHamedTiles(tiles: List<HamedTile>): List<List<HamedTile>> {
    val rows = ArrayList<List<HamedTile>>(2)
    var index = 0
    while (index < tiles.size) {
        val tile = tiles[index]
        when {
            tile.span >= 2 -> {
                rows.add(listOf(tile))
                index++
            }
            index + 1 < tiles.size && tiles[index + 1].span == 1 -> {
                rows.add(listOf(tile, tiles[index + 1]))
                index += 2
            }
            else -> {
                rows.add(listOf(tile))
                index++
            }
        }
    }
    return rows
}

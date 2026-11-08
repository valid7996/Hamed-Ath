package com.v2ray.ang.ui.main

import android.content.Intent
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.enums.AetherObfuscation
import com.v2ray.ang.enums.AetherProtocol
import com.v2ray.ang.enums.AetherPsiphon
import com.v2ray.ang.enums.AetherTor
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.HamedAetherManager
import com.v2ray.ang.handler.MmkvManager.rememberMmkvString
import com.v2ray.ang.ui.compose.HamedThemeColors
import com.v2ray.ang.ui.server.ServerAetherKeysActivity
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
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

    // Auto fast IP detection and ping test on connect, reset on disconnect
    LaunchedEffect(isRunning) {
        if (isRunning) {
            delay(500)
            HamedAetherManager.fetchFastPublicIp()
            delay(1200)
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

    // Connection timer
    var connectionStartTime by rememberSaveable { mutableLongStateOf(0L) }
    var elapsedSeconds by rememberSaveable { mutableLongStateOf(0L) }

    LaunchedEffect(isRunning) {
        if (isRunning) {
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

    val buttonPosition by rememberMmkvString(AppConfig.PREF_HAMED_BUTTON_POSITION, "middle")
    val isConnecting = status is MainStatus.Connecting || preparationStatus != null

    val scrollState = rememberScrollState()
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Scaffold(
        containerColor = HamedThemeColors.Background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
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
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onNavigate(MainDestination.Logcat) },
                        shape = RoundedCornerShape(12.dp),
                        color = HamedThemeColors.InnerCard,
                        border = BorderStroke(1.dp, HamedThemeColors.Border)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.ic_grid_24dp),
                                contentDescription = "Menu",
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
                                text = "Hamed Aether",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = HamedThemeColors.TextPrimary
                            )
                        }
                        val statusSubText = when {
                            preparationStatus != null -> preparationStatus!!
                            isRunning -> {
                                val hours = elapsedSeconds / 3600
                                val minutes = (elapsedSeconds % 3600) / 60
                                val seconds = elapsedSeconds % 60
                                if (hours > 0) String.format(java.util.Locale.US, "Connected • %02d:%02d:%02d", hours, minutes, seconds)
                                else String.format(java.util.Locale.US, "Connected • %02d:%02d", minutes, seconds)
                            }
                            isConnecting -> "Connecting..."
                            else -> "Secure Tunnel Manager"
                        }
                        val statusSubColor = when {
                            isRunning -> HamedThemeColors.SuccessGreen
                            isConnecting -> HamedThemeColors.WarningOrange
                            else -> HamedThemeColors.TextSecondary
                        }
                        Text(
                            text = statusSubText,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isRunning) FontWeight.Bold else FontWeight.Normal),
                            color = statusSubColor
                        )
                    }

                    // Right Buttons: Notification Bell & Settings
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Bell Button
                        Surface(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { },
                            shape = RoundedCornerShape(12.dp),
                            color = HamedThemeColors.InnerCard,
                            border = BorderStroke(1.dp, HamedThemeColors.Border)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_bell_24dp),
                                    contentDescription = "Notifications",
                                    tint = HamedThemeColors.TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                // Cyan notification dot
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .align(Alignment.TopEnd)
                                        .padding(top = 8.dp, end = 8.dp)
                                        .clip(CircleShape)
                                        .background(HamedThemeColors.AccentCyan)
                                )
                            }
                        }

                        // Settings Gear Button
                        Surface(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onNavigate(MainDestination.Settings) },
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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (buttonPosition == "top") {
                HamedConnectSection(
                    isRunning = isRunning,
                    isConnecting = isConnecting,
                    preparationStatus = preparationStatus,
                    elapsedSeconds = elapsedSeconds,
                    activeProtocol = activeProtocol,
                    activeChain = activeChain,
                    activePsiphonRegion = activePsiphonRegion,
                    activeObfs = activeObfs,
                    onToggle = { onAction(MainAction.ToggleService) }
                )
            }

            // ==========================================
            // CARD 1: Protocol & Tunnel Chain
            // ==========================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = HamedThemeColors.CardBackground),
                border = BorderStroke(1.dp, HamedThemeColors.Border),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_routing_24dp),
                                contentDescription = null,
                                tint = HamedThemeColors.SecondaryPurple,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Protocol & Tunnel Chain",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = HamedThemeColors.TextPrimary
                            )
                        }

                        // Preset Pill
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF142442),
                            border = BorderStroke(1.dp, HamedThemeColors.Border),
                            modifier = Modifier.clickable { }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_subscriptions_24dp),
                                    contentDescription = null,
                                    tint = HamedThemeColors.TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Preset",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = HamedThemeColors.TextSecondary
                                )
                            }
                        }
                    }

                    // 1. Selector: Protocol
                    HamedProtocolSelector(
                        selectedProtocol = activeProtocol,
                        onSelect = { selected ->
                            activeProtocol = selected
                            HamedAetherManager.updateProtocol(selected)
                            if (isRunning) onAction(MainAction.RestartService)
                        }
                    )

                    // 2. Selector: Chain Carrier (Psiphon / Tor / Default)
                    HamedChainSelector(
                        selectedChain = activeChain,
                        onSelect = { selected ->
                            activeChain = selected
                            val psiphon = if (selected == HamedChainType.PSIPHON) AetherPsiphon.CHAIN else AetherPsiphon.OFF
                            val tor = if (selected == HamedChainType.TOR) AetherTor.CHAIN else AetherTor.OFF
                            HamedAetherManager.updateChain(psiphon, tor)
                            if (isRunning) onAction(MainAction.RestartService)
                        }
                    )

                    // 2.5 Psiphon Country Selector (Shown when Psiphon chain is active)
                    AnimatedVisibility(visible = activeChain == HamedChainType.PSIPHON) {
                        HamedPsiphonCountrySelector(
                            selectedRegion = activePsiphonRegion,
                            onSelect = { selectedRegion ->
                                activePsiphonRegion = selectedRegion
                                HamedAetherManager.updatePsiphonRegion(selectedRegion)
                                if (isRunning) onAction(MainAction.RestartService)
                            }
                        )
                    }

                    // 3. Row: Obfuscation (60%) & Renew WARP Keys (40%)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            HamedObfsSelector(
                                selectedObfs = activeObfs,
                                onSelect = { selected ->
                                    activeObfs = selected
                                    HamedAetherManager.updateObfuscation(selected)
                                    if (isRunning) onAction(MainAction.RestartService)
                                }
                            )
                        }

                        // Renew WARP Keys Button with Neon Purple Border
                        Surface(
                            modifier = Modifier
                                .weight(0.7f)
                                .height(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    context.startActivity(Intent(context, ServerAetherKeysActivity::class.java))
                                },
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF141935),
                            border = BorderStroke(1.5.dp, HamedThemeColors.SecondaryPurple)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_key_24dp),
                                    contentDescription = null,
                                    tint = HamedThemeColors.SecondaryPurple,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Renew\nWARP Keys",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 13.sp
                                    ),
                                    color = HamedThemeColors.TextPrimary
                                )
                            }
                        }
                    }
                }
            }

            if (buttonPosition == "middle") {
                HamedConnectSection(
                    isRunning = isRunning,
                    isConnecting = isConnecting,
                    preparationStatus = preparationStatus,
                    elapsedSeconds = elapsedSeconds,
                    activeProtocol = activeProtocol,
                    activeChain = activeChain,
                    activePsiphonRegion = activePsiphonRegion,
                    activeObfs = activeObfs,
                    onToggle = { onAction(MainAction.ToggleService) }
                )
            }

            // ==========================================
            // CARD 2: Connection Dashboard
            // ==========================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = HamedThemeColors.CardBackground),
                border = BorderStroke(1.dp, HamedThemeColors.Border),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_routing_24dp),
                                contentDescription = null,
                                tint = HamedThemeColors.AccentCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Connection Dashboard",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = HamedThemeColors.TextPrimary
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = HamedThemeColors.InnerCard,
                            border = BorderStroke(1.dp, HamedThemeColors.Border),
                            modifier = Modifier
                                .size(28.dp)
                                .clickable { }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_more_vert_24dp),
                                    contentDescription = null,
                                    tint = HamedThemeColors.TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Speed Waveform Cards: Download & Upload
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Download Card
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp),
                            color = HamedThemeColors.InnerCard,
                            border = BorderStroke(1.dp, HamedThemeColors.Border)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "↓",
                                        fontWeight = FontWeight.Bold,
                                        color = HamedThemeColors.AccentCyan
                                    )
                                    Text(
                                        text = "Download",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = HamedThemeColors.TextSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = trafficState.downloadSpeedStr,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 18.sp
                                    ),
                                    color = HamedThemeColors.TextPrimary
                                )
                                Text(
                                    text = trafficState.totalDownloadStr,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = HamedThemeColors.TextSecondary
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                // Smooth Animated Cyan Waveform Graph
                                HamedWaveformGraph(
                                    graphColor = HamedThemeColors.AccentCyan,
                                    isActive = isRunning,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(34.dp)
                                )
                            }
                        }

                        // Upload Card
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp),
                            color = HamedThemeColors.InnerCard,
                            border = BorderStroke(1.dp, HamedThemeColors.Border)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "↑",
                                        fontWeight = FontWeight.Bold,
                                        color = HamedThemeColors.SecondaryPurple
                                    )
                                    Text(
                                        text = "Upload",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = HamedThemeColors.TextSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = trafficState.uploadSpeedStr,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 18.sp
                                    ),
                                    color = HamedThemeColors.TextPrimary
                                )
                                Text(
                                    text = trafficState.totalUploadStr,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = HamedThemeColors.TextSecondary
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                // Smooth Animated Purple Waveform Graph
                                HamedWaveformGraph(
                                    graphColor = HamedThemeColors.SecondaryPurple,
                                    isActive = isRunning,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(34.dp)
                                )
                            }
                        }
                    }

                    // IP & Location Row (Smart IP & Flag & Carrier/Exit for chained tunnels)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = HamedThemeColors.InnerCard,
                        border = BorderStroke(1.dp, HamedThemeColors.Border)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    modifier = Modifier.size(38.dp),
                                    shape = CircleShape,
                                    color = Color(0xFF0C243B)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (connectionInfo.flagEmoji != "🌐") {
                                            Text(
                                                text = connectionInfo.flagEmoji,
                                                fontSize = 20.sp
                                            )
                                        } else {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_globe_24dp),
                                                contentDescription = null,
                                                tint = HamedThemeColors.AccentCyan,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }

                                Column {
                                    if (connectionInfo.isChained) {
                                        Text(
                                            text = "${connectionInfo.carrierFlagEmoji} Cloudflare ➔ ${connectionInfo.flagEmoji} Psiphon Exit",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = HamedThemeColors.TextPrimary
                                        )
                                    } else {
                                        val locTitle = if (connectionInfo.country != "---" && connectionInfo.country.isNotBlank()) {
                                            "${connectionInfo.flagEmoji} ${connectionInfo.country} (${connectionInfo.carrierName})"
                                        } else {
                                            "IP & Location"
                                        }
                                        Text(
                                            text = locTitle,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = HamedThemeColors.TextPrimary
                                        )
                                    }

                                    val ipDisplay = when {
                                        connectionInfo.ip != "---" -> "${connectionInfo.ip}${if (connectionInfo.pingMs >= 0) " • ${connectionInfo.pingMs} ms" else ""}"
                                        isRunning -> "Fetching IP..."
                                        else -> "Not Connected"
                                    }
                                    Text(
                                        text = ipDisplay,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (connectionInfo.ip != "---") HamedThemeColors.AccentCyan else HamedThemeColors.TextSecondary
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    if (isRunning) {
                                        HamedAetherManager.fetchFastPublicIp()
                                        onAction(MainAction.TestCurrentServer)
                                    } else {
                                        context.toast(R.string.hamed_please_connect_first)
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_refresh_24dp),
                                    contentDescription = "Refresh",
                                    tint = HamedThemeColors.AccentCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (buttonPosition == "bottom") {
                HamedConnectSection(
                    isRunning = isRunning,
                    isConnecting = isConnecting,
                    preparationStatus = preparationStatus,
                    elapsedSeconds = elapsedSeconds,
                    activeProtocol = activeProtocol,
                    activeChain = activeChain,
                    activePsiphonRegion = activePsiphonRegion,
                    activeObfs = activeObfs,
                    onToggle = { onAction(MainAction.ToggleService) }
                )
            }

            Spacer(modifier = Modifier.height(navBarPadding + 8.dp))
        }
    }
}

@Composable
fun HamedConnectSection(
    isRunning: Boolean,
    isConnecting: Boolean,
    preparationStatus: String?,
    elapsedSeconds: Long,
    activeProtocol: AetherProtocol,
    activeChain: HamedChainType,
    activePsiphonRegion: String,
    activeObfs: AetherObfuscation,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Large Pill Button (Electric Blue to Neon Purple gradient)
        val buttonGradient = when {
            isRunning -> Brush.horizontalGradient(listOf(HamedThemeColors.SuccessGreen, Color(0xFF00B0FF)))
            isConnecting -> Brush.horizontalGradient(listOf(HamedThemeColors.WarningOrange, Color(0xFFFFAB00)))
            else -> Brush.horizontalGradient(listOf(HamedThemeColors.PrimaryBlue, HamedThemeColors.SecondaryPurple))
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(30.dp))
                .shadow(12.dp, RoundedCornerShape(30.dp))
                .clickable {
                    if (!isConnecting) {
                        onToggle()
                    }
                },
            shape = RoundedCornerShape(30.dp),
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(buttonGradient),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_power_24dp),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    val buttonText = when {
                        preparationStatus != null -> preparationStatus
                        isRunning -> {
                            val hours = elapsedSeconds / 3600
                            val minutes = (elapsedSeconds % 3600) / 60
                            val seconds = elapsedSeconds % 60
                            val timeStr = if (hours > 0) {
                                String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
                            } else {
                                String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
                            }
                            "Disconnect ($timeStr)"
                        }
                        isConnecting -> "Connecting..."
                        else -> "Connect"
                    }

                    Text(
                        text = buttonText,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            letterSpacing = 0.5.sp
                        ),
                        color = Color.White
                    )
                }
            }
        }

        // Breadcrumbs: WireGuard > Psiphon (US) > Auto
        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BreadcrumbChip(
                iconRes = R.drawable.ic_shield_24dp,
                label = activeProtocol.name
            )
            Text(text = ">", style = MaterialTheme.typography.labelSmall, color = HamedThemeColors.TextSecondary)
            val chainLabel = if (activeChain == HamedChainType.PSIPHON && activePsiphonRegion.isNotBlank()) {
                "Psiphon (${activePsiphonRegion.uppercase()})"
            } else {
                activeChain.displayName
            }
            BreadcrumbChip(
                iconRes = R.drawable.ic_routing_24dp,
                label = chainLabel
            )
            Text(text = ">", style = MaterialTheme.typography.labelSmall, color = HamedThemeColors.TextSecondary)
            BreadcrumbChip(
                iconRes = R.drawable.ic_flash_on_24dp,
                label = activeObfs.name
            )
        }
    }
}

@Composable
fun BreadcrumbChip(iconRes: Int, label: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = HamedThemeColors.CardBackground,
        border = BorderStroke(1.dp, HamedThemeColors.Border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = HamedThemeColors.AccentCyan,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = HamedThemeColors.TextSecondary
            )
        }
    }
}

/**
 * Draws a smooth animated neon oscilloscope waveform curve matching the futuristic dashboard.
 */
@Composable
fun HamedWaveformGraph(
    graphColor: Color,
    isActive: Boolean = true,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveTransition")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isActive) 2400 else 6500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height * 0.52f
        val amplitude = if (isActive) height * 0.32f else height * 0.12f

        val path = Path()
        val fillPath = Path()
        val step = 4f
        var first = true

        var x = 0f
        while (x <= width) {
            val progress = x / width
            val y = centerY +
                (kotlin.math.sin(progress * 2 * Math.PI + phase).toFloat() * amplitude * 0.75f) +
                (kotlin.math.cos(progress * 3.5 * Math.PI - phase * 0.6f).toFloat() * amplitude * 0.25f)

            if (first) {
                path.moveTo(x, y)
                fillPath.moveTo(x, y)
                first = false
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
            x += step
        }

        fillPath.lineTo(width, height)
        fillPath.lineTo(0f, height)
        fillPath.close()

        // Gradient under-glow
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(graphColor.copy(alpha = 0.35f), Color.Transparent)
            )
        )

        // Wave stroke
        drawPath(
            path = path,
            color = graphColor,
            style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

enum class HamedChainType(val displayName: String) {
    DEFAULT("Direct"),
    PSIPHON("Psiphon"),
    TOR("Tor");
}

@Composable
fun HamedProtocolSelector(
    selectedProtocol: AetherProtocol,
    onSelect: (AetherProtocol) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val protocols = listOf(
        Pair(AetherProtocol.WIREGUARD, "WireGuard"),
        Pair(AetherProtocol.MASQUE, "Wasque (MASQUE)"),
        Pair(AetherProtocol.WG_OVER_MASQUE, "WireGuard over Masque"),
        Pair(AetherProtocol.MIM, "Masque over Masque (MIM)"),
        Pair(AetherProtocol.GOOL, "Warp to Warp")
    )

    val currentName = protocols.find { it.first == selectedProtocol }?.second ?: selectedProtocol.name

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { expanded = true },
            shape = RoundedCornerShape(16.dp),
            color = HamedThemeColors.InnerCard,
            border = BorderStroke(1.dp, HamedThemeColors.Border)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1E1938)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.ic_shield_24dp),
                                contentDescription = null,
                                tint = HamedThemeColors.SecondaryPurple,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "Protocol",
                            style = MaterialTheme.typography.labelSmall,
                            color = HamedThemeColors.TextSecondary
                        )
                        Text(
                            text = currentName,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = HamedThemeColors.TextPrimary
                        )
                    }
                }

                Icon(
                    painter = painterResource(R.drawable.ic_expand_more_24dp),
                    contentDescription = null,
                    tint = HamedThemeColors.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .background(HamedThemeColors.CardBackground)
        ) {
            protocols.forEach { (proto, label) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label,
                            fontWeight = if (proto == selectedProtocol) FontWeight.Bold else FontWeight.Normal,
                            color = if (proto == selectedProtocol) HamedThemeColors.AccentCyan else HamedThemeColors.TextPrimary
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(proto)
                    }
                )
            }
        }
    }
}

@Composable
fun HamedChainSelector(
    selectedChain: HamedChainType,
    onSelect: (HamedChainType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val chains = listOf(
        Pair(HamedChainType.PSIPHON, "Psiphon"),
        Pair(HamedChainType.DEFAULT, "Direct (Default)"),
        Pair(HamedChainType.TOR, "Tor")
    )

    val currentName = chains.find { it.first == selectedChain }?.second ?: selectedChain.displayName

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { expanded = true },
            shape = RoundedCornerShape(16.dp),
            color = HamedThemeColors.InnerCard,
            border = BorderStroke(1.dp, HamedThemeColors.Border)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF142442)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.ic_routing_24dp),
                                contentDescription = null,
                                tint = HamedThemeColors.PrimaryBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "Chain Carrier",
                            style = MaterialTheme.typography.labelSmall,
                            color = HamedThemeColors.TextSecondary
                        )
                        Text(
                            text = currentName,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = HamedThemeColors.TextPrimary
                        )
                    }
                }

                Icon(
                    painter = painterResource(R.drawable.ic_expand_more_24dp),
                    contentDescription = null,
                    tint = HamedThemeColors.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .background(HamedThemeColors.CardBackground)
        ) {
            chains.forEach { (chain, label) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label,
                            fontWeight = if (chain == selectedChain) FontWeight.Bold else FontWeight.Normal,
                            color = if (chain == selectedChain) HamedThemeColors.AccentCyan else HamedThemeColors.TextPrimary
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(chain)
                    }
                )
            }
        }
    }
}

@Composable
fun HamedObfsSelector(
    selectedObfs: AetherObfuscation,
    onSelect: (AetherObfuscation) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val options = listOf(
        Pair(AetherObfuscation.AUTO, "Auto (خودکار)"),
        Pair(AetherObfuscation.FIREWALL, "Firewall"),
        Pair(AetherObfuscation.BALANCED, "Balanced"),
        Pair(AetherObfuscation.AGGRESSIVE, "Aggressive"),
        Pair(AetherObfuscation.OFF, "Off")
    )

    val currentName = options.find { it.first == selectedObfs }?.second ?: selectedObfs.name

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable { expanded = true },
            shape = RoundedCornerShape(16.dp),
            color = HamedThemeColors.InnerCard,
            border = BorderStroke(1.dp, HamedThemeColors.Border)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_flash_off_24dp),
                        contentDescription = null,
                        tint = HamedThemeColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = "Obfuscation",
                            style = MaterialTheme.typography.labelSmall,
                            color = HamedThemeColors.TextSecondary,
                            fontSize = 10.sp
                        )
                        Text(
                            text = currentName,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = HamedThemeColors.TextPrimary,
                            maxLines = 1
                        )
                    }
                }

                Icon(
                    painter = painterResource(R.drawable.ic_expand_more_24dp),
                    contentDescription = null,
                    tint = HamedThemeColors.TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .background(HamedThemeColors.CardBackground)
        ) {
            options.forEach { (obfs, label) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label,
                            fontWeight = if (obfs == selectedObfs) FontWeight.Bold else FontWeight.Normal,
                            color = if (obfs == selectedObfs) HamedThemeColors.AccentCyan else HamedThemeColors.TextPrimary
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(obfs)
                    }
                )
            }
        }
    }
}

data class HamedPsiphonCountry(
    val code: String,
    val name: String,
    val flag: String
)

private val psiphonCountryList = listOf(
    HamedPsiphonCountry("", "Auto / Best Location (بهترین موقعیت)", "🌐"),
    HamedPsiphonCountry("US", "United States (ایالات متحده)", "🇺🇸"),
    HamedPsiphonCountry("DE", "Germany (آلمان)", "🇩🇪"),
    HamedPsiphonCountry("GB", "United Kingdom (انگلستان)", "🇬🇧"),
    HamedPsiphonCountry("NL", "Netherlands (هلند)", "🇳🇱"),
    HamedPsiphonCountry("CA", "Canada (کانادا)", "🇨🇦"),
    HamedPsiphonCountry("FR", "France (فرانسه)", "🇫🇷"),
    HamedPsiphonCountry("CH", "Switzerland (سوئیس)", "🇨🇭"),
    HamedPsiphonCountry("JP", "Japan (ژاپن)", "🇯🇵"),
    HamedPsiphonCountry("SG", "Singapore (سنگاپور)", "🇸🇬"),
    HamedPsiphonCountry("SE", "Sweden (سوئد)", "🇸🇪"),
    HamedPsiphonCountry("FI", "Finland (فنلاند)", "🇫🇮"),
    HamedPsiphonCountry("TR", "Turkey (ترکیه)", "🇹🇷"),
    HamedPsiphonCountry("IT", "Italy (ایتالیا)", "🇮🇹"),
    HamedPsiphonCountry("ES", "Spain (اسپانیا)", "🇪🇸"),
    HamedPsiphonCountry("AU", "Australia (استرالیا)", "🇦🇺"),
    HamedPsiphonCountry("PL", "Poland (لهستان)", "🇵🇱")
)

@Composable
fun HamedPsiphonCountrySelector(
    selectedRegion: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val current = psiphonCountryList.find { it.code.equals(selectedRegion, ignoreCase = true) }
        ?: HamedPsiphonCountry(selectedRegion, selectedRegion.ifBlank { "Auto" }, HamedAetherManager.countryCodeToEmojiFlag(selectedRegion))

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { expanded = true },
            shape = RoundedCornerShape(16.dp),
            color = HamedThemeColors.InnerCard,
            border = BorderStroke(1.dp, HamedThemeColors.Border)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF142442)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = current.flag,
                                fontSize = 20.sp
                            )
                        }
                    }

                    Column {
                        Text(
                            text = stringResource(R.string.hamed_psiphon_country_title),
                            style = MaterialTheme.typography.labelSmall,
                            color = HamedThemeColors.TextSecondary
                        )
                        Text(
                            text = current.name,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = HamedThemeColors.TextPrimary
                        )
                    }
                }

                Icon(
                    painter = painterResource(R.drawable.ic_expand_more_24dp),
                    contentDescription = null,
                    tint = HamedThemeColors.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .background(HamedThemeColors.CardBackground)
        ) {
            psiphonCountryList.forEach { item ->
                DropdownMenuItem(
                    leadingIcon = {
                        Text(text = item.flag, fontSize = 18.sp)
                    },
                    text = {
                        Text(
                            text = item.name,
                            fontWeight = if (item.code.equals(selectedRegion, ignoreCase = true)) FontWeight.Bold else FontWeight.Normal,
                            color = if (item.code.equals(selectedRegion, ignoreCase = true)) HamedThemeColors.AccentCyan else HamedThemeColors.TextPrimary
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(item.code)
                    }
                )
            }
        }
    }
}

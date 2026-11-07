package com.v2ray.ang.ui.main

import android.content.Intent
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.enums.AetherObfuscation
import com.v2ray.ang.enums.AetherProtocol
import com.v2ray.ang.enums.AetherPsiphon
import com.v2ray.ang.enums.AetherTor
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.HamedAetherManager
import com.v2ray.ang.ui.compose.AppTopBar
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

    // Auto real ping test on connect and reset on disconnect
    LaunchedEffect(isRunning) {
        if (isRunning) {
            delay(1500)
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
    var activeObfs by remember {
        val (_, prof) = HamedAetherManager.getActiveAetherProfile()
        mutableStateOf(AetherObfuscation.fromString(prof.aetherObfuscation))
    }

    val scrollState = rememberScrollState()
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = statusBarPadding)
                        .height(64.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Logcat Button (Left)
                    IconButton(onClick = { onNavigate(MainDestination.Logcat) }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_logcat_24dp),
                            contentDescription = stringResource(R.string.title_logcat),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Title Header (Center)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.hamed_aether_title),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isRunning) stringResource(R.string.connection_connected)
                            else stringResource(R.string.connection_not_connected),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isRunning) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline
                        )
                    }

                    // Settings Button (Right)
                    IconButton(onClick = { onNavigate(MainDestination.Settings) }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings_24dp),
                            contentDescription = stringResource(R.string.title_settings),
                            tint = MaterialTheme.colorScheme.primary
                        )
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ==========================================
            // TILE 1 (کاشی اول بالا): تنظیمات پروتکل و زنجیره
            // ==========================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_routing_24dp),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.hamed_tile_protocol_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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

                    // 2. Selector: Chain (Psiphon / Tor / Default)
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

                    // 3. Selector: Obfuscation & Quick Keys
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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

                        OutlinedButton(
                            onClick = {
                                context.startActivity(Intent(context, ServerAetherKeysActivity::class.java))
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.hamed_renew_warp_keys),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }
            }

            // ==========================================
            // TILE 2 (کاشی دومی وسطی): داشبورد اتصال (پینگ، آپلود/دانلود، آی‌پی/پرچم)
            // ==========================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.hamed_tile_dashboard_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // HTTPS Ping Button & Badge
                        val isTesting = uiState.isTesting || connectionInfo.isTesting
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (connectionInfo.pingMs >= 0) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable {
                                if (isRunning) {
                                    onAction(MainAction.TestCurrentServer)
                                } else {
                                    context.toast(R.string.hamed_please_connect_first)
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isTesting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = stringResource(R.string.hamed_testing_https),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                } else {
                                    val pingText = when {
                                        connectionInfo.pingMs >= 0 -> "${connectionInfo.pingMs} ms"
                                        isRunning -> stringResource(R.string.hamed_test_https)
                                        else -> "---"
                                    }
                                    val pingColor = when {
                                        connectionInfo.pingMs in 0..200 -> Color(0xFF2E7D32)
                                        connectionInfo.pingMs > 200 -> Color(0xFFEF6C00)
                                        isRunning -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.outline
                                    }
                                    Text(
                                        text = pingText,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = pingColor
                                    )
                                }
                            }
                        }
                    }

                    // Speedometer: Upload & Download Cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Download
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
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
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = stringResource(R.string.hamed_download),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = trafficState.downloadSpeedStr,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "کل: ${trafficState.totalDownloadStr}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        // Upload
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
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
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        text = stringResource(R.string.hamed_upload),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = trafficState.uploadSpeedStr,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "کل: ${trafficState.totalUploadStr}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }

                    // IP & Location Flag
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
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
                                Text(
                                    text = connectionInfo.flagEmoji,
                                    fontSize = 24.sp
                                )
                                Column {
                                    Text(
                                        text = connectionInfo.ip,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (connectionInfo.country != "---") connectionInfo.country
                                        else stringResource(R.string.hamed_ip_flag),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    if (isRunning) {
                                        onAction(MainAction.TestCurrentServer)
                                    } else {
                                        context.toast(R.string.hamed_please_connect_first)
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_refresh_24dp),
                                    contentDescription = stringResource(R.string.pull_down_to_refresh),
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // ==========================================
            // TILE 3 (کاشی سوم پایین): دکمه اتصال
            // ==========================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Pulsing / Rotating Animation for Connect Button
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = if (isRunning) 1.05f else 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1200, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulseScale"
                    )

                    val isConnecting = status is MainStatus.Connecting || preparationStatus != null
                    val buttonBgColor by animateColorAsState(
                        targetValue = when {
                            isRunning -> Color(0xFF2E7D32)
                            isConnecting -> Color(0xFFF57F17)
                            else -> MaterialTheme.colorScheme.primary
                        },
                        label = "buttonBgColor"
                    )

                    // Big Power Button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .scale(pulseScale)
                            .size(96.dp)
                            .shadow(8.dp, CircleShape)
                            .clip(CircleShape)
                            .background(buttonBgColor)
                            .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape)
                            .clickable {
                                if (!isConnecting) {
                                    onAction(MainAction.ToggleService)
                                }
                            }
                    ) {
                        if (isConnecting) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(42.dp),
                                strokeWidth = 3.dp
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.ic_power_24dp),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }

                    // Button Action Label
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = when {
                                preparationStatus != null -> preparationStatus!!
                                isRunning -> stringResource(R.string.hamed_btn_disconnect)
                                status is MainStatus.Connecting -> status.message.ifBlank { stringResource(R.string.hamed_btn_connecting) }
                                else -> stringResource(R.string.hamed_btn_connect)
                            },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        if (isRunning) {
                            val hours = elapsedSeconds / 3600
                            val minutes = (elapsedSeconds % 3600) / 60
                            val seconds = elapsedSeconds % 60
                            val timeStr = if (hours > 0) {
                                String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
                            } else {
                                String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
                            }
                            Text(
                                text = stringResource(R.string.hamed_connected_time, timeStr),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = Color(0xFF2E7D32),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        } else {
                            Text(
                                text = "${activeProtocol.name} • ${activeChain.displayName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(navBarPadding))
        }
    }
}

enum class HamedChainType(val displayName: String) {
    DEFAULT("دیفالت (مستقیم)"),
    PSIPHON("سایفون (Psiphon)"),
    TOR("تور (Tor)");
}

@Composable
fun HamedProtocolSelector(
    selectedProtocol: AetherProtocol,
    onSelect: (AetherProtocol) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val protocols = listOf(
        Pair(AetherProtocol.MASQUE, stringResource(R.string.hamed_protocol_wasque)),
        Pair(AetherProtocol.WG_OVER_MASQUE, stringResource(R.string.hamed_protocol_warp)),
        Pair(AetherProtocol.WIREGUARD, stringResource(R.string.hamed_protocol_wireguard)),
        Pair(AetherProtocol.GOOL, stringResource(R.string.hamed_protocol_warp_to_warp)),
        Pair(AetherProtocol.MIM, stringResource(R.string.hamed_protocol_masque_to_masque)),
    )

    val currentName = protocols.find { it.first == selectedProtocol }?.second
        ?: selectedProtocol.name

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.hamed_protocol_title),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = currentName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Icon(
                    painter = painterResource(R.drawable.ic_expand_more_24dp),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            protocols.forEach { (proto, label) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label,
                            fontWeight = if (proto == selectedProtocol) FontWeight.Bold else FontWeight.Normal,
                            color = if (proto == selectedProtocol) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
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
        Pair(HamedChainType.DEFAULT, stringResource(R.string.hamed_chain_default)),
        Pair(HamedChainType.PSIPHON, stringResource(R.string.hamed_chain_psiphon)),
        Pair(HamedChainType.TOR, stringResource(R.string.hamed_chain_tor))
    )

    val currentName = chains.find { it.first == selectedChain }?.second ?: selectedChain.displayName

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.hamed_chain_title),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = currentName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Icon(
                    painter = painterResource(R.drawable.ic_expand_more_24dp),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            chains.forEach { (chain, label) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label,
                            fontWeight = if (chain == selectedChain) FontWeight.Bold else FontWeight.Normal,
                            color = if (chain == selectedChain) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
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
        Pair(AetherObfuscation.FIREWALL, "Firewall (فایروال)"),
        Pair(AetherObfuscation.BALANCED, "Balanced (متعادل)"),
        Pair(AetherObfuscation.AGGRESSIVE, "Aggressive (تهاجمی)"),
        Pair(AetherObfuscation.OFF, "Off (خاموش)")
    )

    val currentName = options.find { it.first == selectedObfs }?.second ?: selectedObfs.name

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.hamed_obfs_title),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = currentName,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
                Icon(
                    painter = painterResource(R.drawable.ic_expand_more_24dp),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.7f)
        ) {
            options.forEach { (obfs, label) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label,
                            fontWeight = if (obfs == selectedObfs) FontWeight.Bold else FontWeight.Normal,
                            color = if (obfs == selectedObfs) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
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

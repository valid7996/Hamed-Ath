package com.v2ray.ang.ui.main

import android.content.Intent
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.enums.AetherIpVersion
import com.v2ray.ang.enums.AetherObfuscation
import com.v2ray.ang.enums.AetherProtocol
import com.v2ray.ang.handler.HamedAetherManager
import com.v2ray.ang.handler.HamedConnectionInfo
import com.v2ray.ang.handler.HamedTrafficState
import com.v2ray.ang.ui.compose.HamedFontScale
import com.v2ray.ang.ui.compose.HamedPaletteId
import com.v2ray.ang.ui.compose.HamedPalettes
import com.v2ray.ang.ui.compose.HamedThemeColors
import com.v2ray.ang.ui.compose.ThemeManager
import com.v2ray.ang.ui.server.ServerAetherKeysActivity
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Everything a dashboard tile needs to draw and dispatch: live tunnel state plus the callbacks
 * that update the Aether profile or trigger main actions.
 */
data class HamedTileContext(
    val isRunning: Boolean,
    val isConnecting: Boolean,
    val isActuallyConnected: Boolean,
    val trafficState: HamedTrafficState,
    val connectionInfo: HamedConnectionInfo,
    val elapsedSeconds: Long,
    val premiumEnabled: Boolean,
    val onTogglePremium: () -> Unit,
    val activeProtocol: AetherProtocol,
    val activeChain: HamedChainType,
    val activePsiphonRegion: String,
    val activeObfs: AetherObfuscation,
    val activeIpVersion: AetherIpVersion,
    val onProtocol: (AetherProtocol) -> Unit,
    val onChain: (HamedChainType) -> Unit,
    val onPsiphonRegion: (String) -> Unit,
    val onObfs: (AetherObfuscation) -> Unit,
    val onIpVersion: (AetherIpVersion) -> Unit,
    val onRefreshIp: () -> Unit,
    val onToggle: () -> Unit,
)

enum class HamedChainType(val displayName: String) {
    DEFAULT("Direct"),
    PSIPHON("Psiphon"),
    TOR("Tor");
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

/** Localized title of a tile, for the hidden list and content description. */
@Composable
fun hamedTileTitleResource(tile: HamedTile): Int = when (tile) {
    HamedTile.DOWNLOAD -> R.string.hamed_download
    HamedTile.UPLOAD -> R.string.hamed_upload
    HamedTile.TOTAL -> R.string.hamed_tile_total_title
    HamedTile.IP_INFO -> R.string.hamed_ip_flag
    HamedTile.PROTOCOL -> R.string.hamed_protocol_title
    HamedTile.CHAIN -> R.string.hamed_chain_title
    HamedTile.OBFS -> R.string.hamed_obfs_title
    HamedTile.IP_VERSION -> R.string.hamed_scan_ip_title
    HamedTile.WARP_KEYS -> R.string.hamed_renew_warp_keys
    HamedTile.PREMIUM -> R.string.hamed_tile_premium_title
    HamedTile.THEME -> R.string.hamed_tile_theme_title
    HamedTile.FONT -> R.string.hamed_tile_font_title
}

/** Localized palette name. */
@Composable
fun hamedPaletteNameResource(id: HamedPaletteId): Int = when (id) {
    HamedPaletteId.NEON -> R.string.hamed_theme_name_neon
    HamedPaletteId.EMERALD -> R.string.hamed_theme_name_emerald
    HamedPaletteId.VIOLET -> R.string.hamed_theme_name_violet
    HamedPaletteId.SUNSET -> R.string.hamed_theme_name_sunset
    HamedPaletteId.ROSE -> R.string.hamed_theme_name_rose
}

// ============================================================
// Hero connect button (+ ultra premium chip) with press and shake feedback
// ============================================================

@Composable
fun HamedHeroConnect(
    isActuallyConnected: Boolean,
    isConnecting: Boolean,
    preparationStatus: String?,
    elapsedSeconds: Long,
    premiumEnabled: Boolean,
    shakeTick: Long,
    tileContext: HamedTileContext,
) {
    val onToggle = tileContext.onToggle
    val onTogglePremium = tileContext.onTogglePremium

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HamedPremiumChip(
            enabled = premiumEnabled,
            onToggle = onTogglePremium,
            modifier = Modifier.fillMaxWidth(),
        )

        val rotation = remember { Animatable(0f) }
        LaunchedEffect(shakeTick) {
            if (shakeTick > 0L) {
                listOf(-9f, 9f, -7f, 7f, -4f, 4f, 0f).forEach { target ->
                    rotation.animateTo(
                        targetValue = target,
                        animationSpec = tween(durationMillis = 60),
                    )
                }
            }
        }

        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val pressScale by animateFloatAsState(
            targetValue = if (isPressed) 0.97f else 1f,
            label = "hamedHeroPress"
        )

        val bannerBackground = when {
            isActuallyConnected -> Brush.horizontalGradient(
                listOf(Color(0xFFB8F438), Color(0xFF9EE824))
            )
            isConnecting -> HamedThemeColors.ConnectingGradient
            premiumEnabled -> HamedThemeColors.PremiumGradient
            else -> Brush.horizontalGradient(
                listOf(Color(0xFF1B2030), Color(0xFF151926))
            )
        }

        Surface(
            onClick = onToggle,
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                    rotationZ = rotation.value
                },
            shape = RoundedCornerShape(26.dp),
            border = if (!isActuallyConnected && !isConnecting) {
                BorderStroke(1.5.dp, HamedThemeColors.PrimaryBlue.copy(alpha = 0.5f))
            } else null,
            interactionSource = interactionSource,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bannerBackground)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Power button icon badge
                    val badgeBg = when {
                        isActuallyConnected -> Color(0xFF162002)
                        isConnecting -> Color(0x33FFFFFF)
                        else -> HamedThemeColors.InnerCard
                    }
                    val iconTint = when {
                        isActuallyConnected -> Color(0xFFB8F438)
                        else -> Color.White
                    }

                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(badgeBg),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isConnecting) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(26.dp),
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.ic_power_24dp),
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    // Right: Status label, Timer digits, Subtext
                    val primaryTextColor = when {
                        isActuallyConnected -> Color(0xFF162002)
                        else -> Color.White
                    }
                    val secondaryTextColor = when {
                        isActuallyConnected -> Color(0xFF2C3E08)
                        else -> HamedThemeColors.TextSecondary
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.Center
                    ) {
                        val statusLabel = when {
                            isActuallyConnected -> stringResource(R.string.hamed_status_connected)
                            isConnecting -> preparationStatus ?: stringResource(R.string.hamed_btn_connecting)
                            else -> stringResource(R.string.hamed_status_ready)
                        }

                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            color = primaryTextColor
                        )

                        val timerDigits = if (isActuallyConnected) {
                            formatHamedElapsed(elapsedSeconds)
                        } else if (isConnecting) {
                            "..."
                        } else {
                            "00:00"
                        }

                        Text(
                            text = timerDigits,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 32.sp
                            ),
                            color = primaryTextColor
                        )

                        val subText = when {
                            isActuallyConnected -> stringResource(R.string.hamed_tap_to_disconnect)
                            isConnecting -> stringResource(R.string.hamed_btn_disconnect)
                            else -> stringResource(R.string.hamed_tap_to_connect)
                        }

                        Text(
                            text = subText,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = secondaryTextColor
                        )
                    }
                }
            }
        }
    }
}

/** The ultra premium toggle chip above the connect button. */
@Composable
private fun HamedPremiumChip(
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        label = "hamedPremiumChipPress"
    )

    Surface(
        onClick = onToggle,
        modifier = modifier
            .height(44.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            },
        shape = RoundedCornerShape(22.dp),
        color = if (enabled) Color.Transparent else HamedThemeColors.InnerCard,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (enabled) Color.Transparent else HamedThemeColors.Border,
        ),
        interactionSource = interactionSource,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (enabled) HamedThemeColors.PremiumGradient
                    else Brush.horizontalGradient(listOf(HamedThemeColors.InnerCard, HamedThemeColors.InnerCard))
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 14.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_flash_on_24dp),
                    contentDescription = null,
                    tint = if (enabled) Color.White else HamedThemeColors.TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.hamed_tile_premium_title),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    ),
                    color = if (enabled) Color.White else HamedThemeColors.TextSecondary
                )
                HamedMiniSwitch(checked = enabled)
            }
        }
    }
}

/** Minimal animated switch knob. */
@Composable
private fun HamedMiniSwitch(checked: Boolean) {
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        label = "hamedMiniSwitch"
    )
    Box(
        modifier = Modifier
            .width(34.dp)
            .height(18.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(
                if (checked) Color(0x66FFFFFF)
                else HamedThemeColors.Border.copy(alpha = 0.8f)
            )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(2.dp)
                .graphicsLayer { translationX = progress * 16.dp.toPx() }
                .size(14.dp)
                .clip(CircleShape)
                .background(if (checked) Color.White else HamedThemeColors.TextSecondary)
        )
    }
}

// ============================================================
// Tiles switch
// ============================================================

@Composable
fun HamedTileContent(
    tile: HamedTile,
    tileContext: HamedTileContext,
    modifier: Modifier = Modifier,
) {
    when (tile) {
        HamedTile.DOWNLOAD -> HamedSpeedTile(
            labelRes = R.string.hamed_download,
            speed = tileContext.trafficState.downloadSpeedStr,
            total = tileContext.trafficState.totalDownloadStr,
            waveColor = HamedThemeColors.AccentCyan,
            active = tileContext.isRunning,
            modifier = modifier,
        )
        HamedTile.UPLOAD -> HamedSpeedTile(
            labelRes = R.string.hamed_upload,
            speed = tileContext.trafficState.uploadSpeedStr,
            total = tileContext.trafficState.totalUploadStr,
            waveColor = HamedThemeColors.SecondaryPurple,
            active = tileContext.isRunning,
            modifier = modifier,
        )
        HamedTile.TOTAL -> HamedTotalTile(tileContext = tileContext, modifier = modifier)
        HamedTile.IP_INFO -> HamedIpInfoTile(tileContext = tileContext, modifier = modifier)
        HamedTile.PROTOCOL -> HamedProtocolTile(tileContext = tileContext, modifier = modifier)
        HamedTile.CHAIN -> HamedChainTile(tileContext = tileContext, modifier = modifier)
        HamedTile.OBFS -> HamedObfsTile(tileContext = tileContext, modifier = modifier)
        HamedTile.IP_VERSION -> HamedIpVersionTile(tileContext = tileContext, modifier = modifier)
        HamedTile.WARP_KEYS -> HamedWarpKeysTile(tileContext = tileContext, modifier = modifier)
        HamedTile.PREMIUM -> HamedPremiumTile(tileContext = tileContext, modifier = modifier)
        HamedTile.THEME -> HamedThemeTile(modifier = modifier)
        HamedTile.FONT -> HamedFontTile(modifier = modifier)
    }
}

/** A half-width speed tile with the animated waveform. */
@Composable
private fun HamedSpeedTile(
    labelRes: Int,
    speed: String,
    total: String,
    waveColor: Color,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = HamedThemeColors.TextSecondary
        )
        Text(
            text = speed,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 18.sp
            ),
            color = HamedThemeColors.TextPrimary
        )
        Text(
            text = total,
            style = MaterialTheme.typography.labelSmall,
            color = HamedThemeColors.TextSecondary
        )
        Spacer(modifier = Modifier.height(4.dp))
        HamedWaveformGraph(
            graphColor = waveColor,
            isActive = active,
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
        )
    }
}

/** The total transfer tile. */
@Composable
private fun HamedTotalTile(
    tileContext: HamedTileContext,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = stringResource(R.string.hamed_tile_total_title),
            style = MaterialTheme.typography.labelSmall,
            color = HamedThemeColors.TextSecondary
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column {
                Text(
                    text = "↓ " + stringResource(R.string.hamed_download),
                    style = MaterialTheme.typography.labelSmall,
                    color = HamedThemeColors.AccentCyan
                )
                Text(
                    text = tileContext.trafficState.totalDownloadStr,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = HamedThemeColors.TextPrimary
                )
            }
            Column {
                Text(
                    text = "↑ " + stringResource(R.string.hamed_upload),
                    style = MaterialTheme.typography.labelSmall,
                    color = HamedThemeColors.SecondaryPurple
                )
                Text(
                    text = tileContext.trafficState.totalUploadStr,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = HamedThemeColors.TextPrimary
                )
            }
        }
    }
}

/** The IP & location tile with the refresh action. */
@Composable
private fun HamedIpInfoTile(
    tileContext: HamedTileContext,
    modifier: Modifier = Modifier,
) {
    val connectionInfo = tileContext.connectionInfo
    Row(
        modifier = modifier,
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
                color = HamedThemeColors.InnerCard
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
                        text = "${connectionInfo.carrierFlagEmoji} ${connectionInfo.carrierName} ➔ ${connectionInfo.flagEmoji} ${stringResource(R.string.hamed_chain_psiphon)} ${stringResource(R.string.hamed_exit_label)}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = HamedThemeColors.TextPrimary
                    )
                } else {
                    val locTitle = if (connectionInfo.country != "---" && connectionInfo.country.isNotBlank()) {
                        "${connectionInfo.flagEmoji} ${connectionInfo.country} (${connectionInfo.carrierName})"
                    } else {
                        stringResource(R.string.hamed_ip_flag)
                    }
                    Text(
                        text = locTitle,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = HamedThemeColors.TextPrimary
                    )
                }

                val ipDisplay = when {
                    connectionInfo.ip != "---" -> "${connectionInfo.ip}${if (connectionInfo.pingMs >= 0) " • ${connectionInfo.pingMs} ms" else ""}"
                    tileContext.isRunning -> stringResource(R.string.hamed_fetching_ip)
                    else -> stringResource(R.string.hamed_not_connected)
                }
                Text(
                    text = ipDisplay,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (connectionInfo.ip != "---") HamedThemeColors.AccentCyan
                    else HamedThemeColors.TextSecondary
                )
            }
        }

        IconButton(
            onClick = tileContext.onRefreshIp,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_refresh_24dp),
                contentDescription = stringResource(R.string.hamed_tile_refresh),
                tint = HamedThemeColors.AccentCyan,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** A dropdown option. Either a resource label or a ready-made text (data lists). */
data class HamedDropdownOption<T>(
    val value: T,
    val labelRes: Int = 0,
    val label: String = "",
) {
    fun labelOrDefault(): Int = labelRes
}

@Composable
private fun <T> HamedDropdownTile(
    titleRes: Int,
    iconRes: Int,
    iconTint: Color,
    currentText: String,
    options: List<HamedDropdownOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
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
                    color = HamedThemeColors.InnerCard
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Column {
                    Text(
                        text = stringResource(titleRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = HamedThemeColors.TextSecondary
                    )
                    Text(
                        text = currentText,
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

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(HamedThemeColors.CardBackground)
        ) {
            options.forEach { option ->
                val isSelected = option.value == selected
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (option.labelRes != 0) stringResource(option.labelRes) else option.label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) HamedThemeColors.AccentCyan else HamedThemeColors.TextPrimary
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(option.value)
                    }
                )
            }
        }
    }
}

@Composable
private fun HamedProtocolTile(tileContext: HamedTileContext, modifier: Modifier = Modifier) {
    val options = listOf(
        HamedDropdownOption(AetherProtocol.WIREGUARD, labelRes = R.string.hamed_protocol_wireguard),
        HamedDropdownOption(AetherProtocol.MASQUE, labelRes = R.string.hamed_protocol_wasque),
        HamedDropdownOption(AetherProtocol.WG_OVER_MASQUE, labelRes = R.string.hamed_protocol_warp_to_warp),
        HamedDropdownOption(AetherProtocol.MIM, labelRes = R.string.hamed_protocol_masque_to_masque),
        HamedDropdownOption(AetherProtocol.GOOL, labelRes = R.string.hamed_protocol_warp),
    )
    val current = options.firstOrNull { it.value == tileContext.activeProtocol }
    val currentText = current?.let {
        stringResource(it.labelRes)
    } ?: tileContext.activeProtocol.name

    HamedDropdownTile(
        titleRes = R.string.hamed_protocol_title,
        iconRes = R.drawable.ic_shield_24dp,
        iconTint = HamedThemeColors.SecondaryPurple,
        currentText = currentText,
        options = options,
        selected = tileContext.activeProtocol,
        onSelect = tileContext.onProtocol,
        modifier = modifier,
    )
}

@Composable
private fun HamedObfsTile(tileContext: HamedTileContext, modifier: Modifier = Modifier) {
    val options = listOf(
        HamedDropdownOption(AetherObfuscation.AUTO, labelRes = R.string.hamed_obfs_auto),
        HamedDropdownOption(AetherObfuscation.FIREWALL, labelRes = R.string.hamed_obfs_firewall),
        HamedDropdownOption(AetherObfuscation.BALANCED, labelRes = R.string.hamed_obfs_balanced),
        HamedDropdownOption(AetherObfuscation.AGGRESSIVE, labelRes = R.string.hamed_obfs_aggressive),
        HamedDropdownOption(AetherObfuscation.OFF, labelRes = R.string.hamed_obfs_off),
    )
    val current = options.firstOrNull { it.value == tileContext.activeObfs }
    val currentText = current?.let { stringResource(it.labelRes) } ?: tileContext.activeObfs.name

    HamedDropdownTile(
        titleRes = R.string.hamed_obfs_title,
        iconRes = R.drawable.ic_flash_off_24dp,
        iconTint = HamedThemeColors.TextSecondary,
        currentText = currentText,
        options = options,
        selected = tileContext.activeObfs,
        onSelect = tileContext.onObfs,
        modifier = modifier,
    )
}

@Composable
private fun HamedChainTile(tileContext: HamedTileContext, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val options = listOf(
            HamedDropdownOption(HamedChainType.PSIPHON, labelRes = R.string.hamed_chain_psiphon),
            HamedDropdownOption(HamedChainType.DEFAULT, labelRes = R.string.hamed_chain_default),
            HamedDropdownOption(HamedChainType.TOR, labelRes = R.string.hamed_chain_tor),
        )
        val current = options.firstOrNull { it.value == tileContext.activeChain }
        val currentText = current?.let { stringResource(it.labelRes) } ?: tileContext.activeChain.displayName

        HamedDropdownTile(
            titleRes = R.string.hamed_chain_title,
            iconRes = R.drawable.ic_routing_24dp,
            iconTint = HamedThemeColors.PrimaryBlue,
            currentText = currentText,
            options = options,
            selected = tileContext.activeChain,
            onSelect = tileContext.onChain,
            modifier = Modifier,
        )

        AnimatedVisibility(visible = tileContext.activeChain == HamedChainType.PSIPHON) {
            HamedPsiphonCountrySelector(
                selectedRegion = tileContext.activePsiphonRegion,
                onSelect = tileContext.onPsiphonRegion,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun HamedPsiphonCountrySelector(
    selectedRegion: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = psiphonCountryList.firstOrNull { it.code.equals(selectedRegion, ignoreCase = true) }
        ?: HamedPsiphonCountry(selectedRegion, selectedRegion.ifBlank { "Auto" }, HamedAetherManager.countryCodeToEmojiFlag(selectedRegion))

    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = HamedThemeColors.InnerCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, HamedThemeColors.Border),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = current.flag, fontSize = 18.sp)
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.hamed_psiphon_country_title),
                            style = MaterialTheme.typography.labelSmall,
                            color = HamedThemeColors.TextSecondary
                        )
                        Text(
                            text = current.name,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = HamedThemeColors.TextPrimary
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
                .height(280.dp)
                .background(HamedThemeColors.CardBackground)
        ) {
            psiphonCountryList.forEach { item ->
                DropdownMenuItem(
                    leadingIcon = { Text(text = item.flag, fontSize = 18.sp) },
                    text = {
                        Text(
                            text = item.name,
                            fontWeight = if (item.code.equals(selectedRegion, ignoreCase = true)) FontWeight.Bold
                            else FontWeight.Normal,
                            color = if (item.code.equals(selectedRegion, ignoreCase = true)) HamedThemeColors.AccentCyan
                            else HamedThemeColors.TextPrimary
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

@Composable
private fun HamedIpVersionTile(tileContext: HamedTileContext, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.hamed_scan_ip_title),
            style = MaterialTheme.typography.labelSmall,
            color = HamedThemeColors.TextSecondary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                HamedDropdownOption(AetherIpVersion.V4, labelRes = R.string.hamed_scan_ip_v4),
                HamedDropdownOption(AetherIpVersion.V6, labelRes = R.string.hamed_scan_ip_v6),
                HamedDropdownOption(AetherIpVersion.DUAL, labelRes = R.string.hamed_scan_ip_dual),
            ).forEach { option ->
                val isSelected = tileContext.activeIpVersion == option.value
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { tileContext.onIpVersion(option.value) },
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        if (isSelected) 1.5.dp else 1.dp,
                        if (isSelected) HamedThemeColors.AccentCyan else HamedThemeColors.Border
                    ),
                    color = Color.Transparent
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (isSelected) HamedThemeColors.PrimaryGradient
                                else Brush.horizontalGradient(listOf(HamedThemeColors.InnerCard, HamedThemeColors.InnerCard))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(option.labelRes),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp
                            ),
                            color = if (isSelected) Color.White else HamedThemeColors.TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HamedWarpKeysTile(tileContext: HamedTileContext, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        modifier = modifier.clickable {
            context.startActivity(Intent(context, ServerAetherKeysActivity::class.java))
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = RoundedCornerShape(10.dp),
            color = HamedThemeColors.InnerCard
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.ic_key_24dp),
                    contentDescription = null,
                    tint = HamedThemeColors.SecondaryPurple,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = stringResource(R.string.hamed_renew_warp_keys),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = HamedThemeColors.TextPrimary
            )
            Text(
                text = stringResource(R.string.hamed_wg_renew_keys_summary),
                style = MaterialTheme.typography.labelSmall,
                color = HamedThemeColors.TextSecondary
            )
        }
    }
}

@Composable
private fun HamedPremiumTile(tileContext: HamedTileContext, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_flash_on_24dp),
                contentDescription = null,
                tint = if (tileContext.premiumEnabled) Color(0xFFFFC94A) else HamedThemeColors.TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = stringResource(R.string.hamed_tile_premium_title),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = HamedThemeColors.TextPrimary
            )
        }
        HamedMiniSwitch(checked = tileContext.premiumEnabled)
    }
}

@Composable
private fun HamedThemeTile(modifier: Modifier = Modifier) {
    val selectedPalette by ThemeManager.palette.collectAsState()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.hamed_tile_theme_title),
            style = MaterialTheme.typography.labelSmall,
            color = HamedThemeColors.TextSecondary
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HamedPalettes.forEach { palette ->
                val isSelected = palette.id == selectedPalette
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(palette.primary, palette.secondary)))
                        .border(
                            width = if (isSelected) 2.dp else 0.dp,
                            color = if (isSelected) HamedThemeColors.TextPrimary else Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable { ThemeManager.setPalette(palette.id) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_action_done),
                        contentDescription = stringResource(hamedPaletteNameResource(palette.id)),
                        tint = Color.Transparent.takeIf { !isSelected } ?: Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        Text(
            text = stringResource(hamedPaletteNameResource(selectedPalette)),
            style = MaterialTheme.typography.labelSmall,
            color = HamedThemeColors.TextPrimary
        )
    }
}

@Composable
private fun HamedFontTile(modifier: Modifier = Modifier) {
    val fontScale by ThemeManager.fontScale.collectAsState()
    val currentStep = HamedFontScale.entries.firstOrNull { it.scale == fontScale } ?: HamedFontScale.MEDIUM
    val steps = HamedFontScale.entries
    val currentIndex = steps.indexOf(currentStep)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.hamed_tile_font_title),
            style = MaterialTheme.typography.labelSmall,
            color = HamedThemeColors.TextSecondary
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                onClick = {
                    if (currentIndex > 0) ThemeManager.setFontScale(steps[currentIndex - 1].scale)
                },
                shape = CircleShape,
                color = HamedThemeColors.InnerCard,
                border = androidx.compose.foundation.BorderStroke(1.dp, HamedThemeColors.Border),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "A−",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = HamedThemeColors.TextPrimary
                    )
                }
            }
            Surface(
                onClick = {
                    if (currentIndex < steps.lastIndex) ThemeManager.setFontScale(steps[currentIndex + 1].scale)
                },
                shape = CircleShape,
                color = HamedThemeColors.InnerCard,
                border = androidx.compose.foundation.BorderStroke(1.dp, HamedThemeColors.Border),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "A+",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = HamedThemeColors.TextPrimary
                    )
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(hamedFontScaleLabelResource(currentStep)),
                style = MaterialTheme.typography.labelMedium,
                color = HamedThemeColors.TextPrimary
            )
        }
    }
}

@Composable
private fun hamedFontScaleLabelResource(step: HamedFontScale): Int = when (step) {
    HamedFontScale.SMALL -> R.string.hamed_font_small
    HamedFontScale.MEDIUM -> R.string.hamed_font_medium
    HamedFontScale.LARGE -> R.string.hamed_font_large
    HamedFontScale.XLARGE -> R.string.hamed_font_xlarge
}

// ============================================================
// Wide layout left pane status summary
// ============================================================

@Composable
fun HamedStatusSummary(
    isActuallyConnected: Boolean,
    isConnecting: Boolean,
    elapsedSeconds: Long,
    trafficState: HamedTrafficState,
    connectionInfo: HamedConnectionInfo,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = HamedThemeColors.CardBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, HamedThemeColors.Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = HamedThemeColors.InnerCard
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (connectionInfo.flagEmoji != "🌐") {
                            Text(text = connectionInfo.flagEmoji, fontSize = 22.sp)
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.ic_globe_24dp),
                                contentDescription = null,
                                tint = HamedThemeColors.AccentCyan,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
                Column {
                    Text(
                        text = if (connectionInfo.country != "---" && connectionInfo.country.isNotBlank()) {
                            "${connectionInfo.flagEmoji} ${connectionInfo.country}"
                        } else {
                            stringResource(R.string.hamed_ip_flag)
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = HamedThemeColors.TextPrimary
                    )
                    Text(
                        text = "${connectionInfo.ip}  •  ${connectionInfo.pingMs} ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = HamedThemeColors.AccentCyan
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.6.dp)
                    .background(HamedThemeColors.Border.copy(alpha = 0.5f))
            )

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = "↓ " + stringResource(R.string.hamed_download),
                        style = MaterialTheme.typography.labelSmall,
                        color = HamedThemeColors.AccentCyan
                    )
                    Text(
                        text = trafficState.totalDownloadStr,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = HamedThemeColors.TextPrimary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "↑ " + stringResource(R.string.hamed_upload),
                        style = MaterialTheme.typography.labelSmall,
                        color = HamedThemeColors.SecondaryPurple
                    )
                    Text(
                        text = trafficState.totalUploadStr,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = HamedThemeColors.TextPrimary
                    )
                }
            }

            Text(
                text = stringResource(R.string.hamed_connected_time, formatHamedElapsed(elapsedSeconds)),
                style = MaterialTheme.typography.labelSmall,
                color = HamedThemeColors.TextSecondary
            )
        }
    }
}

// ============================================================
// Waveform and breadcrumb (carried over from the previous screen)
// ============================================================

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
                    (sin(progress * 2 * Math.PI + phase).toFloat() * amplitude * 0.75f) +
                    (cos(progress * 3.5 * Math.PI - phase * 0.6f).toFloat() * amplitude * 0.25f)

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

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(graphColor.copy(alpha = 0.35f), Color.Transparent)
            )
        )

        drawPath(
            path = path,
            color = graphColor,
            style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
fun HamedBreadcrumbBar(
    activeProtocol: AetherProtocol,
    activeChain: HamedChainType,
    activePsiphonRegion: String,
    activeObfs: AetherObfuscation,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        val protoLabel = when (activeProtocol) {
            AetherProtocol.WIREGUARD -> "WIREGUARD"
            AetherProtocol.MASQUE -> "MASQUE"
            AetherProtocol.WG_OVER_MASQUE -> "WG+MASQUE"
            AetherProtocol.MIM -> "MIM"
            AetherProtocol.GOOL -> "WARP"
        }
        val chainLabel = if (activeChain == HamedChainType.PSIPHON && activePsiphonRegion.isNotBlank()) {
            "Psiphon (${activePsiphonRegion.uppercase()})"
        } else {
            activeChain.displayName
        }
        val obfsLabel = activeObfs.name.uppercase()

        BreadcrumbChip(
            iconRes = R.drawable.ic_shield_24dp,
            label = protoLabel
        )
        Text(
            text = ">",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = HamedThemeColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
        BreadcrumbChip(
            iconRes = R.drawable.ic_routing_24dp,
            label = chainLabel
        )
        Text(
            text = ">",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = HamedThemeColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
        BreadcrumbChip(
            iconRes = R.drawable.ic_flash_on_24dp,
            label = obfsLabel
        )
    }
}

@Composable
private fun BreadcrumbChip(iconRes: Int, label: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = HamedThemeColors.CardBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, HamedThemeColors.Border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = HamedThemeColors.AccentCyan,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                ),
                color = HamedThemeColors.TextPrimary
            )
        }
    }
}

// ============================================================
// Top Protocol & Server Selection Card (ZedSecure style)
// ============================================================

@Composable
fun HamedServerCard(
    tileContext: HamedTileContext,
    onTestPing: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val connectionInfo = tileContext.connectionInfo

    val protoName = when (tileContext.activeProtocol) {
        AetherProtocol.MASQUE -> "Wasque (MASQUE)"
        AetherProtocol.WIREGUARD -> "WireGuard"
        AetherProtocol.WG_OVER_MASQUE -> "WARP to WARP"
        AetherProtocol.MIM -> "MASQUE to MASQUE"
        AetherProtocol.GOOL -> "WARP"
    }

    val chainSubtext = when (tileContext.activeChain) {
        HamedChainType.PSIPHON -> if (tileContext.activePsiphonRegion.isNotBlank()) "Psiphon (${tileContext.activePsiphonRegion.uppercase()})" else "Psiphon"
        HamedChainType.TOR -> "Tor"
        HamedChainType.DEFAULT -> "Direct"
    }

    val obfsSubtext = tileContext.activeObfs.name.lowercase().replaceFirstChar { it.uppercase() }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF24153B),
        border = BorderStroke(1.dp, Color(0xFF4C2A7D).copy(alpha = 0.6f)),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF2C194D),
                            Color(0xFF1B1233),
                        )
                    )
                )
                .padding(16.dp)
        ) {
            // Header row: Chevron (left), Info (center), Flag & "سرور فعلی" (right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Chevron button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    val chevronRotation by animateFloatAsState(
                        targetValue = if (expanded) 90f else 0f,
                        label = "chevronRot"
                    )
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back_24dp),
                        contentDescription = "Expand",
                        tint = Color.White,
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer { rotationZ = chevronRotation }
                    )
                }

                // Middle: Server / Protocol Name and Subtext
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = stringResource(R.string.hamed_current_server_label),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = Color(0xFFC0AEE6)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = protoName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        ),
                        color = Color.White
                    )
                    Text(
                        text = "$chainSubtext • $obfsSubtext",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = Color(0xFFA595C7)
                    )
                }

                // Right: Country Flag
                Surface(
                    modifier = Modifier.size(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0x33000000),
                    border = BorderStroke(1.dp, Color(0x33FFFFFF))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (connectionInfo.flagEmoji != "🌐") {
                            Text(text = connectionInfo.flagEmoji, fontSize = 26.sp)
                        } else {
                            Text(text = "🛡️", fontSize = 24.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Badges row: IP Pill, Aether Pill, Latency Ping Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // IP badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0x40000000),
                    border = BorderStroke(0.8.dp, Color(0x22FFFFFF))
                ) {
                    Text(
                        text = if (connectionInfo.ip != "---") connectionInfo.ip else "---.---.---.---",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                // Aether / Core Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFB5F43C)
                ) {
                    Text(
                        text = "Aether",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = Color(0xFF141F04),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Speed Test Button (beside Ping)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0x40000000),
                    border = BorderStroke(0.8.dp, Color(0x22FFFFFF)),
                    modifier = Modifier.clickable { onTestPing() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_refresh_24dp),
                            contentDescription = stringResource(R.string.hamed_speed_test),
                            tint = HamedThemeColors.AccentCyan,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = stringResource(R.string.hamed_speed_test),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            ),
                            color = Color.White
                        )
                    }
                }

                // Latency Ping Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0x40000000),
                    border = BorderStroke(0.8.dp, Color(0x22FFFFFF)),
                    modifier = Modifier.clickable { onTestPing() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (connectionInfo.pingMs >= 0) "${connectionInfo.pingMs} ms" else "--- ms",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            ),
                            color = Color.White
                        )
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (connectionInfo.pingMs >= 0) Color(0xFF4CAF50) else Color(0xFF888888))
                        )
                    }
                }
            }

            // Expanded settings section
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.6.dp)
                            .background(Color(0x33FFFFFF))
                    )

                    HamedProtocolTile(tileContext = tileContext)
                    HamedChainTile(tileContext = tileContext)
                    HamedObfsTile(tileContext = tileContext)
                    HamedIpVersionTile(tileContext = tileContext)
                    HamedWarpKeysTile(tileContext = tileContext)
                }
            }
        }
    }
}

// ============================================================
// Bottom Connection & IP Dashboard
// ============================================================

@Composable
fun HamedConnectionDashboardBottom(
    tileContext: HamedTileContext,
    onTestPing: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Row 1: Upload & Download cards side-by-side
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Upload Card
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(20.dp),
                color = HamedThemeColors.CardBackground,
                border = BorderStroke(1.dp, HamedThemeColors.Border)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.hamed_upload),
                            style = MaterialTheme.typography.labelSmall,
                            color = HamedThemeColors.TextSecondary
                        )
                        Text(text = "↑", color = HamedThemeColors.SecondaryPurple, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = tileContext.trafficState.uploadSpeedStr,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 18.sp
                        ),
                        color = HamedThemeColors.TextPrimary
                    )
                    Text(
                        text = tileContext.trafficState.totalUploadStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = HamedThemeColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    HamedWaveformGraph(
                        graphColor = HamedThemeColors.SecondaryPurple,
                        isActive = tileContext.isActuallyConnected,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                    )
                }
            }

            // Download Card
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(20.dp),
                color = HamedThemeColors.CardBackground,
                border = BorderStroke(1.dp, HamedThemeColors.Border)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.hamed_download),
                            style = MaterialTheme.typography.labelSmall,
                            color = HamedThemeColors.TextSecondary
                        )
                        Text(text = "↓", color = HamedThemeColors.AccentCyan, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = tileContext.trafficState.downloadSpeedStr,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 18.sp
                        ),
                        color = HamedThemeColors.TextPrimary
                    )
                    Text(
                        text = tileContext.trafficState.totalDownloadStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = HamedThemeColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    HamedWaveformGraph(
                        graphColor = HamedThemeColors.AccentCyan,
                        isActive = tileContext.isActuallyConnected,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                    )
                }
            }
        }

        // Row 2: Session traffic & duration tiles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Session traffic
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
                color = HamedThemeColors.CardBackground,
                border = BorderStroke(1.dp, HamedThemeColors.Border)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.hamed_session_traffic),
                        style = MaterialTheme.typography.labelSmall,
                        color = HamedThemeColors.TextSecondary
                    )
                    val sessionStr = HamedAetherManager.getSessionTotalTraffic()
                    Text(
                        text = sessionStr,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 16.sp
                        ),
                        color = HamedThemeColors.TextPrimary
                    )
                }
            }

            // Duration
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
                color = HamedThemeColors.CardBackground,
                border = BorderStroke(1.dp, HamedThemeColors.Border)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.hamed_connection_duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = HamedThemeColors.TextSecondary
                    )
                    Text(
                        text = formatHamedElapsed(tileContext.elapsedSeconds),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 16.sp
                        ),
                        color = HamedThemeColors.TextPrimary
                    )
                }
            }
        }
    }
}

// ============================================================
// Gestures: long-press opens the edit mode; in edit mode long-press-drag swaps tiles
// ============================================================

/** Long-press a tile to open the edit mode. Swallows taps so no click action is double-fired. */
fun Modifier.hamedTileLongPress(onLongPress: () -> Unit): Modifier =
    pointerInput("hamedTileLongPress") {
        detectTapGestures(
            onTap = { },
            onLongPress = { onLongPress() },
        )
    }

/**
 * In edit mode: long-press first, then drag; when the drag passes almost half the tile's size
 * the tile swaps with its neighbor in the display order, in the dragged direction.
 */
fun Modifier.hamedTileDrag(
    key: String,
    tileSpan: Int,
    tileSize: State<IntSize>,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onSwap: (direction: Int) -> Unit,
): Modifier = pointerInput(key, tileSpan) {
    var accumulated = Offset.Zero
    detectDragGesturesAfterLongPress(
        onDragStart = { offset ->
            accumulated = Offset.Zero
            onDragStart()
        },
        onDragEnd = onDragEnd,
        onDragCancel = onDragEnd,
        onDrag = { change, dragAmount ->
            change.consume()
            accumulated += dragAmount
            val box = tileSize.value
            val width = box.width.toFloat().coerceAtLeast(1f)
            val height = box.height.toFloat().coerceAtLeast(1f)
            val direction = when {
                tileSpan <= 1 && abs(accumulated.x) >= width * 0.45f ->
                    if (accumulated.x > 0f) 1 else -1

                abs(accumulated.y) >= height * 0.55f ->
                    if (accumulated.y > 0f) 1 else -1

                else -> 0
            }
            if (direction != 0) {
                accumulated = Offset.Zero
                onSwap(direction)
            }
        }
    )
}

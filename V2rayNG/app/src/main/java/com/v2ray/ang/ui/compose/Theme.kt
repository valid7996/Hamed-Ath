package com.v2ray.ang.ui.compose

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The five ready-made color templates of the Hamed Aether dashboard. [storage] names the value
 * persisted in MMKV and never changes once released, so a saved selection always reads correctly.
 */
enum class HamedPaletteId(val storage: String) {
    NEON("neon"),
    EMERALD("emerald"),
    VIOLET("violet"),
    SUNSET("sunset"),
    ROSE("rose");

    companion object {
        fun fromStorage(value: String?): HamedPaletteId = entries.firstOrNull { it.storage == value } ?: NEON
    }
}

/**
 * One complete color template: dark and light surface sets plus the brand colors which drive
 * gradients, badges, and connection states.
 */
data class HamedPalette(
    val id: HamedPaletteId,
    val backgroundDark: Color,
    val cardDark: Color,
    val innerDark: Color,
    val borderDark: Color,
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val textPrimaryDark: Color,
    val textSecondaryDark: Color,
) {
    val brushPrimary: Brush = Brush.horizontalGradient(listOf(primary, secondary))
    val brushAccent: Brush = Brush.horizontalGradient(listOf(accent, secondary))
    val brushSuccess: Brush = Brush.horizontalGradient(listOf(SuccessGreen, Color(0xFF00B0FF)))
    val brushConnecting: Brush = Brush.horizontalGradient(listOf(WarningOrange, Color(0xFFFFAB00)))
    val brushPremium: Brush = Brush.horizontalGradient(listOf(Color(0xFFFFD34D), Color(0xFFFF8A38)))

    companion object {
        val SuccessGreen = Color(0xFF00E676)
        val WarningOrange = Color(0xFFFF9100)
        val ErrorRed = Color(0xFFFF1744)
    }
}

private val NeonPalette = HamedPalette(
    id = HamedPaletteId.NEON,
    backgroundDark = Color(0xFF050B19),
    cardDark = Color(0xFF0D1930),
    innerDark = Color(0xFF070F20),
    borderDark = Color(0xFF1E2D4A),
    primary = Color(0xFF087BFF),
    secondary = Color(0xFF713BFF),
    accent = Color(0xFF00C8FF),
    textPrimaryDark = Color(0xFFF5F7FF),
    textSecondaryDark = Color(0xFF94A9D0),
)

private val EmeraldPalette = HamedPalette(
    id = HamedPaletteId.EMERALD,
    backgroundDark = Color(0xFF04120E),
    cardDark = Color(0xFF0B241D),
    innerDark = Color(0xFF06150F),
    borderDark = Color(0xFF1D4438),
    primary = Color(0xFF00D672),
    secondary = Color(0xFF0FB8A9),
    accent = Color(0xFF6BF7BE),
    textPrimaryDark = Color(0xFFEDFFF8),
    textSecondaryDark = Color(0xFF86BEA9),
)

private val VioletPalette = HamedPalette(
    id = HamedPaletteId.VIOLET,
    backgroundDark = Color(0xFF10051B),
    cardDark = Color(0xFF1C0B31),
    innerDark = Color(0xFF0A0517),
    borderDark = Color(0xFF3A2058),
    primary = Color(0xFFA855F7),
    secondary = Color(0xFF6D28D9),
    accent = Color(0xFFE879F9),
    textPrimaryDark = Color(0xFFF7F2FF),
    textSecondaryDark = Color(0xFFA98FC5),
)

private val SunsetPalette = HamedPalette(
    id = HamedPaletteId.SUNSET,
    backgroundDark = Color(0xFF170B04),
    cardDark = Color(0xFF26130A),
    innerDark = Color(0xFF0E0702),
    borderDark = Color(0xFF55301E),
    primary = Color(0xFFFF8A00),
    secondary = Color(0xFFFF5252),
    accent = Color(0xFFFFB74D),
    textPrimaryDark = Color(0xFFFFF6EE),
    textSecondaryDark = Color(0xFFC39A7B),
)

private val RosePalette = HamedPalette(
    id = HamedPaletteId.ROSE,
    backgroundDark = Color(0xFF160509),
    cardDark = Color(0xFF26090F),
    innerDark = Color(0xFF0D0406),
    borderDark = Color(0xFF521D2A),
    primary = Color(0xFFFF3D71),
    secondary = Color(0xFFE11D48),
    accent = Color(0xFFFF8FB1),
    textPrimaryDark = Color(0xFFFFF2F5),
    textSecondaryDark = Color(0xFFC08A97),
)

/** All palettes, in the order a picker shows them. */
val HamedPalettes: List<HamedPalette> = listOf(
    NeonPalette,
    EmeraldPalette,
    VioletPalette,
    SunsetPalette,
    RosePalette,
)

fun hamedPalette(id: HamedPaletteId): HamedPalette = HamedPalettes.first { it.id == id }

/**
 * The color holder every Hamed Aether surface reads. It always names the current template and
 * mode; [ThemeManager] feeds state flows that make [AppTheme] reapply and recompose from them.
 */
object HamedThemeColors {
    var Background: Color = NeonPalette.backgroundDark
        private set
    var CardBackground: Color = NeonPalette.cardDark
        private set
    var InnerCard: Color = NeonPalette.innerDark
        private set
    var Border: Color = NeonPalette.borderDark
        private set
    var PrimaryBlue: Color = NeonPalette.primary
        private set
    var SecondaryPurple: Color = NeonPalette.secondary
        private set
    var AccentCyan: Color = NeonPalette.accent
        private set
    var TextPrimary: Color = NeonPalette.textPrimaryDark
        private set
    var TextSecondary: Color = NeonPalette.textSecondaryDark
        private set
    var PrimaryGradient: Brush = NeonPalette.brushPrimary
        private set
    var CyanPurpleGradient: Brush = NeonPalette.brushAccent
        private set
    /** Connection-state and premium gradients, the same for every template. */
    var ConnectingGradient: Brush = NeonPalette.brushConnecting
        private set
    var SuccessGradient: Brush = NeonPalette.brushSuccess
        private set
    var PremiumGradient: Brush = NeonPalette.brushPremium
        private set
    var CardBorder: BorderStroke = BorderStroke(1.dp, Border)
        private set

    private val SuccessGreen = HamedPalette.SuccessGreen
    private val WarningOrange = HamedPalette.WarningOrange
    private val ErrorRed = HamedPalette.ErrorRed

    /** The template and mode last applied, to skip redundant work on unchanged recompositions. */
    private var appliedId: HamedPaletteId? = null
    private var appliedDark: Boolean? = null

    /** Success green for every template, connection state coloring. */
    val Success: Color get() = SuccessGreen
    val Warning: Color get() = WarningOrange
    val Error: Color get() = ErrorRed

    /** Applies [palette] to every named color, with the surfaces of [darkMode]. */
    fun apply(palette: HamedPalette, darkMode: Boolean) {
        if (appliedId == palette.id && appliedDark == darkMode) return
        appliedId = palette.id
        appliedDark = darkMode

        val white = Color(0xFFFFFFFF)
        Background = if (darkMode) palette.backgroundDark else lerp(palette.backgroundDark, white, 0.94f)
        CardBackground = if (darkMode) palette.cardDark else lerp(palette.cardDark, white, 0.96f)
        InnerCard = if (darkMode) palette.innerDark else lerp(palette.innerDark, white, 0.90f)
        Border = if (darkMode) palette.borderDark else lerp(white, palette.secondary, 0.20f)
        PrimaryBlue = palette.primary
        SecondaryPurple = palette.secondary
        AccentCyan = palette.accent
        TextPrimary = if (darkMode) palette.textPrimaryDark
        else lerp(Color(0xFF111827), palette.primary, 0.28f)
        TextSecondary = if (darkMode) palette.textSecondaryDark
        else lerp(Color(0xFF5A6472), palette.primary, 0.45f)
        PrimaryGradient = palette.brushPrimary
        CyanPurpleGradient = palette.brushAccent
        ConnectingGradient = palette.brushConnecting
        SuccessGradient = palette.brushSuccess
        PremiumGradient = palette.brushPremium
        CardBorder = BorderStroke(1.dp, Border)
    }
}

// Semantic Colors
val colorPing = Color(0xFF009966) // Green
val colorPingRed = Color(0xFFFF0099) // Pink Red
val colorConfigType = Color(0xFFf97910) // Orange
val colorFabActive = Color(0xFFf97910) // Orange
val colorFabInactiveLight = Color(0xFF9C9C9C) // Gray
val colorFabInactiveDark = Color(0xFF646464) // Dark Gray
val dividerColorLight = Color(0xFFE0E0E0) // Light Gray
val dividerColorDark = Color(0xFF424242) // Dark Gray

// Toast Colors 85%
val toastNormalBgLight = Color(0xD9353A3E) // Dark Gray
val toastNormalBgDark = Color(0xD94A4F54) // Darker Gray
val toastSuccessBg = Color(0xD9388E3C) // Green
val toastErrorBg = Color(0xD9D50000) // Red
val toastInfoBg = Color(0xD93F51B5) // Indigo Blue
val toastIconCircleBg = Color(0x33FFFFFF) // Semi-transparent White
val toastTextColor = Color.White // White

object ThemeManager {
    private val _themeMode = MutableStateFlow(readThemeMode())
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _palette = MutableStateFlow(
        HamedPaletteId.fromStorage(MmkvManager.decodeSettingsString(AppConfig.PREF_HAMED_PALETTE))
    )
    val palette: StateFlow<HamedPaletteId> = _palette.asStateFlow()

    private val _dynamicColorEnabled = MutableStateFlow(
        MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, true)
    )
    val dynamicColorEnabled: StateFlow<Boolean> = _dynamicColorEnabled.asStateFlow()

    private val _fontScale = MutableStateFlow(readFontScale())
    val fontScale: StateFlow<Float> = _fontScale.asStateFlow()

    private fun readThemeMode(): String =
        MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"

    private fun readFontScale(): Float {
        val raw = MmkvManager.decodeSettingsString(AppConfig.PREF_HAMED_FONT_SCALE) ?: return DEFAULT_FONT_SCALE
        return raw.toFloatOrNull()?.takeIf { it in MIN_FONT_SCALE..MAX_FONT_SCALE } ?: DEFAULT_FONT_SCALE
    }

    fun setThemeMode(mode: String) {
        MmkvManager.encodeSettings(AppConfig.PREF_UI_MODE_NIGHT, mode)
        _themeMode.value = mode
    }

    fun setPalette(id: HamedPaletteId) {
        MmkvManager.encodeSettings(AppConfig.PREF_HAMED_PALETTE, id.storage)
        _palette.value = id
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_DYNAMIC_COLOR, enabled)
        _dynamicColorEnabled.value = enabled
    }

    /** The dashboard font step in [MIN_FONT_SCALE, MAX_FONT_SCALE], persisting the exact step value. */
    fun setFontScale(scale: Float) {
        val step = HamedFontScale.entries.firstOrNull { it.scale == scale } ?: HamedFontScale.MEDIUM
        MmkvManager.encodeSettings(AppConfig.PREF_HAMED_FONT_SCALE, step.storage)
        _fontScale.value = step.scale
    }

    fun refresh() {
        _themeMode.value = readThemeMode()
        _palette.value = HamedPaletteId.fromStorage(MmkvManager.decodeSettingsString(AppConfig.PREF_HAMED_PALETTE))
        _dynamicColorEnabled.value =
            MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, true)
        _fontScale.value = readFontScale()
    }

    const val MIN_FONT_SCALE = 0.85f
    const val MAX_FONT_SCALE = 1.30f
    const val DEFAULT_FONT_SCALE = 1.0f
}

/** The stored font steps; [storage] is the persisted MMKV value, stable across releases. */
enum class HamedFontScale(val scale: Float, val storage: String) {
    SMALL(0.85f, "0.85"),
    MEDIUM(1.0f, "1.0"),
    LARGE(1.15f, "1.15"),
    XLARGE(1.30f, "1.30");

    companion object {
        fun fromStorage(value: String?): HamedFontScale = entries.firstOrNull { it.storage == value } ?: MEDIUM
    }
}

@Composable
fun resolveDarkTheme(): Boolean {
    val mode by ThemeManager.themeMode.collectAsState()
    return when (mode) {
        "1" -> false
        "2" -> true
        else -> isSystemInDarkTheme()
    }
}

val LocalDarkTheme = compositionLocalOf { false }

@Composable
private fun hamedColorScheme(palette: HamedPalette): androidx.compose.material3.ColorScheme {
    // Palette mode colors were applied by AppTheme before this reads the holder.
    return darkColorScheme(
        primary = HamedThemeColors.PrimaryBlue,
        onPrimary = Color.White,
        primaryContainer = HamedThemeColors.CardBackground,
        onPrimaryContainer = HamedThemeColors.TextPrimary,
        secondary = HamedThemeColors.SecondaryPurple,
        onSecondary = Color.White,
        secondaryContainer = HamedThemeColors.InnerCard,
        onSecondaryContainer = HamedThemeColors.AccentCyan,
        tertiary = HamedThemeColors.AccentCyan,
        onTertiary = HamedThemeColors.Background,
        tertiaryContainer = HamedThemeColors.InnerCard,
        onTertiaryContainer = HamedThemeColors.AccentCyan,
        background = HamedThemeColors.Background,
        onBackground = HamedThemeColors.TextPrimary,
        surface = HamedThemeColors.CardBackground,
        onSurface = HamedThemeColors.TextPrimary,
        surfaceVariant = HamedThemeColors.InnerCard,
        onSurfaceVariant = HamedThemeColors.TextSecondary,
        outline = HamedThemeColors.Border,
        outlineVariant = HamedThemeColors.CardBackground,
        surfaceContainer = HamedThemeColors.CardBackground,
        surfaceContainerHigh = HamedThemeColors.InnerCard,
        surfaceContainerHighest = HamedThemeColors.Border,
        surfaceContainerLow = HamedThemeColors.InnerCard,
        surfaceContainerLowest = HamedThemeColors.Background,
        error = HamedPalette.ErrorRed,
        onError = Color.White,
    )
}

@Composable
fun AppTheme(
    darkTheme: Boolean = resolveDarkTheme(),
    content: @Composable () -> Unit
) {
    val dynamicColor by ThemeManager.dynamicColorEnabled.collectAsState()
    val paletteId by ThemeManager.palette.collectAsState()
    val fontScale by ThemeManager.fontScale.collectAsState()
    val context = LocalContext.current
    val palette = hamedPalette(paletteId)

    // The template colors feed every Hamed surface by name; applying before the color scheme and
    // content of this recomposition keeps the whole tree consistent in the same pass.
    HamedThemeColors.apply(palette, darkTheme)
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        else -> hamedColorScheme(palette)
    }
    val snackbarController = rememberAppSnackbarController()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    val baseDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalAppSnackbar provides snackbarController,
        LocalDensity provides Density(baseDensity.density, baseDensity.fontScale * fontScale),
    ) {
        MaterialTheme(
            colorScheme = colorScheme
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AppSnackbarBridge(controller = snackbarController)
                content()
                AppSnackbarHost(hostState = snackbarController.hostState)
            }
        }
    }
}

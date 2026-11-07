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
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object HamedThemeColors {
    val Background = Color(0xFF050B19)
    val CardBackground = Color(0xFF0D1930)
    val InnerCard = Color(0xFF070F20)
    val Border = Color(0xFF1E2D4A)
    val PrimaryBlue = Color(0xFF087BFF)
    val SecondaryPurple = Color(0xFF713BFF)
    val AccentCyan = Color(0xFF00C8FF)
    val TextPrimary = Color(0xFFF5F7FF)
    val TextSecondary = Color(0xFF94A9D0)
    val SuccessGreen = Color(0xFF00E676)
    val WarningOrange = Color(0xFFFF9100)
    val ErrorRed = Color(0xFFFF1744)

    val PrimaryGradient = Brush.horizontalGradient(listOf(PrimaryBlue, SecondaryPurple))
    val CyanPurpleGradient = Brush.horizontalGradient(listOf(AccentCyan, SecondaryPurple))
    val CardBorder = BorderStroke(1.dp, Border)
}

private val LightColor = darkColorScheme(
    primary = Color(0xFF087BFF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF0D1930),
    onPrimaryContainer = Color(0xFFF5F7FF),
    secondary = Color(0xFF713BFF),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF142442),
    onSecondaryContainer = Color(0xFF00C8FF),
    tertiary = Color(0xFF00C8FF),
    onTertiary = Color(0xFF050B19),
    tertiaryContainer = Color(0xFF070F20),
    onTertiaryContainer = Color(0xFF00C8FF),
    background = Color(0xFF050B19),
    onBackground = Color(0xFFF5F7FF),
    surface = Color(0xFF0D1930),
    onSurface = Color(0xFFF5F7FF),
    surfaceVariant = Color(0xFF070F20),
    onSurfaceVariant = Color(0xFF94A9D0),
    outline = Color(0xFF1E2D4A),
    outlineVariant = Color(0xFF142442),
    surfaceContainer = Color(0xFF0D1930),
    surfaceContainerHigh = Color(0xFF142442),
    surfaceContainerHighest = Color(0xFF1A3057),
    surfaceContainerLow = Color(0xFF070F20),
    surfaceContainerLowest = Color(0xFF050B19),
)

private val DarkColor = darkColorScheme(
    primary = Color(0xFF087BFF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF0D1930),
    onPrimaryContainer = Color(0xFFF5F7FF),
    secondary = Color(0xFF713BFF),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF142442),
    onSecondaryContainer = Color(0xFF00C8FF),
    tertiary = Color(0xFF00C8FF),
    onTertiary = Color(0xFF050B19),
    tertiaryContainer = Color(0xFF070F20),
    onTertiaryContainer = Color(0xFF00C8FF),
    background = Color(0xFF050B19),
    onBackground = Color(0xFFF5F7FF),
    surface = Color(0xFF0D1930),
    onSurface = Color(0xFFF5F7FF),
    surfaceVariant = Color(0xFF070F20),
    onSurfaceVariant = Color(0xFF94A9D0),
    outline = Color(0xFF1E2D4A),
    outlineVariant = Color(0xFF142442),
    surfaceContainer = Color(0xFF0D1930),
    surfaceContainerHigh = Color(0xFF142442),
    surfaceContainerHighest = Color(0xFF1A3057),
    surfaceContainerLow = Color(0xFF070F20),
    surfaceContainerLowest = Color(0xFF050B19),
)

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
    private val _themeMode = MutableStateFlow(
        MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _dynamicColorEnabled = MutableStateFlow(
        MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, true)
    )
    val dynamicColorEnabled: StateFlow<Boolean> = _dynamicColorEnabled.asStateFlow()

    fun setThemeMode(mode: String) {
        MmkvManager.encodeSettings(AppConfig.PREF_UI_MODE_NIGHT, mode)
        _themeMode.value = mode
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_DYNAMIC_COLOR, enabled)
        _dynamicColorEnabled.value = enabled
    }

    fun refresh() {
        _themeMode.value =
            MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
        _dynamicColorEnabled.value =
            MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, true)
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
fun AppTheme(
    darkTheme: Boolean = resolveDarkTheme(),
    content: @Composable () -> Unit
) {
    val dynamicColor by ThemeManager.dynamicColorEnabled.collectAsState()
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColor
        else -> LightColor
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

    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalAppSnackbar provides snackbarController
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

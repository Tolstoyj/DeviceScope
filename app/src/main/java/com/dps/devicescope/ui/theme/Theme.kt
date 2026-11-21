package com.dps.devicescope.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.dps.devicescope.data.model.StatusLevel
import com.dps.devicescope.data.model.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutline.copy(alpha = 0.6f),
    error = StatusCriticalDark
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutline.copy(alpha = 0.7f),
    error = StatusCriticalLight
)

@Immutable
data class TelemetryColors(
    val good: Color,
    val info: Color,
    val warning: Color,
    val critical: Color,
    val neutral: Color,
    val isDark: Boolean
) {
    fun forLevel(level: StatusLevel): Color = when (level) {
        StatusLevel.GOOD -> good
        StatusLevel.INFO -> info
        StatusLevel.WARNING -> warning
        StatusLevel.CRITICAL -> critical
        StatusLevel.NEUTRAL -> neutral
    }
}

val LocalTelemetryColors = staticCompositionLocalOf {
    TelemetryColors(
        good = StatusGoodDark,
        info = StatusInfoDark,
        warning = StatusWarningDark,
        critical = StatusCriticalDark,
        neutral = StatusNeutralDark,
        isDark = true
    )
}

@Composable
fun resolveDarkTheme(themeMode: ThemeMode): Boolean = when (themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
}

@Composable
fun DeviceScopeTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isDark = resolveDarkTheme(themeMode)
    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme
    val telemetryColors = if (isDark) {
        TelemetryColors(
            good = StatusGoodDark,
            info = StatusInfoDark,
            warning = StatusWarningDark,
            critical = StatusCriticalDark,
            neutral = StatusNeutralDark,
            isDark = true
        )
    } else {
        TelemetryColors(
            good = StatusGoodLight,
            info = StatusInfoLight,
            warning = StatusWarningLight,
            critical = StatusCriticalLight,
            neutral = StatusNeutralLight,
            isDark = false
        )
    }

    CompositionLocalProvider(LocalTelemetryColors provides telemetryColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = DeviceScopeTypography,
            content = content
        )
    }
}

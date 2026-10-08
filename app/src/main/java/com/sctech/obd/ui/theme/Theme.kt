package com.sctech.obd.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.sctech.obd.data.Severity
import com.sctech.obd.data.ThemeMode

/**
 * SCTech design tokens, shared with the SCTech drone app (FreeFCC-main):
 * near-black surfaces, hairline borders, monochrome accent.
 */
@Immutable
data class SctColors(
    val bg: Color,
    val surface1: Color,
    val surface2: Color,
    val hairline: Color,
    val accent: Color,
    val onAccent: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val topBarTop: Color,
    val isDark: Boolean,
)

private val DarkSct = SctColors(
    bg = Color(0xFF0A0A0B),
    surface1 = Color(0xFF161618),
    surface2 = Color(0xFF212124),
    hairline = Color(0x1AFFFFFF),
    accent = Color(0xFFF5F5F7),
    onAccent = Color(0xFF0A0A0B),
    success = Color(0xFF30D158),
    warning = Color(0xFFFFB020),
    danger = Color(0xFFFF453A),
    textPrimary = Color(0xFFF5F5F7),
    textSecondary = Color(0xFF9A9AA0),
    textTertiary = Color(0xFF5C5C63),
    topBarTop = Color(0xFF1A1A1D),
    isDark = true,
)

/** Same language inverted for daylight: white panels, black accent. */
private val LightSct = SctColors(
    bg = Color(0xFFF2F2F4),
    surface1 = Color(0xFFFFFFFF),
    surface2 = Color(0xFFEBEBEE),
    hairline = Color(0x17000000),
    accent = Color(0xFF111113),
    onAccent = Color(0xFFFFFFFF),
    success = Color(0xFF1E8E3E),
    warning = Color(0xFFB26A00),
    danger = Color(0xFFD70015),
    textPrimary = Color(0xFF111113),
    textSecondary = Color(0xFF6E6E73),
    textTertiary = Color(0xFF9E9EA4),
    topBarTop = Color(0xFFFFFFFF),
    isDark = false,
)

private val LocalSctColors = staticCompositionLocalOf { DarkSct }

object Sct {
    val colors: SctColors
        @Composable @ReadOnlyComposable get() = LocalSctColors.current
}

@Composable
@ReadOnlyComposable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun ObdTheme(dark: Boolean, content: @Composable () -> Unit) {
    val c = if (dark) DarkSct else LightSct
    CompositionLocalProvider(LocalSctColors provides c) {
        MaterialTheme(colorScheme = c.toMaterial(), content = content)
    }
}

/** Material components (dialogs, menus, checkboxes) pick up the SCTech palette. */
private fun SctColors.toMaterial(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent, onPrimary = onAccent,
        primaryContainer = surface2, onPrimaryContainer = textPrimary,
        secondary = success, onSecondary = onAccent,
        secondaryContainer = surface2, onSecondaryContainer = textPrimary,
        tertiary = warning,
        background = bg, onBackground = textPrimary,
        surface = surface1, onSurface = textPrimary,
        surfaceVariant = surface2, onSurfaceVariant = textSecondary,
        surfaceContainerLowest = surface1, surfaceContainerLow = surface1,
        surfaceContainer = surface1, surfaceContainerHigh = surface1,
        surfaceContainerHighest = surface2,
        error = danger, onError = Color.White,
        outline = hairline, outlineVariant = hairline,
    )
}

@Composable
@ReadOnlyComposable
fun Severity.color(): Color = when (this) {
    Severity.LOW -> Sct.colors.success
    Severity.MEDIUM -> Sct.colors.warning
    Severity.HIGH -> Sct.colors.danger
}

package com.sangita.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.sangita.app.data.local.preferences.UserPreferences

data class SangitaColors(
    val background: Color,
    val surface: Color,
    val card: Color,
    val cardHover: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val border: Color,
    val borderHi: Color,
    val accent: Color
)

val LocalSangitaColors = staticCompositionLocalOf {
    SangitaColors(
        background = Color(0xFF080B11),
        surface = Color(0xFF0D111A),
        card = Color(0xFF111622),
        cardHover = Color(0xFF182030),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFF94A3B8),
        textMuted = Color(0xFF64748B),
        border = Color(0xFF1A2234),
        borderHi = Color(0xFF2D3B55),
        accent = Color(0xFF38BDF8)
    )
}

object SangitaThemeColors {
    val current: SangitaColors
        @Composable
        get() = LocalSangitaColors.current
}

private val ObsidianColorScheme = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF080B11),
    primaryContainer = Color(0xFF0284C7),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFF60A5FA),
    onSecondary = Color(0xFF080B11),
    background = Color(0xFF080B11),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF0D111A),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF111622),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF1A2234),
    error = SangitaRed
)

private val MidnightDarkColorScheme = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF080B11),
    primaryContainer = Color(0xFF0369A1),
    onPrimaryContainer = Color(0xFFF0F6FC),
    secondary = Color(0xFF60A5FA),
    onSecondary = Color(0xFF080B11),
    background = Color(0xFF080B11),
    onBackground = Color(0xFFF0F6FC),
    surface = Color(0xFF0D111A),
    onSurface = Color(0xFFF0F6FC),
    surfaceVariant = Color(0xFF111622),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF1A2234),
    error = SangitaRed
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBAE6FD),
    onPrimaryContainer = Color(0xFF0369A1),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color.White,
    background = Color(0xFFF1F5F9),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    error = Color(0xFFDC2626)
)

@Composable
fun SangitaTheme(
    themeMode: String = UserPreferences.THEME_AMOLED,
    content: @Composable () -> Unit
) {
    val isLight = themeMode == UserPreferences.THEME_LIGHT
    val colorScheme = when (themeMode) {
        UserPreferences.THEME_LIGHT -> LightColorScheme
        UserPreferences.THEME_DARK -> MidnightDarkColorScheme
        else -> ObsidianColorScheme
    }

    val customColors = when (themeMode) {
        UserPreferences.THEME_LIGHT -> SangitaColors(
            background = Color(0xFFF8FAFC),
            surface = Color(0xFFFFFFFF),
            card = Color(0xFFFFFFFF),
            cardHover = Color(0xFFF1F5F9),
            textPrimary = Color(0xFF0F172A),
            textSecondary = Color(0xFF475569),
            textMuted = Color(0xFF64748B),
            border = Color(0xFFE2E8F0),
            borderHi = Color(0xFFCBD5E1),
            accent = Color(0xFF0284C7)
        )
        UserPreferences.THEME_DARK -> SangitaColors(
            background = Color(0xFF080B11),
            surface = Color(0xFF0D111A),
            card = Color(0xFF111622),
            cardHover = Color(0xFF182030),
            textPrimary = Color(0xFFF0F6FC),
            textSecondary = Color(0xFF94A3B8),
            textMuted = Color(0xFF64748B),
            border = Color(0xFF1A2234),
            borderHi = Color(0xFF2D3B55),
            accent = Color(0xFF38BDF8)
        )
        else -> SangitaColors(
            background = Color(0xFF080B11),
            surface = Color(0xFF0D111A),
            card = Color(0xFF111622),
            cardHover = Color(0xFF182030),
            textPrimary = Color(0xFFFFFFFF),
            textSecondary = Color(0xFF94A3B8),
            textMuted = Color(0xFF64748B),
            border = Color(0xFF1A2234),
            borderHi = Color(0xFF2D3B55),
            accent = Color(0xFF38BDF8)
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val bgArgb = customColors.background.toArgb()
            window.statusBarColor = bgArgb
            window.navigationBarColor = bgArgb
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = isLight
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = isLight
        }
    }

    CompositionLocalProvider(LocalSangitaColors provides customColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

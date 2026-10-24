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
    val backgroundSec: Color,
    val surface: Color,
    val elevated: Color,
    val card: Color,
    val cardHover: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val border: Color,
    val borderHi: Color,
    val accent: Color,
    val auraBlue: Color,
    val lavender: Color
)

val LocalSangitaColors = staticCompositionLocalOf {
    SangitaColors(
        background = AmbientBackground,
        backgroundSec = AmbientBackgroundSec,
        surface = AmbientCard,
        elevated = AmbientElevated,
        card = AmbientCard,
        cardHover = AmbientElevated,
        textPrimary = AmbientTextPrimary,
        textSecondary = AmbientTextSecondary,
        textMuted = AmbientTextMuted,
        border = AmbientBorder,
        borderHi = AmbientBorderHi,
        accent = AmbientAccentBlue,
        auraBlue = AmbientAuraBlue,
        lavender = AmbientLavender
    )
}

object SangitaThemeColors {
    val current: SangitaColors
        @Composable
        get() = LocalSangitaColors.current
}

private val AmbientDarkColorScheme = darkColorScheme(
    primary = AmbientAccentBlue,
    onPrimary = AmbientTextPrimary,
    primaryContainer = AmbientAuraBlue,
    onPrimaryContainer = AmbientTextPrimary,
    secondary = AmbientLavender,
    onSecondary = AmbientBackground,
    background = AmbientBackground,
    onBackground = AmbientTextPrimary,
    surface = AmbientCard,
    onSurface = AmbientTextPrimary,
    surfaceVariant = AmbientElevated,
    onSurfaceVariant = AmbientTextSecondary,
    outline = AmbientBorder,
    error = SangitaRed
)

private val AmoledColorScheme = darkColorScheme(
    primary = AmbientAccentBlue,
    onPrimary = Color(0xFF000000),
    primaryContainer = AmbientAuraBlue,
    onPrimaryContainer = AmbientTextPrimary,
    secondary = AmbientLavender,
    onSecondary = Color(0xFF000000),
    background = Color(0xFF000000),
    onBackground = AmbientTextPrimary,
    surface = Color(0xFF080A0F),
    onSurface = AmbientTextPrimary,
    surfaceVariant = AmbientCard,
    onSurfaceVariant = AmbientTextSecondary,
    outline = AmbientBorder,
    error = SangitaRed
)

private val ObsidianColorScheme = AmoledColorScheme
private val MidnightDarkColorScheme = AmbientDarkColorScheme

private val LightColorScheme = lightColorScheme(
    primary = AmbientAccentBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBAE6FD),
    onPrimaryContainer = Color(0xFF0369A1),
    secondary = AmbientLavender,
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
    themeMode: String = UserPreferences.THEME_DARK,
    content: @Composable () -> Unit
) {
    val isLight = themeMode == UserPreferences.THEME_LIGHT
    val colorScheme = when (themeMode) {
        UserPreferences.THEME_LIGHT -> LightColorScheme
        UserPreferences.THEME_AMOLED -> AmoledColorScheme
        UserPreferences.THEME_DARK -> AmbientDarkColorScheme
        else -> AmbientDarkColorScheme
    }

    val customColors = when (themeMode) {
        UserPreferences.THEME_LIGHT -> SangitaColors(
            background = Color(0xFFF8FAFC),
            backgroundSec = Color(0xFFEDF2F7),
            surface = Color(0xFFFFFFFF),
            elevated = Color(0xFFF1F5F9),
            card = Color(0xFFFFFFFF),
            cardHover = Color(0xFFF1F5F9),
            textPrimary = Color(0xFF0F172A),
            textSecondary = Color(0xFF475569),
            textMuted = Color(0xFF64748B),
            border = Color(0xFFE2E8F0),
            borderHi = Color(0xFFCBD5E1),
            accent = AmbientAccentBlue,
            auraBlue = AmbientAuraBlue,
            lavender = AmbientLavender
        )
        UserPreferences.THEME_AMOLED -> SangitaColors(
            background = Color(0xFF000000),
            backgroundSec = Color(0xFF080A0F),
            surface = Color(0xFF080A0F),
            elevated = AmbientElevated,
            card = AmbientCard,
            cardHover = AmbientElevated,
            textPrimary = AmbientTextPrimary,
            textSecondary = AmbientTextSecondary,
            textMuted = AmbientTextMuted,
            border = AmbientBorder,
            borderHi = AmbientBorderHi,
            accent = AmbientAccentBlue,
            auraBlue = AmbientAuraBlue,
            lavender = AmbientLavender
        )
        UserPreferences.THEME_DARK -> SangitaColors(
            background = AmbientBackground,
            backgroundSec = AmbientBackgroundSec,
            surface = AmbientCard,
            elevated = AmbientElevated,
            card = AmbientCard,
            cardHover = AmbientElevated,
            textPrimary = AmbientTextPrimary,
            textSecondary = AmbientTextSecondary,
            textMuted = AmbientTextMuted,
            border = AmbientBorder,
            borderHi = AmbientBorderHi,
            accent = AmbientAccentBlue,
            auraBlue = AmbientAuraBlue,
            lavender = AmbientLavender
        )
        else -> SangitaColors(
            background = AmbientBackground,
            backgroundSec = AmbientBackgroundSec,
            surface = AmbientCard,
            elevated = AmbientElevated,
            card = AmbientCard,
            cardHover = AmbientElevated,
            textPrimary = AmbientTextPrimary,
            textSecondary = AmbientTextSecondary,
            textMuted = AmbientTextMuted,
            border = AmbientBorder,
            borderHi = AmbientBorderHi,
            accent = AmbientAccentBlue,
            auraBlue = AmbientAuraBlue,
            lavender = AmbientLavender
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

package com.sangita.app.ui.theme

import android.app.Activity
import android.os.Build
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
    val lavender: Color,
    val buttonBg: Color = SteelBlueLight,
    val buttonContent: Color = DeepNavyButtonText,
    val warningBg: Color = SemanticWarningBg,
    val isAmoled: Boolean = false
)

val LocalSangitaColors = staticCompositionLocalOf {
    SangitaColors(
        background = GraphiteBackground,
        backgroundSec = GraphiteCard,
        surface = GraphiteCard,
        elevated = GraphiteElevated,
        card = GraphiteCard,
        cardHover = GraphiteElevated,
        textPrimary = GraphiteTextPrimary,
        textSecondary = GraphiteTextSecondary,
        textMuted = GraphiteTextMuted,
        border = GraphiteBorder,
        borderHi = AmbientBorderHi,
        accent = SteelBlueLight,
        auraBlue = AmbientAuraBlue,
        lavender = AmbientLavender,
        buttonBg = SteelBlueLight,
        buttonContent = DeepNavyButtonText,
        warningBg = SemanticWarningBg,
        isAmoled = false
    )
}

object SangitaThemeColors {
    val current: SangitaColors
        @Composable
        get() = LocalSangitaColors.current
}

internal val AmbientDarkColorScheme = darkColorScheme(
    primary = SteelBlueLight,
    onPrimary = DeepNavyButtonText,
    primaryContainer = GraphiteElevated,
    onPrimaryContainer = GraphiteTextPrimary,
    secondary = AmbientLavender,
    onSecondary = GraphiteBackground,
    background = GraphiteBackground,
    onBackground = GraphiteTextPrimary,
    surface = GraphiteCard,
    onSurface = GraphiteTextPrimary,
    surfaceVariant = GraphiteElevated,
    onSurfaceVariant = GraphiteTextSecondary,
    outline = GraphiteBorder,
    error = SemanticError
)

internal val AmoledColorScheme = darkColorScheme(
    primary = SteelBlueLight,
    onPrimary = DeepNavyButtonText,
    primaryContainer = AmoledElevated,
    onPrimaryContainer = GraphiteTextPrimary,
    secondary = AmbientLavender,
    onSecondary = AmoledBackground,
    background = AmoledBackground,
    onBackground = GraphiteTextPrimary,
    surface = AmoledBackground,
    onSurface = GraphiteTextPrimary,
    surfaceVariant = AmoledElevated,
    onSurfaceVariant = GraphiteTextSecondary,
    outline = AmoledBorder,
    error = SemanticError
)

internal val ObsidianColorScheme = AmoledColorScheme
internal val MidnightDarkColorScheme = AmbientDarkColorScheme

internal val LightColorScheme = lightColorScheme(
    primary = SteelBluePrimary,
    onPrimary = Color.White,
    primaryContainer = LightElevated,
    onPrimaryContainer = LightTextPrimary,
    secondary = SteelBlueLight,
    onSecondary = Color.White,
    background = LightPageBackground,
    onBackground = LightTextPrimary,
    surface = LightCard,
    onSurface = LightTextPrimary,
    surfaceVariant = LightElevated,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    error = SemanticError
)

fun getSangitaColorScheme(themeMode: String): androidx.compose.material3.ColorScheme = when (themeMode) {
    UserPreferences.THEME_LIGHT -> LightColorScheme
    UserPreferences.THEME_AMOLED -> AmoledColorScheme
    UserPreferences.THEME_DARK -> AmbientDarkColorScheme
    else -> AmbientDarkColorScheme
}

fun getSangitaColors(themeMode: String): SangitaColors = when (themeMode) {
    UserPreferences.THEME_LIGHT -> SangitaColors(
        background = LightPageBackground,
        backgroundSec = LightElevated,
        surface = LightCard,
        elevated = LightElevated,
        card = LightCard,
        cardHover = LightElevated,
        textPrimary = LightTextPrimary,
        textSecondary = LightTextSecondary,
        textMuted = LightTextMuted,
        border = LightBorder,
        borderHi = Color(0xFFD4D4D8),
        accent = SteelBluePrimary,
        auraBlue = AmbientAuraBlue,
        lavender = AmbientLavender,
        buttonBg = SteelBluePrimary,
        buttonContent = Color.White,
        warningBg = SemanticWarningBg,
        isAmoled = false
    )
    UserPreferences.THEME_AMOLED -> SangitaColors(
        background = AmoledBackground,
        backgroundSec = AmoledBackground,
        surface = AmoledBackground,
        elevated = AmoledElevated,
        card = AmoledCard,
        cardHover = AmoledElevated,
        textPrimary = GraphiteTextPrimary,
        textSecondary = GraphiteTextSecondary,
        textMuted = GraphiteTextMuted,
        border = AmoledBorder,
        borderHi = Color(0xFF252B37),
        accent = SteelBlueLight,
        auraBlue = AmbientAuraBlue,
        lavender = AmbientLavender,
        buttonBg = SteelBlueLight,
        buttonContent = DeepNavyButtonText,
        warningBg = SemanticWarningBg,
        isAmoled = true
    )
    UserPreferences.THEME_DARK -> SangitaColors(
        background = GraphiteBackground,
        backgroundSec = GraphiteCard,
        surface = GraphiteCard,
        elevated = GraphiteElevated,
        card = GraphiteCard,
        cardHover = GraphiteElevated,
        textPrimary = GraphiteTextPrimary,
        textSecondary = GraphiteTextSecondary,
        textMuted = GraphiteTextMuted,
        border = GraphiteBorder,
        borderHi = AmbientBorderHi,
        accent = SteelBlueLight,
        auraBlue = AmbientAuraBlue,
        lavender = AmbientLavender,
        buttonBg = SteelBlueLight,
        buttonContent = DeepNavyButtonText,
        warningBg = SemanticWarningBg,
        isAmoled = false
    )
    else -> SangitaColors(
        background = GraphiteBackground,
        backgroundSec = GraphiteCard,
        surface = GraphiteCard,
        elevated = GraphiteElevated,
        card = GraphiteCard,
        cardHover = GraphiteElevated,
        textPrimary = GraphiteTextPrimary,
        textSecondary = GraphiteTextSecondary,
        textMuted = GraphiteTextMuted,
        border = GraphiteBorder,
        borderHi = AmbientBorderHi,
        accent = SteelBlueLight,
        auraBlue = AmbientAuraBlue,
        lavender = AmbientLavender,
        buttonBg = SteelBlueLight,
        buttonContent = DeepNavyButtonText,
        warningBg = SemanticWarningBg,
        isAmoled = false
    )
}

@Composable
fun SangitaTheme(
    themeMode: String = UserPreferences.THEME_DARK,
    content: @Composable () -> Unit
) {
    val isLight = themeMode == UserPreferences.THEME_LIGHT
    val colorScheme = getSangitaColorScheme(themeMode)
    val customColors = getSangitaColors(themeMode)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = isLight
            insetsController.isAppearanceLightNavigationBars = isLight

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                @Suppress("DEPRECATION")
                window.statusBarColor = customColors.background.toArgb()
                @Suppress("DEPRECATION")
                window.navigationBarColor = customColors.background.toArgb()
            }
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

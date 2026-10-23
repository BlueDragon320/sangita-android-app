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
        background = Color(0xFF000000),
        surface = Color(0xFF0C0C0E),
        card = Color(0xFF18181B),
        cardHover = Color(0xFF27272A),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFA1A1AA),
        textMuted = Color(0xFF71717A),
        border = Color(0x1AFFFFFF),
        borderHi = Color(0x33FFFFFF),
        accent = Color(0xFF34D399)
    )
}

object SangitaThemeColors {
    val current: SangitaColors
        @Composable
        get() = LocalSangitaColors.current
}

private val AmoledColorScheme = darkColorScheme(
    primary = SangitaEmerald,
    onPrimary = RawSangitaBlack,
    primaryContainer = SangitaEmeraldDim,
    onPrimaryContainer = RawSangitaText,
    secondary = SangitaEmeraldHi,
    onSecondary = RawSangitaBlack,
    background = RawSangitaBlack,
    onBackground = RawSangitaText,
    surface = RawSangitaSurface,
    onSurface = RawSangitaText,
    surfaceVariant = RawSangitaCard,
    onSurfaceVariant = RawSangitaTextSub,
    outline = RawSangitaBorder,
    error = SangitaRed
)

private val MidnightDarkColorScheme = darkColorScheme(
    primary = Color(0xFF10B981),
    onPrimary = Color(0xFF0D1117),
    primaryContainer = Color(0xFF047857),
    onPrimaryContainer = Color(0xFFF3F4F6),
    secondary = Color(0xFF34D399),
    onSecondary = Color(0xFF0D1117),
    background = Color(0xFF0D1117),
    onBackground = Color(0xFFF3F4F6),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFF3F4F6),
    surfaceVariant = Color(0xFF21262D),
    onSurfaceVariant = Color(0xFF8B949E),
    outline = Color(0xFF30363D),
    error = SangitaRed
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF059669),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA7F3D0),
    onPrimaryContainer = Color(0xFF065F46),
    secondary = Color(0xFF10B981),
    onSecondary = Color.White,
    background = Color(0xFFF1F2F8),
    onBackground = Color(0xFF18181B),
    surface = Color.White,
    onSurface = Color(0xFF18181B),
    surfaceVariant = Color(0xFFE4E4E7),
    onSurfaceVariant = Color(0xFF52525B),
    outline = Color(0xFFD4D4D8),
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
        else -> AmoledColorScheme
    }

    val customColors = when (themeMode) {
        UserPreferences.THEME_LIGHT -> SangitaColors(
            background = Color(0xFFF4F4F5),
            surface = Color(0xFFFFFFFF),
            card = Color(0xFFFFFFFF),
            cardHover = Color(0xFFE4E4E7),
            textPrimary = Color(0xFF18181B),
            textSecondary = Color(0xFF52525B),
            textMuted = Color(0xFF71717A),
            border = Color(0xFFE4E4E7),
            borderHi = Color(0xFFD4D4D8),
            accent = Color(0xFF059669)
        )
        UserPreferences.THEME_DARK -> SangitaColors(
            background = Color(0xFF0D1117),
            surface = Color(0xFF161B22),
            card = Color(0xFF21262D),
            cardHover = Color(0xFF30363D),
            textPrimary = Color(0xFFF0F6FC),
            textSecondary = Color(0xFF8B949E),
            textMuted = Color(0xFF6E7681),
            border = Color(0xFF30363D),
            borderHi = Color(0xFF8B949E),
            accent = Color(0xFF10B981)
        )
        else -> SangitaColors(
            background = Color(0xFF000000),
            surface = Color(0xFF0C0C0E),
            card = Color(0xFF18181B),
            cardHover = Color(0xFF27272A),
            textPrimary = Color(0xFFFFFFFF),
            textSecondary = Color(0xFFA1A1AA),
            textMuted = Color(0xFF71717A),
            border = Color(0x1AFFFFFF),
            borderHi = Color(0x33FFFFFF),
            accent = Color(0xFF34D399)
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

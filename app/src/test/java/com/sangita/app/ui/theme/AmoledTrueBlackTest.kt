package com.sangita.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.sangita.app.data.local.preferences.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class AmoledTrueBlackTest {

    /**
     * WCAG 2.1 relative luminance calculation:
     * https://www.w3.org/WAI/GL/wiki/Relative_luminance
     */
    private fun calculateRelativeLuminance(color: Color): Double {
        fun componentLuminance(component: Float): Double {
            val c = component.toDouble()
            return if (c <= 0.04045) {
                c / 12.92
            } else {
                ((c + 0.055) / 1.055).pow(2.4)
            }
        }

        val r = componentLuminance(color.red)
        val g = componentLuminance(color.green)
        val b = componentLuminance(color.blue)

        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /**
     * WCAG 2.1 contrast ratio calculation:
     * (L1 + 0.05) / (L2 + 0.05) where L1 is the lighter color.
     */
    private fun calculateContrastRatio(foreground: Color, background: Color): Double {
        val lum1 = calculateRelativeLuminance(foreground)
        val lum2 = calculateRelativeLuminance(background)
        val lighter = max(lum1, lum2)
        val darker = min(lum1, lum2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    @Test
    fun testAmoledColorSchemeIsPureBlack() {
        // Assert that AmoledColorScheme.background == Color(0xFF000000)
        assertEquals(
            "AmoledColorScheme background must be pure black #000000 (RGB 0, 0, 0)",
            Color(0xFF000000),
            AmoledColorScheme.background
        )
        // Assert that AmoledColorScheme.surface == Color(0xFF000000)
        assertEquals(
            "AmoledColorScheme surface must be pure black #000000 (RGB 0, 0, 0)",
            Color(0xFF000000),
            AmoledColorScheme.surface
        )
        // Verify underlying RGB values are 0, 0, 0
        assertEquals(0f, AmoledColorScheme.background.red, 0.0001f)
        assertEquals(0f, AmoledColorScheme.background.green, 0.0001f)
        assertEquals(0f, AmoledColorScheme.background.blue, 0.0001f)
        assertEquals(0f, AmoledColorScheme.surface.red, 0.0001f)
        assertEquals(0f, AmoledColorScheme.surface.green, 0.0001f)
        assertEquals(0f, AmoledColorScheme.surface.blue, 0.0001f)
    }

    @Test
    fun testCustomColorsWhenThemeModeIsAmoled() {
        val customColors = getSangitaColors(UserPreferences.THEME_AMOLED)

        // Assert customColors.background == Color(0xFF000000) when themeMode == THEME_AMOLED
        assertEquals(
            "customColors.background must be pure black #000000 when themeMode == THEME_AMOLED",
            Color(0xFF000000),
            customColors.background
        )
        // Assert customColors.surface == Color(0xFF000000)
        assertEquals(
            "customColors.surface must be pure black #000000 when themeMode == THEME_AMOLED",
            Color(0xFF000000),
            customColors.surface
        )
        assertEquals(
            "customColors.backgroundSec must be pure black #000000 when themeMode == THEME_AMOLED",
            Color(0xFF000000),
            customColors.backgroundSec
        )

        // Assert customColors.isAmoled == true
        assertTrue(
            "customColors.isAmoled must be true when themeMode == THEME_AMOLED",
            customColors.isAmoled
        )
    }

    @Test
    fun testContrastRatioAmoledBackgroundAndGraphiteTextPrimary() {
        // Assert contrast ratios between AmoledBackground (#000000) and GraphiteTextPrimary (#EDEDED) >= 15:1
        // (exceeding WCAG AAA 7:1)
        val contrastRatio = calculateContrastRatio(GraphiteTextPrimary, AmoledBackground)
        assertTrue(
            "Contrast ratio between AmoledBackground (#000000) and GraphiteTextPrimary (#EDEDED) must be >= 15:1 (WCAG AAA requires 7:1). Actual: $contrastRatio:1",
            contrastRatio >= 15.0
        )
    }

    @Test
    fun testContrastRatioAmoledBackgroundAndSteelBlueLight() {
        // Assert contrast ratio between AmoledBackground (#000000) and SteelBlueLight (#5B95C7) >= 6:1
        // (exceeding WCAG AA 4.5:1)
        val contrastRatio = calculateContrastRatio(SteelBlueLight, AmoledBackground)
        assertTrue(
            "Contrast ratio between AmoledBackground (#000000) and SteelBlueLight (#5B95C7) must be >= 6:1 (WCAG AA requires 4.5:1). Actual: $contrastRatio:1",
            contrastRatio >= 6.0
        )
    }
}

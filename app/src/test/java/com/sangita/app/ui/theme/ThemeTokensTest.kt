package com.sangita.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTokensTest {

    @Test
    fun testSteelBlueBrandTokens() {
        assertEquals(Color(0xFF5B95C7), SteelBlueLight)
        assertEquals(Color(0xFF4A80B0), SteelBluePrimary)
        assertEquals(Color(0xFF35638A), SteelBlueDark)
        assertEquals(Color(0xFF071B2F), DeepNavyButtonText)

        // Backward-compatibility aliases
        assertEquals(SteelBlueLight, SoftSteelBlue)
        assertEquals(DeepNavyButtonText, DeepNavyBlue)
    }

    @Test
    fun testGraphiteNeutralRamp() {
        assertEquals(Color(0xFF141415), GraphiteBackground)
        assertEquals(Color(0xFF19191B), GraphiteCard)
        assertEquals(Color(0xFF232325), GraphiteElevated)
        assertEquals(Color(0xFF26282E), GraphiteBorder)
        assertEquals(Color(0xFFEDEDED), GraphiteTextPrimary)
        assertEquals(Color(0xFFA1A1A6), GraphiteTextSecondary)
        assertEquals(Color(0xFF85858B), GraphiteTextMuted)
    }

    @Test
    fun testAmoledTokens() {
        assertEquals(Color(0xFF000000), AmoledBackground)
        assertEquals(Color(0xFF08090C), AmoledCard)
        assertEquals(Color(0xFF101217), AmoledElevated)
        assertEquals(Color(0xFF1C2029), AmoledBorder)
    }

    @Test
    fun testLightNeutralRamp() {
        assertEquals(Color(0xFFFAFAFA), LightPageBackground)
        assertEquals(Color(0xFFFFFFFF), LightCard)
        assertEquals(Color(0xFFF4F4F5), LightElevated)
        assertEquals(Color(0xFFE4E4E7), LightBorder)
        assertEquals(Color(0xFF171717), LightTextPrimary)
        assertEquals(Color(0xFF525252), LightTextSecondary)
        assertEquals(Color(0xFF737373), LightTextMuted)
    }

    @Test
    fun testSemanticStatusColors() {
        assertEquals(Color(0xFF3DA673), SemanticSuccess)
        assertEquals(Color(0xFFD97706), SemanticWarning)
        assertEquals(Color(0x1FD97706), SemanticWarningBg)
        assertEquals(Color(0xFFE5484D), SemanticError)
    }

    @Test
    fun testTypographyScale() {
        // Minimum labelSmall is 11sp (lineHeight 15sp), labelMedium is 12sp
        assertTrue("labelSmall font size must be at least 11sp", Typography.labelSmall.fontSize >= 11.sp)
        assertEquals(11.sp, Typography.labelSmall.fontSize)
        assertEquals(15.sp, Typography.labelSmall.lineHeight)

        assertTrue("labelMedium font size must be at least 12sp", Typography.labelMedium.fontSize >= 12.sp)
        assertEquals(12.sp, Typography.labelMedium.fontSize)

        // Tabular figures
        assertEquals("tnum", TabularNumbers.fontFeatureSettings)
        assertEquals("tnum", TabularNumbersMedium.fontFeatureSettings)
        assertEquals("tnum", TabularStatHeader.fontFeatureSettings)
    }

    @Test
    fun testSangitaColorsDataClassDefaults() {
        val colors = SangitaColors(
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
            lavender = AmbientLavender
        )

        assertEquals(SteelBlueLight, colors.buttonBg)
        assertEquals(DeepNavyButtonText, colors.buttonContent)
        assertEquals(SemanticWarningBg, colors.warningBg)
        assertFalse(colors.isAmoled)
    }

    @Test
    fun testSangitaColorsAmoled() {
        val colors = SangitaColors(
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

        assertTrue(colors.isAmoled)
        assertEquals(AmoledBackground, colors.background)
        assertEquals(DeepNavyButtonText, colors.buttonContent)
    }
}

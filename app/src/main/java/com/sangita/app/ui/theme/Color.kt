package com.sangita.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// ==========================================
// Steel Blue Brand Tokens
// ==========================================
val SteelBlueLight = Color(0xFF5B95C7)
val SteelBluePrimary = Color(0xFF4A80B0)
val SteelBlueDark = Color(0xFF35638A)
val DeepNavyButtonText = Color(0xFF071B2F)

// Backward-compatibility aliases
val SoftSteelBlue = SteelBlueLight
val DeepNavyBlue = DeepNavyButtonText

val DeepOceanBlue = SoftSteelBlue              // Backward-compatibility alias
val GlowIceBlue = SoftSteelBlue                // Backward-compatibility alias

val AmbientAccentBlue = SoftSteelBlue          // Soft Steel Blue #5B95C7 accent
val AmbientAuraBlue = Color(0xFF142B4E)        // Soft ambient blue glow
val AmbientLavender = Color(0xFF9A9FE8)        // Muted lavender
val LightBlueAccent = SoftSteelBlue            // Soft Steel Blue

// ==========================================
// Dark Graphite Neutral Ramp
// ==========================================
val GraphiteBackground = Color(0xFF141415)
val GraphiteCard = Color(0xFF19191B)
val GraphiteElevated = Color(0xFF232325)
val GraphiteBorder = Color(0xFF26282E)
val GraphiteTextPrimary = Color(0xFFEDEDED)
val GraphiteTextSecondary = Color(0xFFA1A1A6)
val GraphiteTextMuted = Color(0xFF85858B)

// ==========================================
// AMOLED True Black Tokens
// ==========================================
val AmoledBackground = Color(0xFF000000)
val AmoledCard = Color(0xFF08090C)
val AmoledElevated = Color(0xFF101217)
val AmoledBorder = Color(0xFF1C2029)

// ==========================================
// Light Neutral Ramp
// ==========================================
val LightPageBackground = Color(0xFFFAFAFA)
val LightCard = Color(0xFFFFFFFF)
val LightElevated = Color(0xFFF4F4F5)
val LightBorder = Color(0xFFE4E4E7)
val LightTextPrimary = Color(0xFF171717)
val LightTextSecondary = Color(0xFF525252)
val LightTextMuted = Color(0xFF737373)

// ==========================================
// Semantic Status Colors
// ==========================================
val SemanticSuccess = Color(0xFF3DA673)
val SemanticWarning = Color(0xFFD97706)
val SemanticWarningBg = Color(0x1FD97706)
val SemanticError = Color(0xFFE5484D)

// ==========================================
// Ambient Dark Static Tokens (Legacy Compatibility)
// ==========================================
val AmbientBackground = GraphiteBackground
val AmbientBackgroundSec = Color(0xFF18181A)
val AmbientCard = GraphiteCard
val AmbientElevated = GraphiteElevated
val AmbientBorder = GraphiteBorder
val AmbientBorderHi = Color(0xFF33363F)
val AmbientTextPrimary = GraphiteTextPrimary
val AmbientTextSecondary = GraphiteTextSecondary
val AmbientTextMuted = GraphiteTextMuted

// Backward-compatible Raw Static Values
val RawSangitaBlack = AmoledBackground
val RawSangitaSurface = GraphiteCard
val RawSangitaSurfaceHi = GraphiteElevated
val RawSangitaCard = GraphiteCard
val RawSangitaCardHover = GraphiteElevated
val RawSangitaText = GraphiteTextPrimary
val RawSangitaTextSub = GraphiteTextSecondary
val RawSangitaTextDim = GraphiteTextMuted
val RawSangitaBorder = GraphiteBorder
val RawSangitaBorderHi = AmbientBorderHi

// Static highlights & constants
val SangitaEmerald = SoftSteelBlue
val SangitaEmeraldHi = Color(0xFF93C0D6)
val SangitaEmeraldDim = Color(0xFF4A7A92)
val SangitaZinc300 = GraphiteTextSecondary

val SangitaRed = SemanticError
val SangitaRedLight = Color(0xFFF87171)
val SangitaRedBg = Color(0x1AE5484D)
val SangitaRedBorder = Color(0x4DE5484D)
val FavoritePink = Color(0xFFEC4899)
val ErrorRed = SemanticError

// Restrained Metric Tokens
val MetricBlue = AmbientAccentBlue
val MetricBlueBg = GraphiteElevated
val MetricEmerald = AmbientLavender
val MetricEmeraldBg = GraphiteElevated
val MetricAmber = GraphiteTextPrimary
val MetricAmberBg = GraphiteElevated
val MetricPink = AmbientLavender
val MetricPinkBg = GraphiteElevated
val Emerald500 = SoftSteelBlue
val EmeraldDim = SangitaEmeraldDim

// Dynamic Composable Theme Properties - read from LocalSangitaColors for instant theme reaction
val DarkBackground: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSangitaColors.current.background

val DarkBackgroundSec: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSangitaColors.current.backgroundSec

val DarkSurface: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSangitaColors.current.surface

val DarkElevated: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSangitaColors.current.elevated

val DarkSurfaceVariant: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSangitaColors.current.cardHover

val SangitaCard: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSangitaColors.current.card

val SangitaCardHover: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSangitaColors.current.cardHover

val DarkBorder: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSangitaColors.current.border

val DarkBorderHi: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSangitaColors.current.borderHi

val SangitaBorder: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSangitaColors.current.border

val SangitaBorderHi: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSangitaColors.current.borderHi

val TextPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSangitaColors.current.textPrimary

val TextSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSangitaColors.current.textSecondary

val TextMuted: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSangitaColors.current.textMuted

val SangitaText: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSangitaColors.current.textPrimary

val SangitaTextSub: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSangitaColors.current.textSecondary

val SangitaTextDim: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSangitaColors.current.textMuted

val SangitaBlack: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSangitaColors.current.background

val SangitaSurface: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSangitaColors.current.surface

val SangitaAccent: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSangitaColors.current.accent

val AmbientAccent: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSangitaColors.current.accent

val AmbientAura: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSangitaColors.current.auraBlue

val AmbientMutedLavender: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSangitaColors.current.lavender

val SangitaButtonBg: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSangitaColors.current.buttonBg

val SangitaButtonContent: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSangitaColors.current.buttonContent

val WarningBg: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSangitaColors.current.warningBg

val IsAmoled: Boolean
    @Composable
    @ReadOnlyComposable
    get() = LocalSangitaColors.current.isAmoled

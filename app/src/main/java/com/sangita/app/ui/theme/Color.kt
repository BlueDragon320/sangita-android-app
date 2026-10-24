package com.sangita.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// ==========================================
// Ambient Static Tokens - Premium Ambient Dark Aesthetic
// ==========================================
val AmbientBackground = Color(0xFF080A0F)      // Primary near-black charcoal foundation
val AmbientBackgroundSec = Color(0xFF10131B)   // Secondary background
val AmbientCard = Color(0xFF121620)            // Card surface
val AmbientElevated = Color(0xFF181D2A)        // Elevated surface
val AmbientBorder = Color(0xFF262C3B)          // Subtle borders and dividers
val AmbientBorderHi = Color(0xFF3A4256)        // Elevated borders
val AmbientTextPrimary = Color(0xFFF1F2F7)     // Primary text
val AmbientTextSecondary = Color(0xFF9299AE)   // Secondary text
val AmbientTextMuted = Color(0xFF656D83)       // Muted text

val SoftSteelBlue = Color(0xFF66A3BF)          // Outer container / button background (#66A3BF)
val DeepNavyBlue = Color(0xFF032F72)           // Inside button glyph / text / active accent (#032F72)

val DeepOceanBlue = SoftSteelBlue              // Backward-compatibility alias
val GlowIceBlue = SoftSteelBlue                // Backward-compatibility alias

val AmbientAccentBlue = SoftSteelBlue          // Soft Steel Blue #66A3BF accent
val AmbientAuraBlue = Color(0xFF142B4E)        // Soft ambient blue glow
val AmbientLavender = Color(0xFF9A9FE8)        // Muted lavender
val LightBlueAccent = SoftSteelBlue            // Soft Steel Blue #66A3BF

// Backward-compatible Raw Static Values
val RawSangitaBlack = Color(0xFF000000)
val RawSangitaSurface = AmbientCard
val RawSangitaSurfaceHi = AmbientElevated
val RawSangitaCard = AmbientCard
val RawSangitaCardHover = AmbientElevated
val RawSangitaText = AmbientTextPrimary
val RawSangitaTextSub = AmbientTextSecondary
val RawSangitaTextDim = AmbientTextMuted
val RawSangitaBorder = AmbientBorder
val RawSangitaBorderHi = AmbientBorderHi

// Static highlights & constants
val SangitaEmerald = SoftSteelBlue             // Soft Steel Blue #66A3BF for active highlights, lyrics & devices
val SangitaEmeraldHi = Color(0xFF93C0D6)
val SangitaEmeraldDim = Color(0xFF4A7A92)
val SangitaZinc300 = AmbientTextSecondary

val SangitaRed = Color(0xFFEF4444)
val SangitaRedLight = Color(0xFFF87171)
val SangitaRedBg = Color(0x1AEF4444)
val SangitaRedBorder = Color(0x4DEF4444)
val FavoritePink = Color(0xFFEC4899)
val ErrorRed = SangitaRed

// Restrained Metric Tokens (defaulting to restrained lavender or soft white/muted colors)
val MetricBlue = AmbientAccentBlue
val MetricBlueBg = AmbientElevated
val MetricEmerald = AmbientLavender
val MetricEmeraldBg = AmbientElevated
val MetricAmber = AmbientTextPrimary
val MetricAmberBg = AmbientElevated
val MetricPink = AmbientLavender
val MetricPinkBg = AmbientElevated
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

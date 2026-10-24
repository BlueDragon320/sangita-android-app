package com.sangita.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// Raw Static Values - AMOLED True Black Aesthetic
val RawSangitaBlack = Color(0xFF000000)
val RawSangitaSurface = Color(0xFF0C0C0E)
val RawSangitaSurfaceHi = Color(0xFF141418)
val RawSangitaCard = Color(0xFF141418)
val RawSangitaCardHover = Color(0xFF1F1F24)
val RawSangitaText = Color(0xFFFFFFFF)
val RawSangitaTextSub = Color(0xFF94A3B8)
val RawSangitaTextDim = Color(0xFF64748B)
val RawSangitaBorder = Color(0x1FFFFFFF)
val RawSangitaBorderHi = Color(0x33FFFFFF)

// Static highlights & constants
val SangitaEmerald = Color(0xFF34D399)       // Vibrant emerald accent matching website
val SangitaEmeraldHi = Color(0xFF6EE7B7)
val SangitaEmeraldDim = Color(0xFF059669)
val SangitaZinc300 = Color(0xFFD4D4D8)

val SangitaRed = Color(0xFFEF4444)
val SangitaRedLight = Color(0xFFF87171)
val SangitaRedBg = Color(0x1AEF4444)
val SangitaRedBorder = Color(0x4DEF4444)
val FavoritePink = Color(0xFFEC4899)
val ErrorRed = SangitaRed

val MetricBlue = Color(0xFF38BDF8)
val MetricBlueBg = Color(0xFF18181B)
val MetricEmerald = Color(0xFF34D399)
val MetricEmeraldBg = Color(0xFF18181B)
val MetricAmber = Color(0xFFFBBF24)
val MetricAmberBg = Color(0xFF18181B)
val MetricPink = Color(0xFFF472B6)
val MetricPinkBg = Color(0xFF18181B)
val Emerald500 = SangitaEmerald
val EmeraldDim = SangitaEmeraldDim

// Dynamic Composable Theme Properties - read from LocalSangitaColors for instant theme reaction
val DarkBackground: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSangitaColors.current.background

val DarkSurface: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSangitaColors.current.surface

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

package com.sangita.app.lyrics

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlin.math.cos
import kotlin.math.sin

enum class LyricsThemePalette(
    val id: String,
    val title: String,
    val isLight: Boolean,
    val isStatic: Boolean,
    val bgColor: Color,
    val color1: Color,
    val color2: Color,
    val color3: Color,
    val activeTextColor: Color,
    val inactiveTextColor: Color
) {
    COMBO1(
        id = "combo1",
        title = "Blue Green",
        isLight = false,
        isStatic = false,
        bgColor = Color(0xFF0C1322),
        color1 = Color(0xFF1E1B4B),
        color2 = Color(0xFF064E3B),
        color3 = Color(0xFF2E1065),
        activeTextColor = Color.White,
        inactiveTextColor = Color(0xFF94A3B8)
    ),
    COMBO2(
        id = "combo2",
        title = "Sunset Twilight",
        isLight = false,
        isStatic = false,
        bgColor = Color(0xFF2A0815),
        color1 = Color(0xFF4C0519),
        color2 = Color(0xFF7C2D12),
        color3 = Color(0xFF581C87),
        activeTextColor = Color.White,
        inactiveTextColor = Color(0xFFFDA4AF)
    ),
    COMBO3(
        id = "combo3",
        title = "Aurora Borealis",
        isLight = false,
        isStatic = false,
        bgColor = Color(0xFF022C22),
        color1 = Color(0xFF064E3B),
        color2 = Color(0xFF083344),
        color3 = Color(0xFF0F766E),
        activeTextColor = Color.White,
        inactiveTextColor = Color(0xFF6EE7B7)
    ),
    COMBO4(
        id = "combo4",
        title = "Cosmic Nebula",
        isLight = false,
        isStatic = false,
        bgColor = Color(0xFF1E1035),
        color1 = Color(0xFF3B0764),
        color2 = Color(0xFF4A044E),
        color3 = Color(0xFF1E1B4B),
        activeTextColor = Color.White,
        inactiveTextColor = Color(0xFFD8B4FE)
    ),
    RED(
        id = "red",
        title = "Deep Red",
        isLight = false,
        isStatic = true,
        bgColor = Color(0xFF1F0404),
        color1 = Color(0xFF450A0A),
        color2 = Color(0xFF280505),
        color3 = Color(0xFF150202),
        activeTextColor = Color.White,
        inactiveTextColor = Color(0xFFFCA5A5)
    ),
    GREEN(
        id = "green",
        title = "Deep Green",
        isLight = false,
        isStatic = true,
        bgColor = Color(0xFF02140A),
        color1 = Color(0xFF052E16),
        color2 = Color(0xFF03200E),
        color3 = Color(0xFF010E06),
        activeTextColor = Color.White,
        inactiveTextColor = Color(0xFF86EFAC)
    ),
    BLUE(
        id = "blue",
        title = "Deep Blue",
        isLight = false,
        isStatic = true,
        bgColor = Color(0xFF040A17),
        color1 = Color(0xFF0C1C38),
        color2 = Color(0xFF071224),
        color3 = Color(0xFF030711),
        activeTextColor = Color.White,
        inactiveTextColor = Color(0xFF93C5FD)
    ),
    BLACK(
        id = "black",
        title = "Pure Black",
        isLight = false,
        isStatic = true,
        bgColor = Color(0xFF000000),
        color1 = Color(0xFF000000),
        color2 = Color(0xFF000000),
        color3 = Color(0xFF000000),
        activeTextColor = Color.White,
        inactiveTextColor = Color(0xFF71717A)
    ),
    WHITE(
        id = "white",
        title = "Pure White",
        isLight = true,
        isStatic = true,
        bgColor = Color(0xFFFFFFFF),
        color1 = Color(0xFFF8FAFC),
        color2 = Color(0xFFF1F5F9),
        color3 = Color(0xFFE2E8F0),
        activeTextColor = Color(0xFF0F172A),
        inactiveTextColor = Color(0xFF64748B)
    );

    companion object {
        val COLORFUL_THEMES = listOf(COMBO1, COMBO2, COMBO3, COMBO4, RED, GREEN, BLUE)

        fun fromId(id: String): LyricsThemePalette {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: BLACK
        }

        fun getNextTheme(currentId: String): LyricsThemePalette {
            val curr = fromId(currentId)
            if (curr == BLACK || curr == WHITE) {
                return COMBO1
            }
            val idx = COLORFUL_THEMES.indexOf(curr)
            return if (idx == -1 || idx == COLORFUL_THEMES.size - 1) {
                COLORFUL_THEMES.first()
            } else {
                COLORFUL_THEMES[idx + 1]
            }
        }

        fun toggleMonochrome(currentId: String): LyricsThemePalette {
            val curr = fromId(currentId)
            return if (curr == BLACK) WHITE else BLACK
        }
    }
}

@Composable
fun isReducedMotionRequested(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        try {
            val resolver = context.contentResolver
            val durationScale = Settings.Global.getFloat(
                resolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            )
            val transitionScale = Settings.Global.getFloat(
                resolver,
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                1.0f
            )
            durationScale == 0f || transitionScale == 0f
        } catch (_: Throwable) {
            false
        }
    }
}

@Composable
fun DynamicAmbientCanvas(
    modifier: Modifier = Modifier,
    theme: String = "black",
    reducedMotion: Boolean = isReducedMotionRequested()
) {
    val palette = remember(theme) { LyricsThemePalette.fromId(theme) }

    if (palette == LyricsThemePalette.BLACK || palette == LyricsThemePalette.WHITE) {
        Canvas(modifier = modifier.fillMaxSize()) {
            drawRect(color = palette.bgColor)
        }
    } else if (palette.isStatic || reducedMotion) {
        Canvas(modifier = modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(palette.color1, palette.bgColor),
                    startY = 0f,
                    endY = size.height
                )
            )
        }
    } else {
        AnimatedMeshCanvas(palette = palette, modifier = modifier)
    }
}

@Composable
private fun AnimatedMeshCanvas(
    palette: LyricsThemePalette,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "ambient_mesh")

    val phase1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase1"
    )

    val phase2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase2"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Base background fill
        drawRect(color = palette.bgColor)

        // Animated organic harmonics matching website combos
        val cx1 = w * 0.35f + (w * 0.25f) * cos(phase1)
        val cy1 = h * 0.30f + (h * 0.20f) * sin(phase1)
        val radius1 = (w + h) * 0.45f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(palette.color1.copy(alpha = 0.50f), Color.Transparent),
                center = Offset(cx1, cy1),
                radius = radius1
            ),
            radius = radius1,
            center = Offset(cx1, cy1)
        )

        // Center 2: Counter harmonics
        val cx2 = w * 0.65f - (w * 0.25f) * sin(phase2)
        val cy2 = h * 0.70f + (h * 0.20f) * cos(phase2)
        val radius2 = (w + h) * 0.40f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(palette.color2.copy(alpha = 0.45f), Color.Transparent),
                center = Offset(cx2, cy2),
                radius = radius2
            ),
            radius = radius2,
            center = Offset(cx2, cy2)
        )

        // Center 3: Accent
        val cx3 = w * 0.50f + (w * 0.20f) * cos(phase2 * 0.7f)
        val cy3 = h * 0.50f - (h * 0.25f) * sin(phase1 * 0.8f)
        val radius3 = (w + h) * 0.35f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(palette.color3.copy(alpha = 0.45f), Color.Transparent),
                center = Offset(cx3, cy3),
                radius = radius3
            ),
            radius = radius3,
            center = Offset(cx3, cy3)
        )
    }
}

package com.sangita.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sangita.app.ui.theme.SangitaEmerald
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun PlayingBars(
    modifier: Modifier = Modifier,
    barColor: Color = SangitaEmerald,
    isAnimating: Boolean = true
) {
    val animatedPhase = if (isAnimating) {
        val transition = rememberInfiniteTransition(label = "playing_bars")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1100, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "bars_phase"
        )
    } else {
        null
    }

    Canvas(modifier = modifier.size(width = 16.dp, height = 14.dp)) {
        val phase = animatedPhase?.value ?: 0.25f
        val totalW = size.width
        val totalH = size.height
        val barWidth = 2.5.dp.toPx()
        val spacing = (totalW - (barWidth * 4)) / 3

        val f1 = 0.25f + 0.70f * abs(sin((phase * Math.PI * 2).toDouble())).toFloat()
        val f2 = 0.25f + 0.70f * abs(sin(((phase + 0.28f) * Math.PI * 2).toDouble())).toFloat()
        val f3 = 0.25f + 0.70f * abs(sin(((phase + 0.56f) * Math.PI * 2).toDouble())).toFloat()
        val f4 = 0.25f + 0.70f * abs(sin(((phase + 0.82f) * Math.PI * 2).toDouble())).toFloat()

        val corner = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
        var x = 0f

        val barH1 = totalH * f1
        drawRoundRect(
            color = barColor,
            topLeft = Offset(x, totalH - barH1),
            size = Size(barWidth, barH1),
            cornerRadius = corner
        )
        x += barWidth + spacing

        val barH2 = totalH * f2
        drawRoundRect(
            color = barColor,
            topLeft = Offset(x, totalH - barH2),
            size = Size(barWidth, barH2),
            cornerRadius = corner
        )
        x += barWidth + spacing

        val barH3 = totalH * f3
        drawRoundRect(
            color = barColor,
            topLeft = Offset(x, totalH - barH3),
            size = Size(barWidth, barH3),
            cornerRadius = corner
        )
        x += barWidth + spacing

        val barH4 = totalH * f4
        drawRoundRect(
            color = barColor,
            topLeft = Offset(x, totalH - barH4),
            size = Size(barWidth, barH4),
            cornerRadius = corner
        )
    }
}

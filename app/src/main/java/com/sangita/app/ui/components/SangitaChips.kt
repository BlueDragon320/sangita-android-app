package com.sangita.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sangita.app.ui.theme.LocalSangitaColors
import com.sangita.app.ui.theme.SemanticWarning

enum class SangitaChipVariant {
    Active,
    Muted,
    Warning
}

/**
 * Compact status chip conforming to Unslop guidelines:
 * Height 26dp, corner radius 6dp, font 11sp medium (line height 15sp).
 */
@Composable
fun SangitaStatusChip(
    text: String,
    modifier: Modifier = Modifier,
    variant: SangitaChipVariant = SangitaChipVariant.Muted,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null
) {
    val colors = LocalSangitaColors.current
    val shape = RoundedCornerShape(6.dp)

    val (bgColor, contentColor, borderColor) = when (variant) {
        SangitaChipVariant.Active -> Triple(
            colors.accent.copy(alpha = 0.15f),
            colors.accent,
            colors.accent.copy(alpha = 0.35f)
        )
        SangitaChipVariant.Muted -> Triple(
            colors.elevated,
            colors.textSecondary,
            colors.border
        )
        SangitaChipVariant.Warning -> Triple(
            colors.warningBg,
            SemanticWarning,
            SemanticWarning.copy(alpha = 0.35f)
        )
    }

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .height(26.dp)
            .clip(shape)
            .background(bgColor)
            .border(BorderStroke(1.dp, borderColor), shape)
            .then(clickableModifier)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = text,
                color = contentColor,
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                ),
                maxLines = 1
            )
        }
    }
}

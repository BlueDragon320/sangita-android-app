package com.sangita.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sangita.app.ui.theme.SangitaThemeColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongActionsSheet(
    title: String,
    subtitle: String,
    artwork: Any?,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = SangitaThemeColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = colors.card
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(colors.elevated).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (artwork != null) {
                    AsyncImage(
                        model = artwork, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(colors.card),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.MusicNote, contentDescription = null, tint = colors.textMuted) }
                }
                Spacer(Modifier.width(14.dp))
                Column(verticalArrangement = Arrangement.Center, modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(3.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun SongActionText(title: String, description: String) {
    val colors = SangitaThemeColors.current
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = colors.textPrimary)
        Text(description, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
    }
}

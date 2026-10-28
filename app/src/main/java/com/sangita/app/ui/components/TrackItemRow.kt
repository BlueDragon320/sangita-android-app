package com.sangita.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sangita.app.data.local.storage.LocalStorageManager
import com.sangita.app.domain.model.DownloadStatus
import com.sangita.app.domain.model.Track
import com.sangita.app.ui.theme.SangitaThemeColors
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackItemRow(
    track: Track,
    serverUrl: String,
    token: String?,
    index: Int? = null,
    isPlayingThisTrack: Boolean = false,
    playCount: Int? = null,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onMenuClick: () -> Unit = {},
    onToggleFavorite: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val colors = SangitaThemeColors.current

    // 3-second hold to reveal download, followed by 3-second countdown
    var isDownloadRevealed by remember { mutableStateOf(false) }
    var countdownSeconds by remember { mutableIntStateOf(3) }

    // 3-second countdown when revealed
    LaunchedEffect(isDownloadRevealed) {
        if (isDownloadRevealed) {
            countdownSeconds = 3
            while (countdownSeconds > 0) {
                delay(1000L)
                countdownSeconds -= 1
            }
            isDownloadRevealed = false
        }
    }

    val coverModel = rememberCoverModel(track.id, track.localFilePath, serverUrl, token, track.isDownloaded)
    val imageRequest = remember(coverModel) {
        ImageRequest.Builder(context)
            .data(coverModel)
            .size(128, 128)
            .build()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isPlayingThisTrack) colors.elevated.copy(alpha = 0.5f) else Color.Transparent)
            .combinedClickable(
                onClick = { onClick() },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    isDownloadRevealed = true
                }
            )
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Slot: CAVA Visualizer (PlayingBars) when playing, or Rank Number
        Box(
            modifier = Modifier.width(24.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isPlayingThisTrack) {
                PlayingBars(modifier = Modifier.size(width = 16.dp, height = 14.dp))
            } else if (index != null) {
                Text(
                    text = "$index",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Thumbnail
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.elevated),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title and Subtitle
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isPlayingThisTrack) colors.accent else colors.textPrimary,
                fontWeight = if (isPlayingThisTrack) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.artist.ifBlank { track.playlist },
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Right side: Clean visible Timestamp OR Download Countdown Pill
        if (isDownloadRevealed) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.accent)
                    .clickable {
                        isDownloadRevealed = false
                        onDownloadClick()
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${countdownSeconds}s",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (track.isDownloaded) {
                    Box(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(colors.accent)
                    )
                } else if (track.downloadStatus == DownloadStatus.DOWNLOADING) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(10.dp),
                        strokeWidth = 1.5.dp,
                        color = colors.accent
                    )
                }

                // Visible duration timestamp
                val sec = track.durationSeconds ?: 0.0
                val durationFormatted = if (sec > 0.0) formatSeconds(sec) else "—"
                Text(
                    text = durationFormatted,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isPlayingThisTrack) colors.accent else colors.textMuted,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
            }
        }
    }
}

fun formatSeconds(seconds: Double): String {
    val totalSec = seconds.toLong()
    val minutes = totalSec / 60
    val secs = totalSec % 60
    return String.format("%d:%02d", minutes, secs)
}

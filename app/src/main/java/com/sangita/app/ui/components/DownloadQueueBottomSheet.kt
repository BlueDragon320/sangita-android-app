package com.sangita.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sangita.app.domain.model.DownloadStatus
import com.sangita.app.download.DownloadFormatting
import com.sangita.app.download.DownloadTask
import com.sangita.app.ui.theme.SangitaThemeColors

private val AccentCyan = Color(0xFF38BDF8)
private val DarkCardBg = Color(0xFF111622)
private val DarkBorderColor = Color(0xFF1A2234)
private val SlateTextSecondary = Color(0xFF94A3B8)
private val SlateTextMuted = Color(0xFF64748B)

@Composable
fun DownloadQueueIconButton(
    activeCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaPulse by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(DarkCardBg)
            .border(1.dp, if (activeCount > 0) AccentCyan.copy(alpha = alphaPulse) else DarkBorderColor, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Download,
            contentDescription = "Downloads",
            tint = if (activeCount > 0) AccentCyan else SlateTextSecondary,
            modifier = Modifier.size(19.dp)
        )

        if (activeCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(15.dp)
                    .clip(CircleShape)
                    .background(AccentCyan),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (activeCount > 9) "9+" else "$activeCount",
                    color = Color.Black,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 9.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadQueueBottomSheet(
    sheetState: SheetState,
    downloadTasks: List<DownloadTask>,
    totalSpeedBytesPerSec: Long,
    serverUrl: String,
    token: String? = null,
    onCancelDownload: (String) -> Unit,
    onCancelAll: () -> Unit,
    onDismiss: () -> Unit,
    onNavigateToDownloads: () -> Unit
) {
    val activeTasks = remember(downloadTasks) {
        downloadTasks.filter { it.status == DownloadStatus.DOWNLOADING }
    }
    val queuedTasks = remember(downloadTasks) {
        downloadTasks.filter { it.status == DownloadStatus.QUEUED }
    }
    val hasTasks = downloadTasks.isNotEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkCardBg,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(DarkBorderColor)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (hasTasks) AccentCyan.copy(alpha = 0.18f) else DarkBorderColor.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = if (hasTasks) AccentCyan else SlateTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Live Downloads",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        val subtitle = when {
                            activeTasks.isNotEmpty() && totalSpeedBytesPerSec > 0 ->
                                "${activeTasks.size} downloading • ⚡ ${DownloadFormatting.formatSpeed(totalSpeedBytesPerSec)}"
                            activeTasks.isNotEmpty() ->
                                "${activeTasks.size} downloading"
                            queuedTasks.isNotEmpty() ->
                                "${queuedTasks.size} queued"
                            else -> "All downloads complete"
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (activeTasks.isNotEmpty()) AccentCyan else SlateTextMuted
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hasTasks) {
                        TextButton(
                            onClick = onCancelAll,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Cancel All",
                                color = Color(0xFFEF4444),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = SlateTextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!hasTasks) {
                // Empty State
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF080B11))
                            .border(1.dp, DarkBorderColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = SlateTextMuted,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "No Active Downloads",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Songs and playlists you download for offline playback will appear here with live speed and progress.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateTextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = {
                            onDismiss()
                            onNavigateToDownloads()
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF182030),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = AccentCyan
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "View Offline Library",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                // Active and Queued Lists
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (activeTasks.isNotEmpty()) {
                        item {
                            Text(
                                text = "DOWNLOADING (${activeTasks.size})",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = SlateTextMuted,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }

                        items(activeTasks, key = { it.trackId }) { task ->
                            DownloadingTaskCard(
                                task = task,
                                serverUrl = serverUrl,
                                token = token,
                                onCancel = { onCancelDownload(task.trackId) }
                            )
                        }
                    }

                    if (queuedTasks.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "WAITING IN QUEUE (${queuedTasks.size})",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = SlateTextMuted,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }

                        items(queuedTasks, key = { it.trackId }) { task ->
                            QueuedTaskCard(
                                task = task,
                                serverUrl = serverUrl,
                                token = token,
                                onCancel = { onCancelDownload(task.trackId) }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF080B11))
                                .border(1.dp, DarkBorderColor, RoundedCornerShape(12.dp))
                                .clickable {
                                    onDismiss()
                                    onNavigateToDownloads()
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = AccentCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "View Offline Downloads Library",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = ">",
                                color = SlateTextMuted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadingTaskCard(
    task: DownloadTask,
    serverUrl: String,
    token: String?,
    onCancel: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = (task.progress / 100f).coerceIn(0f, 1f),
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "progress"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF080B11)),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cover Art / Thumbnail
                TrackThumbnail(
                    trackId = task.trackId,
                    serverUrl = serverUrl,
                    token = token
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.trackTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = task.artist,
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF182030))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel Download",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Animated Smooth Progress Bar
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = AccentCyan,
                trackColor = Color(0xFF182030),
                strokeCap = StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Stats row: Data Size & Transfer Speed
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val downloadedText = if (task.totalBytes > 0) {
                    "${DownloadFormatting.formatBytes(task.bytesDownloaded)} / ${DownloadFormatting.formatBytes(task.totalBytes)} • ${task.progress}%"
                } else {
                    "${DownloadFormatting.formatBytes(task.bytesDownloaded)} • Downloading..."
                }

                Text(
                    text = downloadedText,
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateTextSecondary,
                    fontSize = 11.sp
                )

                Text(
                    text = "⚡ ${DownloadFormatting.formatSpeed(task.speedBytesPerSec)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun QueuedTaskCard(
    task: DownloadTask,
    serverUrl: String,
    token: String?,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF080B11)),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorderColor.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TrackThumbnail(
                trackId = task.trackId,
                serverUrl = serverUrl,
                token = token
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.trackTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.HourglassEmpty,
                        contentDescription = null,
                        tint = SlateTextMuted,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Waiting in queue...",
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateTextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            IconButton(
                onClick = onCancel,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF182030))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel",
                    tint = SlateTextMuted,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun TrackThumbnail(
    trackId: String,
    serverUrl: String,
    token: String?
) {
    val cleanUrl = remember(serverUrl) {
        if (serverUrl.startsWith("http://") || serverUrl.startsWith("https://")) serverUrl
        else "http://$serverUrl"
    }
    val coverUrl = remember(cleanUrl, trackId, token) {
        val base = "$cleanUrl/api/cover?track_id=${java.net.URLEncoder.encode(trackId, "UTF-8")}"
        if (!token.isNullOrBlank()) "$base&token=${java.net.URLEncoder.encode(token, "UTF-8")}" else base
    }

    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF182030)),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(coverUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.2f),
            modifier = Modifier.size(20.dp)
        )
    }
}

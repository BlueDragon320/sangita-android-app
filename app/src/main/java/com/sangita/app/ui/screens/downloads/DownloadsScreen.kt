package com.sangita.app.ui.screens.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sangita.app.domain.model.Track
import com.sangita.app.ui.components.MiniPlayer
import com.sangita.app.ui.components.formatSeconds
import com.sangita.app.ui.theme.ErrorRed
import com.sangita.app.ui.theme.SangitaThemeColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel,
    onBackClick: () -> Unit,
    onOpenFullPlayer: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val downloadedTracks by viewModel.downloadedTracks.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val colors = SangitaThemeColors.current

    val currentPlayingTrackId = remember(playbackState.currentTrack?.id) {
        playbackState.currentTrack?.id
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = 8.dp),
                title = { Text("Offline Downloads", fontWeight = FontWeight.Bold, color = colors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
            )
        },
        bottomBar = {
            if (playbackState.currentTrack != null) {
                MiniPlayer(
                    playbackState = playbackState,
                    serverUrl = viewModel.serverUrl,
                    token = viewModel.token,
                    onTogglePlayPause = viewModel.playbackController::togglePlayPause,
                    onSkipNext = viewModel.playbackController::skipNext,
                    onClick = onOpenFullPlayer
                )
            }
        },
        containerColor = colors.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Storage card
            item {
                val usage = uiState.storageUsage
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.card),
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.border)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.SdStorage, contentDescription = null, tint = colors.accent)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Local Storage Usage",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        if (usage != null) {
                            Text(
                                text = "Downloaded Music: ${usage.formatBytes(usage.audioBytes)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Streaming Cache: ${usage.formatBytes(usage.cacheBytes)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Free Device Space: ${usage.formatBytes(usage.freeDeviceBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textMuted
                            )

                            // Storage progress meter
                            val totalBytes = (usage.totalAppBytes + usage.freeDeviceBytes).coerceAtLeast(1L)
                            val appProgress = (usage.totalAppBytes.toFloat() / totalBytes.toFloat()).coerceIn(0.01f, 1f)
                            Spacer(modifier = Modifier.height(10.dp))
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { appProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = colors.accent,
                                trackColor = colors.border
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Track list
            if (downloadedTracks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "No downloaded tracks yet.", style = MaterialTheme.typography.bodyLarge, color = colors.textMuted)
                    }
                }
            } else {
                items(downloadedTracks, key = { it.id }) { track ->
                    DownloadedTrackRow(
                        track = track,
                        serverUrl = viewModel.serverUrl,
                        token = viewModel.token,
                        isPlaying = currentPlayingTrackId == track.id,
                        onClick = { viewModel.playTrack(track) },
                        onDelete = { viewModel.deleteDownloadedTrack(track) }
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadedTrackRow(
    track: Track,
    serverUrl: String,
    token: String?,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val coverUrl = "$serverUrl/api/cover/${track.id}?token=${token ?: ""}"
    val colors = SangitaThemeColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.card)
                .border(1.dp, colors.border, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = coverUrl,
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isPlaying) colors.accent else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${track.artist} • ${formatSeconds(track.durationSeconds ?: 0.0)}",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete",
                tint = ErrorRed,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

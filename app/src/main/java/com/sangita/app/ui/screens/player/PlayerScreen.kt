package com.sangita.app.ui.screens.player

import android.app.Activity
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.sangita.app.domain.model.RepeatMode
import com.sangita.app.ui.components.DeviceSyncBottomSheet
import com.sangita.app.ui.components.SystemBarsHelper
import com.sangita.app.ui.components.findActivity
import com.sangita.app.ui.components.formatSeconds
import com.sangita.app.ui.theme.DarkBackground
import com.sangita.app.ui.theme.DarkBorder
import com.sangita.app.ui.theme.Emerald500
import com.sangita.app.ui.theme.FavoritePink
import com.sangita.app.ui.theme.TextMuted
import com.sangita.app.ui.theme.TextPrimary
import com.sangita.app.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onBackClick: () -> Unit,
    onOpenLyrics: () -> Unit
) {
    val state by viewModel.playbackState.collectAsState()
    val devices by viewModel.devices.collectAsState()
    var showDevicesSheet by remember { mutableStateOf(false) }
    val track = state.currentTrack ?: return

    val coverUrl = "${viewModel.serverUrl}/api/cover/${track.id}?token=${viewModel.token ?: ""}"
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val context = LocalContext.current
    val activity = context.findActivity()
    val window = activity?.window
    var isFullscreen by remember { mutableStateOf(false) }

    DisposableEffect(isFullscreen, window) {
        SystemBarsHelper.setFullscreen(window, isFullscreen)
        onDispose {
            SystemBarsHelper.setFullscreen(window, false)
        }
    }

    val posSec = state.currentPositionMs / 1000.0
    val durSec = (state.durationMs / 1000.0).coerceAtLeast(1.0)
    val sliderValue = (state.currentPositionMs.toFloat() / state.durationMs.toFloat().coerceAtLeast(1f))
        .coerceIn(0f, 1f)

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        if (isLandscape) {
        // Landscape Mode: 2-Column Side-by-Side Immersive Layout
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .then(
                    if (isFullscreen) {
                        Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    } else {
                        Modifier
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    }
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Column: Back button + Album Artwork
            Box(
                modifier = Modifier
                    .weight(0.40f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = {
                        if (isFullscreen) {
                            isFullscreen = false
                        }
                        onBackClick()
                    },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }

                AsyncImage(
                    model = coverUrl,
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxHeight(0.85f)
                        .aspectRatio(1f)
                        .shadow(16.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkBorder)
                )
            }

            Spacer(modifier = Modifier.width(20.dp))

            // Right Column: Metadata, Scrubber, Playback Controls
            Column(
                modifier = Modifier
                    .weight(0.60f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                // Top Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "PLAYING FROM",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            fontSize = 9.sp
                        )
                        Text(
                            text = track.playlist,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { isFullscreen = !isFullscreen }) {
                            Icon(
                                imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = if (isFullscreen) "Exit Fullscreen" else "Enter Fullscreen",
                                tint = if (isFullscreen) Emerald500 else TextPrimary
                            )
                        }
                        IconButton(onClick = onOpenLyrics) {
                            Icon(imageVector = Icons.Default.Lyrics, contentDescription = "Lyrics", tint = Emerald500)
                        }
                        IconButton(onClick = { showDevicesSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.Devices,
                                contentDescription = "Connect to device",
                                tint = if (!state.isActiveDevice) Emerald500 else TextPrimary
                            )
                        }
                    }
                }

                // Track Title & Favorite/Download
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(onClick = { viewModel.toggleFavorite(track) }) {
                        Icon(
                            imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (track.isFavorite) FavoritePink else TextMuted,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(onClick = viewModel::downloadCurrentTrack) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download",
                            tint = if (track.isDownloaded) Emerald500 else TextMuted,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Scrubber Slider
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = sliderValue,
                        onValueChange = { frac ->
                            val targetMs = (frac * state.durationMs).toLong()
                            viewModel.seekTo(targetMs)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Emerald500,
                            activeTrackColor = Emerald500,
                            inactiveTrackColor = DarkBorder
                        ),
                        modifier = Modifier.fillMaxWidth().height(24.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = formatSeconds(posSec), style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Text(text = formatSeconds(durSec), style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                }

                // Controls Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = viewModel::toggleShuffle) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (state.shuffleModeEnabled) Emerald500 else TextMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(onClick = viewModel::skipPrevious) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = TextPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Emerald500),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = viewModel::togglePlayPause) {
                            if (state.isBuffering) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(26.dp),
                                    color = Color.White,
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Icon(
                                    imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }

                    IconButton(onClick = viewModel::skipNext) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = TextPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    IconButton(onClick = viewModel::toggleRepeatMode) {
                        Icon(
                            imageVector = if (state.repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                            contentDescription = "Repeat",
                            tint = if (state.repeatMode != RepeatMode.OFF) Emerald500 else TextMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    } else {
        // Portrait Mode
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .then(
                    if (isFullscreen) {
                        Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    } else {
                        Modifier
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(top = 8.dp)
                            .padding(horizontal = 24.dp)
                    }
                )
        ) {
            TopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "PLAYING FROM",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                        Text(
                            text = track.playlist,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isFullscreen) {
                            isFullscreen = false
                        }
                        onBackClick()
                    }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { isFullscreen = !isFullscreen }) {
                        Icon(
                            imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = if (isFullscreen) "Exit Fullscreen" else "Enter Fullscreen",
                            tint = if (isFullscreen) Emerald500 else TextPrimary
                        )
                    }
                    IconButton(onClick = onOpenLyrics) {
                        Icon(imageVector = Icons.Default.Lyrics, contentDescription = "Lyrics", tint = Emerald500)
                    }
                    IconButton(onClick = { showDevicesSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Devices,
                            contentDescription = "Connect to device",
                            tint = if (!state.isActiveDevice) Emerald500 else TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Center Album Artwork
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .aspectRatio(1f)
                        .shadow(16.dp, RoundedCornerShape(20.dp))
                        .clip(RoundedCornerShape(20.dp))
                        .background(DarkBorder)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Track Title & Artist & Favorite
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = { viewModel.toggleFavorite(track) }) {
                    Icon(
                        imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (track.isFavorite) FavoritePink else TextMuted,
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(onClick = viewModel::downloadCurrentTrack) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download",
                        tint = if (track.isDownloaded) Emerald500 else TextMuted,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Scrubber Slider
            Slider(
                value = sliderValue,
                onValueChange = { frac ->
                    val targetMs = (frac * state.durationMs).toLong()
                    viewModel.seekTo(targetMs)
                },
                colors = SliderDefaults.colors(
                    thumbColor = Emerald500,
                    activeTrackColor = Emerald500,
                    inactiveTrackColor = DarkBorder
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = formatSeconds(posSec), style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Text(text = formatSeconds(durSec), style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Playback Controls Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle
                IconButton(onClick = viewModel::toggleShuffle) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (state.shuffleModeEnabled) Emerald500 else TextMuted
                    )
                }

                // Previous
                IconButton(onClick = viewModel::skipPrevious, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = TextPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Big Play/Pause Button
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(Emerald500),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = viewModel::togglePlayPause) {
                        if (state.isBuffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = Color.White,
                                strokeWidth = 3.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (state.isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }
                }

                // Next
                IconButton(onClick = viewModel::skipNext, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = TextPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Repeat
                IconButton(onClick = viewModel::toggleRepeatMode) {
                    Icon(
                        imageVector = if (state.repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = "Repeat",
                        tint = if (state.repeatMode != RepeatMode.OFF) Emerald500 else TextMuted
                    )
                }
            }
        }
    }
}

    if (showDevicesSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        DeviceSyncBottomSheet(
            sheetState = sheetState,
            devices = devices,
            onTransfer = { targetId ->
                viewModel.transferPlayback(targetId)
                showDevicesSheet = false
            },
            onDismiss = { showDevicesSheet = false },
            onRefresh = viewModel::refreshDevices
        )
    }
}

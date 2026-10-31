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
import androidx.compose.foundation.layout.sizeIn
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
import com.sangita.app.data.local.storage.LocalStorageManager
import com.sangita.app.ui.components.rememberCoverModel
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import com.sangita.app.ui.theme.DeepNavyBlue
import com.sangita.app.ui.theme.DeepOceanBlue
import com.sangita.app.ui.theme.Emerald500
import com.sangita.app.ui.theme.FavoritePink
import com.sangita.app.ui.theme.SangitaThemeColors
import com.sangita.app.ui.theme.SoftSteelBlue
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

    val colors = SangitaThemeColors.current
    val receiverName = if (!state.isActiveDevice) {
        state.activeDeviceName ?: devices.firstOrNull { it.isActive }?.deviceName ?: "Web Player"
    } else {
        "this phone"
    }
    val timestampStyle = MaterialTheme.typography.labelSmall.copy(
        fontFeatureSettings = "tnum"
    )
    val shuffleDesc = if (state.shuffleModeEnabled) "Shuffle on" else "Shuffle off"
    val (repeatIcon, repeatDesc) = when (state.repeatMode) {
        RepeatMode.ONE -> Icons.Default.RepeatOne to "Repeat one"
        RepeatMode.ALL -> Icons.Default.Repeat to "Repeat all"
        RepeatMode.OFF -> Icons.Default.Repeat to "Repeat off"
    }

    val context = LocalContext.current
    val coverModel = rememberCoverModel(track.id, track.localFilePath, viewModel.serverUrl, viewModel.token, track.isDownloaded)
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val activity = context.findActivity()
    val window = activity?.window
    var isFullscreen by remember { mutableStateOf(false) }

    DisposableEffect(isFullscreen, window) {
        SystemBarsHelper.setFullscreen(window, isFullscreen)
        onDispose {
            SystemBarsHelper.setFullscreen(window, false)
        }
    }

    // Keep the thumb under the finger while playback progress continues to update.
    // Seek once when the gesture ends instead of issuing a stream seek for every pixel.
    var scrubFraction by remember(track.id) { mutableStateOf<Float?>(null) }
    val durationMs = state.durationMs.coerceAtLeast(0L)
    val playbackFraction = (state.currentPositionMs.toFloat() / durationMs.toFloat().coerceAtLeast(1f))
        .coerceIn(0f, 1f)
    val sliderValue = scrubFraction ?: playbackFraction
    val posSec = (scrubFraction?.let { it * durationMs } ?: state.currentPositionMs.toFloat()) / 1000.0
    val durSec = (durationMs / 1000.0).coerceAtLeast(1.0)
    val finishScrubbing = {
        scrubFraction?.let { fraction ->
            if (durationMs > 0) viewModel.seekTo((fraction * durationMs).toLong())
        }
        scrubFraction = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        if (isLandscape) {
        // Landscape Mode: 2-Column Side-by-Side Immersive Layout
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
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
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .align(Alignment.TopStart)
                        .padding(top = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textPrimary
                    )
                }

                AsyncImage(
                    model = coverModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxHeight(0.85f)
                        .aspectRatio(1f)
                        .shadow(if (colors.isAmoled) 0.dp else 16.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.border)
                )
            }

            Spacer(modifier = Modifier.width(20.dp))

            // Right Column: Metadata, Scrubber, Playback Controls, Device Status
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
                            text = "Playing from",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textMuted
                        )
                        Text(
                            text = track.playlist,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                            modifier = Modifier.semantics { heading() }
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { isFullscreen = !isFullscreen },
                            modifier = Modifier
                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                .semantics {
                                    stateDescription = if (isFullscreen) "Fullscreen active" else "Fullscreen inactive"
                                }
                        ) {
                            Icon(
                                imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = if (isFullscreen) "Exit Fullscreen" else "Enter Fullscreen",
                                tint = if (isFullscreen) colors.accent else colors.textPrimary
                            )
                        }
                        IconButton(
                            onClick = onOpenLyrics,
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Lyrics, contentDescription = "Lyrics", tint = colors.accent)
                        }
                        IconButton(
                            onClick = { showDevicesSheet = true },
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Devices,
                                contentDescription = "Output device: $receiverName",
                                tint = if (!state.isActiveDevice) colors.accent else colors.textPrimary
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
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = { viewModel.toggleFavorite(track) },
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .semantics {
                                stateDescription = if (track.isFavorite) "In favorites" else "Not in favorites"
                                role = Role.Checkbox
                            }
                    ) {
                        Icon(
                            imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (track.isFavorite) "Remove from favorites" else "Add to favorites",
                            tint = if (track.isFavorite) FavoritePink else colors.textMuted,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = viewModel::downloadCurrentTrack,
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .semantics {
                                stateDescription = if (track.isDownloaded) "Downloaded" else "Not downloaded"
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = if (track.isDownloaded) "Downloaded offline" else "Download track",
                            tint = if (track.isDownloaded) colors.accent else colors.textMuted,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Scrubber Slider
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = sliderValue,
                        onValueChange = { scrubFraction = it },
                        onValueChangeFinished = finishScrubbing,
                        enabled = durationMs > 0,
                        colors = SliderDefaults.colors(
                            thumbColor = colors.accent,
                            activeTrackColor = colors.accent,
                            inactiveTrackColor = colors.borderHi.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth().height(24.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = formatSeconds(posSec), style = timestampStyle, color = colors.accent)
                        Text(text = formatSeconds(durSec), style = timestampStyle, color = colors.textMuted)
                    }
                }

                // Controls Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = viewModel::toggleShuffle,
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .semantics {
                                stateDescription = if (state.shuffleModeEnabled) "Shuffle on" else "Shuffle off"
                                role = Role.Checkbox
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = shuffleDesc,
                            tint = if (state.shuffleModeEnabled) colors.accent else colors.textMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = viewModel::skipPrevious,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(colors.accent),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = viewModel::togglePlayPause,
                            modifier = Modifier
                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                .semantics {
                                    stateDescription = if (state.isPlaying) "Playing" else "Paused"
                                }
                        ) {
                            if (state.isBuffering) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(26.dp),
                                    color = colors.buttonContent,
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Icon(
                                    imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                                    tint = colors.buttonContent,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = viewModel::skipNext,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    IconButton(
                        onClick = viewModel::toggleRepeatMode,
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .semantics {
                                stateDescription = when (state.repeatMode) {
                                    RepeatMode.ONE -> "Repeat one"
                                    RepeatMode.ALL -> "Repeat all"
                                    RepeatMode.OFF -> "Repeat off"
                                }
                            }
                    ) {
                        Icon(
                            imageVector = repeatIcon,
                            contentDescription = repeatDesc,
                            tint = if (state.repeatMode != RepeatMode.OFF) colors.accent else colors.textMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Explicit Output Device Indication
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(16.dp))
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "Change output device"
                        ) { showDevicesSheet = true }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Devices,
                        contentDescription = "Output device: $receiverName",
                        tint = if (!state.isActiveDevice) colors.accent else colors.textMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Playing on $receiverName",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (!state.isActiveDevice) colors.accent else colors.textMuted
                    )
                }
            }
        }
    } else {
        // Portrait Mode
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
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
                            text = "Playing from",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textMuted
                        )
                        Text(
                            text = track.playlist,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                            modifier = Modifier.semantics { heading() }
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isFullscreen) {
                                isFullscreen = false
                            }
                            onBackClick()
                        },
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { isFullscreen = !isFullscreen },
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .semantics {
                                stateDescription = if (isFullscreen) "Fullscreen active" else "Fullscreen inactive"
                            }
                    ) {
                        Icon(
                            imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = if (isFullscreen) "Exit Fullscreen" else "Enter Fullscreen",
                            tint = if (isFullscreen) colors.accent else colors.textPrimary
                        )
                    }
                    IconButton(
                        onClick = onOpenLyrics,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Lyrics, contentDescription = "Lyrics", tint = colors.accent)
                    }
                    IconButton(
                        onClick = { showDevicesSheet = true },
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Devices,
                            contentDescription = "Output device: $receiverName",
                            tint = if (!state.isActiveDevice) colors.accent else colors.textPrimary
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
                    model = coverModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .aspectRatio(1f)
                        .shadow(if (colors.isAmoled) 0.dp else 16.dp, RoundedCornerShape(20.dp))
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.border)
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
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = { viewModel.toggleFavorite(track) },
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics {
                            stateDescription = if (track.isFavorite) "In favorites" else "Not in favorites"
                            role = Role.Checkbox
                        }
                ) {
                    Icon(
                        imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (track.isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (track.isFavorite) FavoritePink else colors.textMuted,
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(
                    onClick = viewModel::downloadCurrentTrack,
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics {
                            stateDescription = if (track.isDownloaded) "Downloaded" else "Not downloaded"
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = if (track.isDownloaded) "Downloaded offline" else "Download track",
                        tint = if (track.isDownloaded) colors.accent else colors.textMuted,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Scrubber Slider
            Slider(
                value = sliderValue,
                onValueChange = { scrubFraction = it },
                onValueChangeFinished = finishScrubbing,
                enabled = durationMs > 0,
                colors = SliderDefaults.colors(
                    thumbColor = colors.accent,
                    activeTrackColor = colors.accent,
                    inactiveTrackColor = colors.borderHi.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = formatSeconds(posSec), style = timestampStyle, color = colors.accent)
                Text(text = formatSeconds(durSec), style = timestampStyle, color = colors.textMuted)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Playback Controls Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle
                IconButton(
                    onClick = viewModel::toggleShuffle,
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics {
                            stateDescription = if (state.shuffleModeEnabled) "Shuffle on" else "Shuffle off"
                            role = Role.Checkbox
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = shuffleDesc,
                        tint = if (state.shuffleModeEnabled) colors.accent else colors.textMuted
                    )
                }

                // Previous
                IconButton(onClick = viewModel::skipPrevious, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Big Play/Pause Button
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(colors.accent),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = viewModel::togglePlayPause,
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .semantics {
                                stateDescription = if (state.isPlaying) "Playing" else "Paused"
                            }
                    ) {
                        if (state.isBuffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = colors.buttonContent,
                                strokeWidth = 3.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (state.isPlaying) "Pause" else "Play",
                                tint = colors.buttonContent,
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
                        tint = colors.textPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Repeat
                IconButton(
                    onClick = viewModel::toggleRepeatMode,
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics {
                            stateDescription = when (state.repeatMode) {
                                RepeatMode.ONE -> "Repeat one"
                                RepeatMode.ALL -> "Repeat all"
                                RepeatMode.OFF -> "Repeat off"
                            }
                        }
                ) {
                    Icon(
                        imageVector = repeatIcon,
                        contentDescription = repeatDesc,
                        tint = if (state.repeatMode != RepeatMode.OFF) colors.accent else colors.textMuted
                    )
                }
            }

            // Explicit Output Device Indication
            Row(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(20.dp))
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "Change output device"
                    ) { showDevicesSheet = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Devices,
                    contentDescription = "Output device: $receiverName",
                    tint = if (!state.isActiveDevice) colors.accent else colors.textMuted,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Playing on $receiverName",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (!state.isActiveDevice) colors.accent else colors.textMuted
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
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

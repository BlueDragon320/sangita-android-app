package com.sangita.app.ui.screens.lyrics

import android.app.Activity
import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.sangita.app.data.local.preferences.UserPreferences
import com.sangita.app.domain.model.LyricLine
import com.sangita.app.lyrics.DynamicAmbientCanvas
import com.sangita.app.lyrics.LyricsThemePalette
import com.sangita.app.ui.components.SystemBarsHelper
import com.sangita.app.ui.components.findActivity
import com.sangita.app.ui.components.formatSeconds
import com.sangita.app.ui.theme.DarkBorder
import com.sangita.app.ui.theme.Emerald500
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsOverlay(
    viewModel: LyricsViewModel,
    onCloseClick: () -> Unit
) {
    val playbackState by viewModel.playbackState.collectAsState()
    val lyricsData by viewModel.lyricsData.collectAsState()
    val activeIndex by viewModel.activeLineIndex.collectAsState()
    val currentTheme by viewModel.currentTheme.collectAsState()
    val palette = remember(currentTheme) { LyricsThemePalette.fromId(currentTheme) }

    // Fullscreen and auto-hiding controls state
    var isFullscreen by remember { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var lastThemeClickTime by remember { mutableLongStateOf(0L) }
    val onThemeClick = {
        val now = System.currentTimeMillis()
        if (now - lastThemeClickTime < 350L) {
            viewModel.toggleMonochrome()
            lastThemeClickTime = 0L
        } else {
            lastThemeClickTime = now
            viewModel.cycleTheme()
        }
        lastInteractionTime = System.currentTimeMillis()
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val screenHeight = configuration.screenHeightDp.dp
    val coverSize = if (screenHeight < 360.dp) 140.dp else 170.dp

    val context = LocalContext.current
    val activity = context.findActivity()
    val window = activity?.window

    val serverUrl = viewModel.serverUrl.ifEmpty {
        remember(context) { UserPreferences(context).getServerUrl() }
    }
    val token = viewModel.token ?: remember(context) { UserPreferences(context).getToken() }

    val track = playbackState.currentTrack
    val coverUrl = if (track != null && serverUrl.isNotEmpty()) {
        "$serverUrl/api/cover/${track.id}?token=${token ?: ""}"
    } else null

    val listState = rememberLazyListState()

    // Auto-hide controls after 2 seconds of inactivity
    LaunchedEffect(controlsVisible, lastInteractionTime) {
        if (controlsVisible) {
            delay(2000L)
            controlsVisible = false
        }
    }

    // Manage System Bars (Status bar & Navigation bar) for true immersive mode
    DisposableEffect(isFullscreen, window) {
        SystemBarsHelper.setFullscreen(window, isFullscreen)
        onDispose {
            SystemBarsHelper.setFullscreen(window, false)
        }
    }

    // Auto-scroll to center on the active line
    val lines = lyricsData?.lines ?: emptyList()
    LaunchedEffect(activeIndex) {
        if (activeIndex in lines.indices) {
            listState.animateScrollToItem(activeIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                controlsVisible = !controlsVisible
                lastInteractionTime = System.currentTimeMillis()
            }
    ) {
        // GPU-Accelerated Dynamic Moving Ambient Canvas Background
        DynamicAmbientCanvas(modifier = Modifier.fillMaxSize(), theme = currentTheme)

        if (isLandscape) {
            // Immersive Landscape 2-Column Split Layout
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = if (isFullscreen) 8.dp else 24.dp,
                        bottom = 8.dp,
                        start = 16.dp,
                        end = 16.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Column (~36% width): Album art, Title/Artist, Timestamps, Playback controls
                Column(
                    modifier = Modifier
                        .weight(0.36f)
                        .fillMaxHeight()
                        .padding(start = 16.dp, end = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Rounded album cover art with subtle shadow
                    Box(
                        modifier = Modifier
                            .size(coverSize)
                            .shadow(12.dp, RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .background(DarkBorder),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!coverUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = coverUrl,
                                contentDescription = track?.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(54.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Track Title (bold, 18sp, white, truncated)
                    Text(
                        text = track?.title ?: "No track playing",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Artist Name (14sp, white.copy(alpha=0.7f))
                    Text(
                        text = track?.artist ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Timestamp display: currentPosition / duration
                    val posSec = playbackState.currentPositionMs / 1000.0
                    val durSec = (playbackState.durationMs / 1000.0).coerceAtLeast(0.0)
                    Text(
                        text = "${formatSeconds(posSec)} / ${formatSeconds(durSec)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.65f),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Playback controls: Previous, Play/Pause/BufferSpinner, Next
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        IconButton(
                            onClick = {
                                viewModel.skipPrevious()
                                lastInteractionTime = System.currentTimeMillis()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = {
                                viewModel.togglePlayPause()
                                lastInteractionTime = System.currentTimeMillis()
                            }
                        ) {
                            if (playbackState.isBuffering) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Icon(
                                    imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Toggle Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = {
                                viewModel.skipNext()
                                lastInteractionTime = System.currentTimeMillis()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }

                // Right Column (~64% width): Centered auto-scrolling lyrics
                Box(
                    modifier = Modifier
                        .weight(0.64f)
                        .fillMaxHeight()
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (lyricsData == null) {
                        CircularProgressIndicator(color = Emerald500)
                    } else if (lines.isEmpty() || !lyricsData!!.hasLyrics) {
                        Text(
                            text = "No Synced Lyrics Available",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        LyricsList(
                            lines = lines,
                            activeIndex = activeIndex,
                            listState = listState,
                            isLandscape = true,
                            palette = palette,
                            onSeek = { sec ->
                                viewModel.seekToLine(sec)
                                lastInteractionTime = System.currentTimeMillis()
                            }
                        )
                    }
                }
            }
        } else {
            // Portrait Mode: Top bar + centered lyrics LazyColumn
            Column(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    TopAppBar(
                        modifier = if (isFullscreen) Modifier else Modifier.statusBarsPadding(),
                        title = {
                            Column {
                                Text(
                                    text = playbackState.currentTrack?.title ?: "Lyrics",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.activeTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = playbackState.currentTrack?.artist ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.inactiveTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        actions = {
                            // Theme switcher button (cycles website themes, double click toggles monochrome)
                            IconButton(onClick = onThemeClick) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Switch Theme",
                                    tint = palette.activeTextColor
                                )
                            }

                            // Fullscreen toggle in portrait
                            IconButton(
                                onClick = {
                                    isFullscreen = !isFullscreen
                                    lastInteractionTime = System.currentTimeMillis()
                                }
                            ) {
                                Icon(
                                    imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = if (isFullscreen) "Exit Fullscreen" else "Enter Fullscreen",
                                    tint = palette.activeTextColor
                                )
                            }

                            IconButton(
                                onClick = {
                                    viewModel.togglePlayPause()
                                    lastInteractionTime = System.currentTimeMillis()
                                }
                            ) {
                                if (playbackState.isBuffering) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = palette.activeTextColor,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Toggle Play",
                                        tint = palette.activeTextColor
                                    )
                                }
                            }

                            IconButton(onClick = onCloseClick) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = palette.activeTextColor
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                }

                if (lyricsData == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Emerald500)
                    }
                } else if (lines.isEmpty() || !lyricsData!!.hasLyrics) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No Synced Lyrics Available",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    LyricsList(
                        lines = lines,
                        activeIndex = activeIndex,
                        listState = listState,
                        isLandscape = false,
                        palette = palette,
                        onSeek = { sec ->
                            viewModel.seekToLine(sec)
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        }

        // Floating Top-Right Controls Overlay (Fullscreen toggle & Close)
        // Automatically hides after 2 seconds as requested by the user
        AnimatedVisibility(
            visible = controlsVisible && isLandscape,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(
                    top = if (isFullscreen) 16.dp else 36.dp,
                    end = 20.dp
                )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Theme switcher button
                IconButton(
                    onClick = onThemeClick,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Switch Theme",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Fullscreen toggle button
                IconButton(
                    onClick = {
                        isFullscreen = !isFullscreen
                        lastInteractionTime = System.currentTimeMillis()
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                ) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (isFullscreen) "Exit Fullscreen" else "Enter Fullscreen",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Close button
                IconButton(
                    onClick = onCloseClick,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricsList(
    lines: List<LyricLine>,
    activeIndex: Int,
    listState: LazyListState,
    isLandscape: Boolean,
    palette: LyricsThemePalette,
    onSeek: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val screenHeightDp = configuration.screenHeightDp.dp
    // Vertical padding equal to half screen height minus half item height
    // This guarantees that the active lyric line is positioned DEAD CENTER vertically!
    val verticalPaddingAmount = (screenHeightDp / 2 - 36.dp).coerceAtLeast(20.dp)
    val contentPadding = PaddingValues(top = verticalPaddingAmount, bottom = verticalPaddingAmount)

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        itemsIndexed(lines) { index, line ->
            val isActive = index == activeIndex

            val textColor by animateColorAsState(
                targetValue = if (isActive) palette.activeTextColor else palette.inactiveTextColor,
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "lyric_color"
            )

            val targetScale = if (isActive) {
                if (isLandscape) 1.08f else 1.05f
            } else {
                1.0f
            }

            val scale by animateFloatAsState(
                targetValue = targetScale,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "lyric_scale"
            )

            val fontSize = if (isLandscape) {
                if (isActive) 28.sp else 22.sp
            } else {
                if (isActive) 24.sp else (if (line.isInstrumental) 28.sp else 20.sp)
            }

            val lineHeight = if (isLandscape) {
                if (isActive) 38.sp else 30.sp
            } else {
                if (isActive) 34.sp else 28.sp
            }

            val itemVerticalPadding = if (isLandscape) 8.dp else 12.dp

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (line.time >= 0) {
                            onSeek(line.time)
                        }
                    }
                    .padding(vertical = itemVerticalPadding)
                    .scale(scale),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = line.text,
                    fontSize = fontSize,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    color = textColor,
                    textAlign = TextAlign.Center,
                    lineHeight = lineHeight,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

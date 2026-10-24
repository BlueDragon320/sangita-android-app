package com.sangita.app.ui.screens.playlist

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sangita.app.data.local.storage.LocalStorageManager
import com.sangita.app.domain.model.Track
import com.sangita.app.ui.components.DeviceSyncBottomSheet
import com.sangita.app.ui.components.MiniPlayer
import com.sangita.app.ui.components.TrackItemRow
import kotlin.math.absoluteValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.sangita.app.ui.theme.DarkBorder
import com.sangita.app.ui.theme.Emerald500
import com.sangita.app.ui.theme.SangitaBlack
import com.sangita.app.ui.theme.SangitaCard
import com.sangita.app.ui.theme.SangitaEmerald
import com.sangita.app.ui.theme.SangitaText
import com.sangita.app.ui.theme.SangitaTextDim
import com.sangita.app.ui.theme.SangitaTextSub
import com.sangita.app.ui.theme.SangitaThemeColors
import com.sangita.app.ui.theme.TextMuted
import com.sangita.app.ui.theme.TextPrimary
import com.sangita.app.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    viewModel: PlaylistViewModel,
    onBackClick: () -> Unit,
    onOpenFullPlayer: () -> Unit
) {
    val tracks by viewModel.tracks.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val devices by viewModel.devices.collectAsState()
    var showDevicesSheet by remember { mutableStateOf(false) }
    val username = viewModel.userPreferences.getUsername() ?: "User"

    val currentPlayingTrackId = remember(playbackState.currentTrack?.id) {
        playbackState.currentTrack?.id
    }

    val totalDurationSeconds = tracks.mapNotNull { it.durationSeconds }.sum()
    val themeColors = SangitaThemeColors.current
    val isLight = themeColors.background.luminance() > 0.5f

    val context = LocalContext.current
    val representativeTrack = remember(tracks) {
        tracks.firstOrNull() ?: tracks.getOrNull(1)
    }

    var dominantColor by remember(viewModel.playlistName) {
        // Deterministic seed based on playlist name as zero-latency default
        val hue = (viewModel.playlistName.hashCode().absoluteValue % 360).toFloat()
        mutableStateOf(Color.hsl(hue, 0.45f, 0.20f))
    }

    LaunchedEffect(representativeTrack?.id) {
        val track = representativeTrack ?: return@LaunchedEffect
        val localCover = LocalStorageManager.getOrExtractCoverFile(context, track.id, track.localFilePath)
        val coverModel: Any = if (localCover != null && localCover.exists()) localCover else "${viewModel.serverUrl}/api/cover/${track.id}?token=${viewModel.token ?: ""}"

        withContext(Dispatchers.IO) {
            try {
                val imageLoader = coil.Coil.imageLoader(context)
                val req = ImageRequest.Builder(context)
                    .data(coverModel)
                    .allowHardware(false)
                    .size(256, 256)
                    .build()
                val result = imageLoader.execute(req)
                if (result is coil.request.SuccessResult) {
                    val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    if (bitmap != null) {
                        val palette = Palette.from(bitmap).generate()
                        val colorInt = palette.getVibrantColor(
                            palette.getDominantColor(
                                palette.getDarkVibrantColor(
                                    palette.getMutedColor(0)
                                )
                            )
                        )
                        if (colorInt != 0) {
                            withContext(Dispatchers.Main) {
                                dominantColor = Color(colorInt)
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    val animatedDominantColor by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(durationMillis = 600),
        label = "PlaylistDominantColor"
    )

    // Dynamic background gradient based on album art / palette
    val gradientColors = if (isLight) {
        listOf(
            animatedDominantColor.copy(alpha = 0.25f),
            animatedDominantColor.copy(alpha = 0.10f),
            animatedDominantColor.copy(alpha = 0.03f),
            themeColors.background,
            themeColors.background
        )
    } else {
        listOf(
            animatedDominantColor.copy(alpha = 0.55f),
            animatedDominantColor.copy(alpha = 0.25f),
            animatedDominantColor.copy(alpha = 0.08f),
            themeColors.background,
            themeColors.background
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = 8.dp),
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = themeColors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        bottomBar = {
            if (playbackState.currentTrack != null) {
                MiniPlayer(
                    playbackState = playbackState,
                    serverUrl = viewModel.serverUrl,
                    token = viewModel.token,
                    onTogglePlayPause = viewModel.playbackController::togglePlayPause,
                    onSkipPrevious = viewModel.playbackController::skipPrevious,
                    onSkipNext = viewModel.playbackController::skipNext,
                    onOpenDevices = { showDevicesSheet = true },
                    onClick = onOpenFullPlayer
                )
            }
        },
        containerColor = themeColors.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(gradientColors))
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 36.dp)
            ) {
                // HERO SECTION (Cover Collage, Meta, Controls)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(6.dp))

                        // 1. 2x2 Artwork Collage or Single Cover
                        PlaylistCollageCover(
                            tracks = tracks,
                            serverUrl = viewModel.serverUrl,
                            token = viewModel.token,
                            modifier = Modifier.size(200.dp)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // 2. Metadata (PUBLIC PLAYLIST, Title, username, songs & duration)
                        Text(
                            text = "PUBLIC PLAYLIST",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = themeColors.textSecondary,
                            letterSpacing = 2.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = viewModel.playlistName,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = themeColors.textPrimary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // User badge: (Initial) username •
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(SangitaEmerald),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${username.firstOrNull()?.uppercaseChar() ?: 'V'}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$username •",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = themeColors.textPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // "30 songs, about 2 hr 13 min"
                        Text(
                            text = "${tracks.size} songs, ${formatDurationNatural(totalDurationSeconds)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = themeColors.textMuted
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Single Centered [🔀 Mix] Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(themeColors.card)
                                .border(1.dp, themeColors.border, RoundedCornerShape(20.dp))
                                .clickable { viewModel.playAll(shuffle = true) }
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Shuffle,
                                    contentDescription = "Mix",
                                    tint = themeColors.textPrimary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Mix",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = themeColors.textPrimary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))
                    }
                }

                // TRACKS LIST
                itemsIndexed(
                    items = tracks,
                    key = { _, track -> track.id },
                    contentType = { _, _ -> "track" }
                ) { index, track ->
                    TrackItemRow(
                        track = track,
                        serverUrl = viewModel.serverUrl,
                        token = viewModel.token,
                        index = index + 1,
                        isPlayingThisTrack = currentPlayingTrackId == track.id,
                        onClick = { viewModel.playTrack(track) },
                        onToggleFavorite = { viewModel.toggleFavorite(track) },
                        onDownloadClick = { viewModel.downloadTrack(track) },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
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

@Composable
fun PlaylistCollageCover(
    tracks: List<Track>,
    serverUrl: String,
    token: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val firstFour = tracks.take(4)

    Box(
        modifier = modifier
            .shadow(16.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(SangitaThemeColors.current.card)
    ) {
        if (firstFour.size >= 4) {
            // 2x2 Collage Quadrants
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f)) {
                    CollageCell(
                        track = firstFour[0],
                        serverUrl = serverUrl,
                        token = token,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    CollageCell(
                        track = firstFour[1],
                        serverUrl = serverUrl,
                        token = token,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
                Row(modifier = Modifier.weight(1f)) {
                    CollageCell(
                        track = firstFour[2],
                        serverUrl = serverUrl,
                        token = token,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    CollageCell(
                        track = firstFour[3],
                        serverUrl = serverUrl,
                        token = token,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        } else if (firstFour.isNotEmpty()) {
            CollageCell(
                track = firstFour[0],
                serverUrl = serverUrl,
                token = token,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = SangitaThemeColors.current.textMuted,
                modifier = Modifier.size(64.dp).align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun CollageCell(
    track: Track,
    serverUrl: String,
    token: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val localCover = remember(track.id) {
        LocalStorageManager.getOrExtractCoverFile(context, track.id, track.localFilePath)
    }
    val coverModel: Any = if (localCover != null && localCover.exists()) localCover else "$serverUrl/api/cover/${track.id}?token=${token ?: ""}"
    val imageRequest = remember(coverModel) {
        ImageRequest.Builder(context)
            .data(coverModel)
            .size(200, 200)
            .crossfade(100)
            .build()
    }

    AsyncImage(
        model = imageRequest,
        contentDescription = track.title,
        contentScale = ContentScale.Crop,
        modifier = modifier
    )
}

fun formatDurationNatural(totalSeconds: Double): String {
    val totalSec = totalSeconds.toLong()
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    return if (hours > 0) {
        "about $hours hr $minutes min"
    } else {
        "about $minutes min"
    }
}

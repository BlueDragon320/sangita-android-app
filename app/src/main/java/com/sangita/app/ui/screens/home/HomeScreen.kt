package com.sangita.app.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sangita.app.R
import com.sangita.app.data.local.preferences.UserPreferences
import com.sangita.app.domain.model.DownloadStatus
import com.sangita.app.domain.model.Track
import com.sangita.app.ui.components.DeviceSyncBottomSheet
import com.sangita.app.ui.components.DownloadQueueBottomSheet
import com.sangita.app.ui.components.MiniPlayer
import com.sangita.app.ui.components.SettingsBottomSheet
import com.sangita.app.ui.components.TrackItemRow
import com.sangita.app.ui.theme.AmbientAuraBlue
import com.sangita.app.ui.theme.SangitaThemeColors
import java.util.Calendar

@Composable
fun AmbientAuraCanvas(
    modifier: Modifier = Modifier
) {
    Spacer(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val w = size.width
                val h = size.height
                // Atmospheric radial gradient drawn statically for 120 FPS buttery smooth scrolling
                val center = Offset(w * 0.50f, h * 0.22f)
                val radius = (w + h) * 0.44f

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            AmbientAuraBlue.copy(alpha = 0.28f),
                            AmbientAuraBlue.copy(alpha = 0.10f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )
            }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToLibrary: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onOpenFullPlayer: () -> Unit,
    onLogout: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val dataUsageToday by viewModel.dataUsageToday.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val tracksMap by viewModel.tracksMap.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val activeDownloads by viewModel.activeDownloads.collectAsState()
    val activeDownloadsCount by viewModel.activeDownloadsCount.collectAsState()
    val totalDownloadSpeed by viewModel.totalDownloadSpeed.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = SangitaThemeColors.current

    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when {
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }

    val userInitial = remember(uiState.username) {
        uiState.username.firstOrNull()?.uppercaseChar()?.toString() ?: "U"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Atmospheric deep-blue glow drawn statically behind content
        AmbientAuraCanvas(modifier = Modifier.fillMaxSize())

        Scaffold(
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 8.dp)
                ) {
                    TopAppBar(
                        modifier = Modifier.padding(top = 4.dp),
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.elevated)
                                        .border(1.dp, colors.border, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_sangita_brand_logo),
                                        contentDescription = "Sangita Logo",
                                        tint = colors.lavender,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "SANGITA",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary,
                                        letterSpacing = 1.5.sp,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "$greeting, ${uiState.username}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.textSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        ),
                        actions = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(end = 12.dp)
                            ) {
                                // Profile Avatar Button with Username's First Letter
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(colors.elevated)
                                        .border(1.dp, colors.border, CircleShape)
                                        .clickable(onClick = viewModel::openSettingsSheet),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = userInitial,
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    )
                    // Fine divider beneath top header
                    HorizontalDivider(
                        color = colors.border,
                        thickness = 1.dp
                    )
                }
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
                        onOpenDevices = viewModel::openDevicesSheet,
                        onClick = onOpenFullPlayer
                    )
                }
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {

            if (!isOnline) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(colors.card)
                            .border(1.dp, colors.border, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = "Offline",
                            tint = colors.textMuted,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "No Connection",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "You're currently offline. Listen to your downloaded playlists and songs in your library.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onNavigateToLibrary,
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accent,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.height(46.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LibraryMusic,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Go to Library",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                val stats = uiState.stats
                val totalSeconds = stats?.totalSeconds ?: 0L
                val totalPlays = stats?.totalPlays ?: 0
                val uniqueTracks = stats?.uniqueTracks ?: 0
                val topTracks = stats?.topTracks ?: emptyList()
                val topSong = topTracks.firstOrNull()

                val allTopTracksDomain = remember(topTracks, tracksMap) {
                    topTracks.map { tt ->
                        val repoTrack = tracksMap[tt.path]
                        Track(
                            id = tt.path,
                            title = tt.name,
                            artist = tt.artist,
                            playlist = tt.playlist,
                            durationSeconds = repoTrack?.durationSeconds ?: (if (tt.totalSeconds > 0 && tt.playCount > 0) (tt.totalSeconds / tt.playCount).toDouble() else null),
                            localFilePath = repoTrack?.localFilePath,
                            isDownloaded = repoTrack?.isDownloaded ?: false,
                            downloadStatus = repoTrack?.downloadStatus ?: DownloadStatus.IDLE,
                            downloadProgress = repoTrack?.downloadProgress ?: 0,
                            fileSizeBytes = repoTrack?.fileSizeBytes ?: 0L,
                            isFavorite = repoTrack?.isFavorite ?: false
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    // 1. QUICK ACTIONS: 2x LARGER DUAL BUTTONS (50/50 HORIZONTAL SPLIT)
                    item(contentType = "quick_actions") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // 2x Larger Library Card
                                ActionNavCard(
                                    title = "Library",
                                    subtitle = "Playlists & tracks",
                                    painter = painterResource(id = R.drawable.ic_folder_music),
                                    modifier = Modifier.weight(1f),
                                    onClick = onNavigateToLibrary
                                )

                                // 2x Larger Storage / Data Usage Card
                                ActionNavCard(
                                    title = dataUsageToday.ifBlank { "0 B" },
                                    subtitle = "Data Today",
                                    icon = Icons.Default.DataUsage,
                                    modifier = Modifier.weight(1f),
                                    onClick = { viewModel.loadHomeData(force = true) }
                                )
                            }
                        }

                        // Fine divider between Quick Actions and Listening Overview
                        HorizontalDivider(
                            color = colors.border,
                            thickness = 1.dp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }

                    // 2. LISTENING OVERVIEW & 2x2 METRIC CARDS (CUSTOM SVG ICONS)
                    item(contentType = "metrics_grid") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = null,
                                        tint = colors.lavender,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Listening Overview",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary,
                                        fontSize = 16.sp
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable(onClick = onNavigateToLibrary)
                                ) {
                                    Text(
                                        text = "View All",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.textSecondary,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = colors.textMuted,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Row 1: Listening Time & Songs Played
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                MetricOverviewCard(
                                    label = "Listening Time",
                                    value = formatListeningTime(totalSeconds),
                                    painter = painterResource(id = R.drawable.ic_stat_time),
                                    modifier = Modifier.weight(1f)
                                )
                                MetricOverviewCard(
                                    label = "Songs Played",
                                    value = "$totalPlays",
                                    icon = Icons.Default.GraphicEq,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Row 2: Tracks Explored & #1 Top Song
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                MetricOverviewCard(
                                    label = "Tracks Explored",
                                    value = "$uniqueTracks",
                                    painter = painterResource(id = R.drawable.ic_stat_route),
                                    modifier = Modifier.weight(1f)
                                )
                                MetricOverviewCard(
                                    label = "#1 Top Song",
                                    value = topSong?.name ?: "No plays yet",
                                    painter = painterResource(id = R.drawable.ic_stat_star),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Fine divider between Listening Overview and Top 10
                        HorizontalDivider(
                            color = colors.border,
                            thickness = 1.dp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }

                    // 3. TOP 10 MOST PLAYED SONGS (Clean Minimal Library Rows)
                    if (topTracks.isNotEmpty()) {
                        item(contentType = "top_header") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Top 10 Most Played",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary,
                                    fontSize = 17.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Your all-time repeats on Sangita",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textSecondary,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }

                        itemsIndexed(
                            items = topTracks,
                            key = { _, tt -> tt.path },
                            contentType = { _, _ -> "track_item" }
                        ) { index, topTrack ->
                            val domainTrack = allTopTracksDomain[index]
                            TrackItemRow(
                                track = domainTrack,
                                serverUrl = viewModel.serverUrl,
                                token = viewModel.token,
                                index = index + 1,
                                isPlayingThisTrack = playbackState.currentTrack?.id == domainTrack.id,
                                playCount = topTrack.playCount,
                                onClick = { viewModel.playTrack(domainTrack, allTopTracksDomain) },
                                onDownloadClick = { viewModel.downloadTrack(domainTrack) },
                                onMenuClick = { /* Track context menu */ },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 1.dp)
                            )
                            // Subtle divider between list items
                            if (index < topTracks.size - 1) {
                                HorizontalDivider(
                                    color = colors.border.copy(alpha = 0.5f),
                                    thickness = 0.75.dp,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        }

        // Settings Bottom Sheet
        if (uiState.showSettingsSheet) {
            SettingsBottomSheet(
                sheetState = sheetState,
                connectedDomain = uiState.connectedDomain,
                username = uiState.username,
                currentTheme = uiState.currentTheme,
                isSyncing = uiState.isSyncing,
                onDismiss = viewModel::closeSettingsSheet,
                onSaveDomain = { newDomain, callback ->
                    viewModel.updateDomain(newDomain, callback)
                },
                onThemeSelect = viewModel::setTheme,
                onSyncNow = viewModel::syncLibraryNow,
                onLogout = {
                    viewModel.closeSettingsSheet()
                    viewModel.logout(onLogout)
                }
            )
        }

        // Multi-Device Sync / Cast Bottom Sheet
        if (uiState.showDevicesSheet) {
            val devicesSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            DeviceSyncBottomSheet(
                sheetState = devicesSheetState,
                devices = devices,
                onTransfer = viewModel::transferPlayback,
                onDismiss = viewModel::closeDevicesSheet,
                onRefresh = viewModel::refreshDevices
            )
        }

        // Live Download Queue Bottom Sheet
        if (uiState.showDownloadSheet) {
            val downloadSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            DownloadQueueBottomSheet(
                sheetState = downloadSheetState,
                downloadTasks = activeDownloads,
                totalSpeedBytesPerSec = totalDownloadSpeed,
                serverUrl = viewModel.serverUrl,
                token = viewModel.token,
                onCancelDownload = viewModel::cancelDownload,
                onCancelAll = viewModel::cancelAllDownloads,
                onDismiss = viewModel::closeDownloadSheet,
                onNavigateToDownloads = {
                    viewModel.closeDownloadSheet()
                    onNavigateToDownloads()
                }
            )
        }
    }
}

@Composable
private fun ActionNavCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    painter: Painter? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    val colors = SangitaThemeColors.current
    Card(
        modifier = modifier
            .height(88.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.card),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(colors.elevated)
                        .border(1.dp, colors.border, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (painter != null) {
                        Icon(
                            painter = painter,
                            contentDescription = null,
                            tint = colors.lavender,
                            modifier = Modifier.size(24.dp)
                        )
                    } else if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = colors.lavender,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(16.dp)
                )
            }

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun ActionNavCard(
    title: String,
    subtitle: String,
    painter: Painter,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) = ActionNavCard(
    title = title,
    subtitle = subtitle,
    modifier = modifier,
    painter = painter,
    icon = null,
    onClick = onClick
)

@Composable
private fun ActionNavCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) = ActionNavCard(
    title = title,
    subtitle = subtitle,
    modifier = modifier,
    painter = null,
    icon = icon,
    onClick = onClick
)

@Composable
private fun MetricOverviewCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    painter: Painter? = null,
    icon: ImageVector? = null
) {
    val colors = SangitaThemeColors.current
    Card(
        modifier = modifier.height(98.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.card),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(colors.elevated)
                    .border(1.dp, colors.border, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (painter != null) {
                    Icon(
                        painter = painter,
                        contentDescription = null,
                        tint = colors.lavender,
                        modifier = Modifier.size(16.dp)
                    )
                } else if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = colors.lavender,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 17.sp
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun MetricOverviewCard(
    label: String,
    value: String,
    painter: Painter,
    modifier: Modifier = Modifier
) = MetricOverviewCard(
    label = label,
    value = value,
    modifier = modifier,
    painter = painter,
    icon = null
)

@Composable
private fun MetricOverviewCard(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) = MetricOverviewCard(
    label = label,
    value = value,
    modifier = modifier,
    painter = null,
    icon = icon
)

fun formatListeningTime(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) {
        "${hours}h ${minutes}m"
    } else {
        "${minutes}m"
    }
}

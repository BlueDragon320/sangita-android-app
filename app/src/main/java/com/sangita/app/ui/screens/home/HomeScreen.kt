package com.sangita.app.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import com.sangita.app.R
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sangita.app.data.local.preferences.UserPreferences
import com.sangita.app.domain.model.DownloadStatus
import com.sangita.app.domain.model.Track
import com.sangita.app.ui.components.DeviceSyncBottomSheet
import com.sangita.app.ui.components.DownloadQueueBottomSheet
import com.sangita.app.ui.components.DownloadQueueIconButton
import com.sangita.app.ui.components.MiniPlayer
import com.sangita.app.ui.components.SettingsBottomSheet
import com.sangita.app.ui.components.TrackItemRow
import com.sangita.app.ui.theme.DarkBackground
import com.sangita.app.ui.theme.DarkBorder
import com.sangita.app.ui.theme.Emerald500
import com.sangita.app.ui.theme.MetricAmber
import com.sangita.app.ui.theme.MetricAmberBg
import com.sangita.app.ui.theme.MetricBlue
import com.sangita.app.ui.theme.MetricBlueBg
import com.sangita.app.ui.theme.MetricEmerald
import com.sangita.app.ui.theme.MetricEmeraldBg
import com.sangita.app.ui.theme.MetricPink
import com.sangita.app.ui.theme.MetricPinkBg
import com.sangita.app.ui.theme.SangitaCard
import com.sangita.app.ui.theme.SangitaThemeColors
import com.sangita.app.ui.theme.TextMuted
import com.sangita.app.ui.theme.TextPrimary
import com.sangita.app.ui.theme.TextSecondary
import java.util.Calendar

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
            .background(SangitaThemeColors.current.background)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(top = 8.dp),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SangitaThemeColors.current.card)
                                    .border(1.dp, SangitaThemeColors.current.border, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_sangita_brand_logo),
                                    contentDescription = "Sangita Logo",
                                    tint = SangitaThemeColors.current.accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "SANGITA",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "$greeting, ${uiState.username}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
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
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(SangitaThemeColors.current.card)
                                    .border(1.dp, SangitaThemeColors.current.border, CircleShape)
                                    .clickable(onClick = viewModel::openSettingsSheet),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = userInitial,
                                    color = SangitaThemeColors.current.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
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
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(SangitaThemeColors.current.card)
                            .border(1.dp, SangitaThemeColors.current.border, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = "Offline",
                            tint = SangitaThemeColors.current.textMuted,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Device is offline",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = SangitaThemeColors.current.textPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "You're currently offline. Listen to your downloaded playlists and songs in your library.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SangitaThemeColors.current.textSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onNavigateToLibrary,
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SangitaThemeColors.current.accent,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier.height(48.dp)
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
                            fontSize = 15.sp
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
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    // 1. HERO SECTION & ACTION CARDS
                    item(contentType = "hero_banner") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Welcome back,",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = uiState.username,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // 3 Action Cards (with chevron >)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ActionNavCard(
                                    title = "Library",
                                    icon = Icons.Default.LibraryMusic,
                                    accentColor = Color(0xFFA855F7),
                                    modifier = Modifier.weight(1f),
                                    onClick = onNavigateToLibrary
                                )
                                ActionNavCard(
                                    title = "Downloads",
                                    icon = Icons.Default.Download,
                                    accentColor = MetricBlue,
                                    modifier = Modifier.weight(1f),
                                    onClick = onNavigateToDownloads
                                )
                                ActionNavCard(
                                    title = dataUsageToday.ifBlank { "0 B" },
                                    icon = Icons.Default.DataUsage,
                                    accentColor = Color(0xFF06B6D4),
                                    modifier = Modifier.weight(1f),
                                    onClick = { viewModel.loadHomeData(force = true) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // 2. LISTENING OVERVIEW & 2x2 METRIC CARDS MATCHING REFERENCE DESIGN
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
                                        tint = Color(0xFF818CF8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Listening Overview",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable(onClick = onNavigateToLibrary)
                                ) {
                                    Text(
                                        text = "View All",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMuted,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = TextMuted,
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
                                    icon = Icons.Default.Timer,
                                    accentColor = MetricBlue,
                                    iconBg = MetricBlueBg,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricOverviewCard(
                                    label = "Songs Played",
                                    value = "$totalPlays",
                                    icon = Icons.Default.MusicNote,
                                    accentColor = MetricEmerald,
                                    iconBg = MetricEmeraldBg,
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
                                    icon = Icons.Default.GraphicEq,
                                    accentColor = MetricAmber,
                                    iconBg = MetricAmberBg,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricOverviewCard(
                                    label = "#1 Top Song",
                                    value = topSong?.name ?: "No plays yet",
                                    icon = Icons.Default.Favorite,
                                    accentColor = MetricPink,
                                    iconBg = MetricPinkBg,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    // 3. TOP 10 MOST PLAYED SONGS (Exact Match to Screenshot)
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
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Your all-time repeats on Sangita",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
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
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                            )
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
}

@Composable
private fun ActionNavCard(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SangitaThemeColors.current.card)
            .border(1.dp, SangitaThemeColors.current.border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF182030)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(15.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = SangitaThemeColors.current.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = SangitaThemeColors.current.textMuted,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun MetricOverviewCard(
    label: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    iconBg: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(96.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SangitaThemeColors.current.card),
        border = androidx.compose.foundation.BorderStroke(1.dp, SangitaThemeColors.current.border)
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
                    .background(Color(0xFF182030)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }

            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SangitaThemeColors.current.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = SangitaThemeColors.current.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

fun formatListeningTime(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) {
        "${hours}h ${minutes}m"
    } else {
        "${minutes}m"
    }
}

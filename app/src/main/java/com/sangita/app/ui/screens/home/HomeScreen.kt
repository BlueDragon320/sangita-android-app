package com.sangita.app.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sangita.app.ui.components.rememberCoverModel
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
import com.sangita.app.ui.components.NavTab
import com.sangita.app.ui.components.SangitaBottomNavBar
import com.sangita.app.ui.components.SettingsBottomSheet
import com.sangita.app.ui.components.TrackItemRow
import com.sangita.app.ui.dialogs.AddToPlaylistSheet
import com.sangita.app.ui.dialogs.CreatePlaylistDialog
import com.sangita.app.ui.theme.DeepNavyBlue
import com.sangita.app.ui.theme.SangitaThemeColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToLibrary: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToDiscover: () -> Unit = {},
    onOpenFullPlayer: () -> Unit,
    onLogout: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val pendingChanges by viewModel.pendingChanges.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val playbackState = viewModel.playbackState.collectAsState()
    val tracksMap by viewModel.tracksMap.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val activeDownloads by viewModel.activeDownloads.collectAsState()
    val activeDownloadsCount by viewModel.activeDownloadsCount.collectAsState()
    val totalDownloadSpeed by viewModel.totalDownloadSpeed.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = SangitaThemeColors.current

    var showAddToPlaylistSheet by remember { mutableStateOf(false) }
    var selectedTrackForPlaylist by remember { mutableStateOf<Track?>(null) }
    var showCreateDialogFromSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val currentPlayingTrackId by remember(playbackState) {
        derivedStateOf { playbackState.value.currentTrack?.id }
    }

    val userInitial = remember(uiState.username) {
        uiState.username.firstOrNull()?.uppercaseChar()?.toString() ?: "U"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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
                                        tint = colors.textPrimary,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Sangita",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary,
                                        fontSize = 16.sp,
                                        modifier = Modifier.semantics { heading() }
                                    )
                                    Text(
                                        text = "Personal library",
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
                                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                        .clickable(
                                            role = Role.Button,
                                            onClickLabel = "Open settings",
                                            onClick = viewModel::openSettingsSheet
                                        )
                                        .semantics {
                                            contentDescription = "Account and Settings"
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(colors.elevated)
                                            .border(1.dp, colors.border, CircleShape),
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
                Column(modifier = Modifier.fillMaxWidth()) {
                    val miniPlaybackState by viewModel.playbackState.collectAsState()
                    if (miniPlaybackState.currentTrack != null) {
                        MiniPlayer(
                            playbackState = miniPlaybackState,
                            serverUrl = viewModel.serverUrl,
                            token = viewModel.token,
                            onTogglePlayPause = viewModel.playbackController::togglePlayPause,
                            onSkipPrevious = viewModel.playbackController::skipPrevious,
                            onSkipNext = viewModel.playbackController::skipNext,
                            onOpenDevices = viewModel::openDevicesSheet,
                            onClick = onOpenFullPlayer
                        )
                    }
                    SangitaBottomNavBar(
                        currentTab = NavTab.HOME,
                        onTabSelected = { tab ->
                            when (tab) {
                                NavTab.HOME -> {}
                                NavTab.DISCOVER -> onNavigateToDiscover()
                                NavTab.LIBRARY -> onNavigateToLibrary()
                                NavTab.DOWNLOADS -> onNavigateToDownloads()
                                else -> {}
                            }
                        }
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
                            containerColor = colors.buttonBg,
                            contentColor = DeepNavyBlue
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
                val recentlyPlayed = stats?.recentlyPlayed ?: emptyList()

                val allRecentlyPlayedDomain = remember(recentlyPlayed, tracksMap) {
                    recentlyPlayed.map { rp ->
                        val repoTrack = tracksMap[rp.path]
                        Track(
                            id = rp.path,
                            title = rp.name,
                            artist = rp.artist,
                            playlist = rp.playlist,
                            durationSeconds = repoTrack?.durationSeconds ?: (if (rp.durationSec > 0) rp.durationSec.toDouble() else null),
                            localFilePath = repoTrack?.localFilePath,
                            isDownloaded = repoTrack?.isDownloaded ?: false,
                            downloadStatus = repoTrack?.downloadStatus ?: DownloadStatus.IDLE,
                            downloadProgress = repoTrack?.downloadProgress ?: 0,
                            fileSizeBytes = repoTrack?.fileSizeBytes ?: 0L,
                            isFavorite = repoTrack?.isFavorite ?: false
                        )
                    }
                }

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
                    // 1. QUICK ACTIONS: CLEAN LISTENING SHORTCUTS
                    item(contentType = "quick_actions") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ActionNavCard(
                                    title = "Discover",
                                    icon = Icons.Default.Explore,
                                    modifier = Modifier.weight(1f),
                                    onClick = onNavigateToDiscover
                                )

                                ActionNavCard(
                                    title = "Library",
                                    painter = painterResource(id = R.drawable.ic_folder_music),
                                    modifier = Modifier.weight(1f),
                                    onClick = onNavigateToLibrary
                                )

                                ActionNavCard(
                                    title = "Quick Shuffle",
                                    icon = Icons.Default.Shuffle,
                                    modifier = Modifier.weight(1f),
                                    onClick = viewModel::quickShuffle
                                )
                            }
                        }

                        // Fine divider between Quick Actions and Next Section
                        HorizontalDivider(
                            color = colors.border,
                            thickness = 1.dp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    // 2. JUMP BACK IN: RECENTLY PLAYED SHELF
                    if (allRecentlyPlayedDomain.isNotEmpty()) {
                        item(contentType = "jump_back_in_shelf") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                ) {
                                    Text(
                                        text = "Jump Back In",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary,
                                        fontSize = 16.sp,
                                        modifier = Modifier.semantics { heading() }
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Pick up right where you left off",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.textSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    itemsIndexed(
                                        items = allRecentlyPlayedDomain,
                                        key = { index, track -> "recent_${track.id}_$index" }
                                    ) { _, track ->
                                        RecentlyPlayedCard(
                                            track = track,
                                            serverUrl = viewModel.serverUrl,
                                            token = viewModel.token,
                                            isPlaying = currentPlayingTrackId == track.id,
                                            onClick = { viewModel.playTrack(track, allRecentlyPlayedDomain) },
                                            onDownloadClick = { viewModel.downloadTrack(track) },
                                            onDeleteDownload = { viewModel.deleteDownloadedTrack(track) },
                                            onToggleFavorite = { viewModel.toggleFavorite(track) },
                                            onAddToPlaylist = {
                                                selectedTrackForPlaylist = track
                                                showAddToPlaylistSheet = true
                                            }
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(
                                color = colors.border,
                                thickness = 1.dp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                    }

                    // 3. LISTENING OVERVIEW & 2x2 METRIC CARDS
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
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Listening Overview",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary,
                                        fontSize = 16.sp,
                                        modifier = Modifier.semantics { heading() }
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                        .clickable(
                                            role = Role.Button,
                                            onClickLabel = "View all in library",
                                            onClick = onNavigateToLibrary
                                        )
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
                                    fontSize = 17.sp,
                                    modifier = Modifier.semantics { heading() }
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
                                isPlayingThisTrack = currentPlayingTrackId == domainTrack.id,
                                playCount = topTrack.playCount,
                                onClick = { viewModel.playTrack(domainTrack, allTopTracksDomain) },
                                onDownloadClick = { viewModel.downloadTrack(domainTrack) },
                                onDeleteDownload = { viewModel.deleteDownloadedTrack(domainTrack) },
                                onToggleFavorite = { viewModel.toggleFavorite(domainTrack) },
                                onAddToPlaylist = {
                                    selectedTrackForPlaylist = domainTrack
                                    showAddToPlaylistSheet = true
                                },
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
                pendingChanges = pendingChanges,
                syncError = uiState.syncError,
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

        // Add to Playlist Bottom Sheet
        if (showAddToPlaylistSheet && selectedTrackForPlaylist != null) {
            val addSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            AddToPlaylistSheet(
                track = selectedTrackForPlaylist,
                playlists = playlists,
                onSelectPlaylist = { targetPlaylist ->
                    selectedTrackForPlaylist?.let { tr ->
                        viewModel.addTrackToPlaylist(targetPlaylist, tr) { success ->
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (success) "Added to ${targetPlaylist.title}" else "Failed to add to playlist"
                                )
                            }
                        }
                    }
                    showAddToPlaylistSheet = false
                    selectedTrackForPlaylist = null
                },
                onCreateNewPlaylist = {
                    showAddToPlaylistSheet = false
                    showCreateDialogFromSheet = true
                },
                onDismiss = {
                    showAddToPlaylistSheet = false
                    selectedTrackForPlaylist = null
                },
                sheetState = addSheetState
            )
        }

        // Create New Playlist Dialog from AddToPlaylistSheet
        if (showCreateDialogFromSheet) {
            CreatePlaylistDialog(
                onDismiss = {
                    showCreateDialogFromSheet = false
                    selectedTrackForPlaylist = null
                },
                onCreate = { title, description, isPublic ->
                    showCreateDialogFromSheet = false
                    viewModel.createPlaylist(title, description, isPublic) { newPlaylist ->
                        selectedTrackForPlaylist?.let { tr ->
                            viewModel.addTrackToPlaylist(newPlaylist, tr) { success ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        if (success) "Added to ${newPlaylist.title}" else "Failed to add to playlist"
                                    )
                                }
                            }
                            selectedTrackForPlaylist = null
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun ActionNavCard(
    title: String,
    modifier: Modifier = Modifier,
    painter: Painter? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    val colors = SangitaThemeColors.current
    Card(
        modifier = modifier
            .height(72.dp)
            .clickable(
                role = Role.Button,
                onClickLabel = "Open $title",
                onClick = onClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.card),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(colors.elevated),
                contentAlignment = Alignment.Center
            ) {
                if (painter != null) {
                    Icon(
                        painter = painter,
                        contentDescription = null,
                        tint = colors.textPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                } else if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = colors.textPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ActionNavCard(
    title: String,
    painter: Painter,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) = ActionNavCard(
    title = title,
    modifier = modifier,
    painter = painter,
    icon = null,
    onClick = onClick
)

@Composable
private fun ActionNavCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) = ActionNavCard(
    title = title,
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
        shape = RoundedCornerShape(12.dp),
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
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(colors.elevated),
                contentAlignment = Alignment.Center
            ) {
                if (painter != null) {
                    Icon(
                        painter = painter,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                } else if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFeatureSettings = "tnum"
                    ),
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 16.sp
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentlyPlayedCard(
    track: Track,
    serverUrl: String,
    token: String?,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit = {},
    onDeleteDownload: (() -> Unit)? = null,
    onToggleFavorite: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null
) {
    val colors = SangitaThemeColors.current
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    val coverModel = rememberCoverModel(track.id, track.localFilePath, serverUrl, token, track.isDownloaded)
    val imageRequest = remember(coverModel) {
        ImageRequest.Builder(context)
            .data(coverModel)
            .size(256, 256)
            .crossfade(false)
            .build()
    }

    Card(
        modifier = Modifier
            .width(136.dp)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = "Play ${track.title}",
                onClick = onClick,
                onLongClick = { menuExpanded = true }
            )
            .semantics {
                if (isPlaying) {
                    stateDescription = "Currently playing"
                }
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.card),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isPlaying) colors.accent else colors.border
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.elevated),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.40f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Playing",
                            tint = colors.accent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Download badge on top-left if downloaded
                if (track.isDownloaded) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .size(20.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Downloaded offline",
                            tint = colors.accent,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                // 3-dots button and context menu in top-right
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options for ${track.title}",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(colors.card)
                    ) {
                        if (onToggleFavorite != null) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (track.isFavorite) "Remove from favorites" else "Add to favorites",
                                        color = colors.textPrimary
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = null,
                                        tint = if (track.isFavorite) colors.accent else colors.textPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onToggleFavorite()
                                }
                            )
                        }
                        if (track.isDownloaded) {
                            if (onDeleteDownload != null) {
                                DropdownMenuItem(
                                    text = {
                                        Text("Delete download", color = colors.textPrimary)
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = colors.textMuted,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onDeleteDownload()
                                    }
                                )
                            }
                        } else {
                            DropdownMenuItem(
                                text = {
                                    Text("Download track", color = colors.textPrimary)
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = null,
                                        tint = colors.accent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onDownloadClick()
                                }
                            )
                        }
                        if (onAddToPlaylist != null) {
                            DropdownMenuItem(
                                text = {
                                    Text("Add to playlist", color = colors.textPrimary)
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = colors.textPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onAddToPlaylist()
                                }
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isPlaying) colors.accent else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.artist.ifBlank { "Unknown Artist" },
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 11.sp
            )
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

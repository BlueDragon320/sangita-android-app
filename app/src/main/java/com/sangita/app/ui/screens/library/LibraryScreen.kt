package com.sangita.app.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sangita.app.domain.model.Playlist
import com.sangita.app.domain.model.Track
import com.sangita.app.ui.components.DeviceSyncBottomSheet
import com.sangita.app.ui.components.DownloadQueueBottomSheet
import com.sangita.app.ui.components.DownloadQueueIconButton
import com.sangita.app.ui.components.MiniPlayer
import com.sangita.app.ui.components.SettingsBottomSheet
import com.sangita.app.ui.components.TrackItemRow
import com.sangita.app.ui.theme.SangitaThemeColors

private val LikedMusicPurple = Color(0xFF8B5CF6)
private val LikedMusicMagenta = Color(0xFFD946EF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onPlaylistClick: (String) -> Unit,
    onNavigateToDownloads: () -> Unit = {},
    onBackClick: () -> Unit = {},
    onOpenFullPlayer: () -> Unit,
    onLogout: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val filteredTracks by viewModel.filteredTracks.collectAsState()
    val filteredPlaylists by viewModel.filteredPlaylists.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val activeDownloads by viewModel.activeDownloads.collectAsState()
    val activeDownloadsCount by viewModel.activeDownloadsCount.collectAsState()
    val totalDownloadSpeed by viewModel.totalDownloadSpeed.collectAsState()
    var showDevicesSheet by remember { mutableStateOf(false) }

    val colors = SangitaThemeColors.current

    val currentPlayingTrackId = remember(playbackState.currentTrack?.id) {
        playbackState.currentTrack?.id
    }

    val regularPlaylists = remember(filteredPlaylists) {
        filteredPlaylists.filter { !it.isFavorites && !it.name.equals("Favorites", ignoreCase = true) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background)
                    .statusBarsPadding()
                    .padding(top = 8.dp)
            ) {
                // Top Bar: Back button + Large bold 'Library' title + Profile avatar circle (NO History icon)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = colors.textPrimary
                            )
                        }
                        Text(
                            text = "Library",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Live Download Queue Button opposite Library (only visible when downloading)
                        if (activeDownloadsCount > 0) {
                            DownloadQueueIconButton(
                                activeCount = activeDownloadsCount,
                                onClick = viewModel::openDownloadSheet
                            )
                        }

                        // Profile avatar circle (clickable to open Settings)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(colors.cardHover)
                                .border(1.dp, colors.border, CircleShape)
                                .clickable { viewModel.openSettingsSheet() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${viewModel.userInitial}",
                                color = colors.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Search bar: Sleek pill style with vertically centered BasicTextField (no text clipping)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(colors.card)
                        .border(1.dp, colors.border, RoundedCornerShape(22.dp))
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = colors.textMuted,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (uiState.searchQuery.isEmpty()) {
                                Text(
                                    text = "Search songs, playlists...",
                                    color = colors.textMuted,
                                    fontSize = 14.sp
                                )
                            }
                            BasicTextField(
                                value = uiState.searchQuery,
                                onValueChange = viewModel::onSearchQueryChange,
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = colors.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                cursorBrush = SolidColor(colors.accent),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (uiState.searchQuery.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = colors.textMuted,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { viewModel.onSearchQueryChange("") }
                            )
                        }
                    }
                }

                // Sub-header: Clean 'Recent activity' label (removed dropdown arrow and 4-box grid icon)
                if (uiState.searchQuery.isBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent activity",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            // New Playlist FAB: shown only in standard library view
            if (uiState.searchQuery.isBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(colors.textPrimary)
                        .clickable(onClick = viewModel::openCreateDialog)
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New",
                            tint = colors.background,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "New",
                            color = colors.background,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
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
                    onOpenDevices = { showDevicesSheet = true },
                    onClick = onOpenFullPlayer
                )
            }
        },
        containerColor = colors.background
    ) { innerPadding ->
        val isSearchActive = uiState.searchQuery.isNotBlank()
        val hasMatches = filteredTracks.isNotEmpty() || filteredPlaylists.isNotEmpty()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            if (isSearchActive) {
                if (!hasMatches) {
                    item(key = "empty_search_results") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 48.dp, start = 24.dp, end = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No matching songs or playlists found",
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.textMuted,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    // Matching Playlists (Shown First)
                    if (filteredPlaylists.isNotEmpty()) {
                        item(key = "header_playlists") {
                            Text(
                                text = "Playlists",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp)
                            )
                        }

                        items(
                            items = filteredPlaylists,
                            key = { "playlist_${it.name}" },
                            contentType = { "playlist" }
                        ) { playlist ->
                            PlaylistItemRow(
                                playlist = playlist,
                                username = viewModel.username,
                                serverUrl = viewModel.serverUrl,
                                token = viewModel.token,
                                onClick = { onPlaylistClick(playlist.name) },
                                onDownloadAll = { viewModel.downloadPlaylist(playlist) }
                            )
                        }
                    }

                    // Matching Songs (Shown Below Playlists)
                    if (filteredTracks.isNotEmpty()) {
                        item(key = "header_songs") {
                            Text(
                                text = "Songs",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp)
                            )
                        }

                        items(
                            items = filteredTracks,
                            key = { "track_${it.id}" },
                            contentType = { "track" }
                        ) { track ->
                            TrackItemRow(
                                track = track,
                                serverUrl = viewModel.serverUrl,
                                token = viewModel.token,
                                isPlayingThisTrack = currentPlayingTrackId == track.id,
                                onClick = { viewModel.playTrack(track) },
                                onDownloadClick = { viewModel.downloadTrack(track) },
                                onToggleFavorite = { viewModel.toggleFavorite(track) },
                                onMenuClick = { /* Track context menu */ },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            } else {
                // First Item: 'Liked music' (pinned auto playlist)
                item(key = "liked_music", contentType = "pinned_playlist") {
                    LikedMusicRow(
                        onClick = { onPlaylistClick("Favorites") },
                        onDownloadAll = {
                            viewModel.downloadPlaylist(
                                Playlist(name = "Favorites", isFavorites = true)
                            )
                        }
                    )
                }

                // Regular Playlists:
                items(
                    items = regularPlaylists,
                    key = { it.name },
                    contentType = { "playlist" }
                ) { playlist ->
                    PlaylistItemRow(
                        playlist = playlist,
                        username = viewModel.username,
                        serverUrl = viewModel.serverUrl,
                        token = viewModel.token,
                        onClick = { onPlaylistClick(playlist.name) },
                        onDownloadAll = { viewModel.downloadPlaylist(playlist) }
                    )
                }
            }
        }

        // Create Playlist Dialog
        if (uiState.showCreateDialog) {
            AlertDialog(
                onDismissRequest = viewModel::closeCreateDialog,
                title = {
                    Text(
                        text = "New Playlist",
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                },
                text = {
                    OutlinedTextField(
                        value = uiState.newPlaylistName,
                        onValueChange = viewModel::onNewPlaylistNameChange,
                        placeholder = { Text("Playlist name", color = colors.textMuted) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = colors.background,
                            unfocusedContainerColor = colors.background,
                            focusedBorderColor = colors.borderHi,
                            unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        )
                    )
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.createPlaylist() }) {
                        Text("Create", color = colors.accent, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::closeCreateDialog) {
                        Text("Cancel", color = colors.textMuted)
                    }
                },
                containerColor = colors.card
            )
        }

        // Settings Bottom Sheet
        if (uiState.showSettingsSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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

/**
 * Pinned Liked Music Row:
 * 48dp rounded square with purple/magenta gradient and white thumb up.
 * Title: 'Liked music' (16sp, bold)
 * Subtitle: PushPin + 'Auto playlist' (12sp, muted)
 * Right: 3-dots menu
 */
@Composable
fun LikedMusicRow(
    onClick: () -> Unit,
    onDownloadAll: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = SangitaThemeColors.current
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.linearGradient(listOf(LikedMusicPurple, LikedMusicMagenta))),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ThumbUp,
                contentDescription = "Liked music",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Liked music",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Auto playlist",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    fontSize = 12.sp
                )
            }
        }

        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (onDownloadAll != null) {
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.background(colors.card)
                ) {
                    DropdownMenuItem(
                        text = { Text("Download All", color = colors.textPrimary) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download All",
                                tint = colors.textPrimary
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDownloadAll()
                        }
                    )
                }
            }
        }
    }
}

/**
 * Playlist Item Row matching YouTube Music design:
 * 48dp x 48dp 2x2 collage artwork (or single cover, cached with remember).
 * Title: Playlist Name (16sp, bold).
 * Subtitle: 'Playlist • {username} • {trackCount} tracks' (13sp, secondary).
 * Right: 3-dots menu.
 */
@Composable
fun PlaylistItemRow(
    playlist: Playlist,
    username: String,
    serverUrl: String,
    token: String?,
    onClick: () -> Unit,
    onDownloadAll: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = SangitaThemeColors.current
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 48dp x 48dp thumbnail
        PlaylistThumbnail(
            playlist = playlist,
            serverUrl = serverUrl,
            token = token,
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Playlist • $username • ${playlist.trackCount} tracks",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.background(colors.card)
            ) {
                DropdownMenuItem(
                    text = { Text("Download All", color = colors.textPrimary) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download All",
                            tint = colors.textPrimary
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onDownloadAll()
                    }
                )
            }
        }
    }
}

/**
 * 2x2 collage artwork or single cover thumbnail for smooth scrolling.
 */
@Composable
fun PlaylistThumbnail(
    playlist: Playlist,
    serverUrl: String,
    token: String?,
    modifier: Modifier = Modifier
) {
    val colors = SangitaThemeColors.current
    val covers = remember(playlist.coverTrackPath) {
        playlist.coverTrackPath?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
    }

    Box(
        modifier = modifier
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.cardHover)
    ) {
        if (covers.size >= 4) {
            // Fixed 24dp quadrants (zero double-pass measurement for 120 FPS scrolling)
            Column(modifier = Modifier.size(48.dp)) {
                Row(modifier = Modifier.size(width = 48.dp, height = 24.dp)) {
                    ThumbnailQuadrant(trackId = covers[0], serverUrl = serverUrl, token = token, modifier = Modifier.size(24.dp))
                    ThumbnailQuadrant(trackId = covers[1], serverUrl = serverUrl, token = token, modifier = Modifier.size(24.dp))
                }
                Row(modifier = Modifier.size(width = 48.dp, height = 24.dp)) {
                    ThumbnailQuadrant(trackId = covers[2], serverUrl = serverUrl, token = token, modifier = Modifier.size(24.dp))
                    ThumbnailQuadrant(trackId = covers[3], serverUrl = serverUrl, token = token, modifier = Modifier.size(24.dp))
                }
            }
        } else if (covers.isNotEmpty()) {
            ThumbnailQuadrant(
                trackId = covers[0],
                serverUrl = serverUrl,
                token = token,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun ThumbnailQuadrant(
    trackId: String,
    serverUrl: String,
    token: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coverUrl = "$serverUrl/api/cover/$trackId?token=${token ?: ""}"
    val request = remember(coverUrl) {
        ImageRequest.Builder(context)
            .data(coverUrl)
            .size(96, 96)
            .crossfade(100)
            .build()
    }

    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
    )
}

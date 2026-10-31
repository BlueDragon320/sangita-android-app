package com.sangita.app.ui.screens.library

import com.sangita.app.data.local.storage.LocalStorageManager
import com.sangita.app.ui.components.rememberCoverModel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import com.sangita.app.ui.dialogs.RecoverLegacyPlaylistsDialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sangita.app.data.remote.dto.ArtistDto
import com.sangita.app.domain.model.Playlist
import com.sangita.app.domain.model.PlaylistSyncState
import com.sangita.app.domain.model.Track
import com.sangita.app.ui.dialogs.AddToPlaylistSheet
import com.sangita.app.ui.dialogs.CreatePlaylistDialog
import com.sangita.app.ui.components.DeviceSyncBottomSheet
import com.sangita.app.ui.components.DownloadQueueBottomSheet
import com.sangita.app.ui.components.DownloadQueueIconButton
import com.sangita.app.ui.components.MiniPlayer
import com.sangita.app.ui.components.NavTab
import com.sangita.app.ui.components.SangitaBottomNavBar
import com.sangita.app.ui.components.SettingsBottomSheet
import com.sangita.app.ui.components.TrackItemRow
import com.sangita.app.ui.theme.SangitaColors
import com.sangita.app.ui.theme.SangitaThemeColors



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onPlaylistClick: (String) -> Unit,
    onArtistClick: (String) -> Unit = {},
    onNavigateToHome: () -> Unit = {},
    onNavigateToDiscover: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
    onBackClick: () -> Unit = {},
    onOpenFullPlayer: () -> Unit,
    onLogout: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val pendingChanges by viewModel.pendingChanges.collectAsState()
    val filteredTracks by viewModel.filteredTracks.collectAsState()
    val filteredPlaylists by viewModel.filteredPlaylists.collectAsState()
    val libraryPlaylists by viewModel.libraryPlaylists.collectAsState()
    val libraryArtists by viewModel.libraryArtists.collectAsState()
    val librarySongs by viewModel.librarySongs.collectAsState()
    val downloadedTracks by viewModel.downloadedTracks.collectAsState()
    val playbackState = viewModel.playbackState.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val activeDownloads by viewModel.activeDownloads.collectAsState()
    val activeDownloadsCount by viewModel.activeDownloadsCount.collectAsState()
    val totalDownloadSpeed by viewModel.totalDownloadSpeed.collectAsState()
    val recoverablePlaylists by viewModel.recoverableLegacyPlaylists.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showDevicesSheet by remember { mutableStateOf(false) }
    var showAddToPlaylistSheet by remember { mutableStateOf(false) }
    var selectedTrackForPlaylist by remember { mutableStateOf<Track?>(null) }

    val colors = SangitaThemeColors.current
    val activeTab = uiState.selectedFilter ?: "Playlists"

    val currentPlayingTrackId by remember(playbackState) {
        derivedStateOf { playbackState.value.currentTrack?.id }
    }

    val regularPlaylists = remember(libraryPlaylists) {
        libraryPlaylists.filter { !it.isFavorites && !it.name.equals("Favorites", ignoreCase = true) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                            color = colors.textPrimary,
                            modifier = Modifier.semantics { heading() }
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
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(colors.cardHover)
                                    .border(1.dp, colors.border, CircleShape),
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics {
                                        contentDescription = "Search songs and playlists"
                                    }
                            )
                        }
                        if (uiState.searchQuery.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search query",
                                tint = colors.textMuted,
                                modifier = Modifier
                                    .size(18.dp)
                                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    .clickable(
                                        role = Role.Button,
                                        onClickLabel = "Clear search query"
                                    ) { viewModel.onSearchQueryChange("") }
                            )
                        }
                    }
                }

                // Horizontal filter chips: Playlists, Artists, Songs, Downloads
                val filterTabs = listOf("Playlists", "Artists", "Songs", "Downloads")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filterTabs.forEach { tab ->
                        val isSelected = activeTab.equals(tab, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) colors.textPrimary else colors.card)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) colors.textPrimary else colors.border,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable(
                                    role = Role.Tab,
                                    onClick = { viewModel.selectFilter(tab) }
                                )
                                .semantics {
                                    selected = isSelected
                                    stateDescription = if (isSelected) "Selected" else "Not selected"
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab,
                                color = if (isSelected) colors.background else colors.textSecondary,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            // New Playlist FAB: shown on Playlists tab when search query is blank
            if (uiState.searchQuery.isBlank() && activeTab.equals("Playlists", ignoreCase = true)) {
                Box(
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(colors.textPrimary)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "Create new playlist",
                            onClick = viewModel::openCreateDialog
                        )
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
                        onOpenDevices = { showDevicesSheet = true },
                        onClick = onOpenFullPlayer
                    )
                }
                SangitaBottomNavBar(
                    currentTab = NavTab.LIBRARY,
                    onTabSelected = { tab ->
                        when (tab) {
                            NavTab.HOME -> onNavigateToHome()
                            NavTab.DISCOVER -> onNavigateToDiscover()
                            NavTab.LIBRARY -> {}
                            NavTab.DOWNLOADS -> onNavigateToDownloads()
                            else -> {}
                        }
                    }
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
                                modifier = Modifier
                                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp)
                                    .semantics { heading() }
                            )
                        }

                        items(
                            items = filteredPlaylists,
                            key = { "playlist_${it.id}" },
                            contentType = { "playlist" }
                        ) { playlist ->
                            PlaylistItemRow(
                                playlist = playlist,
                                username = viewModel.username,
                                serverUrl = viewModel.serverUrl,
                                token = viewModel.token,
                                onClick = { onPlaylistClick(playlist.id) },
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
                                modifier = Modifier
                                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp)
                                    .semantics { heading() }
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
                                onDeleteDownload = { viewModel.deleteDownloadedTrack(track) },
                                onToggleFavorite = { viewModel.toggleFavorite(track) },
                                onAddToPlaylist = {
                                    selectedTrackForPlaylist = track
                                    showAddToPlaylistSheet = true
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            } else {
                when (activeTab) {
                    "Playlists" -> {
                        // Recoverable offline playlists banner
                        if (recoverablePlaylists.isNotEmpty()) {
                            item(key = "recoverable_legacy_banner") {
                                LegacyRecoveryBanner(
                                    count = recoverablePlaylists.size,
                                    onInspect = viewModel::openRecoveryDialog,
                                    onRecoverAll = {
                                        viewModel.recoverAllLegacyPlaylists { count ->
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Recovered $count offline playlist${if (count == 1) "" else "s"}")
                                            }
                                        }
                                    }
                                )
                            }
                        }

                        val favPlaylist = libraryPlaylists.firstOrNull { it.isFavorites }
                        val isPlaylistsEmpty = regularPlaylists.isEmpty() && (favPlaylist == null || favPlaylist.trackCount == 0)

                        if (isPlaylistsEmpty) {
                            item(key = "empty_playlists") {
                                LibraryEmptyState(
                                    icon = Icons.Default.MusicNote,
                                    title = "Your library is empty",
                                    description = "Discover shared music in the catalog or create your personal playlist.",
                                    colors = colors,
                                    primaryButtonText = "Explore Discover",
                                    onPrimaryClick = onNavigateToDiscover,
                                    secondaryButtonText = "Create Playlist",
                                    onSecondaryClick = viewModel::openCreateDialog
                                )
                            }
                        } else {
                            // First Item: 'Liked music' (pinned auto playlist)
                            item(key = "liked_music", contentType = "pinned_playlist") {
                                LikedMusicRow(
                                    onClick = { onPlaylistClick(favPlaylist?.id ?: "Favorites") },
                                    onDownloadAll = {
                                        viewModel.downloadPlaylist(
                                            favPlaylist ?: Playlist(name = "Favorites", isFavorites = true)
                                        )
                                    }
                                )
                            }

                            // Regular Playlists
                            items(
                                items = regularPlaylists,
                                key = { it.id },
                                contentType = { "playlist" }
                            ) { playlist ->
                                PlaylistItemRow(
                                    playlist = playlist,
                                    username = viewModel.username,
                                    serverUrl = viewModel.serverUrl,
                                    token = viewModel.token,
                                    onClick = { onPlaylistClick(playlist.id) },
                                    onDownloadAll = { viewModel.downloadPlaylist(playlist) }
                                )
                            }
                        }
                    }

                    "Artists" -> {
                        if (libraryArtists.isEmpty()) {
                            item(key = "empty_artists") {
                                LibraryEmptyState(
                                    icon = Icons.Default.Person,
                                    title = "No saved artists",
                                    description = "Find your favorite artists in Discover and save them to your library.",
                                    colors = colors,
                                    primaryButtonText = "Explore Discover",
                                    onPrimaryClick = onNavigateToDiscover
                                )
                            }
                        } else {
                            items(
                                items = libraryArtists,
                                key = { "artist_${it.id}_${it.name}" },
                                contentType = { "artist" }
                            ) { artist ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.weight(1f)) {
                                        LibraryArtistRow(artist = artist, serverUrl = viewModel.serverUrl, onClick = { onArtistClick(artist.id) })
                                    }
                                    TextButton(
                                        onClick = { viewModel.removeSavedArtist(artist.id) { ok -> scope.launch { snackbarHostState.showSnackbar(if (ok) "Artist removed from library" else "Could not remove artist") } } },
                                        modifier = Modifier
                                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                            .semantics { contentDescription = "Remove ${artist.name} from library" }
                                    ) { Text("Remove") }
                                }
                            }
                        }
                    }

                    "Songs" -> {
                        if (librarySongs.isEmpty()) {
                            item(key = "empty_songs") {
                                LibraryEmptyState(
                                    icon = Icons.Default.MusicNote,
                                    title = "No songs in library",
                                    description = "Save tracks or playlists from Discover to start building your personal collection.",
                                    colors = colors,
                                    primaryButtonText = "Explore Discover",
                                    onPrimaryClick = onNavigateToDiscover
                                )
                            }
                        } else {
                            items(
                                items = librarySongs,
                                key = { "song_${it.id}" },
                                contentType = { "song" }
                            ) { track ->
                                TrackItemRow(
                                    track = track,
                                    serverUrl = viewModel.serverUrl,
                                    token = viewModel.token,
                                    isPlayingThisTrack = currentPlayingTrackId == track.id,
                                    onClick = { viewModel.playTrack(track) },
                                    onDownloadClick = { viewModel.downloadTrack(track) },
                                    onDeleteDownload = { viewModel.deleteDownloadedTrack(track) },
                                    onToggleFavorite = { viewModel.toggleFavorite(track) },
                                    onAddToPlaylist = {
                                        selectedTrackForPlaylist = track
                                        showAddToPlaylistSheet = true
                                    },
                                    onToggleSave = {
                                        viewModel.removeSavedTrack(track.id) { ok ->
                                            scope.launch {
                                                snackbarHostState.showSnackbar(
                                                    if (ok) "Song removed from library" else "Could not remove song"
                                                )
                                            }
                                        }
                                    },
                                    isSaved = true,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    "Downloads" -> {
                        if (downloadedTracks.isEmpty()) {
                            item(key = "empty_downloads") {
                                LibraryEmptyState(
                                    icon = Icons.Default.Download,
                                    title = "No offline downloads",
                                    description = "Download songs or playlists to listen offline anywhere without an internet connection.",
                                    colors = colors,
                                    primaryButtonText = "Explore Discover",
                                    onPrimaryClick = onNavigateToDiscover
                                )
                            }
                        } else {
                            items(
                                items = downloadedTracks,
                                key = { "downloaded_${it.id}" },
                                contentType = { "downloaded_track" }
                            ) { track ->
                                TrackItemRow(
                                    track = track,
                                    serverUrl = viewModel.serverUrl,
                                    token = viewModel.token,
                                    isPlayingThisTrack = currentPlayingTrackId == track.id,
                                    onClick = { viewModel.playTrack(track) },
                                    onDownloadClick = { viewModel.downloadTrack(track) },
                                    onDeleteDownload = { viewModel.deleteDownloadedTrack(track) },
                                    onToggleFavorite = { viewModel.toggleFavorite(track) },
                                    onAddToPlaylist = {
                                        selectedTrackForPlaylist = track
                                        showAddToPlaylistSheet = true
                                    },
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Add to Playlist Sheet
        if (showAddToPlaylistSheet && selectedTrackForPlaylist != null) {
            AddToPlaylistSheet(
                track = selectedTrackForPlaylist,
                playlists = playlists,
                onSelectPlaylist = { playlist ->
                    showAddToPlaylistSheet = false
                    viewModel.addTrackToPlaylist(playlist, selectedTrackForPlaylist!!) { success ->
                        scope.launch {
                            snackbarHostState.showSnackbar(if (success) "Added to ${playlist.name}" else "Could not add track")
                        }
                    }
                },
                onCreateNewPlaylist = {
                    showAddToPlaylistSheet = false
                    viewModel.openCreatePlaylistDialog()
                },
                onDismiss = { showAddToPlaylistSheet = false }
            )
        }

        // Create Playlist Dialog
        if (uiState.showCreateDialog) {
            CreatePlaylistDialog(
                onDismiss = viewModel::closeCreateDialog,
                onCreate = { title, description, isPublic ->
                    viewModel.createPlaylist(title, description, isPublic)
                }
            )
        }

        // Recover Legacy Playlists Dialog
        if (uiState.showRecoveryDialog && recoverablePlaylists.isNotEmpty()) {
            RecoverLegacyPlaylistsDialog(
                playlists = recoverablePlaylists,
                onDismiss = viewModel::closeRecoveryDialog,
                onRecoverSingle = { localKey ->
                    viewModel.recoverLegacyPlaylist(localKey) { success ->
                        if (success) {
                            scope.launch {
                                snackbarHostState.showSnackbar("Playlist recovered to your library")
                            }
                        }
                    }
                },
                onRecoverAll = {
                    viewModel.recoverAllLegacyPlaylists { count ->
                        scope.launch {
                            snackbarHostState.showSnackbar("Recovered $count offline playlist${if (count == 1) "" else "s"}")
                        }
                    }
                }
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
            .clickable(
                role = Role.Button,
                onClickLabel = "Open Liked music",
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.elevated)
                .border(1.dp, colors.border, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ThumbUp,
                contentDescription = null,
                tint = colors.accent,
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
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options for Liked music",
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
            .clickable(
                role = Role.Button,
                onClickLabel = "Open playlist ${playlist.name}",
                onClick = onClick
            )
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (playlist.isDownloaded) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Downloaded offline",
                        tint = colors.accent,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Sync state badge
                if (playlist.syncState != PlaylistSyncState.SYNCED) {
                    when (playlist.syncState) {
                        PlaylistSyncState.PENDING_CREATE, PlaylistSyncState.PENDING_UPDATE -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = colors.accent
                            )
                        }
                        PlaylistSyncState.ERROR, PlaylistSyncState.CONFLICT -> {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Sync issue",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        else -> Unit
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))
            val displayOwner = playlist.owner ?: username
            val prefix = if (playlist.isPublic) "Public Playlist" else "Playlist"
            Text(
                text = "$prefix • $displayOwner • ${playlist.trackCount} tracks",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val cleanDescription = playlist.description?.takeIf {
                !it.trim().startsWith("Folder playlist for", ignoreCase = true)
            }
            if (!cleanDescription.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = cleanDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options for ${playlist.name}",
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
    val coverModel = rememberCoverModel(trackId, null, serverUrl, token)
    val request = remember(coverModel) {
        ImageRequest.Builder(context)
            .data(coverModel)
            .size(96, 96)
            .crossfade(false)
            .build()
    }

    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
    )
}

@Composable
private fun LegacyRecoveryBanner(
    count: Int,
    onInspect: () -> Unit,
    onRecoverAll: () -> Unit
) {
    val colors = SangitaThemeColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.cardHover)
            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.Restore,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Recover Offline Playlists",
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$count unassigned playlist${if (count == 1) "" else "s"} from prior version",
                    color = colors.textMuted,
                    fontSize = 12.sp
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onInspect,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(40.dp)
            ) {
                Text(
                    text = "Review",
                    color = colors.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Button(
                onClick = onRecoverAll,
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.buttonContent
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(40.dp)
            ) {
                Text(
                    text = "Recover All",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun LibraryEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    colors: SangitaColors,
    primaryButtonText: String,
    onPrimaryClick: () -> Unit,
    secondaryButtonText: String? = null,
    onSecondaryClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(colors.card)
                .border(1.dp, colors.border, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            fontSize = 18.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
            textAlign = TextAlign.Center,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onPrimaryClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.textPrimary,
                    contentColor = colors.background
                ),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Text(
                    text = primaryButtonText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            if (secondaryButtonText != null && onSecondaryClick != null) {
                Button(
                    onClick = onSecondaryClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.card,
                        contentColor = colors.textPrimary
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = secondaryButtonText,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun LibraryArtistRow(
    artist: ArtistDto,
    serverUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SangitaThemeColors.current
    val context = LocalContext.current
    val fullImageUrl = remember(artist.imageUrl, serverUrl) {
        val url = artist.imageUrl
        if (url.isNullOrBlank()) null
        else if (url.startsWith("http://") || url.startsWith("https://")) url
        else "${serverUrl.trimEnd('/')}/${url.trimStart('/')}"
    }

    val imageRequest = remember(fullImageUrl) {
        fullImageUrl?.let {
            ImageRequest.Builder(context)
                .data(it)
                .crossfade(true)
                .size(120, 120)
                .build()
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = "Open artist ${artist.name}",
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(colors.card)
                .border(1.dp, colors.border, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (imageRequest != null) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = artist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            val trackCountText = if (artist.trackCount > 0) "${artist.trackCount} ${if (artist.trackCount == 1) "track" else "tracks"}" else "Artist"
            Text(
                text = "Artist • $trackCountText",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

package com.sangita.app.ui.screens.discover

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sangita.app.data.remote.dto.ArtistDto
import com.sangita.app.domain.model.Playlist
import com.sangita.app.ui.components.MiniPlayer
import com.sangita.app.ui.components.NavTab
import com.sangita.app.ui.components.SangitaBottomNavBar
import com.sangita.app.ui.screens.library.PlaylistThumbnail
import com.sangita.app.ui.theme.SangitaColors
import com.sangita.app.ui.theme.SangitaThemeColors

@Composable
fun DiscoverScreen(
    viewModel: DiscoverViewModel,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onOpenFullPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val savedPlaylistIds by viewModel.savedPlaylistIds.collectAsState()
    val colors = SangitaThemeColors.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Large Top Title
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Discover",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        fontSize = 28.sp,
                        modifier = Modifier.semantics { heading() }
                    )

                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = colors.accent,
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(
                            onClick = viewModel::refresh,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh catalog",
                                tint = colors.textSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Search Filter TextField
                DiscoverSearchBar(
                    searchQuery = uiState.searchQuery,
                    onSearchQueryChange = viewModel::onSearchQueryChange,
                    colors = colors
                )

                // Offline Notice Banner
                AnimatedVisibility(
                    visible = uiState.isOffline,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.card),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = "Offline",
                                tint = colors.textMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Offline — showing cached catalog",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "Retry",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.accent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    .clickable(
                                        role = Role.Button,
                                        onClickLabel = "Retry catalog connection"
                                    ) { viewModel.refresh() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (playbackState.currentTrack != null) {
                    MiniPlayer(
                        playbackState = playbackState,
                        serverUrl = viewModel.serverUrl,
                        token = viewModel.token,
                        onTogglePlayPause = viewModel.playbackController::togglePlayPause,
                        onSkipPrevious = viewModel.playbackController::skipPrevious,
                        onSkipNext = viewModel.playbackController::skipNext,
                        onOpenDevices = {},
                        onClick = onOpenFullPlayer
                    )
                }

                SangitaBottomNavBar(
                    currentTab = NavTab.DISCOVER,
                    onTabSelected = { tab ->
                        when (tab) {
                            NavTab.HOME -> onNavigateToHome()
                            NavTab.DISCOVER -> {}
                            NavTab.LIBRARY -> onNavigateToLibrary()
                            NavTab.DOWNLOADS -> onNavigateToDownloads()
                            else -> {}
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                // Shimmer / Loading State when initially empty
                uiState.isLoading && uiState.artists.isEmpty() && uiState.folderPlaylists.isEmpty() && uiState.communityPlaylists.isEmpty() -> {
                    DiscoverLoadingState(colors = colors)
                }

                // Error State with Retry Button
                uiState.errorMessage != null && uiState.artists.isEmpty() && uiState.folderPlaylists.isEmpty() && uiState.communityPlaylists.isEmpty() -> {
                    DiscoverErrorState(
                        errorMessage = uiState.errorMessage ?: "Failed to load discovery content",
                        onRetry = viewModel::refresh,
                        colors = colors
                    )
                }

                // Empty State when Search yields no results
                uiState.searchQuery.isNotBlank() && uiState.artists.isEmpty() && uiState.folderPlaylists.isEmpty() && uiState.communityPlaylists.isEmpty() -> {
                    DiscoverEmptySearchState(
                        query = uiState.searchQuery,
                        colors = colors
                    )
                }

                // Empty State when catalog is completely empty
                !uiState.isLoading && uiState.errorMessage == null && uiState.searchQuery.isBlank() &&
                    uiState.artists.isEmpty() && uiState.folderPlaylists.isEmpty() && uiState.communityPlaylists.isEmpty() -> {
                    DiscoverEmptyCatalogState(
                        onRefresh = viewModel::refresh,
                        colors = colors
                    )
                }

                // Main Content List
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
                    ) {
                        // Section 1: Artists (Horizontal carousel)
                        if (uiState.artists.isNotEmpty()) {
                            item(key = "section_artists_header") {
                                SectionHeader(
                                    title = "Artists",
                                    count = uiState.artists.size,
                                    colors = colors
                                )
                            }
                            item(key = "section_artists_carousel") {
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(
                                        items = uiState.artists,
                                        key = { it.id }
                                    ) { artist ->
                                        ArtistCard(
                                            artist = artist,
                                            serverUrl = viewModel.serverUrl,
                                            onClick = { onArtistClick(artist.id) },
                                            colors = colors
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(20.dp))
                            }
                        }

                        // Section 2: Folder Playlists (Classified catalog folders)
                        if (uiState.folderPlaylists.isNotEmpty()) {
                            item(key = "section_folders_header") {
                                SectionHeader(
                                    title = "Folder Playlists",
                                    subtitle = "Classified folders from catalog",
                                    count = uiState.folderPlaylists.size,
                                    colors = colors
                                )
                            }
                            items(
                                items = uiState.folderPlaylists,
                                key = { "folder_${it.id}" }
                            ) { playlist ->
                                FolderPlaylistItem(
                                    playlist = playlist,
                                    serverUrl = viewModel.serverUrl,
                                    token = viewModel.token,
                                    onClick = { onPlaylistClick(playlist.id) },
                                    onPlayClick = { viewModel.playPlaylist(playlist) },
                                    colors = colors,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                            }
                            item(key = "section_folders_spacer") {
                                Spacer(modifier = Modifier.height(20.dp))
                            }
                        }

                        // Section 3: Community Playlists (Public playlists)
                        if (uiState.communityPlaylists.isNotEmpty()) {
                            item(key = "section_community_header") {
                                SectionHeader(
                                    title = "Community Playlists",
                                    subtitle = "Public playlists from listeners",
                                    count = uiState.communityPlaylists.size,
                                    colors = colors
                                )
                            }
                            items(
                                items = uiState.communityPlaylists,
                                key = { "community_${it.id}" }
                            ) { playlist ->
                                val isSaved = savedPlaylistIds.contains(playlist.id) ||
                                    (playlist.serverId != null && savedPlaylistIds.contains(playlist.serverId)) ||
                                    playlist.isSaved
                                CommunityPlaylistItem(
                                    playlist = playlist,
                                    isSaved = isSaved,
                                    serverUrl = viewModel.serverUrl,
                                    token = viewModel.token,
                                    onClick = { onPlaylistClick(playlist.id) },
                                    onPlayClick = { viewModel.playPlaylist(playlist) },
                                    onToggleSave = { viewModel.toggleSavePlaylist(playlist) },
                                    colors = colors,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverSearchBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    colors: SangitaColors
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card)
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search",
            tint = colors.textMuted,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            if (searchQuery.isEmpty()) {
                Text(
                    text = "Search artists, playlists...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                    fontSize = 14.sp
                )
            }
            BasicTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
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
                        contentDescription = "Search artists and playlists"
                    }
            )
        }
        if (searchQuery.isNotEmpty()) {
            IconButton(
                onClick = { onSearchQueryChange("") },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Clear search",
                    tint = colors.textMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String? = null,
    count: Int? = null,
    colors: SangitaColors
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                fontSize = 18.sp,
                modifier = Modifier.semantics { heading() }
            )
            if (count != null) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                    fontSize = 12.sp
                )
            }
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun ArtistCard(
    artist: ArtistDto,
    serverUrl: String,
    onClick: () -> Unit,
    colors: SangitaColors
) {
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
                .size(180, 180)
                .build()
        }
    }

    Column(
        modifier = Modifier
            .width(96.dp)
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                role = Role.Button,
                onClickLabel = "View artist ${artist.name}",
                onClick = onClick
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(colors.elevated)
                .border(1.5.dp, colors.border, CircleShape),
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
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            fontSize = 13.sp
        )

        Text(
            text = "${artist.trackCount} tracks",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
            maxLines = 1,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun FolderPlaylistItem(
    playlist: Playlist,
    serverUrl: String,
    token: String?,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    colors: SangitaColors,
    modifier: Modifier = Modifier
) {
    val cleanDescription = playlist.description?.takeIf {
        !it.trim().startsWith("Folder playlist for", ignoreCase = true)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                role = Role.Button,
                onClickLabel = "Open playlist ${playlist.title}",
                onClick = onClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.card),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlaylistThumbnail(
                playlist = playlist,
                serverUrl = serverUrl,
                token = token,
                modifier = Modifier.size(44.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${playlist.trackCount} tracks",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    fontSize = 12.sp
                )
                if (!cleanDescription.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = cleanDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 11.sp
                    )
                }
            }

            IconButton(
                onClick = onPlayClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play playlist ${playlist.title}",
                    tint = colors.accent
                )
            }
        }
    }
}

@Composable
private fun CommunityPlaylistItem(
    playlist: Playlist,
    isSaved: Boolean,
    serverUrl: String,
    token: String?,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    onToggleSave: () -> Unit,
    colors: SangitaColors,
    modifier: Modifier = Modifier
) {
    val cleanDescription = playlist.description?.takeIf {
        !it.trim().startsWith("Folder playlist for", ignoreCase = true)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                role = Role.Button,
                onClickLabel = "Open playlist ${playlist.title}",
                onClick = onClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.card),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlaylistThumbnail(
                playlist = playlist,
                serverUrl = serverUrl,
                token = token,
                modifier = Modifier.size(44.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                val creator = playlist.owner?.let { "by $it • " }.orEmpty()
                Text(
                    text = "Public Playlist • $creator${playlist.trackCount} tracks",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp
                )
                if (!cleanDescription.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = cleanDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 11.sp
                    )
                }
            }

            // Save / Bookmark button: "Add to Library" / "In Library"
            IconButton(
                onClick = onToggleSave,
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        stateDescription = if (isSaved) "Saved to library" else "Not in library"
                        role = Role.Checkbox
                    }
            ) {
                Icon(
                    imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = if (isSaved) "Remove ${playlist.title} from library" else "Save ${playlist.title} to library",
                    tint = if (isSaved) colors.accent else colors.textSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }

            IconButton(
                onClick = onPlayClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play playlist ${playlist.title}",
                    tint = colors.accent
                )
            }
        }
    }
}

@Composable
private fun DiscoverLoadingState(colors: SangitaColors) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            color = colors.accent,
            strokeWidth = 3.dp,
            modifier = Modifier.size(36.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Loading discovery catalog...",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun DiscoverErrorState(
    errorMessage: String,
    onRetry: () -> Unit,
    colors: SangitaColors
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(colors.card)
                .border(1.dp, colors.border, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = "Error",
                tint = colors.textMuted,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Couldn't load discovery",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                contentColor = colors.buttonContent
            ),
            modifier = Modifier
                .height(48.dp)
                .padding(horizontal = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Retry",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun DiscoverEmptySearchState(
    query: String,
    colors: SangitaColors
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(colors.card)
                .border(1.dp, colors.border, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SearchOff,
                contentDescription = "No results",
                tint = colors.textMuted,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No results found for \"$query\"",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            fontSize = 17.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Try searching for a different artist name or playlist title.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun DiscoverEmptyCatalogState(
    onRefresh: () -> Unit,
    colors: SangitaColors
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(colors.card)
                .border(1.dp, colors.border, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Explore,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Catalog is empty",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "No artists or playlists available right now.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onRefresh,
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                contentColor = colors.buttonContent
            ),
            modifier = Modifier
                .height(48.dp)
                .padding(horizontal = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Refresh",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

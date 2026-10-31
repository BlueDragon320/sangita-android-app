package com.sangita.app.ui.screens.artist

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sangita.app.data.remote.dto.ArtistDetailDto
import com.sangita.app.data.remote.dto.ArtistTrackDto
import com.sangita.app.domain.model.Playlist
import com.sangita.app.ui.components.MiniPlayer
import com.sangita.app.ui.components.SongActionsSheet
import com.sangita.app.ui.components.rememberCoverModel
import com.sangita.app.ui.components.PlayingBars
import com.sangita.app.ui.screens.playlist.formatDurationNatural
import com.sangita.app.ui.theme.SangitaColors
import com.sangita.app.ui.theme.SangitaThemeColors
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.absoluteValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val artistDominantColorCache = ConcurrentHashMap<String, Color>()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    artistId: String,
    viewModel: ArtistDetailViewModel,
    onBackClick: () -> Unit,
    onOpenFullPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val savedArtists by viewModel.savedArtists.collectAsState()
    val savedTracks by viewModel.savedTracks.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val downloadedTracks by viewModel.downloadedTracks.collectAsState()
    val downloadedIds = remember(downloadedTracks) { downloadedTracks.map { it.id }.toHashSet() }
    val favoriteIds = remember(favorites) { favorites.map { it.id }.toHashSet() }
    val savedIds = remember(savedTracks) { savedTracks.map { it.id }.toHashSet() }
    val colors = SangitaThemeColors.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val currentPlayingTrackId by remember(playbackState) {
        derivedStateOf { playbackState.currentTrack?.id }
    }

    var trackForAddToPlaylist by remember { mutableStateOf<ArtistTrackDto?>(null) }
    var showAddAllDialog by remember { mutableStateOf(false) }
    var isBioExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedbackMessage()
        }
    }

    val artist = uiState.artist
    val tracks = artist?.tracks ?: emptyList()
    val totalDurationSeconds = remember(tracks) { tracks.sumOf { it.duration ?: 0.0 } }
    val isLight = colors.background.luminance() > 0.5f
    val isAmoled = colors.isAmoled
    val context = LocalContext.current

    val firstTrackPath = remember(tracks) { tracks.firstOrNull()?.path }
    val localCover = rememberCoverModel(firstTrackPath, null, viewModel.serverUrl, viewModel.token)

    val resolvedImageUrl = remember(artist?.imageUrl, viewModel.serverUrl, viewModel.token, firstTrackPath) {
        val raw = when {
            !artist?.imageUrl.isNullOrBlank() -> artist?.imageUrl
            !firstTrackPath.isNullOrBlank() -> "/api/cover/${android.net.Uri.encode(firstTrackPath, "/")}"
            else -> null
        }
        if (raw.isNullOrBlank()) null
        else {
            val fullUrl = if (raw.startsWith("http://") || raw.startsWith("https://")) {
                raw
            } else {
                "${viewModel.serverUrl.trimEnd('/')}/${raw.trimStart('/')}"
            }
            if (fullUrl.contains("/api/cover/") && !fullUrl.contains("token=")) {
                val separator = if (fullUrl.contains("?")) "&" else "?"
                val tokenPart = if (!viewModel.token.isNullOrBlank()) {
                    "token=${android.net.Uri.encode(viewModel.token)}&v=2"
                } else "v=2"
                "$fullUrl$separator$tokenPart"
            } else {
                fullUrl
            }
        }
    }

    val finalCoverModel: Any? = when {
        localCover is java.io.File && localCover.exists() -> localCover
        !resolvedImageUrl.isNullOrBlank() -> resolvedImageUrl
        else -> localCover
    }

    var dominantColor by remember(artist?.id ?: artistId) {
        val key = artist?.id ?: artistId
        val cached = artistDominantColorCache[key]
        if (cached != null) {
            mutableStateOf(cached)
        } else {
            val hue = (key.hashCode().absoluteValue % 360).toFloat()
            mutableStateOf(Color.hsl(hue, 0.45f, 0.20f))
        }
    }

    LaunchedEffect(finalCoverModel) {
        val model = finalCoverModel ?: return@LaunchedEffect
        val key = artist?.id ?: artistId
        if (artistDominantColorCache.containsKey(key)) {
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(240)
        withContext(Dispatchers.IO) {
            try {
                val imageLoader = coil.Coil.imageLoader(context)
                val req = ImageRequest.Builder(context)
                    .data(model)
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
                                val resolved = Color(colorInt)
                                dominantColor = resolved
                                artistDominantColorCache[key] = resolved
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    val gradientColors = if (isAmoled) {
        listOf(
            colors.background,
            colors.background
        )
    } else if (isLight) {
        listOf(
            dominantColor.copy(alpha = 0.25f),
            dominantColor.copy(alpha = 0.10f),
            dominantColor.copy(alpha = 0.03f),
            colors.background,
            colors.background
        )
    } else {
        listOf(
            dominantColor.copy(alpha = 0.55f),
            dominantColor.copy(alpha = 0.25f),
            dominantColor.copy(alpha = 0.08f),
            colors.background,
            colors.background
        )
    }

    val isSaved = savedArtists.any { it.id == (artist?.id ?: artistId) }
    val onToggleSave: () -> Unit = {
        viewModel.toggleSavedArtist(isSaved) { ok ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    if (ok) {
                        if (isSaved) "Artist removed from library" else "Artist saved to library"
                    } else "Could not update library"
                )
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = 8.dp),
                title = { },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary
                        )
                    }
                },
                actions = {
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
                            contentDescription = if (isSaved) "Remove from Library" else "Save to Library",
                            tint = if (isSaved) colors.accent else colors.textPrimary
                        )
                    }
                    IconButton(
                        onClick = { showAddAllDialog = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = "Add to Playlist",
                            tint = colors.textPrimary
                        )
                    }
                    IconButton(
                        onClick = viewModel::refresh,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = colors.textSecondary
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
                    onOpenDevices = {},
                    onClick = onOpenFullPlayer
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isAmoled) {
                        Modifier.background(colors.background)
                    } else {
                        Modifier.background(Brush.verticalGradient(gradientColors))
                    }
                )
                .padding(innerPadding)
        ) {
            when {
                // Loading State
                uiState.isLoading && artist == null -> {
                    ArtistDetailLoadingState(colors = colors)
                }

                // Error State
                uiState.errorMessage != null && artist == null -> {
                    ArtistDetailErrorState(
                        errorMessage = uiState.errorMessage ?: "Failed to load artist",
                        onRetry = viewModel::refresh,
                        colors = colors
                    )
                }

                // Main Content
                artist != null -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 36.dp)
                    ) {
                        // Header: Artwork, Name, Bio, Track Count, Actions
                        item(key = "artist_header") {
                            ArtistHeaderSection(
                                artist = artist,
                                coverModel = finalCoverModel,
                                totalDurationSeconds = totalDurationSeconds,
                                isBioExpanded = isBioExpanded,
                                isSaved = isSaved,
                                onToggleBio = { isBioExpanded = !isBioExpanded },
                                onToggleSave = onToggleSave,
                                onPlayAll = viewModel::playAll,
                                onShuffle = viewModel::shuffle,
                                colors = colors,
                                token = viewModel.token
                            )
                        }

                        // Tracks Divider
                        if (tracks.isNotEmpty()) {
                            item(key = "tracks_divider") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    HorizontalDivider(
                                        color = colors.border,
                                        thickness = 1.dp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Tracks",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary,
                                        fontSize = 17.sp,
                                        modifier = Modifier.semantics { heading() }
                                    )
                                }
                            }

                            // Track Rows
                            itemsIndexed(
                                items = tracks,
                                key = { _, track -> track.path }
                            ) { index, track ->
                                val isDownloaded = downloadedIds.contains(track.path)
                                val isFavorite = favoriteIds.contains(track.path)
                                val isSaved = savedIds.contains(track.path)
                                ArtistTrackItemRow(
                                    track = track,
                                    serverUrl = viewModel.serverUrl,
                                    token = viewModel.token,
                                    artistFallback = artist.name,
                                    index = index + 1,
                                    isPlayingThisTrack = currentPlayingTrackId == track.path,
                                    isDownloaded = isDownloaded,
                                    isFavorite = isFavorite,
                                    isSaved = isSaved,
                                    onClick = { viewModel.playTrack(track) },
                                    onDownloadClick = {
                                        viewModel.downloadTrack(track)
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Download started for ${track.title}")
                                        }
                                    },
                                    onRemoveDownloadClick = {
                                        viewModel.deleteDownloadedTrack(track)
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Download removed for ${track.title}")
                                        }
                                    },
                                    onToggleFavorite = {
                                        viewModel.toggleFavorite(track) { ok ->
                                            scope.launch {
                                                snackbarHostState.showSnackbar(if (ok) "Favorites updated" else "Could not update favorites")
                                            }
                                        }
                                    },
                                    onAddToPlaylistClick = { trackForAddToPlaylist = track },
                                    onToggleSaved = {
                                        viewModel.toggleSavedTrack(track.path, isSaved) { ok ->
                                            scope.launch {
                                                snackbarHostState.showSnackbar(
                                                    if (ok) {
                                                        if (isSaved) "Song removed from library" else "Song saved to library"
                                                    } else "Could not update library"
                                                )
                                            }
                                        }
                                    },
                                    colors = colors,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        } else {
                            item(key = "empty_tracks") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No tracks available for this artist.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.textMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add to Playlist Dialog
    trackForAddToPlaylist?.let { targetTrack ->
        val playlists by viewModel.allPlaylists.collectAsState()
        val personalPlaylists = remember(playlists) {
            playlists.filter { !it.isFavorites && it.isPersonal }
        }

        AddToPlaylistDialog(
            track = targetTrack,
            playlists = personalPlaylists,
            onDismiss = { trackForAddToPlaylist = null },
            onSelectPlaylist = { selectedPlaylist ->
                trackForAddToPlaylist = null
                viewModel.addTrackToPlaylist(selectedPlaylist.id, targetTrack) { success ->
                    scope.launch {
                        if (success) {
                            snackbarHostState.showSnackbar("Added to ${selectedPlaylist.title}")
                        } else {
                            snackbarHostState.showSnackbar("Failed to add to playlist")
                        }
                    }
                }
            },
            colors = colors
        )
    }

    // Add Complete Artist Collection to Playlist Dialog
    if (showAddAllDialog && artist != null) {
        val playlists by viewModel.allPlaylists.collectAsState()
        val personalPlaylists = remember(playlists) {
            playlists.filter { !it.isFavorites && it.isPersonal }
        }

        AddArtistCollectionDialog(
            artistName = artist.name,
            trackCount = if (artist.trackCount > 0) artist.trackCount else tracks.size,
            playlists = personalPlaylists,
            onDismiss = { showAddAllDialog = false },
            onSelectExistingPlaylist = { selectedPlaylist: Playlist ->
                showAddAllDialog = false
                viewModel.addArtistCollectionToPlaylist(
                    targetPlaylistId = selectedPlaylist.serverId ?: selectedPlaylist.id,
                    newPlaylistTitle = null
                ) { result ->
                    if (result != null) {
                        scope.launch {
                            snackbarHostState.showSnackbar(result.feedbackMessage)
                        }
                    }
                }
            },
            onCreateNewPlaylist = { title: String ->
                showAddAllDialog = false
                viewModel.addArtistCollectionToPlaylist(
                    targetPlaylistId = null,
                    newPlaylistTitle = title
                ) { result ->
                    if (result != null) {
                        scope.launch {
                            snackbarHostState.showSnackbar(result.feedbackMessage)
                        }
                    }
                }
            },
            colors = colors
        )
    }
}

@Composable
private fun ArtistHeaderSection(
    artist: ArtistDetailDto,
    coverModel: Any?,
    totalDurationSeconds: Double,
    isBioExpanded: Boolean,
    isSaved: Boolean,
    onToggleBio: () -> Unit,
    onToggleSave: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    colors: SangitaColors,
    token: String?
) {
    val context = LocalContext.current
    var isImageError by remember(coverModel) { mutableStateOf(false) }

    val imageRequest = remember(coverModel, token) {
        coverModel?.let { model ->
            ImageRequest.Builder(context)
                .data(model)
                .crossfade(true)
                .apply {
                    if (!token.isNullOrBlank() && model is String && (model.startsWith("http://") || model.startsWith("https://"))) {
                        addHeader("Authorization", "Bearer $token")
                    }
                }
                .size(400, 400)
                .build()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        // Artist Circular Artwork
        Box(
            modifier = Modifier
                .size(190.dp)
                .shadow(if (colors.isAmoled) 0.dp else 16.dp, CircleShape)
                .clip(CircleShape)
                .background(colors.card)
                .border(2.dp, colors.border.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (imageRequest != null && !isImageError) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = artist.name,
                    contentScale = ContentScale.Crop,
                    onError = { isImageError = true },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(72.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Badge/Tag: "Artist" label row (like "Public Playlist" in PlaylistDetailScreen.kt)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = "Artist",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
                letterSpacing = 0.5.sp
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Artist Name
        Text(
            text = artist.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Track Count & Duration Stats Row
        val trackCount = if (artist.trackCount > 0) artist.trackCount else artist.tracks.size
        val trackCountText = if (trackCount == 1) "1 track" else "$trackCount tracks"
        val statsText = if (totalDurationSeconds > 0) {
            "$trackCountText • ${formatDurationNatural(totalDurationSeconds)}"
        } else {
            trackCountText
        }
        Text(
            text = statsText,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted
        )

        // Bio (if present)
        if (artist.bio.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = artist.bio,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggleBio),
                maxLines = if (isBioExpanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )
            if (artist.bio.length > 120) {
                Text(
                    text = if (isBioExpanded) "Show less" else "Read more",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.accent,
                    modifier = Modifier
                        .clickable(onClick = onToggleBio)
                        .padding(top = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Action Buttons Row (matching PlaylistDetailScreen.kt lines 474-555)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // "Play" Button
            Button(
                onClick = onPlayAll,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.buttonContent
                ),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Play",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // "Shuffle" Button
            OutlinedButton(
                onClick = onShuffle,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = colors.textPrimary
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Shuffle",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // "Save" / "Saved" Bookmark Button
            OutlinedButton(
                onClick = onToggleSave,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isSaved) colors.accent.copy(alpha = 0.15f) else colors.card,
                    contentColor = if (isSaved) colors.accent else colors.textPrimary
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isSaved) colors.accent else colors.border
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(48.dp)
                    .semantics {
                        stateDescription = if (isSaved) "Saved to library" else "Not in library"
                        role = Role.Checkbox
                    }
            ) {
                Icon(
                    imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = if (isSaved) colors.accent else colors.textPrimary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isSaved) "Saved" else "Save",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ArtistTrackItemRow(
    track: ArtistTrackDto,
    serverUrl: String,
    token: String?,
    artistFallback: String,
    index: Int,
    isPlayingThisTrack: Boolean,
    isDownloaded: Boolean,
    isFavorite: Boolean,
    isSaved: Boolean,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onRemoveDownloadClick: () -> Unit = {},
    onToggleFavorite: () -> Unit,
    onToggleSaved: () -> Unit,
    onAddToPlaylistClick: () -> Unit,
    colors: SangitaColors,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val trackTitle = track.title.ifBlank { track.path.substringAfterLast('/').substringBeforeLast('.') }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isPlayingThisTrack) colors.elevated.copy(alpha = 0.5f) else Color.Transparent)
            .clickable(
                role = Role.Button,
                onClickLabel = "Play $trackTitle",
                onClick = onClick
            )
            .semantics {
                if (isPlayingThisTrack) {
                    stateDescription = "Currently playing"
                }
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track Index / Visualizer
        Box(
            modifier = Modifier.width(28.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isPlayingThisTrack) {
                PlayingBars(modifier = Modifier.size(width = 16.dp, height = 14.dp))
            } else {
                Text(
                    text = "$index",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Title & Artist
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title.ifBlank { track.path.substringAfterLast('/').substringBeforeLast('.') },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isPlayingThisTrack) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isPlayingThisTrack) colors.accent else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.artist.ifBlank { artistFallback },
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Duration
        val durationFormatted = formatTrackDuration(track.duration)
        Text(
            text = durationFormatted,
            style = MaterialTheme.typography.bodySmall,
            color = if (isPlayingThisTrack) colors.accent else colors.textMuted,
            fontSize = 13.sp
        )

        // Overflow Menu (Touch target >= 48dp)
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options for $trackTitle",
                    tint = colors.textMuted,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (showMenu) {
            val artwork = rememberCoverModel(track.path, null, serverUrl, token, isDownloaded)
            SongActionsSheet(
                title = trackTitle,
                subtitle = listOfNotNull(track.artist?.takeIf { it.isNotBlank() } ?: artistFallback, formatTrackDuration(track.duration).takeIf { it != "—" }).joinToString(" • "),
                artwork = artwork,
                onDismiss = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (isFavorite) colors.accent else colors.textPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isFavorite) "Remove from favorites" else "Add to favorites")
                        }
                    },
                    onClick = {
                        showMenu = false
                        onToggleFavorite()
                    }
                )
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isDownloaded) Icons.Default.Delete else Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (isDownloaded) colors.accent else colors.textPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isDownloaded) "Remove download" else "Download track")
                        }
                    },
                    onClick = {
                        showMenu = false
                        if (isDownloaded) {
                            onRemoveDownloadClick()
                        } else {
                            onDownloadClick()
                        }
                    }
                )
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (isSaved) colors.accent else colors.textPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isSaved) "Remove from library" else "Save to library")
                        }
                    },
                    onClick = {
                        showMenu = false
                        onToggleSaved()
                    }
                )
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add to Playlist")
                        }
                    },
                    onClick = {
                        showMenu = false
                        onAddToPlaylistClick()
                    }
                )
            }
            }
        }
    }
}

@Composable
private fun AddToPlaylistDialog(
    track: ArtistTrackDto,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onSelectPlaylist: (Playlist) -> Unit,
    colors: SangitaColors
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = colors.card),
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Add to Playlist",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        fontSize = 18.sp
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = colors.textMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (playlists.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No personal playlists found.\nCreate one in Library first.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((playlists.size * 54).coerceAtMost(250).dp)
                    ) {
                        items(playlists.size) { idx ->
                            val playlist = playlists[idx]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onSelectPlaylist(playlist) }
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(20.dp)
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
                                    Text(
                                        text = "${playlist.trackCount} tracks",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddArtistCollectionDialog(
    artistName: String,
    trackCount: Int,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onSelectExistingPlaylist: (Playlist) -> Unit,
    onCreateNewPlaylist: (String) -> Unit,
    colors: SangitaColors
) {
    var mode by remember { mutableStateOf("existing") } // "existing" or "new"
    var newTitle by remember { mutableStateOf("Best of $artistName") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = colors.card),
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Add Complete Collection",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        fontSize = 18.sp
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = colors.textMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$trackCount tracks by $artistName",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Mode Selector: Existing vs New Playlist
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.elevated)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (mode == "existing") colors.accent else Color.Transparent)
                            .clickable { mode = "existing" }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Existing Playlist",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (mode == "existing") FontWeight.Bold else FontWeight.Normal,
                            color = if (mode == "existing") Color.White else colors.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (mode == "new") colors.accent else Color.Transparent)
                            .clickable { mode = "new" }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "New Playlist",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (mode == "new") FontWeight.Bold else FontWeight.Normal,
                            color = if (mode == "new") Color.White else colors.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (mode == "existing") {
                    if (playlists.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No personal playlists found.\nCreate a new playlist below.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((playlists.size * 54).coerceAtMost(220).dp)
                        ) {
                            items(playlists.size) { idx ->
                                val playlist = playlists[idx]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onSelectExistingPlaylist(playlist) }
                                        .padding(horizontal = 8.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                        contentDescription = null,
                                        tint = colors.accent,
                                        modifier = Modifier.size(20.dp)
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
                                        Text(
                                            text = "${playlist.trackCount} tracks",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.textSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("Playlist Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedLabelColor = colors.accent,
                            unfocusedLabelColor = colors.textSecondary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (newTitle.isNotBlank()) {
                                onCreateNewPlaylist(newTitle.trim())
                            }
                        },
                        enabled = newTitle.isNotBlank(),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accent,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = "Create and Add Tracks",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistDetailLoadingState(colors: SangitaColors) {
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
            text = "Loading artist...",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary
        )
    }
}

@Composable
private fun ArtistDetailErrorState(
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
            text = "Couldn't load artist",
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
                contentColor = Color.White
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

fun formatTrackDuration(seconds: Double?): String {
    if (seconds == null || seconds <= 0.0) return "—"
    val totalSec = seconds.toLong()
    val minutes = totalSec / 60
    val secs = totalSec % 60
    return String.format("%d:%02d", minutes, secs)
}

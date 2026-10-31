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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sangita.app.data.local.storage.LocalStorageManager
import com.sangita.app.domain.model.DownloadStatus
import com.sangita.app.domain.model.Track
import com.sangita.app.ui.components.DeviceSyncBottomSheet
import com.sangita.app.ui.components.MiniPlayer
import com.sangita.app.ui.components.TrackItemRow
import com.sangita.app.ui.components.rememberCoverModel
import com.sangita.app.ui.dialogs.AddToPlaylistSheet
import com.sangita.app.ui.dialogs.CreatePlaylistDialog
import com.sangita.app.ui.dialogs.EditPlaylistDialog
import com.sangita.app.ui.theme.FavoritePink
import com.sangita.app.ui.theme.SangitaEmerald
import com.sangita.app.ui.theme.SangitaThemeColors
import kotlin.math.absoluteValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    viewModel: PlaylistViewModel,
    onBackClick: () -> Unit,
    onOpenFullPlayer: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val savedTracks by viewModel.savedTracks.collectAsState()
    val playlist = uiState.playlist
    val tracks = uiState.tracks
    val isOwner = uiState.isOwner
    val unavailableCount = uiState.unavailableCount
    val userMessage = uiState.userMessage

    val playbackState = viewModel.playbackState.collectAsState()
    val devices by viewModel.devices.collectAsState()
    var showDevicesSheet by remember { mutableStateOf(false) }

    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCloneConfirm by remember { mutableStateOf(false) }
    var isReorderMode by remember { mutableStateOf(false) }
    var selectedTrackForMenu by remember { mutableStateOf<Track?>(null) }
    var showAddToPlaylistSheet by remember { mutableStateOf(false) }
    var showCreateDialogFromSheet by remember { mutableStateOf(false) }

    val username = viewModel.userPreferences.getUsername() ?: "User"
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(userMessage) {
        if (userMessage != null) {
            snackbarHostState.showSnackbar(userMessage)
            viewModel.clearUserMessage()
        }
    }

    val currentPlayingTrackId by remember(playbackState) {
        derivedStateOf { playbackState.value.currentTrack?.id }
    }

    val totalDurationSeconds = remember(tracks) { tracks.sumOf { it.durationSeconds ?: 0.0 } }
    val themeColors = SangitaThemeColors.current
    val isLight = themeColors.background.luminance() > 0.5f

    val context = LocalContext.current
    val representativeTrack = remember(tracks) {
        tracks.firstOrNull() ?: tracks.getOrNull(1)
    }

    var dominantColor by remember(viewModel.playlistName) {
        val hue = (viewModel.playlistName.hashCode().absoluteValue % 360).toFloat()
        mutableStateOf(Color.hsl(hue, 0.45f, 0.20f))
    }

    LaunchedEffect(representativeTrack?.id) {
        val track = representativeTrack ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val localCover = LocalStorageManager.getOrExtractCoverFile(context, track.id, track.localFilePath)
                val coverModel: Any = localCover ?: "${viewModel.serverUrl}/api/cover/${android.net.Uri.encode(track.id, "/")}"
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

    val isAmoled = themeColors.isAmoled

    val gradientColors = if (isAmoled) {
        listOf(
            themeColors.background,
            themeColors.background
        )
    } else if (isLight) {
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

    val displayTitle = playlist?.title ?: viewModel.playlistName
    val displayOwner = playlist?.owner ?: username
    val hasUnavailableTracks = unavailableCount > 0 || tracks.any { !it.isAvailable }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                actions = {
                    if (isOwner) {
                        IconButton(
                            onClick = { isReorderMode = !isReorderMode },
                            modifier = Modifier
                                .size(48.dp)
                                .semantics {
                                    stateDescription = if (isReorderMode) "Reordering active" else "Reordering inactive"
                                    role = Role.Checkbox
                                }
                        ) {
                            Icon(
                                imageVector = if (isReorderMode) Icons.Default.Check else Icons.Default.SwapVert,
                                contentDescription = if (isReorderMode) "Done Reordering" else "Edit Order",
                                tint = if (isReorderMode) themeColors.accent else themeColors.textPrimary
                            )
                        }
                        IconButton(onClick = { showEditDialog = true }, modifier = Modifier.size(48.dp)) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Playlist",
                                tint = themeColors.textPrimary
                            )
                        }
                        IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(48.dp)) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Playlist",
                                tint = themeColors.textPrimary
                            )
                        }
                    } else {
                        val isSaved = uiState.isSaved
                        IconButton(
                            onClick = {
                                if (isSaved) viewModel.removeFromLibrary() else viewModel.saveToLibrary()
                            },
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
                                tint = if (isSaved) themeColors.accent else themeColors.textPrimary
                            )
                        }
                        IconButton(onClick = { showCloneConfirm = true }, modifier = Modifier.size(48.dp)) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Save to My Playlists",
                                tint = themeColors.textPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        bottomBar = {
            val miniPlaybackState by viewModel.playbackState.collectAsState()
            if (miniPlaybackState.currentTrack != null) {
                MiniPlayer(
                    playbackState = miniPlaybackState,
                    serverUrl = viewModel.serverUrl,
                    token = viewModel.token,
                    onTogglePlayPause = viewModel::togglePlayPause,
                    onSkipPrevious = viewModel::skipPrevious,
                    onSkipNext = viewModel::skipNext,
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
                .then(
                    if (isAmoled) {
                        Modifier.background(themeColors.background)
                    } else {
                        Modifier.background(Brush.verticalGradient(gradientColors))
                    }
                )
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 36.dp)
            ) {
                // HEADER SECTION
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(6.dp))

                        // Cover Collage or AsyncImage
                        PlaylistCollageCover(
                            tracks = tracks,
                            serverUrl = viewModel.serverUrl,
                            token = viewModel.token,
                            modifier = Modifier.size(200.dp)
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Unobtrusive metadata text for Public Playlist
                        if (playlist?.isPublic == true) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = null,
                                    tint = themeColors.textMuted,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Public Playlist",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = themeColors.textMuted,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        // Title
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = themeColors.textPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.semantics { heading() }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Owner name: "Created by ..."
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
                                    text = "${displayOwner.firstOrNull()?.uppercaseChar() ?: 'U'}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Created by $displayOwner",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = themeColors.textPrimary
                            )
                        }

                        // Description if present (filter out generated folder text)
                        val cleanDescription = playlist?.description?.takeIf {
                            !it.trim().startsWith("Folder playlist for", ignoreCase = true)
                        }
                        if (!cleanDescription.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = cleanDescription,
                                style = MaterialTheme.typography.bodySmall,
                                color = themeColors.textSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp),
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Total track count & duration
                        Text(
                            text = "${tracks.size} tracks • ${formatDurationNatural(totalDurationSeconds)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = themeColors.textMuted
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // UNAVAILABLE WARNING BANNER
                        if (hasUnavailableTracks) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFFEF3C7).copy(alpha = if (isLight) 0.90f else 0.16f))
                                    .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Warning",
                                        tint = Color(0xFFF59E0B),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Some tracks are unavailable or restricted.",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isLight) Color(0xFF92400E) else Color(0xFFFDE68A)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // ACTION BUTTONS ROW
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Play Button (plays available tracks)
                            Button(
                                onClick = { viewModel.playAll() },
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = themeColors.accent,
                                    contentColor = themeColors.buttonContent
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

                            // Shuffle Button (shuffles available tracks)
                            OutlinedButton(
                                onClick = { viewModel.shufflePlay() },
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = themeColors.textPrimary
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, themeColors.border),
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
                                    color = themeColors.textPrimary
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            // If Owner or Admin: Edit Order, Edit & Delete buttons
                            if (isOwner) {
                                OutlinedButton(
                                    onClick = { isReorderMode = !isReorderMode },
                                    shape = RoundedCornerShape(24.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (isReorderMode) themeColors.accent.copy(alpha = 0.2f) else themeColors.card,
                                        contentColor = if (isReorderMode) themeColors.accent else themeColors.textPrimary
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isReorderMode) themeColors.accent else themeColors.border
                                    ),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .semantics {
                                            stateDescription = if (isReorderMode) "Reordering active" else "Reordering inactive"
                                            role = Role.Checkbox
                                        }
                                ) {
                                    Icon(
                                        imageVector = if (isReorderMode) Icons.Default.Check else Icons.Default.SwapVert,
                                        contentDescription = if (isReorderMode) "Done Reordering" else "Edit Order",
                                        tint = if (isReorderMode) themeColors.accent else themeColors.textPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isReorderMode) "Done" else "Edit Order",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isReorderMode) themeColors.accent else themeColors.textPrimary
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                IconButton(
                                    onClick = { showEditDialog = true },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Playlist",
                                        tint = themeColors.textPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                IconButton(
                                    onClick = { showDeleteConfirm = true },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Playlist",
                                        tint = themeColors.textPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else {
                                // If Not Owner: Save to Library / Remove from Library button
                                val isSaved = uiState.isSaved
                                OutlinedButton(
                                    onClick = {
                                        if (isSaved) {
                                            viewModel.removeFromLibrary()
                                        } else {
                                            viewModel.saveToLibrary()
                                        }
                                    },
                                    shape = RoundedCornerShape(24.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (isSaved) themeColors.accent.copy(alpha = 0.15f) else themeColors.card,
                                        contentColor = if (isSaved) themeColors.accent else themeColors.textPrimary
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSaved) themeColors.accent else themeColors.border
                                    ),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .semantics {
                                            stateDescription = if (isSaved) "In Library" else "Not in Library"
                                            role = Role.Checkbox
                                        }
                                ) {
                                    Icon(
                                        imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                        contentDescription = if (isSaved) "Remove from Library" else "Save to Library",
                                        tint = if (isSaved) themeColors.accent else themeColors.textPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isSaved) "In Library" else "Save to Library",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSaved) themeColors.accent else themeColors.textPrimary
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                IconButton(
                                    onClick = { showCloneConfirm = true },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Clone Playlist",
                                        tint = themeColors.textPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Download Playlist Button
                            IconButton(
                                onClick = { viewModel.downloadAll() },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Download Playlist",
                                    tint = themeColors.textPrimary,
                                    modifier = Modifier.size(18.dp)
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
                    var trackMenuExpanded by remember { mutableStateOf(false) }

                    if (isReorderMode && isOwner) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Move Up / Move Down controls
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                IconButton(
                                    onClick = {
                                        if (index > 0) {
                                            val mutable = tracks.toMutableList()
                                            java.util.Collections.swap(mutable, index, index - 1)
                                            viewModel.reorderTracks(mutable)
                                        }
                                    },
                                    enabled = index > 0,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Move ${track.title} up",
                                        tint = if (index > 0) themeColors.accent else themeColors.textMuted.copy(alpha = 0.3f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (index < tracks.size - 1) {
                                            val mutable = tracks.toMutableList()
                                            java.util.Collections.swap(mutable, index, index + 1)
                                            viewModel.reorderTracks(mutable)
                                        }
                                    },
                                    enabled = index < tracks.size - 1,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Move ${track.title} down",
                                        tint = if (index < tracks.size - 1) themeColors.accent else themeColors.textMuted.copy(alpha = 0.3f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Box(modifier = Modifier.weight(1f)) {
                                TrackItemRow(
                                    track = track,
                                    serverUrl = viewModel.serverUrl,
                                    token = viewModel.token,
                                    index = index + 1,
                                    isPlayingThisTrack = currentPlayingTrackId == track.id,
                                    onClick = { viewModel.playTrack(track) },
                                    onToggleFavorite = { viewModel.toggleFavorite(track) },
                                    onDownloadClick = { viewModel.downloadTrack(track) },
                                    onDeleteDownload = { viewModel.deleteDownloadedTrack(track) },
                                    onMenuClick = { trackMenuExpanded = true }
                                )

                                DropdownMenu(
                                    expanded = trackMenuExpanded,
                                    onDismissRequest = { trackMenuExpanded = false },
                                    modifier = Modifier.background(themeColors.card)
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (track.isFavorite) "Remove from favorites" else "Add to favorites",
                                                color = themeColors.textPrimary
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                contentDescription = null,
                                                tint = if (track.isFavorite) FavoritePink else themeColors.textPrimary
                                            )
                                        },
                                        onClick = {
                                            trackMenuExpanded = false
                                            viewModel.toggleFavorite(track)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            val downloadText = when {
                                                track.downloadStatus == DownloadStatus.DOWNLOADING -> "Downloading..."
                                                track.isDownloaded -> "Remove download"
                                                else -> "Download track"
                                            }
                                            Text(downloadText, color = themeColors.textPrimary)
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (track.isDownloaded) Icons.Default.DownloadDone else Icons.Default.Download,
                                                contentDescription = null,
                                                tint = if (track.isDownloaded) themeColors.accent else themeColors.textPrimary
                                            )
                                        },
                                        enabled = track.downloadStatus != DownloadStatus.DOWNLOADING && (track.isDownloaded || track.isAvailable),
                                        onClick = {
                                            trackMenuExpanded = false
                                            if (track.isDownloaded) {
                                                viewModel.deleteDownloadedTrack(track)
                                            } else {
                                                viewModel.downloadTrack(track)
                                            }
                                        }
                                    )
                                    if (track.isAvailable) {
                                        val saved = savedTracks.any { it.id == track.id }
                                        DropdownMenuItem(
                                            text = { Text(if (saved) "Remove from library" else "Save to library", color = themeColors.textPrimary) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                                    contentDescription = null,
                                                    tint = themeColors.textPrimary
                                                )
                                            },
                                            onClick = {
                                                trackMenuExpanded = false
                                                viewModel.toggleSavedTrack(track, saved)
                                            }
                                        )
                                    }
                                    if (index > 0) {
                                        DropdownMenuItem(
                                            text = { Text("Move up", color = themeColors.textPrimary) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.KeyboardArrowUp,
                                                    contentDescription = null,
                                                    tint = themeColors.accent
                                                )
                                            },
                                            onClick = {
                                                trackMenuExpanded = false
                                                val mutable = tracks.toMutableList()
                                                java.util.Collections.swap(mutable, index, index - 1)
                                                viewModel.reorderTracks(mutable)
                                            }
                                        )
                                    }
                                    if (index < tracks.size - 1) {
                                        DropdownMenuItem(
                                            text = { Text("Move down", color = themeColors.textPrimary) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.KeyboardArrowDown,
                                                    contentDescription = null,
                                                    tint = themeColors.accent
                                                )
                                            },
                                            onClick = {
                                                trackMenuExpanded = false
                                                val mutable = tracks.toMutableList()
                                                java.util.Collections.swap(mutable, index, index + 1)
                                                viewModel.reorderTracks(mutable)
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Add to playlist", color = themeColors.textPrimary) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.MusicNote,
                                                contentDescription = null,
                                                tint = themeColors.textPrimary
                                            )
                                        },
                                        onClick = {
                                            trackMenuExpanded = false
                                            selectedTrackForMenu = track
                                            showAddToPlaylistSheet = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Remove from playlist", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        onClick = {
                                            trackMenuExpanded = false
                                            viewModel.removeTrack(track)
                                        }
                                    )
                                }
                            }

                            Icon(
                                imageVector = Icons.Default.DragHandle,
                                contentDescription = "Drag to reorder",
                                tint = themeColors.textMuted,
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .size(20.dp)
                            )
                        }
                    } else {
                        Box {
                            TrackItemRow(
                                track = track,
                                serverUrl = viewModel.serverUrl,
                                token = viewModel.token,
                                index = index + 1,
                                isPlayingThisTrack = currentPlayingTrackId == track.id,
                                onClick = { viewModel.playTrack(track) },
                                onToggleFavorite = { viewModel.toggleFavorite(track) },
                                onDownloadClick = { viewModel.downloadTrack(track) },
                                onDeleteDownload = { viewModel.deleteDownloadedTrack(track) },
                                onMenuClick = { trackMenuExpanded = true },
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )

                            // Track Context Dropdown Menu
                            DropdownMenu(
                                expanded = trackMenuExpanded,
                                onDismissRequest = { trackMenuExpanded = false },
                                modifier = Modifier.background(themeColors.card)
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (track.isFavorite) "Remove from favorites" else "Add to favorites",
                                            color = themeColors.textPrimary
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            contentDescription = null,
                                            tint = if (track.isFavorite) FavoritePink else themeColors.textPrimary
                                        )
                                    },
                                    onClick = {
                                        trackMenuExpanded = false
                                        viewModel.toggleFavorite(track)
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        val downloadText = when {
                                            track.downloadStatus == DownloadStatus.DOWNLOADING -> "Downloading..."
                                            track.isDownloaded -> "Remove download"
                                            else -> "Download track"
                                        }
                                        Text(downloadText, color = themeColors.textPrimary)
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (track.isDownloaded) Icons.Default.DownloadDone else Icons.Default.Download,
                                            contentDescription = null,
                                            tint = if (track.isDownloaded) themeColors.accent else themeColors.textPrimary
                                        )
                                    },
                                    enabled = track.downloadStatus != DownloadStatus.DOWNLOADING && (track.isDownloaded || track.isAvailable),
                                    onClick = {
                                        trackMenuExpanded = false
                                        if (track.isDownloaded) {
                                            viewModel.deleteDownloadedTrack(track)
                                        } else {
                                            viewModel.downloadTrack(track)
                                        }
                                    }
                                )
                                if (track.isAvailable) {
                                    val saved = savedTracks.any { it.id == track.id }
                                    DropdownMenuItem(
                                        text = { Text(if (saved) "Remove from library" else "Save to library", color = themeColors.textPrimary) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                                contentDescription = null,
                                                tint = themeColors.textPrimary
                                            )
                                        },
                                        onClick = {
                                            trackMenuExpanded = false
                                            viewModel.toggleSavedTrack(track, saved)
                                        }
                                    )
                                }
                                if (isOwner && index > 0) {
                                    DropdownMenuItem(
                                        text = { Text("Move up", color = themeColors.textPrimary) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowUp,
                                                contentDescription = null,
                                                tint = themeColors.accent
                                            )
                                        },
                                        onClick = {
                                            trackMenuExpanded = false
                                            val mutable = tracks.toMutableList()
                                            java.util.Collections.swap(mutable, index, index - 1)
                                            viewModel.reorderTracks(mutable)
                                        }
                                    )
                                }
                                if (isOwner && index < tracks.size - 1) {
                                    DropdownMenuItem(
                                        text = { Text("Move down", color = themeColors.textPrimary) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowDown,
                                                contentDescription = null,
                                                tint = themeColors.accent
                                            )
                                        },
                                        onClick = {
                                            trackMenuExpanded = false
                                            val mutable = tracks.toMutableList()
                                            java.util.Collections.swap(mutable, index, index + 1)
                                            viewModel.reorderTracks(mutable)
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Add to playlist", color = themeColors.textPrimary) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = themeColors.textPrimary
                                        )
                                    },
                                    onClick = {
                                        trackMenuExpanded = false
                                        selectedTrackForMenu = track
                                        showAddToPlaylistSheet = true
                                    }
                                )

                                if (isOwner) {
                                    DropdownMenuItem(
                                        text = { Text("Remove from playlist", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        onClick = {
                                            trackMenuExpanded = false
                                            viewModel.removeTrack(track)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit Playlist Dialog
    if (showEditDialog && playlist != null) {
        EditPlaylistDialog(
            initialTitle = playlist.title,
            initialDescription = playlist.description,
            initialVisibility = playlist.visibility,
            onDismiss = { showEditDialog = false },
            onSave = { title, description, visibility ->
                showEditDialog = false
                viewModel.updatePlaylist(title, description, visibility)
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    text = "Delete playlist?",
                    fontWeight = FontWeight.Bold,
                    color = themeColors.textPrimary
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete \"$displayTitle\"? This cannot be undone.",
                    color = themeColors.textSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deletePlaylist { success ->
                            if (success) onBackClick()
                        }
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = themeColors.textMuted)
                }
            },
            containerColor = themeColors.card,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Clone Confirmation Dialog
    if (showCloneConfirm) {
        AlertDialog(
            onDismissRequest = { showCloneConfirm = false },
            title = {
                Text(
                    text = "Save to My Playlists?",
                    fontWeight = FontWeight.Bold,
                    color = themeColors.textPrimary
                )
            },
            text = {
                Text(
                    text = "A personal copy of \"$displayTitle\" will be saved to your library.",
                    color = themeColors.textSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCloneConfirm = false
                        viewModel.clonePlaylist { cloned ->
                            if (cloned != null) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Saved \"${cloned.title}\" to your library")
                                }
                            }
                        }
                    }
                ) {
                    Text("Save", color = themeColors.accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloneConfirm = false }) {
                    Text("Cancel", color = themeColors.textMuted)
                }
            },
            containerColor = themeColors.card,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Add To Playlist Sheet
    if (showAddToPlaylistSheet) {
        val allPlaylists by viewModel.allPlaylists.collectAsState()
        AddToPlaylistSheet(
            track = selectedTrackForMenu,
            playlists = allPlaylists,
            onSelectPlaylist = { targetPlaylist ->
                selectedTrackForMenu?.let { tr ->
                    scope.launch {
                        viewModel.musicRepository.addTrackToPlaylist(targetPlaylist.id, tr)
                        snackbarHostState.showSnackbar("Added to ${targetPlaylist.title}")
                    }
                }
            },
            onCreateNewPlaylist = {
                showCreateDialogFromSheet = true
            },
            onDismiss = { showAddToPlaylistSheet = false }
        )
    }

    // Create New Playlist Dialog from AddToPlaylistSheet
    if (showCreateDialogFromSheet) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialogFromSheet = false },
            onCreate = { title, description, isPublic ->
                showCreateDialogFromSheet = false
                viewModel.createNewPlaylist(title, description, isPublic) { newPlaylist ->
                    selectedTrackForMenu?.let { tr ->
                        scope.launch {
                            viewModel.musicRepository.addTrackToPlaylist(newPlaylist.id, tr)
                            snackbarHostState.showSnackbar("Added to ${newPlaylist.title}")
                        }
                    }
                }
            }
        )
    }

    // Device Sync Bottom Sheet
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
            .shadow(if (SangitaThemeColors.current.isAmoled) 0.dp else 16.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(SangitaThemeColors.current.card)
    ) {
        if (firstFour.size >= 4) {
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
    val coverModel = rememberCoverModel(track.id, track.localFilePath, serverUrl, token, track.isDownloaded)
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

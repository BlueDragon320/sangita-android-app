package com.sangita.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sangita.app.domain.model.DownloadStatus
import com.sangita.app.domain.model.Track
import com.sangita.app.ui.theme.SangitaThemeColors

@Composable
fun TrackItemRow(
    track: Track,
    serverUrl: String = "",
    token: String? = null,
    index: Int? = null,
    isPlayingThisTrack: Boolean = false,
    playCount: Int? = null,
    onClick: () -> Unit = {},
    onDownloadClick: (() -> Unit)? = null,
    onDeleteDownload: (() -> Unit)? = null,
    onToggleFavorite: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onToggleSave: (() -> Unit)? = null,
    isSaved: Boolean = false,
    onMenuClick: (() -> Unit)? = null,
    extraMenuItems: (@Composable ColumnScope.(() -> Unit) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = SangitaThemeColors.current
    var showMenu by remember { mutableStateOf(false) }

    val coverModel = rememberCoverModel(track.id, track.localFilePath, serverUrl, token, track.isDownloaded)
    val imageRequest = remember(coverModel) {
        ImageRequest.Builder(context)
            .data(coverModel)
            .size(128, 128)
            .crossfade(false)
            .build()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .alpha(if (track.isAvailable) 1f else 0.5f)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isPlayingThisTrack) colors.elevated.copy(alpha = 0.5f) else Color.Transparent)
            .clickable(
                enabled = track.isAvailable,
                role = Role.Button,
                onClickLabel = "Play ${track.title}",
                onClick = onClick
            )
            .semantics {
                if (isPlayingThisTrack) {
                    stateDescription = "Currently playing"
                }
            }
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Slot: CAVA Visualizer (PlayingBars) when playing, or Rank Number
        Box(
            modifier = Modifier.width(24.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isPlayingThisTrack) {
                PlayingBars(modifier = Modifier.size(width = 16.dp, height = 14.dp))
            } else if (index != null) {
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

        Spacer(modifier = Modifier.width(8.dp))

        // Thumbnail
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.elevated),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title and Subtitle
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isPlayingThisTrack) colors.accent else colors.textPrimary,
                fontWeight = if (isPlayingThisTrack) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            if (!track.isAvailable) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.border)
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Unavailable",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textMuted
                        )
                    }
                    Text(
                        text = track.artist.ifBlank { track.playlist },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp
                    )
                }
            } else {
                Text(
                    text = track.artist.ifBlank { track.playlist },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Right side: Favorite + Download status + Duration timestamp + 48dp 3-dots Menu Button
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (track.isFavorite) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Favorite",
                    tint = colors.accent,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(15.dp)
                        .testTag("favorite_indicator")
                )
            }

            if (track.isDownloaded) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Downloaded offline",
                    tint = colors.accent,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(15.dp)
                        .testTag("download_indicator")
                )
            } else if (track.downloadStatus == DownloadStatus.DOWNLOADING) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = colors.accent
                )
            }

            // Visible duration timestamp
            val sec = track.durationSeconds ?: 0.0
            val durationFormatted = if (sec > 0.0) formatSeconds(sec) else "—"
            Text(
                text = durationFormatted,
                style = MaterialTheme.typography.bodySmall,
                color = if (isPlayingThisTrack) colors.accent else colors.textMuted,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            )

            Box {
                IconButton(
                    onClick = {
                        if (onMenuClick != null && onAddToPlaylist == null && onDeleteDownload == null) {
                            onMenuClick()
                        } else {
                            showMenu = true
                            onMenuClick?.invoke()
                        }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options for ${track.title}",
                        tint = colors.textMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (onMenuClick == null || onAddToPlaylist != null || onDeleteDownload != null) {
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(colors.card)
                    ) {
                        // a) Favorite toggle
                        val favText = if (track.isFavorite) "Remove from favorites" else "Add to favorites"
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = favText,
                                    color = colors.textPrimary,
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = favText,
                                    tint = if (track.isFavorite) colors.accent else colors.textPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                showMenu = false
                                onToggleFavorite?.invoke()
                            },
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .semantics(mergeDescendants = true) {}
                                .testTag("menu_item_favorite")
                        )

                        // b) Download / Remove download
                        val downloadText = when {
                            track.downloadStatus == DownloadStatus.DOWNLOADING -> "Downloading..."
                            track.isDownloaded -> if (onDeleteDownload != null) "Remove download" else "Downloaded"
                            else -> "Download song"
                        }
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = downloadText,
                                    color = colors.textPrimary,
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (track.isDownloaded) {
                                        if (onDeleteDownload != null) Icons.Default.Delete else Icons.Default.DownloadDone
                                    } else {
                                        Icons.Default.Download
                                    },
                                    contentDescription = downloadText,
                                    tint = if (track.isDownloaded) colors.accent else colors.textPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                showMenu = false
                                if (track.isDownloaded) {
                                    onDeleteDownload?.invoke()
                                } else {
                                    onDownloadClick?.invoke()
                                }
                            },
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .semantics(mergeDescendants = true) {}
                                .testTag("menu_item_download")
                        )

                        // c) Save to library / Remove from library (if onToggleSave != null)
                        if (onToggleSave != null) {
                            val saveText = if (isSaved) "Remove from library" else "Save to library"
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = saveText,
                                        color = colors.textPrimary,
                                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                        contentDescription = saveText,
                                        tint = if (isSaved) colors.accent else colors.textPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onToggleSave()
                                },
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    .semantics(mergeDescendants = true) {}
                                    .testTag("menu_item_save")
                            )
                        }

                        // d) Add to playlist (if onAddToPlaylist != null)
                        if (onAddToPlaylist != null) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Add to playlist",
                                        color = colors.textPrimary,
                                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                                        contentDescription = "Add to playlist",
                                        tint = colors.textPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onAddToPlaylist()
                                },
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    .semantics(mergeDescendants = true) {}
                                    .testTag("menu_item_add_to_playlist")
                            )
                        }

                        // e) Extra menu items
                        extraMenuItems?.invoke(this) { showMenu = false }
                    }
                }
            }
        }
    }
}

fun formatSeconds(seconds: Double): String {
    val totalSec = seconds.toLong()
    val minutes = totalSec / 60
    val secs = totalSec % 60
    return String.format("%d:%02d", minutes, secs)
}

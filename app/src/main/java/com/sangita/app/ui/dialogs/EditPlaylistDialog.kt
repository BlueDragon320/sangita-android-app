package com.sangita.app.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sangita.app.ui.theme.SangitaThemeColors

@Composable
fun EditPlaylistDialog(
    initialTitle: String,
    initialDescription: String?,
    initialVisibility: String,
    onDismiss: () -> Unit,
    onSave: (title: String, description: String?, visibility: String) -> Unit
) {
    val colors = SangitaThemeColors.current
    var title by remember { mutableStateOf(initialTitle) }
    var description by remember { mutableStateOf(initialDescription ?: "") }
    var isPublic by remember { mutableStateOf(initialVisibility.equals("public", ignoreCase = true)) }
    var titleError by remember { mutableStateOf<String?>(null) }

    fun submit() {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty()) {
            titleError = "Title is required"
            return
        }
        val trimmedDesc = description.trim().ifBlank { null }
        val visibility = if (isPublic) "public" else "private"
        onSave(trimmedTitle, trimmedDesc, visibility)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit Playlist",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = colors.textPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Title Field
                Column {
                    OutlinedTextField(
                        value = title,
                        onValueChange = {
                            title = it
                            if (titleError != null && it.isNotBlank()) {
                                titleError = null
                            }
                        },
                        label = { Text("Title *") },
                        isError = titleError != null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = colors.background,
                            unfocusedContainerColor = colors.background,
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedLabelColor = colors.accent,
                            unfocusedLabelColor = colors.textMuted
                        )
                    )
                    if (titleError != null) {
                        Text(
                            text = titleError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 6.dp, top = 4.dp)
                        )
                    }
                }

                // Description Field (Optional)
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    placeholder = { Text("Add notes or description...") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colors.background,
                        unfocusedContainerColor = colors.background,
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedLabelColor = colors.accent,
                        unfocusedLabelColor = colors.textMuted
                    )
                )

                // Visibility Selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Visibility",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textSecondary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Private
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (!isPublic) colors.accent.copy(alpha = 0.15f) else colors.background)
                                .border(
                                    1.dp,
                                    if (!isPublic) colors.accent else colors.border,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { isPublic = false }
                                .padding(vertical = 10.dp, horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Private",
                                    tint = if (!isPublic) colors.accent else colors.textMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Private",
                                    fontWeight = if (!isPublic) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = if (!isPublic) colors.accent else colors.textSecondary
                                )
                            }
                        }

                        // Public
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isPublic) colors.accent.copy(alpha = 0.15f) else colors.background)
                                .border(
                                    1.dp,
                                    if (isPublic) colors.accent else colors.border,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { isPublic = true }
                                .padding(vertical = 10.dp, horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = "Public",
                                    tint = if (isPublic) colors.accent else colors.textMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Public",
                                    fontWeight = if (isPublic) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = if (isPublic) colors.accent else colors.textSecondary
                                )
                            }
                        }
                    }

                    Text(
                        text = if (isPublic) {
                            "Public playlists can be discovered by other users."
                        } else {
                            "Only you can see and play this playlist."
                        },
                        fontSize = 12.sp,
                        color = colors.textMuted,
                        modifier = Modifier.padding(start = 2.dp, top = 2.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = ::submit,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.background
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = colors.textSecondary)
            ) {
                Text("Cancel")
            }
        },
        containerColor = colors.card,
        shape = RoundedCornerShape(16.dp)
    )
}

package com.sangita.app.sync

import com.sangita.app.data.local.entity.SyncQueueEntity
import com.sangita.app.data.remote.dto.ApiErrorResponseDto
import com.sangita.app.data.remote.dto.RecordDataUsageRequest
import com.sangita.app.data.remote.dto.SetFavoriteRequest
import com.sangita.app.data.remote.dto.StatsPingRequest
import com.sangita.app.domain.model.SyncActionType
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class CreatePlaylistPayload(
    @JsonNames("local_key") val localKey: String = "",
    @JsonNames("title", "name", "playlist", "playlist_name") val title: String = "",
    val description: String? = null,
    val visibility: String = "private",
    @JsonNames("idempotency_key", "idempotencyKey") val idempotencyKey: String = UUID.randomUUID().toString()
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class UpdatePlaylistPayload(
    @JsonNames("local_key") val localKey: String = "",
    @JsonNames("server_id") val serverId: String? = null,
    @JsonNames("name") val title: String? = null,
    val description: String? = null,
    val visibility: String? = null,
    @JsonNames("track_paths", "tracks") val trackPaths: List<String>? = null,
    val playlistName: String? = null
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class DeletePlaylistPayload(
    @JsonNames("local_key") val localKey: String = "",
    @JsonNames("server_id") val serverId: String? = null,
    @JsonNames("playlist_name", "name", "playlist", "title") val playlistName: String? = null
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class AddTrackPayload(
    @JsonNames("local_key") val localKey: String = "",
    @JsonNames("server_id") val serverId: String? = null,
    @JsonNames("track_path", "trackId", "track_id", "path") val trackPath: String = "",
    @JsonNames("playlist_name", "name", "playlist", "title") val playlistName: String? = null
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class RemoveTrackPayload(
    @JsonNames("local_key") val localKey: String = "",
    @JsonNames("server_id") val serverId: String? = null,
    @JsonNames("track_path", "trackId", "track_id", "path") val trackPath: String = "",
    @JsonNames("playlist_name", "name", "playlist", "title") val playlistName: String? = null
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ClonePlaylistPayload(
    @JsonNames("source_server_id") val sourceServerId: String = "",
    @JsonNames("new_local_key") val newLocalKey: String = "",
    @JsonNames("title", "name", "playlist", "playlist_name") val title: String? = null,
    val description: String? = null,
    val visibility: String = "private",
    @JsonNames("idempotency_key", "idempotencyKey") val idempotencyKey: String = UUID.randomUUID().toString()
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ToggleFavoritePayload(
    @JsonNames("track_id", "track_path", "trackId", "path") val trackId: String = "",
    @JsonNames("is_favorite", "isFavorite") val isFavorite: Boolean = true
)

object SyncPayloadSerialization {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        coerceInputValues = true
    }

    fun encodeCreatePlaylist(payload: CreatePlaylistPayload): String =
        json.encodeToString(CreatePlaylistPayload.serializer(), payload)

    fun decodeCreatePlaylist(jsonStr: String): CreatePlaylistPayload {
        val decoded = json.decodeFromString(CreatePlaylistPayload.serializer(), jsonStr)
        var result = decoded
        if (result.localKey.isBlank()) {
            result = result.copy(localKey = UUID.randomUUID().toString())
        }
        if (result.idempotencyKey.isBlank()) {
            result = result.copy(idempotencyKey = UUID.randomUUID().toString())
        }
        return result
    }

    fun encodeUpdatePlaylist(payload: UpdatePlaylistPayload): String =
        json.encodeToString(UpdatePlaylistPayload.serializer(), payload)

    fun decodeUpdatePlaylist(jsonStr: String): UpdatePlaylistPayload =
        json.decodeFromString(UpdatePlaylistPayload.serializer(), jsonStr)

    fun encodeDeletePlaylist(payload: DeletePlaylistPayload): String =
        json.encodeToString(DeletePlaylistPayload.serializer(), payload)

    fun decodeDeletePlaylist(jsonStr: String): DeletePlaylistPayload =
        json.decodeFromString(DeletePlaylistPayload.serializer(), jsonStr)

    fun encodeAddTrack(payload: AddTrackPayload): String =
        json.encodeToString(AddTrackPayload.serializer(), payload)

    fun decodeAddTrack(jsonStr: String): AddTrackPayload =
        json.decodeFromString(AddTrackPayload.serializer(), jsonStr)

    fun encodeRemoveTrack(payload: RemoveTrackPayload): String =
        json.encodeToString(RemoveTrackPayload.serializer(), payload)

    fun decodeRemoveTrack(jsonStr: String): RemoveTrackPayload =
        json.decodeFromString(RemoveTrackPayload.serializer(), jsonStr)

    fun encodeClonePlaylist(payload: ClonePlaylistPayload): String =
        json.encodeToString(ClonePlaylistPayload.serializer(), payload)

    fun decodeClonePlaylist(jsonStr: String): ClonePlaylistPayload {
        val decoded = json.decodeFromString(ClonePlaylistPayload.serializer(), jsonStr)
        return if (decoded.idempotencyKey.isBlank()) {
            decoded.copy(idempotencyKey = UUID.randomUUID().toString())
        } else {
            decoded
        }
    }

    fun encodeToggleFavorite(payload: ToggleFavoritePayload): String =
        json.encodeToString(ToggleFavoritePayload.serializer(), payload)

    fun decodeToggleFavorite(jsonStr: String): ToggleFavoritePayload =
        json.decodeFromString(ToggleFavoritePayload.serializer(), jsonStr)

    /**
     * Safely deserializes an action payload according to its [SyncActionType].
     * NEVER throws unhandled exceptions; if corrupt or invalid, returns null.
     */
    fun deserializePayload(actionType: SyncActionType, jsonStr: String): Any? {
        return try {
            when (actionType) {
                SyncActionType.CREATE_PLAYLIST -> decodeCreatePlaylist(jsonStr)
                SyncActionType.UPDATE_PLAYLIST -> decodeUpdatePlaylist(jsonStr)
                SyncActionType.DELETE_PLAYLIST -> decodeDeletePlaylist(jsonStr)
                SyncActionType.ADD_TRACK, SyncActionType.ADD_TRACK_TO_PLAYLIST -> decodeAddTrack(jsonStr)
                SyncActionType.REMOVE_TRACK, SyncActionType.REMOVE_TRACK_FROM_PLAYLIST -> decodeRemoveTrack(jsonStr)
                SyncActionType.CLONE_PLAYLIST -> decodeClonePlaylist(jsonStr)
                SyncActionType.TOGGLE_FAVORITE -> decodeToggleFavorite(jsonStr)
                SyncActionType.SET_FAVORITE -> try {
                    json.decodeFromString<SetFavoriteRequest>(jsonStr)
                } catch (_: Exception) {
                    decodeToggleFavorite(jsonStr)
                }
                SyncActionType.PLAY_EVENT -> json.decodeFromString<StatsPingRequest>(jsonStr)
                SyncActionType.DATA_CONSUMPTION -> json.decodeFromString<RecordDataUsageRequest>(jsonStr)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Determines whether an add track response or conflict error should be treated as success.
     * Code 200..299 is standard success.
     * Code 409 (Conflict) is treated as success because the track already exists in the playlist.
     * If the error body indicates "exists", it is also treated as success.
     */
    fun isAddTrackConflictSuccess(code: Int, errorBody: String? = null): Boolean {
        if (code in 200..299) return true
        if (code == 409) return true
        if (errorBody != null) {
            if (errorBody.contains("exists", ignoreCase = true)) return true
            try {
                val errDto = json.decodeFromString<ApiErrorResponseDto>(errorBody)
                if (errDto.status.equals("exists", ignoreCase = true) ||
                    errDto.error.contains("exists", ignoreCase = true)
                ) {
                    return true
                }
            } catch (_: Exception) {}
        }
        return false
    }

    /**
     * Updates serverId in a serialized action payload when matching the specified localKey or playlist title.
     * Returns the updated json string, or null if the action does not match or cannot be updated.
     */
    fun updateServerIdInPayload(
        actionType: SyncActionType,
        payloadJson: String,
        localKey: String,
        newServerId: String,
        playlistTitle: String? = null
    ): String? {
        return try {
            when (actionType) {
                SyncActionType.UPDATE_PLAYLIST -> {
                    val p = decodeUpdatePlaylist(payloadJson)
                    val matchesKey = p.localKey.isNotBlank() && p.localKey == localKey
                    val matchesTitle = playlistTitle != null && (p.title == playlistTitle || p.playlistName == playlistTitle)
                    if (matchesKey || (p.localKey.isBlank() && matchesTitle)) {
                        encodeUpdatePlaylist(p.copy(localKey = localKey, serverId = newServerId))
                    } else null
                }
                SyncActionType.DELETE_PLAYLIST -> {
                    val p = decodeDeletePlaylist(payloadJson)
                    val matchesKey = p.localKey.isNotBlank() && p.localKey == localKey
                    val matchesTitle = playlistTitle != null && p.playlistName == playlistTitle
                    if (matchesKey || (p.localKey.isBlank() && matchesTitle)) {
                        encodeDeletePlaylist(p.copy(localKey = localKey, serverId = newServerId))
                    } else null
                }
                SyncActionType.ADD_TRACK, SyncActionType.ADD_TRACK_TO_PLAYLIST -> {
                    val p = decodeAddTrack(payloadJson)
                    val matchesKey = p.localKey.isNotBlank() && p.localKey == localKey
                    val matchesTitle = playlistTitle != null && p.playlistName == playlistTitle
                    if (matchesKey || (p.localKey.isBlank() && matchesTitle)) {
                        encodeAddTrack(p.copy(localKey = localKey, serverId = newServerId))
                    } else null
                }
                SyncActionType.REMOVE_TRACK, SyncActionType.REMOVE_TRACK_FROM_PLAYLIST -> {
                    val p = decodeRemoveTrack(payloadJson)
                    val matchesKey = p.localKey.isNotBlank() && p.localKey == localKey
                    val matchesTitle = playlistTitle != null && p.playlistName == playlistTitle
                    if (matchesKey || (p.localKey.isBlank() && matchesTitle)) {
                        encodeRemoveTrack(p.copy(localKey = localKey, serverId = newServerId))
                    } else null
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Safely matches an action against a playlist by exact localKey or exact playlist title.
     * Decodes the payload JSON to prevent SQL LIKE wildcard or escape sequence mismatches.
     * If ambiguous (multiple default_account playlists share oldTitle, and action lacks localKey), returns null.
     * If matched, retargets payload title to newTitle, sets localKey, and returns updated action.
     */
    fun matchAndRetargetPlaylistAction(
        action: SyncQueueEntity,
        localKey: String,
        oldTitle: String,
        newTitle: String = oldTitle,
        isTitleAmbiguous: Boolean = false
    ): SyncQueueEntity? {
        return try {
            when (action.actionType) {
                SyncActionType.CREATE_PLAYLIST -> {
                    val p = try { decodeCreatePlaylist(action.payloadJson) } catch (_: Exception) { null } ?: return null
                    val matchesKey = p.localKey.isNotBlank() && p.localKey == localKey
                    val matchesTitle = p.title.isNotBlank() && p.title == oldTitle
                    if (!matchesKey && (!matchesTitle || isTitleAmbiguous)) return null
                    val updated = p.copy(
                        localKey = localKey,
                        title = newTitle
                    )
                    action.copy(payloadJson = encodeCreatePlaylist(updated))
                }
                SyncActionType.UPDATE_PLAYLIST -> {
                    val p = try { decodeUpdatePlaylist(action.payloadJson) } catch (_: Exception) { null } ?: return null
                    val matchesKey = p.localKey.isNotBlank() && p.localKey == localKey
                    val matchesTitle = (p.title != null && p.title == oldTitle) || (p.playlistName != null && p.playlistName == oldTitle)
                    if (!matchesKey && (!matchesTitle || isTitleAmbiguous)) return null
                    val updated = p.copy(
                        localKey = localKey,
                        title = if (p.title != null && p.title == oldTitle) newTitle else p.title,
                        playlistName = if (p.playlistName != null && p.playlistName == oldTitle) newTitle else p.playlistName
                    )
                    action.copy(payloadJson = encodeUpdatePlaylist(updated))
                }
                SyncActionType.DELETE_PLAYLIST -> {
                    val p = try { decodeDeletePlaylist(action.payloadJson) } catch (_: Exception) { null } ?: return null
                    val matchesKey = p.localKey.isNotBlank() && p.localKey == localKey
                    val matchesTitle = p.playlistName != null && p.playlistName == oldTitle
                    if (!matchesKey && (!matchesTitle || isTitleAmbiguous)) return null
                    val updated = p.copy(
                        localKey = localKey,
                        playlistName = if (p.playlistName != null && p.playlistName == oldTitle) newTitle else p.playlistName
                    )
                    action.copy(payloadJson = encodeDeletePlaylist(updated))
                }
                SyncActionType.ADD_TRACK -> {
                    val p = try { decodeAddTrack(action.payloadJson) } catch (_: Exception) { null } ?: return null
                    val matchesKey = p.localKey.isNotBlank() && p.localKey == localKey
                    val matchesTitle = p.playlistName != null && p.playlistName == oldTitle
                    if (!matchesKey && (!matchesTitle || isTitleAmbiguous)) return null
                    val updated = p.copy(
                        localKey = localKey,
                        playlistName = if (p.playlistName != null && p.playlistName == oldTitle) newTitle else p.playlistName
                    )
                    action.copy(payloadJson = encodeAddTrack(updated))
                }
                SyncActionType.REMOVE_TRACK -> {
                    val p = try { decodeRemoveTrack(action.payloadJson) } catch (_: Exception) { null } ?: return null
                    val matchesKey = p.localKey.isNotBlank() && p.localKey == localKey
                    val matchesTitle = p.playlistName != null && p.playlistName == oldTitle
                    if (!matchesKey && (!matchesTitle || isTitleAmbiguous)) return null
                    val updated = p.copy(
                        localKey = localKey,
                        playlistName = if (p.playlistName != null && p.playlistName == oldTitle) newTitle else p.playlistName
                    )
                    action.copy(payloadJson = encodeRemoveTrack(updated))
                }
                SyncActionType.CLONE_PLAYLIST -> {
                    val p = try { decodeClonePlaylist(action.payloadJson) } catch (_: Exception) { null } ?: return null
                    val matchesKey = p.newLocalKey.isNotBlank() && p.newLocalKey == localKey
                    val matchesTitle = p.title != null && p.title == oldTitle
                    if (!matchesKey && (!matchesTitle || isTitleAmbiguous)) return null
                    val updated = p.copy(
                        newLocalKey = localKey,
                        title = if (p.title != null && p.title == oldTitle) newTitle else p.title
                    )
                    action.copy(payloadJson = encodeClonePlaylist(updated))
                }
                SyncActionType.ADD_TRACK_TO_PLAYLIST, SyncActionType.REMOVE_TRACK_FROM_PLAYLIST -> {
                    val obj = try { json.parseToJsonElement(action.payloadJson).jsonObject } catch (_: Exception) { null } ?: return null
                    val pl = obj["playlist"]?.jsonPrimitive?.content ?: obj["name"]?.jsonPrimitive?.content ?: ""
                    val loc = obj["local_key"]?.jsonPrimitive?.content ?: obj["localKey"]?.jsonPrimitive?.content ?: ""
                    val matchesKey = loc.isNotBlank() && loc == localKey
                    val matchesTitle = pl.isNotBlank() && pl == oldTitle
                    if (!matchesKey && (!matchesTitle || isTitleAmbiguous)) return null
                    val map = obj.toMutableMap()
                    map["local_key"] = JsonPrimitive(localKey)
                    if (map.containsKey("playlist")) map["playlist"] = JsonPrimitive(newTitle)
                    if (map.containsKey("name")) map["name"] = JsonPrimitive(newTitle)
                    action.copy(payloadJson = JsonObject(map).toString())
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }
}

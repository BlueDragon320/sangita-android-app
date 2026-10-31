package com.sangita.app.sync

import com.sangita.app.domain.model.SyncActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPayloadSerializationTest {

    // ==========================================
    // 1. CreatePlaylistPayload
    // ==========================================

    @Test
    fun createPlaylistPayload_fullSerializationAndDeserialization() {
        val payload = CreatePlaylistPayload(
            localKey = "local_pl_100",
            title = "Chill Lofi",
            description = "Relaxing beats to code/study to",
            visibility = "public"
        )
        val jsonStr = SyncPayloadSerialization.encodeCreatePlaylist(payload)
        assertTrue(jsonStr.contains(""""localKey":"local_pl_100""""))
        assertTrue(jsonStr.contains(""""title":"Chill Lofi""""))
        assertTrue(jsonStr.contains(""""description":"Relaxing beats to code/study to""""))
        assertTrue(jsonStr.contains(""""visibility":"public""""))

        val decoded = SyncPayloadSerialization.decodeCreatePlaylist(jsonStr)
        assertEquals(payload.localKey, decoded.localKey)
        assertEquals(payload.title, decoded.title)
        assertEquals(payload.description, decoded.description)
        assertEquals(payload.visibility, decoded.visibility)
    }

    @Test
    fun createPlaylistPayload_defaults() {
        val payload = CreatePlaylistPayload(
            localKey = "local_pl_101",
            title = "Default Playlist"
        )
        assertEquals("private", payload.visibility)
        assertNull(payload.description)

        val jsonStr = SyncPayloadSerialization.encodeCreatePlaylist(payload)
        val decoded = SyncPayloadSerialization.decodeCreatePlaylist(jsonStr)
        assertEquals("local_pl_101", decoded.localKey)
        assertEquals("Default Playlist", decoded.title)
        assertNull(decoded.description)
        assertEquals("private", decoded.visibility)
    }

    @Test
    fun createPlaylistPayload_snakeCaseAndLegacyNameDeserialization() {
        val rawJson = """
            {
                "local_key": "local_pl_legacy",
                "name": "Legacy Rock",
                "visibility": "public"
            }
        """.trimIndent()
        val decoded = SyncPayloadSerialization.decodeCreatePlaylist(rawJson)
        assertEquals("local_pl_legacy", decoded.localKey)
        assertEquals("Legacy Rock", decoded.title)
        assertEquals("public", decoded.visibility)
        assertNull(decoded.description)
    }

    // ==========================================
    // 2. UpdatePlaylistPayload
    // ==========================================

    @Test
    fun updatePlaylistPayload_fullSerializationAndDeserialization() {
        val payload = UpdatePlaylistPayload(
            localKey = "local_pl_200",
            serverId = "srv_pl_200",
            title = "Updated Title",
            description = "Updated Description",
            visibility = "public"
        )
        val jsonStr = SyncPayloadSerialization.encodeUpdatePlaylist(payload)
        val decoded = SyncPayloadSerialization.decodeUpdatePlaylist(jsonStr)
        assertEquals("local_pl_200", decoded.localKey)
        assertEquals("srv_pl_200", decoded.serverId)
        assertEquals("Updated Title", decoded.title)
        assertEquals("Updated Description", decoded.description)
        assertEquals("public", decoded.visibility)
    }

    @Test
    fun updatePlaylistPayload_nullServerIdAndOptionalFields() {
        val payload = UpdatePlaylistPayload(
            localKey = "local_pl_201",
            serverId = null,
            title = "Only Title Updated"
        )
        val jsonStr = SyncPayloadSerialization.encodeUpdatePlaylist(payload)
        val decoded = SyncPayloadSerialization.decodeUpdatePlaylist(jsonStr)
        assertEquals("local_pl_201", decoded.localKey)
        assertNull(decoded.serverId)
        assertEquals("Only Title Updated", decoded.title)
        assertNull(decoded.description)
        assertNull(decoded.visibility)
    }

    @Test
    fun updatePlaylistPayload_snakeCaseDeserialization() {
        val rawJson = """
            {
                "local_key": "local_pl_snake",
                "server_id": "srv_pl_snake",
                "name": "Snake Title",
                "visibility": "private"
            }
        """.trimIndent()
        val decoded = SyncPayloadSerialization.decodeUpdatePlaylist(rawJson)
        assertEquals("local_pl_snake", decoded.localKey)
        assertEquals("srv_pl_snake", decoded.serverId)
        assertEquals("Snake Title", decoded.title)
        assertEquals("private", decoded.visibility)
    }

    // ==========================================
    // 3. DeletePlaylistPayload
    // ==========================================

    @Test
    fun deletePlaylistPayload_withServerId() {
        val payload = DeletePlaylistPayload(
            localKey = "local_pl_300",
            serverId = "srv_pl_300"
        )
        val jsonStr = SyncPayloadSerialization.encodeDeletePlaylist(payload)
        val decoded = SyncPayloadSerialization.decodeDeletePlaylist(jsonStr)
        assertEquals("local_pl_300", decoded.localKey)
        assertEquals("srv_pl_300", decoded.serverId)
    }

    @Test
    fun deletePlaylistPayload_nullServerId() {
        val payload = DeletePlaylistPayload(
            localKey = "local_pl_301",
            serverId = null
        )
        val jsonStr = SyncPayloadSerialization.encodeDeletePlaylist(payload)
        val decoded = SyncPayloadSerialization.decodeDeletePlaylist(jsonStr)
        assertEquals("local_pl_301", decoded.localKey)
        assertNull(decoded.serverId)
    }

    // ==========================================
    // 4. AddTrackPayload
    // ==========================================

    @Test
    fun addTrackPayload_fullSerialization() {
        val payload = AddTrackPayload(
            localKey = "local_pl_400",
            serverId = "srv_pl_400",
            trackPath = "Rock/Bohemian Rhapsody.mp3"
        )
        val jsonStr = SyncPayloadSerialization.encodeAddTrack(payload)
        val decoded = SyncPayloadSerialization.decodeAddTrack(jsonStr)
        assertEquals("local_pl_400", decoded.localKey)
        assertEquals("srv_pl_400", decoded.serverId)
        assertEquals("Rock/Bohemian Rhapsody.mp3", decoded.trackPath)
    }

    @Test
    fun addTrackPayload_nullServerId_and_snakeCase() {
        val rawJson = """
            {
                "local_key": "local_pl_401",
                "server_id": null,
                "track_path": "Jazz/Take Five.mp3"
            }
        """.trimIndent()
        val decoded = SyncPayloadSerialization.decodeAddTrack(rawJson)
        assertEquals("local_pl_401", decoded.localKey)
        assertNull(decoded.serverId)
        assertEquals("Jazz/Take Five.mp3", decoded.trackPath)
    }

    // ==========================================
    // 5. RemoveTrackPayload
    // ==========================================

    @Test
    fun removeTrackPayload_fullSerialization() {
        val payload = RemoveTrackPayload(
            localKey = "local_pl_500",
            serverId = "srv_pl_500",
            trackPath = "Pop/Yesterday.mp3"
        )
        val jsonStr = SyncPayloadSerialization.encodeRemoveTrack(payload)
        val decoded = SyncPayloadSerialization.decodeRemoveTrack(jsonStr)
        assertEquals("local_pl_500", decoded.localKey)
        assertEquals("srv_pl_500", decoded.serverId)
        assertEquals("Pop/Yesterday.mp3", decoded.trackPath)
    }

    @Test
    fun removeTrackPayload_nullServerId() {
        val payload = RemoveTrackPayload(
            localKey = "local_pl_501",
            serverId = null,
            trackPath = "Classical/Symphony5.mp3"
        )
        val jsonStr = SyncPayloadSerialization.encodeRemoveTrack(payload)
        val decoded = SyncPayloadSerialization.decodeRemoveTrack(jsonStr)
        assertEquals("local_pl_501", decoded.localKey)
        assertNull(decoded.serverId)
        assertEquals("Classical/Symphony5.mp3", decoded.trackPath)
    }

    // ==========================================
    // 6. ClonePlaylistPayload
    // ==========================================

    @Test
    fun clonePlaylistPayload_fullSerialization() {
        val payload = ClonePlaylistPayload(
            sourceServerId = "srv_source_600",
            newLocalKey = "local_copy_600",
            title = "My Copy of Synthwave",
            description = "Cloned version",
            visibility = "private"
        )
        val jsonStr = SyncPayloadSerialization.encodeClonePlaylist(payload)
        val decoded = SyncPayloadSerialization.decodeClonePlaylist(jsonStr)
        assertEquals("srv_source_600", decoded.sourceServerId)
        assertEquals("local_copy_600", decoded.newLocalKey)
        assertEquals("My Copy of Synthwave", decoded.title)
        assertEquals("Cloned version", decoded.description)
        assertEquals("private", decoded.visibility)
    }

    @Test
    fun clonePlaylistPayload_defaultsAndSnakeCase() {
        val rawJson = """
            {
                "source_server_id": "srv_src_601",
                "new_local_key": "local_new_601"
            }
        """.trimIndent()
        val decoded = SyncPayloadSerialization.decodeClonePlaylist(rawJson)
        assertEquals("srv_src_601", decoded.sourceServerId)
        assertEquals("local_new_601", decoded.newLocalKey)
        assertNull(decoded.title)
        assertNull(decoded.description)
        assertEquals("private", decoded.visibility)
    }

    // ==========================================
    // 7. ToggleFavoritePayload
    // ==========================================

    @Test
    fun toggleFavoritePayload_serialization_isFavoriteTrue() {
        val payload = ToggleFavoritePayload(
            trackId = "Ambient/Forest Rain.mp3",
            isFavorite = true
        )
        val jsonStr = SyncPayloadSerialization.encodeToggleFavorite(payload)
        val decoded = SyncPayloadSerialization.decodeToggleFavorite(jsonStr)
        assertEquals("Ambient/Forest Rain.mp3", decoded.trackId)
        assertTrue(decoded.isFavorite)
    }

    @Test
    fun toggleFavoritePayload_serialization_isFavoriteFalse() {
        val payload = ToggleFavoritePayload(
            trackId = "Ambient/Forest Rain.mp3",
            isFavorite = false
        )
        val jsonStr = SyncPayloadSerialization.encodeToggleFavorite(payload)
        val decoded = SyncPayloadSerialization.decodeToggleFavorite(jsonStr)
        assertEquals("Ambient/Forest Rain.mp3", decoded.trackId)
        assertFalse(decoded.isFavorite)
    }

    @Test
    fun toggleFavoritePayload_snakeCase_trackPathAndIsFavorite() {
        val rawJson = """
            {
                "track_path": "Blues/Crossroads.mp3",
                "is_favorite": true
            }
        """.trimIndent()
        val decoded = SyncPayloadSerialization.decodeToggleFavorite(rawJson)
        assertEquals("Blues/Crossroads.mp3", decoded.trackId)
        assertTrue(decoded.isFavorite)
    }

    // ==========================================
    // 8. Error Code Handling (409 Conflict as Success for Add Track)
    // ==========================================

    @Test
    fun addTrack_conflict_409WithoutBody_treatedAsSuccess() {
        assertTrue(SyncPayloadSerialization.isAddTrackConflictSuccess(409, null))
    }

    @Test
    fun addTrack_conflict_409WithBackendJsonBody_treatedAsSuccess() {
        // Backend returns: {"error": "Track already exists in playlist", "status": "exists"}
        val backendJson = """{"error": "Track already exists in playlist", "status": "exists"}"""
        assertTrue(SyncPayloadSerialization.isAddTrackConflictSuccess(409, backendJson))
    }

    @Test
    fun addTrack_non409Code_withExistsErrorBody_treatedAsSuccess() {
        val errorBody = """{"error": "Track already exists in playlist", "status": "exists"}"""
        assertTrue(SyncPayloadSerialization.isAddTrackConflictSuccess(400, errorBody))
    }

    @Test
    fun addTrack_plainTextContainsExists_treatedAsSuccess() {
        assertTrue(SyncPayloadSerialization.isAddTrackConflictSuccess(400, "Track already exists in playlist"))
    }

    @Test
    fun addTrack_successCodes_treatedAsSuccess() {
        assertTrue(SyncPayloadSerialization.isAddTrackConflictSuccess(200))
        assertTrue(SyncPayloadSerialization.isAddTrackConflictSuccess(201))
        assertTrue(SyncPayloadSerialization.isAddTrackConflictSuccess(204))
    }

    @Test
    fun addTrack_otherErrors_treatedAsFailure() {
        assertFalse(SyncPayloadSerialization.isAddTrackConflictSuccess(400, """{"error": "Missing parameter"}"""))
        assertFalse(SyncPayloadSerialization.isAddTrackConflictSuccess(404, """{"error": "Playlist not found"}"""))
        assertFalse(SyncPayloadSerialization.isAddTrackConflictSuccess(403, """{"error": "Forbidden"}"""))
        assertFalse(SyncPayloadSerialization.isAddTrackConflictSuccess(500, "Internal Server Error"))
        assertFalse(SyncPayloadSerialization.isAddTrackConflictSuccess(500, null))
    }

    // ==========================================
    // 9. ServerId In Payload Updater
    // ==========================================

    @Test
    fun updateServerIdInPayload_updatePlaylist() {
        val original = UpdatePlaylistPayload(localKey = "loc_pl_1", serverId = null, title = "New Name")
        val jsonStr = SyncPayloadSerialization.encodeUpdatePlaylist(original)

        val updatedJson = SyncPayloadSerialization.updateServerIdInPayload(
            actionType = SyncActionType.UPDATE_PLAYLIST,
            payloadJson = jsonStr,
            localKey = "loc_pl_1",
            newServerId = "srv_generated_99"
        )
        assertNotNull(updatedJson)

        val decoded = SyncPayloadSerialization.decodeUpdatePlaylist(updatedJson!!)
        assertEquals("srv_generated_99", decoded.serverId)
        assertEquals("loc_pl_1", decoded.localKey)
        assertEquals("New Name", decoded.title)
    }

    @Test
    fun updateServerIdInPayload_addTrack() {
        val original = AddTrackPayload(localKey = "loc_pl_1", serverId = null, trackPath = "Song.mp3")
        val jsonStr = SyncPayloadSerialization.encodeAddTrack(original)

        val updatedJson = SyncPayloadSerialization.updateServerIdInPayload(
            actionType = SyncActionType.ADD_TRACK,
            payloadJson = jsonStr,
            localKey = "loc_pl_1",
            newServerId = "srv_generated_99"
        )
        assertNotNull(updatedJson)

        val decoded = SyncPayloadSerialization.decodeAddTrack(updatedJson!!)
        assertEquals("srv_generated_99", decoded.serverId)
        assertEquals("loc_pl_1", decoded.localKey)
        assertEquals("Song.mp3", decoded.trackPath)
    }

    @Test
    fun updateServerIdInPayload_removeTrack() {
        val original = RemoveTrackPayload(localKey = "loc_pl_1", serverId = null, trackPath = "Song.mp3")
        val jsonStr = SyncPayloadSerialization.encodeRemoveTrack(original)

        val updatedJson = SyncPayloadSerialization.updateServerIdInPayload(
            actionType = SyncActionType.REMOVE_TRACK,
            payloadJson = jsonStr,
            localKey = "loc_pl_1",
            newServerId = "srv_generated_99"
        )
        assertNotNull(updatedJson)

        val decoded = SyncPayloadSerialization.decodeRemoveTrack(updatedJson!!)
        assertEquals("srv_generated_99", decoded.serverId)
        assertEquals("loc_pl_1", decoded.localKey)
    }

    @Test
    fun updateServerIdInPayload_deletePlaylist() {
        val original = DeletePlaylistPayload(localKey = "loc_pl_1", serverId = null)
        val jsonStr = SyncPayloadSerialization.encodeDeletePlaylist(original)

        val updatedJson = SyncPayloadSerialization.updateServerIdInPayload(
            actionType = SyncActionType.DELETE_PLAYLIST,
            payloadJson = jsonStr,
            localKey = "loc_pl_1",
            newServerId = "srv_generated_99"
        )
        assertNotNull(updatedJson)

        val decoded = SyncPayloadSerialization.decodeDeletePlaylist(updatedJson!!)
        assertEquals("srv_generated_99", decoded.serverId)
        assertEquals("loc_pl_1", decoded.localKey)
    }

    @Test
    fun updateServerIdInPayload_differentLocalKey_returnsNull() {
        val original = AddTrackPayload(localKey = "other_key", serverId = null, trackPath = "Song.mp3")
        val jsonStr = SyncPayloadSerialization.encodeAddTrack(original)

        val updatedJson = SyncPayloadSerialization.updateServerIdInPayload(
            actionType = SyncActionType.ADD_TRACK,
            payloadJson = jsonStr,
            localKey = "loc_pl_1",
            newServerId = "srv_generated_99"
        )
        assertNull(updatedJson)
    }
}

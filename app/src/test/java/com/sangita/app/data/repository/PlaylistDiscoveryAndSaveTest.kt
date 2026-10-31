package com.sangita.app.data.repository

import android.content.Context
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.sangita.app.data.local.dao.FavoriteDao
import com.sangita.app.data.local.dao.PlaylistDao
import com.sangita.app.data.local.dao.SyncQueueDao
import com.sangita.app.data.local.dao.TrackDao
import com.sangita.app.data.local.database.SangitaDatabase
import com.sangita.app.data.local.entity.PlaylistEntity
import com.sangita.app.data.local.entity.PlaylistTrackCrossRef
import com.sangita.app.data.local.entity.SyncQueueEntity
import com.sangita.app.data.local.preferences.UserPreferences
import com.sangita.app.data.local.storage.LocalStorageManager
import com.sangita.app.data.remote.api.NetworkClient
import com.sangita.app.data.remote.api.SangitaApiService
import com.sangita.app.data.remote.dto.AddArtistCollectionRequest
import com.sangita.app.data.remote.dto.ArtistAddCollectionResponseDto
import com.sangita.app.data.remote.dto.PlaylistDetailDto
import com.sangita.app.data.remote.dto.PlaylistDto
import com.sangita.app.data.remote.dto.PlaylistTrackDto
import com.sangita.app.data.remote.dto.PlaylistsResponseDto
import com.sangita.app.domain.model.PlaylistKind
import com.sangita.app.domain.model.PlaylistSyncState
import com.sangita.app.domain.model.SyncActionType
import com.sangita.app.sync.AddTrackPayload
import com.sangita.app.sync.RemoveTrackPayload
import com.sangita.app.sync.UpdatePlaylistPayload
import com.sangita.app.sync.SyncPayloadSerialization
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class PlaylistDiscoveryAndSaveTest {

    private val context = mockk<Context>(relaxed = true)
    private val database = mockk<SangitaDatabase>(relaxed = true)
    private val trackDao = mockk<TrackDao>(relaxed = true)
    private val playlistDao = mockk<PlaylistDao>(relaxed = true)
    private val favoriteDao = mockk<FavoriteDao>(relaxed = true)
    private val syncQueueDao = mockk<SyncQueueDao>(relaxed = true)
    private val localStorageManager = mockk<LocalStorageManager>(relaxed = true)
    private lateinit var userPreferences: UserPreferences
    private lateinit var networkClient: NetworkClient
    private val apiService = mockk<SangitaApiService>(relaxed = true)

    private lateinit var repository: MusicRepositoryImpl

    @Before
    fun setUp() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { any<RoomDatabase>().withTransaction(any<suspend () -> Any?>()) } coAnswers {
            val block = secondArg<suspend () -> Any?>()
            block()
        }

        val realUserPrefs = UserPreferences(null)
        userPreferences = spyk(realUserPrefs)
        every { userPreferences.getServerUrl() } returns "http://localhost:8080"
        networkClient = spyk(NetworkClient(userPreferences))
        every { networkClient.createApiService(any()) } returns apiService

        repository = MusicRepositoryImpl(
            context = context,
            database = database,
            trackDao = trackDao,
            playlistDao = playlistDao,
            favoriteDao = favoriteDao,
            syncQueueDao = syncQueueDao,
            localStorageManager = localStorageManager,
            userPreferences = userPreferences,
            networkClient = networkClient
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `savePlaylistToLibrary returns failure and reverts local state when server returns 403`() = runTest {
        val playlistId = "public_pl_403"
        assertFalse(userPreferences.isPlaylistSaved(playlistId))

        coEvery { apiService.savePlaylistToLibrary(playlistId) } returns Response.error(
            403,
            "{\"error\":\"Forbidden\"}".toResponseBody()
        )

        val result = repository.savePlaylistToLibrary(playlistId)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("HTTP 403") == true)
        // Verify local state was reverted to false
        assertFalse("Local state must revert to false on 403", userPreferences.isPlaylistSaved(playlistId))
    }

    @Test
    fun `savePlaylistToLibrary returns failure and reverts local state on network error`() = runTest {
        val playlistId = "public_pl_offline"
        assertFalse(userPreferences.isPlaylistSaved(playlistId))

        coEvery { apiService.savePlaylistToLibrary(playlistId) } throws IOException("Connection timed out")

        val result = repository.savePlaylistToLibrary(playlistId)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        // Verify local state was reverted to false
        assertFalse("Local state must revert to false on network error", userPreferences.isPlaylistSaved(playlistId))
    }

    @Test
    fun `savePlaylistToLibrary succeeds and updates local state when server returns 200`() = runTest {
        val playlistId = "public_pl_200"
        assertFalse(userPreferences.isPlaylistSaved(playlistId))

        coEvery { apiService.savePlaylistToLibrary(playlistId) } returns Response.success(mapOf("status" to "saved"))
        coEvery { apiService.getPlaylists(any()) } returns Response.success(PlaylistsResponseDto(emptyList()))
        coEvery { apiService.getDurations() } returns Response.success(emptyMap())
        coEvery { apiService.getFavorites() } returns Response.success(emptyList())

        val result = repository.savePlaylistToLibrary(playlistId)

        assertTrue(result.isSuccess)
        assertTrue("Local state must be updated to true on 200", userPreferences.isPlaylistSaved(playlistId))
    }

    @Test
    fun `removePlaylistFromLibrary returns failure and reverts local state when server returns 500`() = runTest {
        val playlistId = "saved_pl_500"
        userPreferences.setPlaylistSaved(playlistId, true)
        assertTrue(userPreferences.isPlaylistSaved(playlistId))

        coEvery { apiService.removePlaylistFromLibrary(playlistId) } returns Response.error(
            500,
            "{\"error\":\"Internal Server Error\"}".toResponseBody()
        )

        val result = repository.removePlaylistFromLibrary(playlistId)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("HTTP 500") == true)
        // Verify local state was reverted back to true
        assertTrue("Local state must revert to previous state (true)", userPreferences.isPlaylistSaved(playlistId))
    }

    @Test
    fun `removePlaylistFromLibrary returns failure and reverts local state on network error`() = runTest {
        val playlistId = "saved_pl_offline"
        userPreferences.setPlaylistSaved(playlistId, true)
        assertTrue(userPreferences.isPlaylistSaved(playlistId))

        coEvery { apiService.removePlaylistFromLibrary(playlistId) } throws IOException("Host unreachable")

        val result = repository.removePlaylistFromLibrary(playlistId)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        // Verify local state was reverted back to true
        assertTrue("Local state must revert to previous state (true)", userPreferences.isPlaylistSaved(playlistId))
    }

    @Test
    fun `removePlaylistFromLibrary succeeds and removes from local state when server returns 200`() = runTest {
        val playlistId = "saved_pl_success"
        userPreferences.setPlaylistSaved(playlistId, true)
        assertTrue(userPreferences.isPlaylistSaved(playlistId))

        coEvery { apiService.removePlaylistFromLibrary(playlistId) } returns Response.success(mapOf("status" to "removed"))
        coEvery { apiService.getPlaylists(any()) } returns Response.success(PlaylistsResponseDto(emptyList()))
        coEvery { apiService.getDurations() } returns Response.success(emptyMap())
        coEvery { apiService.getFavorites() } returns Response.success(emptyList())

        val result = repository.removePlaylistFromLibrary(playlistId)

        assertTrue(result.isSuccess)
        assertFalse("Local state must be removed (false)", userPreferences.isPlaylistSaved(playlistId))
    }

    @Test
    fun `getCommunityPlaylists fetches discover playlists from server without saving to local library database`() = runTest {
        val remotePlaylists = listOf(
            PlaylistDto(
                id = "remote_pub_1",
                title = "Lo-Fi Beats",
                description = "Relaxing music",
                owner = "dj_curator",
                visibility = "public",
                trackCount = 10,
                isSaved = false
            ),
            PlaylistDto(
                id = "remote_pub_2",
                title = "Study Vibes",
                description = "Focus music",
                owner = "student_user",
                visibility = "public",
                trackCount = 5,
                isSaved = true
            )
        )

        coEvery { apiService.getPlaylists(discover = true) } returns Response.success(
            PlaylistsResponseDto(playlists = remotePlaylists)
        )

        val result = repository.getCommunityPlaylists()

        assertTrue(result.isSuccess)
        val playlists = result.getOrNull().orEmpty()
        assertEquals(2, playlists.size)

        assertEquals("remote_pub_1", playlists[0].id)
        assertEquals("Lo-Fi Beats", playlists[0].title)
        assertEquals("dj_curator", playlists[0].owner)
        assertFalse("remote_pub_1 is not saved", playlists[0].isSaved)

        assertEquals("remote_pub_2", playlists[1].id)
        assertEquals("Study Vibes", playlists[1].title)
        assertTrue("remote_pub_2 has isSaved=true from server", playlists[1].isSaved)

        // Verify discovery playlists are NOT inserted into Room playlistDao
        coVerify(exactly = 0) { playlistDao.insertOrReplace(any()) }
        coVerify(exactly = 0) { playlistDao.insertOrReplaceAll(any()) }
    }

    @Test
    fun `getCommunityPlaylists falls back to local public playlists when server returns error or is offline`() = runTest {
        val localPublic = listOf(
            PlaylistEntity(
                localKey = "local_pub_1",
                serverId = "srv_pub_1",
                accountKey = "default_account",
                title = "Cached Public Playlist",
                visibility = "public",
                kind = PlaylistKind.PUBLIC.name
            )
        )

        coEvery { apiService.getPlaylists(discover = true) } throws IOException("Server unreachable")
        coEvery { playlistDao.getAllPlaylists(any()) } returns localPublic

        val result = repository.getCommunityPlaylists()

        assertTrue(result.isSuccess)
        val playlists = result.getOrNull().orEmpty()
        assertEquals(1, playlists.size)
        assertEquals("local_pub_1", playlists[0].id)
        assertEquals("Cached Public Playlist", playlists[0].title)
    }

    @Test
    fun `getPlaylist resolves playlist entity by server_id when passed a server ID`() = runTest {
        val serverId = "srv_42"
        val localKey = "loc_local_key_uuid"
        val entity = PlaylistEntity(
            localKey = localKey,
            serverId = serverId,
            accountKey = "default_account",
            title = "Resolved By Server ID",
            kind = PlaylistKind.PERSONAL.name
        )

        coEvery { playlistDao.getPlaylistByKey("default_account", serverId) } returns null
        coEvery { playlistDao.getPlaylistByServerId("default_account", serverId) } returns entity
        coEvery { playlistDao.getPlaylistByTitle("default_account", serverId) } returns null
        every { playlistDao.observePlaylistByKeyOrTitle(any(), any()) } returns flowOf(entity)

        val resolved = repository.getPlaylist(serverId).firstOrNull()

        assertTrue(resolved != null)
        assertEquals(localKey, resolved?.id)
        assertEquals(serverId, resolved?.serverId)
        assertEquals("Resolved By Server ID", resolved?.title)
    }

    @Test
    fun `getPlaylistDetail fetches accessible details from server when playlist is not cached`() = runTest {
        val serverId = "srv_discovered_99"
        val detailDto = PlaylistDetailDto(
            id = serverId,
            title = "Discovered Ambient",
            owner = "ambient_artist",
            visibility = "public",
            trackCount = 1,
            tracks = listOf(
                PlaylistTrackDto(
                    trackPath = "Ambient/track1.mp3",
                    title = "Calm River",
                    artist = "ambient_artist",
                    isAvailable = true
                )
            )
        )

        coEvery { apiService.getPlaylistDetail(serverId) } returns Response.success(detailDto)

        val result = repository.getPlaylistDetail(serverId)

        assertTrue(result.isSuccess)
        val detail = result.getOrNull()
        assertEquals(serverId, detail?.id)
        assertEquals("Discovered Ambient", detail?.title)
        assertEquals("ambient_artist", detail?.owner)
        assertEquals(1, detail?.tracks?.size)
        assertEquals("Calm River", detail?.tracks?.first()?.title)
    }

    @Test
    fun `recoverLegacyPlaylist moves unassigned legacy playlist from default_account to active account`() = runTest {
        val activeAccount = "https://sangita.live|alice"
        every { userPreferences.getServerUrl() } returns "https://sangita.live"
        every { userPreferences.getUsername() } returns "alice"
        every { userPreferences.getAccountKey() } returns activeAccount

        val legacyEntity = PlaylistEntity(
            localKey = "legacy_pl_1",
            serverId = null,
            accountKey = "default_account",
            title = "Old Rock",
            kind = PlaylistKind.PERSONAL.name,
            owner = null
        )

        coEvery { playlistDao.getPlaylistByKey("default_account", "legacy_pl_1") } returns legacyEntity
        coEvery { playlistDao.getAllPlaylists("default_account") } returns listOf(legacyEntity)
        coEvery { playlistDao.getPlaylistByTitle(activeAccount, "Old Rock") } returns null
        coEvery { playlistDao.insertOrReplace(any()) } returns Unit
        coEvery { syncQueueDao.migrateActionsToAccount("legacy_pl_1", activeAccount) } returns Unit
        coEvery { syncQueueDao.migrateActionsForPlaylist("legacy_pl_1", "Old Rock", activeAccount, any(), any()) } returns Unit

        val result = repository.recoverLegacyPlaylist("legacy_pl_1")

        assertTrue(result.isSuccess)
        coVerify {
            playlistDao.insertOrReplace(match {
                it.localKey == "legacy_pl_1" &&
                it.accountKey == activeAccount &&
                it.owner == "alice" &&
                it.title == "Old Rock"
            })
            syncQueueDao.migrateActionsForPlaylist("legacy_pl_1", "Old Rock", activeAccount, "Old Rock", false)
            syncQueueDao.migrateActionsToAccount("legacy_pl_1", activeAccount)
        }
    }

    @Test
    fun `addArtistCollectionToPlaylist resolves server ID and decodes tracks_added correctly`() = runTest {
        val activeAccount = "https://sangita.live|alice"
        every { userPreferences.getServerUrl() } returns "https://sangita.live"
        every { userPreferences.getUsername() } returns "alice"
        every { userPreferences.getAccountKey() } returns activeAccount

        val localKey = "loc_pop_mix"
        val serverId = "srv_pl_88"
        val entity = PlaylistEntity(
            localKey = localKey,
            serverId = serverId,
            accountKey = activeAccount,
            title = "Pop Mix",
            kind = PlaylistKind.PERSONAL.name
        )

        coEvery { playlistDao.getPlaylistByKey(activeAccount, localKey) } returns entity
        coEvery { playlistDao.getPlaylistByServerId(activeAccount, localKey) } returns null
        coEvery { playlistDao.getPlaylistByTitle(activeAccount, localKey) } returns null

        val responseDto = ArtistAddCollectionResponseDto(
            status = "added",
            playlistId = serverId,
            tracksAdded = 14,
            duplicatesSkipped = 2
        )
        coEvery { apiService.addArtistCollectionToPlaylist("artist_coldplay", any()) } returns Response.success(responseDto)
        coEvery { apiService.getPlaylistDetail(serverId) } returns Response.success(
            PlaylistDetailDto(id = serverId, title = "Pop Mix", trackCount = 14)
        )

        val result = repository.addArtistCollectionToPlaylist(
            artistId = "artist_coldplay",
            targetPlaylistId = localKey,
            newPlaylistTitle = null
        )

        assertTrue(result.isSuccess)
        val collectionResult = result.getOrNull()
        assertEquals(14, collectionResult?.addedCount)
        assertEquals(2, collectionResult?.skippedDuplicatesCount)
        assertEquals(0, collectionResult?.failedCount)
        assertEquals(serverId, collectionResult?.playlistId)

        // Verify the API was sent the server ID, NOT the local key
        coVerify {
            apiService.addArtistCollectionToPlaylist(
                "artist_coldplay",
                match { it.playlistId == serverId }
            )
        }
    }

    @Test
    fun `addArtistCollectionToPlaylist preserves pending local edits and pending syncState`() = runTest {
        val activeAccount = "https://sangita.live|alice"
        every { userPreferences.getServerUrl() } returns "https://sangita.live"
        every { userPreferences.getUsername() } returns "alice"
        every { userPreferences.getAccountKey() } returns activeAccount

        val localKey = "loc_pop_mix"
        val serverId = "srv_pl_88"
        val entity = PlaylistEntity(
            localKey = localKey,
            serverId = serverId,
            accountKey = activeAccount,
            title = "Pop Mix",
            kind = PlaylistKind.PERSONAL.name,
            syncState = PlaylistSyncState.PENDING_UPDATE.name
        )

        coEvery { playlistDao.getPlaylistByKey(activeAccount, localKey) } returns entity
        coEvery { playlistDao.getPlaylistByServerId(activeAccount, localKey) } returns null
        coEvery { playlistDao.getPlaylistByTitle(activeAccount, localKey) } returns null

        // Pending actions: 1 offline added track, 1 offline removed track
        val pendingActions = listOf(
            SyncQueueEntity(
                id = 1L,
                accountKey = activeAccount,
                actionType = SyncActionType.ADD_TRACK,
                payloadJson = SyncPayloadSerialization.encodeAddTrack(
                    AddTrackPayload(localKey = localKey, serverId = serverId, trackPath = "offline_local_song.mp3")
                )
            ),
            SyncQueueEntity(
                id = 2L,
                accountKey = activeAccount,
                actionType = SyncActionType.REMOVE_TRACK,
                payloadJson = SyncPayloadSerialization.encodeRemoveTrack(
                    RemoveTrackPayload(localKey = localKey, serverId = serverId, trackPath = "server_deleted_song.mp3")
                )
            ),
            SyncQueueEntity(
                id = 3L, accountKey = activeAccount, actionType = SyncActionType.ADD_TRACK,
                payloadJson = SyncPayloadSerialization.encodeAddTrack(
                    AddTrackPayload(localKey = localKey, serverId = serverId, trackPath = "server_deleted_song.mp3")
                )
            ),
            SyncQueueEntity(
                id = 4L, accountKey = activeAccount, actionType = SyncActionType.ADD_TRACK,
                payloadJson = SyncPayloadSerialization.encodeAddTrack(
                    AddTrackPayload(localKey = localKey, serverId = serverId, trackPath = "temporary.mp3")
                )
            ),
            SyncQueueEntity(
                id = 5L, accountKey = activeAccount, actionType = SyncActionType.REMOVE_TRACK,
                payloadJson = SyncPayloadSerialization.encodeRemoveTrack(
                    RemoveTrackPayload(localKey = localKey, serverId = serverId, trackPath = "temporary.mp3")
                )
            ),
            SyncQueueEntity(
                id = 6L, accountKey = activeAccount, actionType = SyncActionType.UPDATE_PLAYLIST,
                payloadJson = SyncPayloadSerialization.encodeUpdatePlaylist(
                    UpdatePlaylistPayload(localKey = localKey, serverId = serverId, title = "Local rename", description = "Local description", visibility = "public", trackPaths = listOf("offline_local_song.mp3", "artist_song2.mp3", "artist_song1.mp3", "server_deleted_song.mp3"))
                )
            )
        )
        coEvery { syncQueueDao.getPendingActions(activeAccount) } returns pendingActions
        coEvery { syncQueueDao.getPendingActions("default_account") } returns emptyList()

        val responseDto = ArtistAddCollectionResponseDto(
            status = "added",
            playlistId = serverId,
            tracksAdded = 2,
            duplicatesSkipped = 0
        )
        coEvery { apiService.addArtistCollectionToPlaylist("artist_coldplay", any()) } returns Response.success(responseDto)
        coEvery { apiService.getPlaylistDetail(serverId) } returns Response.success(
            PlaylistDetailDto(
                id = serverId,
                title = "Pop Mix",
                tracks = listOf(
                    PlaylistTrackDto(id = 1L, trackPath = "server_deleted_song.mp3", title = "Deleted Song", position = 0, isAvailable = true),
                    PlaylistTrackDto(id = 2L, trackPath = "artist_song1.mp3", title = "Song 1", position = 1, isAvailable = true),
                    PlaylistTrackDto(id = 3L, trackPath = "artist_song2.mp3", title = "Song 2", position = 2, isAvailable = true)
                ),
                trackCount = 3
            )
        )

        var insertedEntity: PlaylistEntity? = null
        coEvery { playlistDao.insertOrReplace(any<PlaylistEntity>()) } answers {
            insertedEntity = firstArg<PlaylistEntity>()
            Unit
        }

        var insertedCrossRefs: List<PlaylistTrackCrossRef>? = null
        coEvery { playlistDao.insertCrossRefs(any()) } answers {
            insertedCrossRefs = firstArg<List<PlaylistTrackCrossRef>>()
            Unit
        }

        val result = repository.addArtistCollectionToPlaylist(
            artistId = "artist_coldplay",
            targetPlaylistId = localKey,
            newPlaylistTitle = null
        )

        assertTrue(result.isSuccess)

        // 1. syncState must be preserved as PENDING_UPDATE (not overwritten to SYNCED)
        assertNotNull(insertedEntity)
        assertEquals(PlaylistSyncState.PENDING_UPDATE.name, insertedEntity?.syncState)

        // 2. Apply repeated edits in order and retain the queued playlist changes.
        assertNotNull(insertedCrossRefs)
        val trackPaths = insertedCrossRefs?.map { it.trackId } ?: emptyList()
        assertEquals(listOf("offline_local_song.mp3", "artist_song2.mp3", "artist_song1.mp3", "server_deleted_song.mp3"), trackPaths)
        assertFalse(trackPaths.contains("temporary.mp3"))
        assertEquals("Local rename", insertedEntity?.title)
        assertEquals("Local description", insertedEntity?.description)
        assertEquals("public", insertedEntity?.visibility)
    }
}

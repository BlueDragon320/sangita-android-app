package com.sangita.app.data.repository

import android.content.Context
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.work.WorkManager
import com.sangita.app.data.local.dao.FavoriteDao
import com.sangita.app.data.local.dao.PlaylistDao
import com.sangita.app.data.local.dao.SyncQueueDao
import com.sangita.app.data.local.dao.TrackDao
import com.sangita.app.data.local.database.SangitaDatabase
import com.sangita.app.data.local.entity.FavoriteEntity
import com.sangita.app.data.local.entity.PlaylistEntity
import com.sangita.app.data.local.entity.SyncQueueEntity
import com.sangita.app.data.local.entity.TrackEntity
import com.sangita.app.data.local.preferences.UserPreferences
import com.sangita.app.data.local.storage.LocalStorageManager
import com.sangita.app.data.remote.api.NetworkClient
import com.sangita.app.domain.model.PlaylistKind
import com.sangita.app.domain.model.PlaylistSyncState
import com.sangita.app.domain.model.SyncActionType
import com.sangita.app.domain.model.Track
import com.sangita.app.sync.AddTrackPayload
import com.sangita.app.sync.CreatePlaylistPayload
import com.sangita.app.sync.SangitaSyncWorker
import com.sangita.app.sync.SyncPayloadSerialization
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FavoritesAndOfflineDeleteTest {

    private val context = mockk<Context>(relaxed = true)
    private val database = mockk<SangitaDatabase>(relaxed = true)
    private val trackDao = mockk<TrackDao>(relaxed = true)
    private val playlistDao = mockk<PlaylistDao>(relaxed = true)
    private val favoriteDao = mockk<FavoriteDao>(relaxed = true)
    private val syncQueueDao = mockk<SyncQueueDao>(relaxed = true)
    private val localStorageManager = mockk<LocalStorageManager>(relaxed = true)
    private val userPreferences = mockk<UserPreferences>(relaxed = true)
    private val networkClient = mockk<NetworkClient>(relaxed = true)

    private lateinit var repository: MusicRepositoryImpl

    @Before
    fun setUp() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        mockkStatic(WorkManager::class)
        val workManager = mockk<WorkManager>(relaxed = true)
        every { WorkManager.getInstance(any()) } returns workManager
        coEvery { any<RoomDatabase>().withTransaction(any<suspend () -> Any?>()) } coAnswers {
            val block = secondArg<suspend () -> Any?>()
            block()
        }

        every { userPreferences.observeAccountKey() } returns flowOf("default_account")
        every { userPreferences.getAccountKey() } returns "default_account"
        coEvery { playlistDao.getPlaylistByKey(any(), any()) } coAnswers {
            playlistDao.getPlaylistByKey(secondArg<String>())
        }
        coEvery { playlistDao.deletePlaylistByKey(any(), any()) } coAnswers {
            playlistDao.deletePlaylistByKey(secondArg<String>())
        }
        every { playlistDao.observePlaylistByKeyOrTitle(any(), any()) } answers {
            playlistDao.observePlaylistByKeyOrTitle(secondArg<String>())
        }
        every { playlistDao.observeTracksForPlaylistWithAvailability(any(), any()) } answers {
            playlistDao.observeTracksForPlaylistWithAvailability(secondArg<String>())
        }

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
    fun `observeTracks with migrated UUID for Favorites returns favorite tracks`() = runTest {
        val migratedUuid = "legacy_0123456789abcdef0123456789abcdef"
        val favPlaylistEntity = PlaylistEntity(
            localKey = migratedUuid,
            serverId = null,
            accountKey = "default_account",
            title = "Favorites",
            kind = PlaylistKind.FAVORITES.name,
            syncState = PlaylistSyncState.SYNCED.name
        )

        coEvery { playlistDao.getPlaylistByKey(migratedUuid) } returns favPlaylistEntity
        every { playlistDao.observePlaylistByKeyOrTitle(migratedUuid) } returns flowOf(favPlaylistEntity)

        val favoriteEntities = listOf(
            FavoriteEntity(trackId = "track_1", addedAt = 2000L),
            FavoriteEntity(trackId = "track_2", addedAt = 1000L)
        )
        val trackEntities = listOf(
            TrackEntity(id = "track_1", playlistName = "Favorites", title = "First Song", artist = "Artist A", durationSeconds = 180.0),
            TrackEntity(id = "track_2", playlistName = "Favorites", title = "Second Song", artist = "Artist B", durationSeconds = 210.0),
            TrackEntity(id = "track_3", playlistName = "Favorites", title = "Other Song", artist = "Artist C", durationSeconds = 240.0)
        )

        every { favoriteDao.observeFavorites() } returns flowOf(favoriteEntities)
        every { trackDao.observeAllTracks() } returns flowOf(trackEntities)

        val tracks = try {
            repository.observeTracks(migratedUuid).first()
        } catch (e: Throwable) {
            e.printStackTrace()
            throw e
        }

        assertEquals(2, tracks.size)
        assertEquals("track_1", tracks[0].id)
        assertEquals("First Song", tracks[0].title)
        assertTrue(tracks[0].isFavorite)
        assertEquals("track_2", tracks[1].id)
        assertEquals("Second Song", tracks[1].title)
        assertTrue(tracks[1].isFavorite)
    }

    @Test
    fun `getPlaylist with migrated UUID for Favorites returns playlist with FAVORITES kind`() = runTest {
        val migratedUuid = "legacy_fedcba9876543210fedcba9876543210"
        val favPlaylistEntity = PlaylistEntity(
            localKey = migratedUuid,
            serverId = null,
            accountKey = "default_account",
            title = "Favorites",
            kind = PlaylistKind.FAVORITES.name,
            syncState = PlaylistSyncState.SYNCED.name
        )

        coEvery { playlistDao.getPlaylistByKey(migratedUuid) } returns favPlaylistEntity
        every { playlistDao.observePlaylistByKeyOrTitle(migratedUuid) } returns flowOf(favPlaylistEntity)

        val playlist = repository.getPlaylist(migratedUuid).first()

        assertNotNull(playlist)
        assertEquals(migratedUuid, playlist?.id)
        assertEquals("Favorites", playlist?.title)
        assertEquals(PlaylistKind.FAVORITES, playlist?.kind)
        assertTrue(playlist?.isFavorites == true)
    }

    @Test
    fun `deleting an offline-created playlist deletes its pending CREATE_PLAYLIST and ADD_TRACK actions from sync_queue`() = runTest {
        val offlineKey = "offline_playlist_abc"
        val offlinePlaylist = PlaylistEntity(
            localKey = offlineKey,
            serverId = null,
            accountKey = "default_account",
            title = "My Offline Playlist",
            syncState = PlaylistSyncState.PENDING_CREATE.name
        )

        coEvery { playlistDao.getPlaylistByKey(offlineKey) } returns offlinePlaylist
        coEvery { syncQueueDao.deleteActionsByLocalKey(offlineKey) } returns Unit
        coEvery { playlistDao.clearTracksForPlaylist(offlineKey) } returns Unit
        coEvery { playlistDao.deletePlaylistByKey(offlineKey) } returns Unit

        val result = repository.deletePlaylist(offlineKey)
        result.exceptionOrNull()?.printStackTrace()
        assertTrue("deletePlaylist failed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        coVerify(exactly = 1) { syncQueueDao.deleteActionsByLocalKey(offlineKey) }
        coVerify(exactly = 1) { playlistDao.clearTracksForPlaylist(offlineKey) }
        coVerify(exactly = 1) { playlistDao.deletePlaylistByKey(offlineKey) }
        // Verify no DELETE action was enqueued for an offline playlist
        coVerify(exactly = 0) { syncQueueDao.enqueueAction(any()) }
    }

    @Test
    fun `offline delete removes queue items matching local key simulation`() = runTest {
        val targetLocalKey = "offline_playlist_target"
        val otherLocalKey = "offline_playlist_other"

        val queue = mutableListOf<SyncQueueEntity>()
        queue.add(
            SyncQueueEntity(
                id = 1L,
                actionType = SyncActionType.CREATE_PLAYLIST,
                payloadJson = SyncPayloadSerialization.encodeCreatePlaylist(
                    CreatePlaylistPayload(localKey = targetLocalKey, title = "Target Playlist")
                )
            )
        )
        queue.add(
            SyncQueueEntity(
                id = 2L,
                actionType = SyncActionType.ADD_TRACK,
                payloadJson = SyncPayloadSerialization.encodeAddTrack(
                    AddTrackPayload(localKey = targetLocalKey, serverId = null, trackPath = "track_xyz")
                )
            )
        )
        queue.add(
            SyncQueueEntity(
                id = 3L,
                actionType = SyncActionType.CREATE_PLAYLIST,
                payloadJson = SyncPayloadSerialization.encodeCreatePlaylist(
                    CreatePlaylistPayload(localKey = otherLocalKey, title = "Other Playlist")
                )
            )
        )

        val targetPlaylist = PlaylistEntity(
            localKey = targetLocalKey,
            serverId = null,
            title = "Target Playlist",
            syncState = PlaylistSyncState.PENDING_CREATE.name
        )

        coEvery { playlistDao.getPlaylistByKey(targetLocalKey) } returns targetPlaylist
        coEvery { syncQueueDao.deleteActionsByLocalKey(targetLocalKey) } answers {
            queue.removeAll { it.payloadJson.contains(targetLocalKey) }
        }
        coEvery { playlistDao.clearTracksForPlaylist(targetLocalKey) } returns Unit
        coEvery { playlistDao.deletePlaylistByKey(targetLocalKey) } returns Unit

        val result = repository.deletePlaylist(targetLocalKey)

        assertTrue(result.isSuccess)
        assertEquals(1, queue.size)
        assertEquals(3L, queue.first().id)
        assertTrue(queue.first().payloadJson.contains(otherLocalKey))
    }

    @Test
    fun `mutations enqueue sync actions with active account key`() = runTest {
        val activeAccount = "https://sangita.example|user_scoping"
        every { userPreferences.getAccountKey() } returns activeAccount
        every { userPreferences.getUsername() } returns "user_scoping"

        val enqueuedEntities = mutableListOf<SyncQueueEntity>()
        coEvery { syncQueueDao.enqueueAction(any()) } answers {
            val entity = firstArg<SyncQueueEntity>()
            enqueuedEntities.add(entity)
            enqueuedEntities.size.toLong()
        }

        val testTrack = Track(
            id = "track_scope_1",
            title = "Test Scope Song",
            artist = "Scope Artist",
            playlist = "Favorites",
            durationSeconds = 120.0
        )

        // 1. toggleFavorite
        val favResult = repository.toggleFavorite(testTrack)
        assertTrue(favResult.isSuccess)
        assertEquals(1, enqueuedEntities.size)
        assertEquals(activeAccount, enqueuedEntities.last().accountKey)
        assertEquals(SyncActionType.SET_FAVORITE, enqueuedEntities.last().actionType)

        // 2. createPlaylist
        val createResult = repository.createPlaylist("Scope Playlist", "desc", false)
        assertTrue(createResult.isSuccess)
        val plKey = createResult.getOrNull()!!.id
        assertEquals(2, enqueuedEntities.size)
        assertEquals(activeAccount, enqueuedEntities.last().accountKey)
        assertEquals(SyncActionType.CREATE_PLAYLIST, enqueuedEntities.last().actionType)

        // Mock playlist in DAO for subsequent operations
        val plEntity = PlaylistEntity(
            localKey = plKey,
            serverId = "srv_scope_pl",
            accountKey = activeAccount,
            title = "Scope Playlist",
            syncState = PlaylistSyncState.SYNCED.name
        )
        coEvery { playlistDao.getPlaylistByKey(activeAccount, plKey) } returns plEntity
        coEvery { playlistDao.getPlaylistByTitle(activeAccount, plKey) } returns plEntity
        coEvery { playlistDao.getPlaylistByServerId(activeAccount, plKey) } returns plEntity

        // 3. updatePlaylist
        val updateResult = repository.updatePlaylist(plKey, title = "Renamed Scope Playlist", description = "new desc", visibility = "private")
        assertTrue(updateResult.isSuccess)
        assertEquals(3, enqueuedEntities.size)
        assertEquals(activeAccount, enqueuedEntities.last().accountKey)
        assertEquals(SyncActionType.UPDATE_PLAYLIST, enqueuedEntities.last().actionType)

        // 4. addTrackToPlaylist
        val addResult = repository.addTrackToPlaylist(plKey, testTrack)
        assertTrue(addResult.isSuccess)
        assertEquals(4, enqueuedEntities.size)
        assertEquals(activeAccount, enqueuedEntities.last().accountKey)
        assertEquals(SyncActionType.ADD_TRACK, enqueuedEntities.last().actionType)

        // 5. removeTrackFromPlaylist
        val removeResult = repository.removeTrackFromPlaylist(plKey, testTrack)
        assertTrue(removeResult.isSuccess)
        assertEquals(5, enqueuedEntities.size)
        assertEquals(activeAccount, enqueuedEntities.last().accountKey)
        assertEquals(SyncActionType.REMOVE_TRACK, enqueuedEntities.last().actionType)

        // 6. deletePlaylist (with serverId)
        val deleteResult = repository.deletePlaylist(plKey)
        assertTrue(deleteResult.isSuccess)
        assertEquals(6, enqueuedEntities.size)
        assertEquals(activeAccount, enqueuedEntities.last().accountKey)
        assertEquals(SyncActionType.DELETE_PLAYLIST, enqueuedEntities.last().actionType)
    }
}

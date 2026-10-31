package com.sangita.app.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sangita.app.data.local.entity.PlaylistEntity
import com.sangita.app.data.local.entity.PlaylistTrackCrossRef
import com.sangita.app.data.local.entity.TrackEntity
import com.sangita.app.domain.model.Playlist
import kotlinx.coroutines.flow.Flow

data class PlaylistTrackItem(
    @Embedded val track: TrackEntity,
    @ColumnInfo(name = "ref_is_available") val isAvailable: Boolean = true
) {
    fun toDomain(): com.sangita.app.domain.model.Track = track.toDomain(isAvailable = isAvailable)
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists WHERE account_key = 'default_account' AND server_id IS NULL AND kind != 'FAVORITES' ORDER BY title ASC")
    fun observeRecoverableLegacyPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE account_key = 'default_account' AND server_id IS NULL AND kind != 'FAVORITES' ORDER BY title ASC")
    suspend fun getRecoverableLegacyPlaylists(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE account_key = :accountKey ORDER BY CASE WHEN kind = 'FAVORITES' THEN 0 ELSE 1 END, title ASC")
    fun observeAllPlaylists(accountKey: String): Flow<List<PlaylistEntity>>

    fun observeAllPlaylists(): Flow<List<PlaylistEntity>> = observeAllPlaylists("default_account")

    @Query("SELECT * FROM playlists WHERE account_key = :accountKey ORDER BY CASE WHEN kind = 'FAVORITES' THEN 0 ELSE 1 END, title ASC")
    suspend fun getAllPlaylists(accountKey: String = "default_account"): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE account_key = :accountKey AND local_key = :localKey LIMIT 1")
    suspend fun getPlaylistByKey(accountKey: String, localKey: String): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE local_key = :localKey LIMIT 1")
    suspend fun getPlaylistByKey(localKey: String): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE account_key = :accountKey AND server_id = :serverId LIMIT 1")
    suspend fun getPlaylistByServerId(accountKey: String, serverId: String): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE account_key = :accountKey AND title = :title LIMIT 1")
    suspend fun getPlaylistByTitle(accountKey: String, title: String): PlaylistEntity?

    suspend fun getPlaylistByName(name: String): PlaylistEntity? = getPlaylistByTitle("default_account", name)

    @Query("SELECT * FROM playlists WHERE account_key = :accountKey AND (local_key = :keyOrTitle OR server_id = :keyOrTitle OR title = :keyOrTitle) LIMIT 1")
    fun observePlaylistByKeyOrTitle(accountKey: String, keyOrTitle: String): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE local_key = :keyOrTitle OR server_id = :keyOrTitle OR title = :keyOrTitle LIMIT 1")
    fun observePlaylistByKeyOrTitle(keyOrTitle: String): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE account_key = :accountKey AND local_key = :localKey LIMIT 1")
    fun observePlaylistByKey(accountKey: String, localKey: String): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE local_key = :localKey LIMIT 1")
    fun observePlaylistByKey(localKey: String): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE account_key = :accountKey AND title = :name LIMIT 1")
    fun observePlaylistByName(accountKey: String, name: String): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE title = :name LIMIT 1")
    fun observePlaylistByName(name: String): Flow<PlaylistEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceAll(playlists: List<PlaylistEntity>)

    @Query("UPDATE playlists SET server_id = :serverId, sync_state = :syncState, last_synced_at = :lastSyncedAt WHERE local_key = :localKey")
    suspend fun updateSyncInfo(localKey: String, serverId: String, syncState: String, lastSyncedAt: Long = System.currentTimeMillis())

    @Query("UPDATE playlists SET sync_state = :syncState, last_synced_at = :lastSyncedAt WHERE local_key = :localKey")
    suspend fun updateSyncState(localKey: String, syncState: String, lastSyncedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM playlists WHERE account_key = :accountKey AND local_key = :localKey")
    suspend fun deletePlaylistByKey(accountKey: String, localKey: String)

    @Query("DELETE FROM playlists WHERE local_key = :localKey")
    suspend fun deletePlaylistByKey(localKey: String)

    @Query("DELETE FROM playlists WHERE account_key = :accountKey AND title = :name")
    suspend fun deletePlaylistByName(accountKey: String, name: String)

    @Query("DELETE FROM playlists WHERE title = :name")
    suspend fun deletePlaylistByName(name: String)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_track_cross_ref WHERE playlist_local_key = :playlistLocalKey")
    suspend fun nextTrackPosition(playlistLocalKey: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRef(ref: PlaylistTrackCrossRef)

    @Query("DELETE FROM playlist_track_cross_ref WHERE playlist_local_key = :playlistLocalKey")
    suspend fun clearTracksForPlaylist(playlistLocalKey: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRefs(refs: List<PlaylistTrackCrossRef>)

    @Query("DELETE FROM playlist_track_cross_ref WHERE playlist_local_key = :playlistLocalKey AND track_id = :trackId")
    suspend fun deleteCrossRef(playlistLocalKey: String, trackId: String)

    @Query("UPDATE playlist_track_cross_ref SET position = :position WHERE playlist_local_key = :playlistLocalKey AND track_id = :trackId")
    suspend fun updateTrackPosition(playlistLocalKey: String, trackId: String, position: Int)

    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN playlist_track_cross_ref r ON t.id = r.track_id
        WHERE r.playlist_local_key = :playlistLocalKey
        ORDER BY r.position ASC, r.added_at ASC
    """)
    suspend fun getTracksForPlaylist(playlistLocalKey: String): List<TrackEntity>

    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN playlist_track_cross_ref r ON t.id = r.track_id
        WHERE r.playlist_local_key = :playlistLocalKey
        ORDER BY r.position ASC, r.added_at ASC
    """)
    fun observeTracksForPlaylist(playlistLocalKey: String): Flow<List<TrackEntity>>

    @Query("""
        SELECT t.*, r.is_available AS ref_is_available FROM tracks t
        INNER JOIN playlist_track_cross_ref r ON t.id = r.track_id
        WHERE r.playlist_local_key = :playlistLocalKey
           OR r.playlist_local_key = (SELECT title FROM playlists WHERE account_key = :accountKey AND local_key = :playlistLocalKey LIMIT 1)
           OR r.playlist_local_key = (SELECT local_key FROM playlists WHERE account_key = :accountKey AND title = :playlistLocalKey LIMIT 1)
           OR r.playlist_local_key = (SELECT local_key FROM playlists WHERE account_key = :accountKey AND server_id = :playlistLocalKey LIMIT 1)
        ORDER BY r.position ASC, r.added_at ASC
    """)
    fun observeTracksForPlaylistWithAvailability(accountKey: String, playlistLocalKey: String): Flow<List<PlaylistTrackItem>>

    @Query("""
        SELECT t.*, r.is_available AS ref_is_available FROM tracks t
        INNER JOIN playlist_track_cross_ref r ON t.id = r.track_id
        WHERE r.playlist_local_key = :playlistLocalKey
           OR r.playlist_local_key = (SELECT title FROM playlists WHERE local_key = :playlistLocalKey LIMIT 1)
           OR r.playlist_local_key = (SELECT local_key FROM playlists WHERE title = :playlistLocalKey LIMIT 1)
           OR r.playlist_local_key = (SELECT local_key FROM playlists WHERE server_id = :playlistLocalKey LIMIT 1)
        ORDER BY r.position ASC, r.added_at ASC
    """)
    fun observeTracksForPlaylistWithAvailability(playlistLocalKey: String): Flow<List<PlaylistTrackItem>>
}

fun PlaylistEntity.toPlaylist(): Playlist = toDomain()
fun Playlist.toEntity(accountKey: String = "default_account"): PlaylistEntity = PlaylistEntity.fromDomain(this, accountKey)

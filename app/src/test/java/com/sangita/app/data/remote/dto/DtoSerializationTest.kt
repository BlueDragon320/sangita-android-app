package com.sangita.app.data.remote.dto

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DtoSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    // ==========================================
    // 1. CatalogSourceDto & SourcesResponseDto
    // ==========================================

    @Test
    fun parseSourcesResponseDto_fullFields() {
        val jsonString = """
            {
                "sources": [
                    {
                        "id": 1,
                        "name": "RockFolder",
                        "display_name": "Rock Music",
                        "classification": "artist",
                        "artist_id": "art-uuid-1",
                        "has_access": true,
                        "track_count": 42,
                        "extra_future_key": "ignore_me"
                    }
                ]
            }
        """.trimIndent()

        val response = json.decodeFromString<SourcesResponseDto>(jsonString)
        assertEquals(1, response.sources.size)

        val source = response.sources[0]
        assertEquals(1, source.id)
        assertEquals("RockFolder", source.name)
        assertEquals("Rock Music", source.displayName)
        assertEquals("artist", source.classification)
        assertEquals("art-uuid-1", source.artistId)
        assertTrue(source.hasAccess)
        assertEquals(42, source.trackCount)
    }

    @Test
    fun parseSourcesResponseDto_missingOptionalFields() {
        val jsonString = """
            {
                "sources": [
                    {
                        "id": 2,
                        "name": "PrivateFolder",
                        "display_name": "Private Folder",
                        "classification": "unclassified",
                        "has_access": false,
                        "track_count": 0
                    }
                ]
            }
        """.trimIndent()

        val response = json.decodeFromString<SourcesResponseDto>(jsonString)
        assertEquals(1, response.sources.size)

        val source = response.sources[0]
        assertEquals(2, source.id)
        assertNull(source.artistId)
        assertFalse(source.hasAccess)
        assertEquals(0, source.trackCount)
    }

    // ==========================================
    // 2. ArtistDto & ArtistsResponseDto
    // ==========================================

    @Test
    fun parseArtistsResponseDto_fullFields() {
        val jsonString = """
            {
                "artists": [
                    {
                        "id": "queen-id",
                        "name": "Queen",
                        "bio": "Legendary British rock band",
                        "image_url": "/api/cover/Queen/A%20Night%20at%20the%20Opera/04%20You%27re%20My%20Best%20Friend.mp3",
                        "track_count": 14
                    }
                ]
            }
        """.trimIndent()

        val response = json.decodeFromString<ArtistsResponseDto>(jsonString)
        assertEquals(1, response.artists.size)

        val artist = response.artists[0]
        assertEquals("queen-id", artist.id)
        assertEquals("Queen", artist.name)
        assertEquals("Legendary British rock band", artist.bio)
        assertEquals(
            "/api/cover/Queen/A%20Night%20at%20the%20Opera/04%20You%27re%20My%20Best%20Friend.mp3",
            artist.imageUrl
        )
        assertEquals(14, artist.trackCount)
    }

    @Test
    fun parseArtistsResponseDto_missingOptionalFields() {
        val jsonString = """
            {
                "artists": [
                    {
                        "id": "indie-artist",
                        "name": "Indie Band",
                        "track_count": 3
                    }
                ]
            }
        """.trimIndent()

        val response = json.decodeFromString<ArtistsResponseDto>(jsonString)
        val artist = response.artists[0]
        assertEquals("indie-artist", artist.id)
        assertEquals("Indie Band", artist.name)
        assertEquals("", artist.bio)
        assertNull(artist.imageUrl)
        assertEquals(3, artist.trackCount)
    }

    // ==========================================
    // 3. ArtistDetailDto & ArtistTrackDto
    // ==========================================

    @Test
    fun parseArtistDetailDto_fullFieldsWithTracks() {
        val jsonString = """
            {
                "id": "queen-id",
                "name": "Queen",
                "bio": "British rock band formed in London in 1970",
                "image_url": "/api/cover/queen.jpg",
                "track_count": 2,
                "tracks": [
                    {
                        "path": "Queen/Bohemian Rhapsody.mp3",
                        "title": "Bohemian Rhapsody",
                        "artist": "Queen",
                        "duration": 354.3
                    },
                    {
                        "path": "Queen/Radio Ga Ga.mp3",
                        "title": "Radio Ga Ga",
                        "artist": "Queen",
                        "duration": 343.0
                    }
                ]
            }
        """.trimIndent()

        val detail = json.decodeFromString<ArtistDetailDto>(jsonString)
        assertEquals("queen-id", detail.id)
        assertEquals("Queen", detail.name)
        assertEquals(2, detail.trackCount)
        assertEquals(2, detail.tracks.size)

        val track1 = detail.tracks[0]
        assertEquals("Queen/Bohemian Rhapsody.mp3", track1.path)
        assertEquals("Bohemian Rhapsody", track1.title)
        assertEquals("Queen", track1.artist)
        assertEquals(354.3, track1.duration!!, 0.001)

        val track2 = detail.tracks[1]
        assertEquals("Queen/Radio Ga Ga.mp3", track2.path)
        assertEquals(343.0, track2.duration!!, 0.001)
    }

    @Test
    fun parseArtistDetailDto_emptyTracksAndMissingOptionals() {
        val jsonString = """
            {
                "id": "empty-artist",
                "name": "Silent Composer"
            }
        """.trimIndent()

        val detail = json.decodeFromString<ArtistDetailDto>(jsonString)
        assertEquals("empty-artist", detail.id)
        assertEquals("Silent Composer", detail.name)
        assertEquals("", detail.bio)
        assertNull(detail.imageUrl)
        assertEquals(0, detail.trackCount)
        assertTrue(detail.tracks.isEmpty())
    }

    @Test
    fun parseArtistTrackDto_missingDurationAndArtist() {
        val jsonString = """
            {
                "path": "Misc/Unknown.mp3",
                "title": "Untitled"
            }
        """.trimIndent()

        val track = json.decodeFromString<ArtistTrackDto>(jsonString)
        assertEquals("Misc/Unknown.mp3", track.path)
        assertEquals("Untitled", track.title)
        assertEquals("", track.artist)
        assertNull(track.duration)
    }

    // ==========================================
    // 4. PlaylistDto & PlaylistsResponseDto
    // ==========================================

    @Test
    fun parsePlaylistsResponseDto_fullFields() {
        val jsonString = """
            {
                "playlists": [
                    {
                        "id": "pl-uuid-1",
                        "title": "Road Trip Hits",
                        "description": "Upbeat tracks for driving",
                        "owner": "alice",
                        "visibility": "public",
                        "is_folder_playlist": false,
                        "track_count": 25,
                        "created_at": "2026-10-15 14:30:00",
                        "updated_at": "2026-10-20 18:00:00"
                    },
                    {
                        "id": "pl-uuid-2",
                        "title": "Workout Mix",
                        "description": "High BPM",
                        "owner": "bob",
                        "visibility": "private",
                        "is_folder_playlist": false,
                        "track_count": 10,
                        "created_at": "2026-10-18 09:00:00",
                        "updated_at": null
                    }
                ]
            }
        """.trimIndent()

        val response = json.decodeFromString<PlaylistsResponseDto>(jsonString)
        assertEquals(2, response.playlists.size)

        val pl1 = response.playlists[0]
        assertEquals("pl-uuid-1", pl1.id)
        assertEquals("Road Trip Hits", pl1.title)
        assertEquals("Upbeat tracks for driving", pl1.description)
        assertEquals("alice", pl1.owner)
        assertEquals("public", pl1.visibility)
        assertFalse(pl1.isFolderPlaylist)
        assertEquals(25, pl1.trackCount)
        assertEquals("2026-10-15 14:30:00", pl1.createdAt)
        assertEquals("2026-10-20 18:00:00", pl1.updatedAt)

        val pl2 = response.playlists[1]
        assertEquals("pl-uuid-2", pl2.id)
        assertEquals("private", pl2.visibility)
        assertNull(pl2.updatedAt)
    }

    @Test
    fun parsePlaylistDto_missingOptionalFields_defaultsApplied() {
        val jsonString = """
            {
                "id": "pl-minimal",
                "title": "Minimal Playlist"
            }
        """.trimIndent()

        val pl = json.decodeFromString<PlaylistDto>(jsonString)
        assertEquals("pl-minimal", pl.id)
        assertEquals("Minimal Playlist", pl.title)
        assertEquals("", pl.description)
        assertNull(pl.owner)
        assertEquals("private", pl.visibility)
        assertFalse(pl.isFolderPlaylist)
        assertEquals(0, pl.trackCount)
        assertNull(pl.createdAt)
        assertNull(pl.updatedAt)
    }

    // ==========================================
    // 5. PlaylistDetailDto & PlaylistTrackDto (including unavailable tracks)
    // ==========================================

    @Test
    fun parsePlaylistDetailDto_fullFieldsWithTracks() {
        val jsonString = """
            {
                "id": "pl-detail-1",
                "title": "Summer Vibes",
                "description": "Chill beats",
                "owner": "charlie",
                "visibility": "public",
                "is_folder_playlist": false,
                "track_count": 2,
                "unavailable_count": 0,
                "tracks": [
                    {
                        "id": 101,
                        "position": 0,
                        "track_path": "Chill/SunnyDay.mp3",
                        "title": "Sunny Day",
                        "artist": "Lo-Fi Collective",
                        "duration": 180.5,
                        "is_available": true
                    },
                    {
                        "id": 102,
                        "position": 1,
                        "track_path": "Chill/Breeze.mp3",
                        "title": "Breeze",
                        "artist": "Lo-Fi Collective",
                        "duration": 210.0,
                        "is_available": true
                    }
                ],
                "created_at": "2026-06-01 10:00:00",
                "updated_at": "2026-06-02 12:00:00"
            }
        """.trimIndent()

        val detail = json.decodeFromString<PlaylistDetailDto>(jsonString)
        assertEquals("pl-detail-1", detail.id)
        assertEquals("Summer Vibes", detail.title)
        assertEquals("Chill beats", detail.description)
        assertEquals("charlie", detail.owner)
        assertEquals("public", detail.visibility)
        assertFalse(detail.isFolderPlaylist)
        assertEquals(2, detail.trackCount)
        assertEquals(0, detail.unavailableCount)
        assertEquals(2, detail.tracks.size)

        val track1 = detail.tracks[0]
        assertEquals(101L, track1.id)
        assertEquals(0, track1.position)
        assertEquals("Chill/SunnyDay.mp3", track1.trackPath)
        assertEquals("Sunny Day", track1.title)
        assertEquals("Lo-Fi Collective", track1.artist)
        assertEquals(180.5, track1.duration!!, 0.001)
        assertTrue(track1.isAvailable)
    }

    @Test
    fun parsePlaylistDetailDto_withUnavailableTracks_ownerView() {
        // Owner/Admin view: unavailable tracks are kept in list with is_available = false
        val jsonString = """
            {
                "id": "pl-mixed-access",
                "title": "Mixed Access Playlist",
                "owner": "alice",
                "visibility": "public",
                "is_folder_playlist": false,
                "track_count": 2,
                "unavailable_count": 1,
                "tracks": [
                    {
                        "id": 201,
                        "position": 0,
                        "track_path": "PublicSource/Track1.mp3",
                        "title": "Track One",
                        "artist": "Artist A",
                        "duration": 200.0,
                        "is_available": true
                    },
                    {
                        "id": 202,
                        "position": 1,
                        "track_path": "RestrictedSource/Track2.mp3",
                        "title": "Track Two",
                        "artist": "Restricted Artist",
                        "duration": 240.0,
                        "is_available": false
                    }
                ]
            }
        """.trimIndent()

        val detail = json.decodeFromString<PlaylistDetailDto>(jsonString)
        assertEquals(2, detail.trackCount)
        assertEquals(1, detail.unavailableCount)
        assertEquals(2, detail.tracks.size)

        val t1 = detail.tracks[0]
        assertTrue(t1.isAvailable)

        val t2 = detail.tracks[1]
        assertFalse(t2.isAvailable)
        assertEquals("RestrictedSource/Track2.mp3", t2.trackPath)
    }

    @Test
    fun parsePlaylistDetailDto_folderPlaylist_trackIdNull() {
        // Folder playlists do not have database track IDs, id is null
        val jsonString = """
            {
                "id": "folder:RockFolder",
                "title": "RockFolder",
                "description": "Folder playlist for RockFolder",
                "owner": "system",
                "visibility": "public",
                "is_folder_playlist": true,
                "track_count": 1,
                "unavailable_count": 0,
                "tracks": [
                    {
                        "position": 0,
                        "track_path": "RockFolder/Song1.mp3",
                        "title": "Song 1",
                        "artist": "Rock Band",
                        "duration": 195.0,
                        "is_available": true
                    }
                ]
            }
        """.trimIndent()

        val detail = json.decodeFromString<PlaylistDetailDto>(jsonString)
        assertTrue(detail.isFolderPlaylist)
        assertEquals(1, detail.tracks.size)

        val track = detail.tracks[0]
        assertNull(track.id)
        assertEquals(0, track.position)
        assertEquals("RockFolder/Song1.mp3", track.trackPath)
        assertTrue(track.isAvailable)
    }

    // ==========================================
    // 6. Request DTOs
    // ==========================================

    @Test
    fun createPlaylistRequest_serialization() {
        val request = CreatePlaylistRequest(
            title = "My New Playlist",
            description = "Description here",
            visibility = "public",
            tracks = listOf("FolderA/Song1.mp3", "FolderB/Song2.mp3")
        )
        val encoded = json.encodeToString(request)
        assertTrue(encoded.contains(""""title":"My New Playlist""""))
        assertTrue(encoded.contains(""""description":"Description here""""))
        assertTrue(encoded.contains(""""visibility":"public""""))
        assertTrue(encoded.contains(""""FolderA/Song1.mp3""""))

        val decoded = json.decodeFromString<CreatePlaylistRequest>(encoded)
        assertEquals(request.title, decoded.title)
        assertEquals(request.description, decoded.description)
        assertEquals(request.visibility, decoded.visibility)
        assertEquals(request.tracks, decoded.tracks)
    }

    @Test
    fun createPlaylistRequest_defaults() {
        val request = CreatePlaylistRequest(title = "Simple")
        assertEquals("private", request.visibility)
        assertNull(request.description)
        assertNull(request.tracks)
    }

    @Test
    fun updatePlaylistRequest_serialization() {
        val req = UpdatePlaylistRequest(
            title = "Renamed Playlist",
            visibility = "public"
        )
        val encoded = json.encodeToString(req)
        assertTrue(encoded.contains(""""title":"Renamed Playlist""""))
        assertTrue(encoded.contains(""""visibility":"public""""))

        val decoded = json.decodeFromString<UpdatePlaylistRequest>(encoded)
        assertEquals("Renamed Playlist", decoded.title)
        assertEquals("public", decoded.visibility)
        assertNull(decoded.description)
        assertNull(decoded.tracks)
    }

    @Test
    fun clonePlaylistRequest_serialization() {
        val req = ClonePlaylistRequest(
            title = "My Cloned Copy",
            visibility = "private"
        )
        val encoded = json.encodeToString(req)
        val decoded = json.decodeFromString<ClonePlaylistRequest>(encoded)
        assertEquals("My Cloned Copy", decoded.title)
        assertEquals("private", decoded.visibility)
        assertNull(decoded.description)
    }

    @Test
    fun addTrackRequest_serialization() {
        val req = AddTrackRequest(trackPath = "Rock/Anthem.mp3")
        val encoded = json.encodeToString(req)
        assertTrue(encoded.contains(""""track_path":"Rock/Anthem.mp3""""))

        val decoded = json.decodeFromString<AddTrackRequest>(encoded)
        assertEquals("Rock/Anthem.mp3", decoded.trackPath)
    }

    // ==========================================
    // 7. Mutation Response DTOs
    // ==========================================

    @Test
    fun addTrackResponseDto_parsing() {
        val jsonString = """
            {
                "status": "added",
                "playlist_id": "pl-123",
                "track_path": "Rock/Anthem.mp3",
                "position": 5
            }
        """.trimIndent()

        val resp = json.decodeFromString<AddTrackResponseDto>(jsonString)
        assertEquals("added", resp.status)
        assertEquals("pl-123", resp.playlistId)
        assertEquals("Rock/Anthem.mp3", resp.trackPath)
        assertEquals(5, resp.position)
    }

    @Test
    fun removeTrackResponseDto_parsing() {
        val jsonString = """
            {
                "status": "removed",
                "playlist_id": "pl-123",
                "track_path": "Rock/Anthem.mp3"
            }
        """.trimIndent()

        val resp = json.decodeFromString<RemoveTrackResponseDto>(jsonString)
        assertEquals("removed", resp.status)
        assertEquals("pl-123", resp.playlistId)
        assertEquals("Rock/Anthem.mp3", resp.trackPath)
    }

    @Test
    fun deletePlaylistResponseDto_parsing() {
        val jsonString = """
            {
                "status": "deleted",
                "id": "pl-delete-id"
            }
        """.trimIndent()

        val resp = json.decodeFromString<DeletePlaylistResponseDto>(jsonString)
        assertEquals("deleted", resp.status)
        assertEquals("pl-delete-id", resp.id)
    }

    // ==========================================
    // 8. ApiErrorResponseDto
    // ==========================================

    @Test
    fun apiErrorResponseDto_parsing_conflict() {
        val jsonString = """
            {
                "error": "Track already exists in playlist",
                "status": "exists"
            }
        """.trimIndent()

        val err = json.decodeFromString<ApiErrorResponseDto>(jsonString)
        assertEquals("Track already exists in playlist", err.error)
        assertEquals("exists", err.status)
    }

    @Test
    fun apiErrorResponseDto_parsing_notFound() {
        val jsonString = """
            {
                "error": "Playlist not found"
            }
        """.trimIndent()

        val err = json.decodeFromString<ApiErrorResponseDto>(jsonString)
        assertEquals("Playlist not found", err.error)
        assertNull(err.status)
    }

    @Test
    fun apiErrorResponseDto_parsing_forbidden() {
        val jsonString = """
            {
                "error": "Forbidden - Only owner or admin can delete this playlist"
            }
        """.trimIndent()

        val err = json.decodeFromString<ApiErrorResponseDto>(jsonString)
        assertEquals("Forbidden - Only owner or admin can delete this playlist", err.error)
        assertNull(err.status)
    }

    // ==========================================
    // 9. Library DTOs (Saved Artists & Tracks)
    // ==========================================

    @Test
    fun parseLibraryArtistsResponseDto() {
        val jsonString = """
            {
                "artists": [
                    {
                        "id": "art-1",
                        "name": "Daft Punk",
                        "bio": "Electronic music duo",
                        "image_url": "/api/cover/DaftPunk/Discovery/01.mp3",
                        "track_count": 14
                    }
                ]
            }
        """.trimIndent()

        val resp = json.decodeFromString<LibraryArtistsResponseDto>(jsonString)
        assertEquals(1, resp.artists.size)
        assertEquals("art-1", resp.artists[0].id)
        assertEquals("Daft Punk", resp.artists[0].name)
        assertEquals(14, resp.artists[0].trackCount)
    }

    @Test
    fun parseLibraryTracksResponseDto() {
        val jsonString = """
            {
                "tracks": [
                    {
                        "track_path": "Electronic/One More Time.mp3",
                        "title": "One More Time",
                        "artist": "Daft Punk",
                        "duration": 320.5,
                        "is_available": true
                    }
                ]
            }
        """.trimIndent()

        val resp = json.decodeFromString<LibraryTracksResponseDto>(jsonString)
        assertEquals(1, resp.tracks.size)
        assertEquals("Electronic/One More Time.mp3", resp.tracks[0].trackPath)
        assertEquals("One More Time", resp.tracks[0].title)
        assertEquals("Daft Punk", resp.tracks[0].artist)
        assertEquals(320.5, resp.tracks[0].duration)
        assertTrue(resp.tracks[0].isAvailable)
    }

    @Test
    fun saveTrackRequest_serialization() {
        val req = SaveTrackRequest(trackPath = "Pop/Song.mp3")
        val encoded = json.encodeToString(req)
        assertTrue(encoded.contains(""""track_path":"Pop/Song.mp3""""))

        val decoded = json.decodeFromString<SaveTrackRequest>(encoded)
        assertEquals("Pop/Song.mp3", decoded.trackPath)
    }

    @Test
    fun saveActionResponseDto_parsing() {
        val jsonString = """
            {
                "status": "saved",
                "track_path": "Rock/Bohemian.mp3"
            }
        """.trimIndent()

        val resp = json.decodeFromString<SaveActionResponseDto>(jsonString)
        assertEquals("saved", resp.status)
        assertEquals("Rock/Bohemian.mp3", resp.trackPath)
    }
}

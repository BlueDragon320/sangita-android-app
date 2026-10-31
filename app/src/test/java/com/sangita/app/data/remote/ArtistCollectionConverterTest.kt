package com.sangita.app.data.remote

import com.sangita.app.data.remote.api.SangitaApiService
import com.sangita.app.data.remote.dto.AddArtistCollectionRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class ArtistCollectionConverterTest {

    @Test
    fun `addArtistCollectionToPlaylist serializes request and deserializes response with real Retrofit and Kotlinx Serialization`() = runTest {
        var recordedRequestBody: String? = null

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val buffer = Buffer()
                request.body?.writeTo(buffer)
                recordedRequestBody = buffer.readUtf8()

                val jsonResponse = """
                    {
                        "status": "added",
                        "playlist_id": "srv_pl_99",
                        "playlist_title": "Coldplay Best",
                        "tracks_added": 12,
                        "duplicates_skipped": 3,
                        "total_tracks": 15,
                        "message": "Added 12 tracks"
                    }
                """.trimIndent()

                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(jsonResponse.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

        val retrofit = Retrofit.Builder()
            .baseUrl("https://sangita.test/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        val apiService = retrofit.create(SangitaApiService::class.java)

        val request = AddArtistCollectionRequest(
            playlistId = "srv_pl_99",
            newPlaylistTitle = null,
            title = null,
            description = "Collection of Coldplay tracks",
            visibility = "private"
        )

        val response = apiService.addArtistCollectionToPlaylist("artist_coldplay", request)

        assertTrue("Expected HTTP 200 success response", response.isSuccessful)
        assertNotNull("Response body should not be null", response.body())

        val body = response.body()!!
        assertEquals("added", body.status)
        assertEquals("srv_pl_99", body.playlistId)
        assertEquals("Coldplay Best", body.playlistTitle)
        assertEquals(12, body.tracksAdded)
        assertEquals(3, body.duplicatesSkipped)
        assertEquals(15, body.totalTracks)
        assertEquals("Added 12 tracks", body.message)

        assertNotNull("Request body should have been transmitted", recordedRequestBody)
        assertTrue("Request body should contain playlist_id", recordedRequestBody!!.contains("\"playlist_id\":\"srv_pl_99\""))
    }
}

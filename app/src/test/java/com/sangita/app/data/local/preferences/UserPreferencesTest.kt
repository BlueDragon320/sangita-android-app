package com.sangita.app.data.local.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class UserPreferencesTest {

    @Test
    fun testDomainNormalization() {
        // Plain domain defaults to https
        assertEquals("https://music.sangita.com", UserPreferences.normalizeUrl("music.sangita.com"))
        assertEquals("https://music.sangita.com", UserPreferences.normalizeUrl("music.sangita.com/"))
        assertEquals("https://sangita.app", UserPreferences.normalizeUrl("  sangita.app/  "))

        // Explicit protocols preserved
        assertEquals("http://myserver.org:5000", UserPreferences.normalizeUrl("http://myserver.org:5000/"))
        assertEquals("https://cloud.sangita.net", UserPreferences.normalizeUrl("https://cloud.sangita.net"))

        // Local IPs default to http
        assertEquals("http://192.168.1.50:5000", UserPreferences.normalizeUrl("192.168.1.50:5000"))
        assertEquals("http://10.0.2.2:5000", UserPreferences.normalizeUrl("10.0.2.2:5000"))
        assertEquals("http://localhost:5000", UserPreferences.normalizeUrl("localhost:5000"))
    }

    @Test
    fun testTokenExpirationCheck() {
        val prefs = UserPreferences(null)

        // Null or blank token is expired
        assertTrue(prefs.isTokenExpired(null))
        assertTrue(prefs.isTokenExpired(""))
        assertTrue(prefs.isTokenExpired("   "))

        // Non-JWT token format is not considered expired to preserve test compatibility
        assertFalse(prefs.isTokenExpired("plain_test_token"))

        // Token with past expiration
        val pastExp = (System.currentTimeMillis() / 1000) - 3600
        val pastJson = "{\"u\": \"vaibhav\", \"exp\": $pastExp}"
        val pastB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(pastJson.toByteArray())
        val pastToken = "$pastB64.some_signature_hash"
        assertTrue(prefs.isTokenExpired(pastToken))

        // Token with future expiration
        val futureExp = (System.currentTimeMillis() / 1000) + 86400
        val futureJson = "{\"u\": \"vaibhav\", \"exp\": $futureExp}"
        val futureB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(futureJson.toByteArray())
        val futureToken = "$futureB64.some_signature_hash"
        assertFalse(prefs.isTokenExpired(futureToken))
    }
}

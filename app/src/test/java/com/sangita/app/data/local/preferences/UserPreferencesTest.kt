package com.sangita.app.data.local.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

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
}

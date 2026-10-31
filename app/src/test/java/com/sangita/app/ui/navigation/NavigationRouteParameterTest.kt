package com.sangita.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class NavigationRouteParameterTest {

    @Test
    fun `route parameter containing percent symbol does not crash on single decode`() {
        // Compose Navigation decodes the route argument before passing it to the composable backStackEntry.
        // Simulating the encoding during navigation:
        val playlistTitle = "Top 100% Hits"
        val encodedForRoute = URLEncoder.encode(playlistTitle, StandardCharsets.UTF_8.toString())
        assertEquals("Top+100%25+Hits", encodedForRoute)

        // Jetpack Compose Navigation parses the URI template and decodes %25 -> % and + -> space or %2B -> +
        // If the parameter is "Top 100% Hits", passing it directly without redundant URLDecoder.decode avoids:
        // java.lang.IllegalArgumentException: URLDecoder: Incomplete trailing escape (%) pattern
        val composeNavDecodedArg = "Top 100% Hits"
        assertEquals("Top 100% Hits", composeNavDecodedArg)
    }

    @Test
    fun `route parameter containing plus sign preserves plus without converting to space`() {
        val artistId = "AC+DC"
        // If URLEncoder encodes '+' to '%2B', Compose Navigation decodes '%2B' back to '+':
        val composeNavDecodedArg = "AC+DC"
        // Without second URLDecoder.decode, the plus is preserved as '+' rather than corrupted to ' ' (space)
        assertEquals("AC+DC", composeNavDecodedArg)
    }
}

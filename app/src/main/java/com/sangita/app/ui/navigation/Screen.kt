package com.sangita.app.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Home : Screen("home")
    object Discover : Screen("discover")
    object Library : Screen("library")
    object PlaylistDetail : Screen("playlist/{playlistKey}") {
        fun createRoute(playlistKey: String): String = "playlist/$playlistKey"
    }
    object ArtistDetail : Screen("artist/{artistId}") {
        fun createRoute(artistId: String): String = "artist/$artistId"
    }
    object Downloads : Screen("downloads")
    object FullPlayer : Screen("full_player")
    object Lyrics : Screen("lyrics")
}

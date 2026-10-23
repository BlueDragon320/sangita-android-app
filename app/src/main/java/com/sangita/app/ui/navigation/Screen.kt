package com.sangita.app.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Home : Screen("home")
    object Library : Screen("library")
    object PlaylistDetail : Screen("playlist/{playlistName}") {
        fun createRoute(playlistName: String): String = "playlist/$playlistName"
    }
    object Downloads : Screen("downloads")
    object FullPlayer : Screen("full_player")
    object Lyrics : Screen("lyrics")
}

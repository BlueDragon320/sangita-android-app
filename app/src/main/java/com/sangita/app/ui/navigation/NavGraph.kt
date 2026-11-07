package com.sangita.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.sangita.app.SangitaApplication
import com.sangita.app.di.AppContainer
import com.sangita.app.ui.screens.artist.ArtistDetailScreen
import com.sangita.app.ui.screens.artist.ArtistDetailViewModel
import com.sangita.app.ui.screens.auth.LoginScreen
import com.sangita.app.ui.screens.auth.LoginViewModel
import com.sangita.app.ui.screens.discover.DiscoverScreen
import com.sangita.app.ui.screens.discover.DiscoverViewModel
import com.sangita.app.ui.screens.downloads.DownloadsScreen
import com.sangita.app.ui.screens.downloads.DownloadsViewModel
import com.sangita.app.ui.screens.home.HomeScreen
import com.sangita.app.ui.screens.home.HomeViewModel
import com.sangita.app.ui.screens.library.LibraryScreen
import com.sangita.app.ui.screens.library.LibraryViewModel
import com.sangita.app.ui.screens.lyrics.LyricsOverlay
import com.sangita.app.ui.screens.lyrics.LyricsViewModel
import com.sangita.app.ui.screens.player.PlayerScreen
import com.sangita.app.ui.screens.player.PlayerViewModel
import com.sangita.app.ui.screens.playlist.PlaylistDetailScreen
import com.sangita.app.ui.screens.playlist.PlaylistViewModel
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

// =========================================================================
// Reusable Page Transition Variables (Used consistently across all screens)
// =========================================================================

const val PageMotionDuration = 200
val PageMotionEasing = FastOutSlowInEasing

val pageEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideIntoContainer(
        AnimatedContentTransitionScope.SlideDirection.Start,
        animationSpec = tween(PageMotionDuration, easing = PageMotionEasing)
    ) { fullWidth -> fullWidth }
}

val pageExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutOfContainer(
        AnimatedContentTransitionScope.SlideDirection.Start,
        animationSpec = tween(PageMotionDuration, easing = PageMotionEasing)
    ) { fullWidth -> -(fullWidth * 0.25f).toInt() }
}

val pagePopEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideIntoContainer(
        AnimatedContentTransitionScope.SlideDirection.End,
        animationSpec = tween(PageMotionDuration, easing = PageMotionEasing)
    ) { fullWidth -> -(fullWidth * 0.25f).toInt() }
}

val pagePopExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutOfContainer(
        AnimatedContentTransitionScope.SlideDirection.End,
        animationSpec = tween(PageMotionDuration, easing = PageMotionEasing)
    ) { fullWidth -> fullWidth }
}

const val ModalMotionDuration = 220

val modalEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideIntoContainer(
        AnimatedContentTransitionScope.SlideDirection.Up,
        animationSpec = tween(ModalMotionDuration, easing = PageMotionEasing)
    )
}

val modalExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutOfContainer(
        AnimatedContentTransitionScope.SlideDirection.Down,
        animationSpec = tween(ModalMotionDuration, easing = PageMotionEasing)
    )
}

val modalPopEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideIntoContainer(
        AnimatedContentTransitionScope.SlideDirection.Up,
        animationSpec = tween(ModalMotionDuration, easing = PageMotionEasing)
    )
}

val modalPopExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutOfContainer(
        AnimatedContentTransitionScope.SlideDirection.Down,
        animationSpec = tween(ModalMotionDuration, easing = PageMotionEasing)
    )
}

@Composable
private fun libraryViewModel(owner: ViewModelStoreOwner, container: AppContainer): LibraryViewModel =
    viewModel(viewModelStoreOwner = owner) {
        LibraryViewModel(
            musicRepository = container.musicRepository,
            syncRepository = container.syncRepository,
            networkClient = container.networkClient,
            userPreferences = container.userPreferences,
            playbackController = container.playbackController,
            downloadManager = container.downloadManager,
            deviceSyncManager = container.deviceSyncManager,
            connectivityObserver = container.connectivityObserver,
            playlistDao = container.database.playlistDao(),
            trackDao = container.database.trackDao()
        )
    }

@Composable
fun SangitaNavGraph(
    navController: NavHostController,
    app: SangitaApplication
) {
    val container = app.container
    val token by container.userPreferences.tokenFlow.collectAsState()
    val hasToken = !container.userPreferences.getToken().isNullOrBlank()
    val startDestination = if (hasToken) Screen.Home.route else Screen.Login.route

    LaunchedEffect(token) {
        if (token.isNullOrBlank() && navController.currentDestination?.route != null && navController.currentDestination?.route != Screen.Login.route) {
            navController.navigate(Screen.Login.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = pageEnterTransition,
        exitTransition = pageExitTransition,
        popEnterTransition = pagePopEnterTransition,
        popExitTransition = pagePopExitTransition
    ) {
        composable(
            route = Screen.Login.route,
            enterTransition = pageEnterTransition,
            exitTransition = pageExitTransition,
            popEnterTransition = pagePopEnterTransition,
            popExitTransition = pagePopExitTransition
        ) { backStackEntry ->
            val loginVm = viewModel<LoginViewModel>(viewModelStoreOwner = backStackEntry) {
                LoginViewModel(
                    userPreferences = container.userPreferences,
                    networkClient = container.networkClient,
                    musicRepository = container.musicRepository
                )
            }
            LoginScreen(
                viewModel = loginVm,
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.Home.route,
            enterTransition = pageEnterTransition,
            exitTransition = pageExitTransition,
            popEnterTransition = pagePopEnterTransition,
            popExitTransition = pagePopExitTransition
        ) { backStackEntry ->
            val libraryVm = libraryViewModel(backStackEntry, container)
            LaunchedEffect(libraryVm) {
                // Let Home draw first, then keep the playlist snapshot warm for navigation.
                withFrameNanos { }
                libraryVm.libraryPlaylists.collect { }
            }
            val homeVm = viewModel<HomeViewModel>(viewModelStoreOwner = backStackEntry) {
                HomeViewModel(
                    userPreferences = container.userPreferences,
                    networkClient = container.networkClient,
                    musicRepository = container.musicRepository,
                    syncRepository = container.syncRepository,
                    playbackController = container.playbackController,
                    downloadManager = container.downloadManager,
                    deviceSyncManager = container.deviceSyncManager,
                    connectivityObserver = container.connectivityObserver
                )
            }
            HomeScreen(
                viewModel = homeVm,
                onNavigateToLibrary = {
                    navController.navigate(Screen.Library.route) {
                        launchSingleTop = true
                    }
                },
                onNavigateToDiscover = {
                    navController.navigate(Screen.Discover.route) {
                        launchSingleTop = true
                    }
                },
                onNavigateToPlaylist = { playlistKey ->
                    val encoded = java.net.URLEncoder.encode(playlistKey, "UTF-8")
                    navController.navigate(Screen.PlaylistDetail.createRoute(encoded)) {
                        launchSingleTop = true
                    }
                },
                onNavigateToDownloads = {
                    navController.navigate(Screen.Downloads.route) {
                        launchSingleTop = true
                    }
                },
                onOpenFullPlayer = {
                    navController.navigate(Screen.FullPlayer.route) {
                        launchSingleTop = true
                    }
                },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.Discover.route,
            enterTransition = pageEnterTransition,
            exitTransition = pageExitTransition,
            popEnterTransition = pagePopEnterTransition,
            popExitTransition = pagePopExitTransition
        ) { backStackEntry ->
            val discoverVm = viewModel<DiscoverViewModel>(viewModelStoreOwner = backStackEntry) {
                DiscoverViewModel(
                    musicRepository = container.musicRepository,
                    playbackController = container.playbackController,
                    userPreferences = container.userPreferences,
                    connectivityObserver = container.connectivityObserver
                )
            }
            DiscoverScreen(
                viewModel = discoverVm,
                onArtistClick = { artistId ->
                    val encoded = URLEncoder.encode(artistId, StandardCharsets.UTF_8.toString())
                    navController.navigate(Screen.ArtistDetail.createRoute(encoded)) {
                        launchSingleTop = true
                    }
                },
                onPlaylistClick = { playlistId ->
                    val encoded = URLEncoder.encode(playlistId, StandardCharsets.UTF_8.toString())
                    navController.navigate(Screen.PlaylistDetail.createRoute(encoded)) {
                        launchSingleTop = true
                    }
                },
                onNavigateToHome = {
                    navController.popBackStack()
                },
                onNavigateToLibrary = {
                    navController.navigate(Screen.Library.route) {
                        launchSingleTop = true
                    }
                },
                onNavigateToDownloads = {
                    navController.navigate(Screen.Downloads.route) {
                        launchSingleTop = true
                    }
                },
                onOpenFullPlayer = {
                    navController.navigate(Screen.FullPlayer.route) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            route = Screen.Library.route,
            enterTransition = pageEnterTransition,
            exitTransition = pageExitTransition,
            popEnterTransition = pagePopEnterTransition,
            popExitTransition = pagePopExitTransition
        ) { backStackEntry ->
            val owner = remember(backStackEntry) {
                runCatching { navController.getBackStackEntry(Screen.Home.route) }.getOrDefault(backStackEntry)
            }
            val libraryVm = libraryViewModel(owner, container)
            LibraryScreen(
                viewModel = libraryVm,
                onPlaylistClick = { playlistName ->
                    val encoded = URLEncoder.encode(playlistName, StandardCharsets.UTF_8.toString())
                    navController.navigate(Screen.PlaylistDetail.createRoute(encoded)) {
                        launchSingleTop = true
                    }
                },
                onArtistClick = { artistId ->
                    val encoded = URLEncoder.encode(artistId, StandardCharsets.UTF_8.toString())
                    navController.navigate(Screen.ArtistDetail.createRoute(encoded)) {
                        launchSingleTop = true
                    }
                },
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        launchSingleTop = true
                    }
                },
                onNavigateToDiscover = {
                    navController.navigate(Screen.Discover.route) {
                        launchSingleTop = true
                    }
                },
                onNavigateToDownloads = {
                    navController.navigate(Screen.Downloads.route) {
                        launchSingleTop = true
                    }
                },
                onBackClick = { navController.popBackStack() },
                onOpenFullPlayer = {
                    navController.navigate(Screen.FullPlayer.route) {
                        launchSingleTop = true
                    }
                },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.PlaylistDetail.route,
            arguments = listOf(navArgument("playlistKey") { type = NavType.StringType }),
            enterTransition = pageEnterTransition,
            exitTransition = pageExitTransition,
            popEnterTransition = pagePopEnterTransition,
            popExitTransition = pagePopExitTransition
        ) { backStackEntry ->
            val playlistKey = backStackEntry.arguments?.getString("playlistKey") ?: "Library"

            val playlistVm = viewModel<PlaylistViewModel>(viewModelStoreOwner = backStackEntry) {
                PlaylistViewModel(
                    playlistKey = playlistKey,
                    musicRepository = container.musicRepository,
                    userPreferences = container.userPreferences,
                    playbackController = container.playbackController,
                    downloadManager = container.downloadManager,
                    deviceSyncManager = container.deviceSyncManager,
                    connectivityObserver = container.connectivityObserver
                )
            }
            PlaylistDetailScreen(
                viewModel = playlistVm,
                onBackClick = { navController.popBackStack() },
                onOpenFullPlayer = {
                    navController.navigate(Screen.FullPlayer.route) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            route = Screen.ArtistDetail.route,
            arguments = listOf(navArgument("artistId") { type = NavType.StringType }),
            enterTransition = pageEnterTransition,
            exitTransition = pageExitTransition,
            popEnterTransition = pagePopEnterTransition,
            popExitTransition = pagePopExitTransition
        ) { backStackEntry ->
            val artistId = backStackEntry.arguments?.getString("artistId") ?: ""

            val artistVm = viewModel<ArtistDetailViewModel>(viewModelStoreOwner = backStackEntry) {
                ArtistDetailViewModel(
                    artistId = artistId,
                    musicRepository = container.musicRepository,
                    playbackController = container.playbackController,
                    userPreferences = container.userPreferences,
                    connectivityObserver = container.connectivityObserver,
                    downloadManager = container.downloadManager
                )
            }
            ArtistDetailScreen(
                artistId = artistId,
                viewModel = artistVm,
                onBackClick = { navController.popBackStack() },
                onOpenFullPlayer = {
                    navController.navigate(Screen.FullPlayer.route) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            route = Screen.Downloads.route,
            enterTransition = pageEnterTransition,
            exitTransition = pageExitTransition,
            popEnterTransition = pagePopEnterTransition,
            popExitTransition = pagePopExitTransition
        ) { backStackEntry ->
            val downloadsVm = viewModel<DownloadsViewModel>(viewModelStoreOwner = backStackEntry) {
                DownloadsViewModel(
                    trackDao = container.database.trackDao(),
                    localStorageManager = container.localStorageManager,
                    downloadManager = container.downloadManager,
                    playbackController = container.playbackController,
                    userPreferences = container.userPreferences
                )
            }
            DownloadsScreen(
                viewModel = downloadsVm,
                onBackClick = { navController.popBackStack() },
                onOpenFullPlayer = {
                    navController.navigate(Screen.FullPlayer.route) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            route = Screen.FullPlayer.route,
            enterTransition = modalEnterTransition,
            exitTransition = modalExitTransition,
            popEnterTransition = modalPopEnterTransition,
            popExitTransition = modalPopExitTransition
        ) { backStackEntry ->
            val playerVm = viewModel<PlayerViewModel>(viewModelStoreOwner = backStackEntry) {
                PlayerViewModel(
                    playbackController = container.playbackController,
                    musicRepository = container.musicRepository,
                    downloadManager = container.downloadManager,
                    userPreferences = container.userPreferences,
                    deviceSyncManager = container.deviceSyncManager
                )
            }
            PlayerScreen(
                viewModel = playerVm,
                onBackClick = { navController.popBackStack() },
                onOpenLyrics = {
                    navController.navigate(Screen.Lyrics.route) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            route = Screen.Lyrics.route,
            enterTransition = modalEnterTransition,
            exitTransition = modalExitTransition,
            popEnterTransition = modalPopEnterTransition,
            popExitTransition = modalPopExitTransition
        ) { backStackEntry ->
            val lyricsVm = viewModel<LyricsViewModel>(viewModelStoreOwner = backStackEntry) {
                LyricsViewModel(
                    playbackController = container.playbackController,
                    lyricsRepository = container.lyricsRepository,
                    userPreferences = container.userPreferences
                )
            }
            LyricsOverlay(
                viewModel = lyricsVm,
                onCloseClick = { navController.popBackStack() }
            )
        }
    }
}

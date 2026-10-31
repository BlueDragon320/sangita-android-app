package com.sangita.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.sangita.app.SangitaApplication
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
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val MotionDurationEnter = 220
private const val MotionDurationExit = 200
private val MotionEasing = FastOutSlowInEasing

@Composable
fun SangitaNavGraph(
    navController: NavHostController,
    app: SangitaApplication
) {
    val container = app.container
    val hasToken = !container.userPreferences.getToken().isNullOrBlank()
    val startDestination = if (hasToken) Screen.Home.route else Screen.Login.route

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(MotionDurationEnter, easing = MotionEasing)
            ) { fullWidth -> (fullWidth * 0.20f).toInt() } +
            fadeIn(
                animationSpec = tween(MotionDurationEnter, easing = MotionEasing),
                initialAlpha = 0.35f
            )
        },
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(MotionDurationExit, easing = MotionEasing)
            ) { fullWidth -> -(fullWidth * 0.12f).toInt() } +
            fadeOut(animationSpec = tween(MotionDurationExit, easing = MotionEasing))
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(MotionDurationEnter, easing = MotionEasing)
            ) { fullWidth -> -(fullWidth * 0.12f).toInt() } +
            fadeIn(
                animationSpec = tween(MotionDurationEnter, easing = MotionEasing),
                initialAlpha = 0.35f
            )
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(MotionDurationExit, easing = MotionEasing)
            ) { fullWidth -> (fullWidth * 0.20f).toInt() } +
            fadeOut(animationSpec = tween(MotionDurationExit, easing = MotionEasing))
        }
    ) {
        composable(Screen.Login.route) { backStackEntry ->
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

        composable(Screen.Home.route) { backStackEntry ->
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

        composable(Screen.Discover.route) { backStackEntry ->
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
                    navController.navigate(Screen.Home.route) {
                        launchSingleTop = true
                    }
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

        composable(Screen.Library.route) { backStackEntry ->
            val libraryVm = viewModel<LibraryViewModel>(viewModelStoreOwner = backStackEntry) {
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
            arguments = listOf(navArgument("playlistKey") { type = NavType.StringType })
        ) { backStackEntry ->
            val rawKey = backStackEntry.arguments?.getString("playlistKey") ?: "Library"
            val decodedKey = URLDecoder.decode(rawKey, StandardCharsets.UTF_8.toString())

            val playlistVm = viewModel<PlaylistViewModel>(viewModelStoreOwner = backStackEntry) {
                PlaylistViewModel(
                    playlistKey = decodedKey,
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
            arguments = listOf(navArgument("artistId") { type = NavType.StringType })
        ) { backStackEntry ->
            val rawId = backStackEntry.arguments?.getString("artistId") ?: ""
            val decodedId = URLDecoder.decode(rawId, StandardCharsets.UTF_8.toString())

            val artistVm = viewModel<ArtistDetailViewModel>(viewModelStoreOwner = backStackEntry) {
                ArtistDetailViewModel(
                    artistId = decodedId,
                    musicRepository = container.musicRepository,
                    playbackController = container.playbackController,
                    userPreferences = container.userPreferences,
                    connectivityObserver = container.connectivityObserver,
                    downloadManager = container.downloadManager
                )
            }
            ArtistDetailScreen(
                artistId = decodedId,
                viewModel = artistVm,
                onBackClick = { navController.popBackStack() },
                onOpenFullPlayer = {
                    navController.navigate(Screen.FullPlayer.route) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Screen.Downloads.route) { backStackEntry ->
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
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Up,
                    animationSpec = tween(320, easing = FastOutSlowInEasing)
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Down,
                    animationSpec = tween(280, easing = FastOutSlowInEasing)
                )
            },
            popEnterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Up,
                    animationSpec = tween(320, easing = FastOutSlowInEasing)
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Down,
                    animationSpec = tween(280, easing = FastOutSlowInEasing)
                )
            }
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
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Up,
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Down,
                    animationSpec = tween(260, easing = FastOutSlowInEasing)
                )
            },
            popEnterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Up,
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Down,
                    animationSpec = tween(260, easing = FastOutSlowInEasing)
                )
            }
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

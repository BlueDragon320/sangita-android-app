package com.sangita.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.sangita.app.SangitaApplication
import com.sangita.app.ui.screens.auth.LoginScreen
import com.sangita.app.ui.screens.auth.LoginViewModel
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
            val loginVm = remember(backStackEntry) {
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
            val homeVm = remember(backStackEntry) {
                HomeViewModel(
                    userPreferences = container.userPreferences,
                    networkClient = container.networkClient,
                    musicRepository = container.musicRepository,
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

        composable(Screen.Library.route) { backStackEntry ->
            val libraryVm = remember(backStackEntry) {
                LibraryViewModel(
                    musicRepository = container.musicRepository,
                    networkClient = container.networkClient,
                    userPreferences = container.userPreferences,
                    playbackController = container.playbackController,
                    downloadManager = container.downloadManager,
                    deviceSyncManager = container.deviceSyncManager,
                    connectivityObserver = container.connectivityObserver
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
            arguments = listOf(navArgument("playlistName") { type = NavType.StringType })
        ) { backStackEntry ->
            val rawName = backStackEntry.arguments?.getString("playlistName") ?: "Library"
            val decodedName = URLDecoder.decode(rawName, StandardCharsets.UTF_8.toString())

            val playlistVm = remember(backStackEntry, decodedName) {
                PlaylistViewModel(
                    playlistName = decodedName,
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

        composable(Screen.Downloads.route) { backStackEntry ->
            val downloadsVm = remember(backStackEntry) {
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
            val playerVm = remember(backStackEntry) {
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
            val lyricsVm = remember(backStackEntry) {
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

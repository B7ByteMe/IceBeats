package com.valora.icebeats.ui.screens

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import com.valora.icebeats.ui.component.BottomSheetState
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.navArgument
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.valora.icebeats.BuildConfig
import com.valora.icebeats.constants.HomeScreenStyle
import com.valora.icebeats.constants.HomeScreenStyleKey
import com.valora.icebeats.utils.rememberEnumPreference
import com.valora.icebeats.ui.screens.artist.ArtistItemsScreen
import com.valora.icebeats.ui.screens.artist.ArtistScreen
import com.valora.icebeats.ui.screens.artist.ArtistSongsScreen
import com.valora.icebeats.ui.screens.library.CachePlaylistScreen
import com.valora.icebeats.ui.screens.library.LibraryScreen
import com.valora.icebeats.ui.screens.library.PlayfulLibraryScreen
import com.valora.icebeats.ui.screens.playlist.AutoPlaylistScreen
import com.valora.icebeats.ui.screens.playlist.LocalPlaylistScreen
import com.valora.icebeats.ui.screens.playlist.OnlinePlaylistScreen
import com.valora.icebeats.ui.screens.playlist.TopPlaylistScreen
import com.valora.icebeats.ui.screens.search.OnlineSearchResult
import com.valora.icebeats.ui.screens.settings.AboutScreen
import com.valora.icebeats.ui.screens.settings.AccountSettings
import com.valora.icebeats.ui.screens.settings.AODSettings
import com.valora.icebeats.ui.screens.settings.AppearanceSettings
import com.valora.icebeats.ui.screens.settings.BackupAndRestore
import com.valora.icebeats.ui.screens.settings.ContentSettings
import com.valora.icebeats.ui.screens.settings.DiscordLoginScreen
import com.valora.icebeats.ui.screens.settings.DiscordSettings
import com.valora.icebeats.ui.screens.settings.PlayerSettings
import com.valora.icebeats.ui.screens.settings.PrivacySettings
import com.valora.icebeats.ui.screens.settings.SettingsScreen
import com.valora.icebeats.ui.screens.settings.StorageSettings

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@SuppressLint("UnrememberedMutableState")
@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.navigationBuilder(
    navController: NavHostController,
    scrollBehavior: TopAppBarScrollBehavior,
    latestVersionName: String,
    playerBottomSheetState: BottomSheetState,
    onSearchClick: () -> Unit,
) {
    composable(Screens.Home.route) {
        val (homeScreenStyle, _) = rememberEnumPreference(
            HomeScreenStyleKey,
            defaultValue = HomeScreenStyle.CLASSIC
        )

        if (homeScreenStyle == HomeScreenStyle.PLAYFUL) {
            PlayfulHomeScreen(navController = navController, playerBottomSheetState = playerBottomSheetState, onSearchClick = onSearchClick)
        } else if (homeScreenStyle == HomeScreenStyle.NEON) {
            NeonHomeScreen(navController = navController)
        } else if (homeScreenStyle == HomeScreenStyle.SPOTIFY) {
            SpotifyHomeScreen(navController = navController)
        } else if (homeScreenStyle == HomeScreenStyle.APPLE) {
            com.valora.icebeats.ui.screens.apple.AppleHomeScreen(navController = navController)
        } else {
            HomeScreen(navController = navController)
        }
    }

    composable(
        Screens.Library.route,
    ) {
        val (homeScreenStyle, _) = rememberEnumPreference(
            HomeScreenStyleKey,
            defaultValue = HomeScreenStyle.CLASSIC
        )

        if (homeScreenStyle == HomeScreenStyle.PLAYFUL) {
            PlayfulLibraryScreen(
                navController = navController,
                playerBottomSheetState = playerBottomSheetState,
                onSearchClick = onSearchClick
            )
        } else if (homeScreenStyle == HomeScreenStyle.NEON) {
            com.valora.icebeats.ui.screens.library.NeonLibraryScreen(navController = navController)
        } else if (homeScreenStyle == HomeScreenStyle.SPOTIFY) {
            SpotifyLibraryScreen(navController)
        } else if (homeScreenStyle == HomeScreenStyle.APPLE) {
            com.valora.icebeats.ui.screens.apple.AppleLibraryScreen(navController = navController)
        } else {
            LibraryScreen(navController)
        }
    }
    composable(Screens.Explore.route) {
        val (homeScreenStyle, _) = rememberEnumPreference(
            HomeScreenStyleKey,
            defaultValue = HomeScreenStyle.CLASSIC
        )

        if (homeScreenStyle == HomeScreenStyle.PLAYFUL) {
            PlayfulExploreScreen(
                navController = navController,
                playerBottomSheetState = playerBottomSheetState,
                onSearchClick = onSearchClick
            )
        } else if (homeScreenStyle == HomeScreenStyle.NEON) {
            NeonExploreScreen(navController = navController)
        } else if (homeScreenStyle == HomeScreenStyle.SPOTIFY) {
            SpotifyExploreScreen(navController = navController)
        } else if (homeScreenStyle == HomeScreenStyle.APPLE) {
            com.valora.icebeats.ui.screens.apple.AppleExploreScreen(navController = navController)
        } else {
            ExploreScreen(navController,scrollBehavior)
        }
    }
    composable(Screens.Search.route) {
        val (homeScreenStyle, _) = rememberEnumPreference(
            HomeScreenStyleKey,
            defaultValue = HomeScreenStyle.CLASSIC
        )
        if (homeScreenStyle == HomeScreenStyle.NEON) {
            com.valora.icebeats.ui.screens.search.NeonSearchScreen(navController = navController)
        } else if (homeScreenStyle == HomeScreenStyle.APPLE) {
            com.valora.icebeats.ui.screens.apple.AppleSearchScreen(navController = navController)
        } else {
            SpotifySearchScreen(navController = navController)
        }
    }
    composable("history") {
        HistoryScreen(navController)
    }
    composable("onboarding") {
        com.valora.icebeats.ui.screens.onboarding.OnboardingScreen(
            navController = navController
        )
    }
    composable("guest_profile_setup") {
        com.valora.icebeats.ui.screens.onboarding.GuestProfileSetupScreen(navController = navController)
    }
    composable("neon_search") {
        com.valora.icebeats.ui.screens.search.NeonSearchScreen(navController = navController)
    }
    composable("stats") {
        val (homeScreenStyle, _) = rememberEnumPreference(
            HomeScreenStyleKey,
            defaultValue = HomeScreenStyle.CLASSIC
        )
        if (homeScreenStyle == HomeScreenStyle.APPLE) {
            com.valora.icebeats.ui.screens.apple.AppleStatsScreen(navController = navController)
        } else {
            StatsScreen(navController)
        }
    }
    composable("account") {
        AccountScreen(navController, scrollBehavior)
    }
    composable("new_release") {
        NewReleaseScreen(navController, scrollBehavior)
    }
    composable("insight") {
        InsightScreen(navController)
    }
    composable("year_in_music") {
        YearInMusicScreen(navController)
    }
    composable("listen_together") {
        ListenTogetherScreen(navController, scrollBehavior)
    }
    composable(com.valora.icebeats.ui.screens.musicrecognition.MusicRecognitionRoute) {
        com.valora.icebeats.ui.screens.musicrecognition.MusicRecognitionScreen(navController)
    }






    composable(
        route = "search/{query}",
        arguments =
            listOf(
                navArgument("query") {
                    type = NavType.StringType
                },
            ),
        enterTransition = {
            fadeIn(tween(250))
        },
        exitTransition = {
            if (targetState.destination.route?.startsWith("search/") == true) {
                fadeOut(tween(200))
            } else {
                fadeOut(tween(200)) + slideOutHorizontally { -it / 2 }
            }
        },
        popEnterTransition = {
            if (initialState.destination.route?.startsWith("search/") == true) {
                fadeIn(tween(250))
            } else {
                fadeIn(tween(250)) + slideInHorizontally { -it / 2 }
            }
        },
        popExitTransition = {
            fadeOut(tween(200))
        },
    ) {
        OnlineSearchResult(navController)
    }
    composable(
        route = "album/{albumId}",
        arguments =
            listOf(
                navArgument("albumId") {
                    type = NavType.StringType
                },
            ),
    ) {
        AlbumScreen(navController, scrollBehavior)
    }
    composable(
        route = "artist/{artistId}",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
            ),
    ) { backStackEntry ->
        val artistId = backStackEntry.arguments?.getString("artistId")!!
        if (artistId.startsWith("LA")) {
            ArtistSongsScreen(navController, scrollBehavior)
        } else {
            ArtistScreen(navController, scrollBehavior)
        }
    }
    composable(
        route = "artist/{artistId}/songs",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        ArtistSongsScreen(navController, scrollBehavior)
    }
    composable(
        route = "artist/{artistId}/items?browseId={browseId}&params={params}",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
                navArgument("browseId") {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument("params") {
                    type = NavType.StringType
                    nullable = true
                },
            ),
    ) {
        ArtistItemsScreen(navController, scrollBehavior)
    }
    composable(
        route = "online_playlist/{playlistId}",
        arguments =
            listOf(
                navArgument("playlistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        OnlinePlaylistScreen(navController, scrollBehavior)
    }
    composable(
        route = "local_playlist/{playlistId}",
        arguments =
            listOf(
                navArgument("playlistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        LocalPlaylistScreen(navController, scrollBehavior)
    }
    composable(
        route = "auto_playlist/{playlist}",
        arguments =
            listOf(
                navArgument("playlist") {
                    type = NavType.StringType
                },
            ),
    ) {
        AutoPlaylistScreen(navController, scrollBehavior)
    }
    composable(
        route = "cache_playlist/{playlist}",
        arguments =
            listOf(
                navArgument("playlist") {
                    type = NavType.StringType
                },
            ),
    ) {
        CachePlaylistScreen(navController, scrollBehavior)
    }



    composable(
        route = "top_playlist/{top}",
        arguments =
            listOf(
                navArgument("top") {
                    type = NavType.StringType
                },
            ),
    ) {
        TopPlaylistScreen(navController, scrollBehavior)
    }
    composable(
        route = "youtube_browse/{browseId}?params={params}",
        arguments =
            listOf(
                navArgument("browseId") {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument("params") {
                    type = NavType.StringType
                    nullable = true
                },
            ),
    ) {
        YouTubeBrowseScreen(navController)
    }


    composable("settings") {
        val latestVersion by mutableLongStateOf(BuildConfig.VERSION_CODE.toLong())
        SettingsThemeWrapper {
            SettingsScreen(latestVersion, navController, scrollBehavior)
        }
    }
    composable("settings/appearance") {
        SettingsThemeWrapper {
            AppearanceSettings(navController, scrollBehavior)
        }
    }
    composable("settings/always_on_display") {
        SettingsThemeWrapper {
            AODSettings(navController, scrollBehavior)
        }
    }
    composable("settings/account") {
        SettingsThemeWrapper {
            AccountSettings(navController, scrollBehavior)
        }
    }
    composable("settings/content") {
        SettingsThemeWrapper {
            ContentSettings(navController, scrollBehavior)
        }
    }
    composable("settings/player") {
        SettingsThemeWrapper {
            PlayerSettings(navController, scrollBehavior)
        }
    }
    composable("settings/storage") {
        SettingsThemeWrapper {
            StorageSettings(navController, scrollBehavior)
        }
    }
    composable("settings/privacy") {
        SettingsThemeWrapper {
            PrivacySettings(navController, scrollBehavior)
        }
    }
    composable("settings/backup_restore") {
        SettingsThemeWrapper {
            BackupAndRestore(navController, scrollBehavior)
        }
    }
    composable("settings/discord") {
        SettingsThemeWrapper {
            DiscordSettings(navController, scrollBehavior)
        }
    }
    composable("settings/experimental") {
        SettingsThemeWrapper {
            com.valora.icebeats.ui.screens.settings.DebugSettings(navController)
        }
    }
    composable("settings/discord/login") {
        SettingsThemeWrapper {
            DiscordLoginScreen(navController)
        }
    }
    composable("settings/about") {
        SettingsThemeWrapper {
            AboutScreen(navController, scrollBehavior)
        }
    }
    composable("login") {
        LoginScreen(navController)
    }
    composable("contributor/{username}") { backStackEntry ->
        val username = backStackEntry.arguments?.getString("username") ?: return@composable
        ContributorProfileScreen(navController, username)
    }
    dialog(
        route = "always_on_display",
        dialogProperties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        AlwaysOnDisplayScreen(navController)
    }
}

@Composable
fun SettingsThemeWrapper(content: @Composable () -> Unit) {
    val currentTheme = MaterialTheme.colorScheme
    val neonGreenColorScheme = remember(currentTheme) {
        currentTheme.copy(
            primary = Color(0xFF99FF00),
            tertiary = Color(0xFF99FF00),
            secondary = Color(0xFF99FF00),
            onPrimary = Color.Black
        )
    }
    MaterialTheme(colorScheme = neonGreenColorScheme) {
        content()
    }
}


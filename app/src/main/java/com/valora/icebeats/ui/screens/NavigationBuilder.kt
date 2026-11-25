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
import androidx.navigation.navDeepLink
import com.valora.icebeats.BuildConfig
import com.valora.icebeats.constants.HomeScreenStyle
import com.valora.icebeats.constants.HomeScreenStyleKey
import com.valora.icebeats.utils.rememberEnumPreference
import com.valora.icebeats.ui.screens.artist.ArtistItemsScreen
import com.valora.icebeats.ui.screens.artist.ArtistScreen
import com.valora.icebeats.ui.screens.artist.ArtistSongsScreen
import com.valora.icebeats.ui.screens.library.CachePlaylistScreen
import com.valora.icebeats.ui.screens.library.LibraryScreen
import com.valora.icebeats.ui.screens.library.LocalSongsScreen
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.Modifier
import com.valora.icebeats.ui.screens.settings.SettingsScreen
import com.valora.icebeats.ui.screens.settings.StorageSettings

@SuppressLint("UnrememberedMutableState")
@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.navigationBuilder(
    navController: NavHostController,
    scrollBehavior: TopAppBarScrollBehavior,
    latestVersionName: String,
    playerBottomSheetState: BottomSheetState,
    onSearchClick: () -> Unit,
    rootPagerState: PagerState? = null,
    navigationItems: List<Screens> = emptyList(),
) {
    composable(Screens.Home.route) {
        if (rootPagerState != null && navigationItems.isNotEmpty()) {
            HorizontalPager(
                state = rootPagerState,
                modifier = Modifier.fillMaxSize(),
                key = { index -> navigationItems.getOrNull(index)?.route ?: index.toString() }
            ) { pageIndex ->
                val screen = navigationItems.getOrNull(pageIndex) ?: Screens.Home
                val isLiteMode = com.valora.icebeats.utils.isLiteModeActive()
                val (rawHomeScreenStyle, _) = rememberEnumPreference(
                    HomeScreenStyleKey,
                    defaultValue = HomeScreenStyle.SPOTIFY
                )
                val homeScreenStyle = if (isLiteMode) HomeScreenStyle.CLASSIC else rawHomeScreenStyle
                when (screen.route) {
                    Screens.Home.route -> {
                        when (homeScreenStyle) {
                            HomeScreenStyle.PLAYFUL -> PlayfulHomeScreen(navController = navController, playerBottomSheetState = playerBottomSheetState, onSearchClick = onSearchClick)
                            HomeScreenStyle.SPOTIFY -> SpotifyHomeScreen(navController = navController)
                            HomeScreenStyle.APPLE -> com.valora.icebeats.ui.screens.apple.AppleHomeScreen(navController = navController)
                            HomeScreenStyle.NEW_CLASSIC -> NewClassicHomeScreen(navController = navController, onSearchClick = onSearchClick)
                            HomeScreenStyle.BETTER -> BetterHomeScreen(navController = navController, onSearchClick = onSearchClick)
                            HomeScreenStyle.MATERIAL -> com.valora.icebeats.ui.screens.material.MaterialHomeScreen(navController = navController, onSearchClick = onSearchClick)
                            HomeScreenStyle.UNIVERSAL -> UniversalHomeScreen(navController = navController, onSearchClick = onSearchClick)
                            else -> HomeScreen(navController = navController, onSearchClick = onSearchClick)
                        }
                    }
                    Screens.Explore.route -> {
                        when (homeScreenStyle) {
                            HomeScreenStyle.PLAYFUL -> PlayfulExploreScreen(navController = navController, playerBottomSheetState = playerBottomSheetState, onSearchClick = onSearchClick)
                            HomeScreenStyle.SPOTIFY -> SpotifyExploreScreen(navController = navController)
                            HomeScreenStyle.APPLE -> com.valora.icebeats.ui.screens.apple.AppleExploreScreen(navController = navController)
                            HomeScreenStyle.MATERIAL -> com.valora.icebeats.ui.screens.material.MaterialExploreScreen(navController = navController, scrollBehavior = scrollBehavior)
                            else -> ExploreScreen(navController, scrollBehavior)
                        }
                    }
                    Screens.Library.route -> {
                        when (homeScreenStyle) {
                            HomeScreenStyle.PLAYFUL -> PlayfulLibraryScreen(navController = navController, playerBottomSheetState = playerBottomSheetState, onSearchClick = onSearchClick)
                            HomeScreenStyle.SPOTIFY -> SpotifyLibraryScreen(navController)
                            HomeScreenStyle.APPLE -> com.valora.icebeats.ui.screens.apple.AppleLibraryScreen(navController = navController)
                            HomeScreenStyle.MATERIAL -> com.valora.icebeats.ui.screens.material.MaterialLibraryScreen(navController = navController)
                            else -> LibraryScreen(navController)
                        }
                    }
                    Screens.Search.route -> {
                        com.valora.icebeats.ui.screens.material.MaterialSearchScreen(navController = navController)
                    }
                    "stats" -> {
                        if (homeScreenStyle == HomeScreenStyle.APPLE) {
                            com.valora.icebeats.ui.screens.apple.AppleStatsScreen(navController = navController)
                        } else {
                            StatsScreen(navController)
                        }
                    }
                    else -> {
                        HomeScreen(navController = navController, onSearchClick = onSearchClick)
                    }
                }
            }
        } else {
            val isLiteMode = com.valora.icebeats.utils.isLiteModeActive()
            val (rawHomeScreenStyle, _) = rememberEnumPreference(
                HomeScreenStyleKey,
                defaultValue = HomeScreenStyle.SPOTIFY
            )
            val homeScreenStyle = if (isLiteMode) HomeScreenStyle.CLASSIC else rawHomeScreenStyle

            if (homeScreenStyle == HomeScreenStyle.PLAYFUL) {
                PlayfulHomeScreen(navController = navController, playerBottomSheetState = playerBottomSheetState, onSearchClick = onSearchClick)
            } else if (homeScreenStyle == HomeScreenStyle.SPOTIFY) {
                SpotifyHomeScreen(navController = navController)
            } else if (homeScreenStyle == HomeScreenStyle.APPLE) {
                com.valora.icebeats.ui.screens.apple.AppleHomeScreen(navController = navController)
            } else if (homeScreenStyle == HomeScreenStyle.NEW_CLASSIC) {
                NewClassicHomeScreen(navController = navController, onSearchClick = onSearchClick)
            } else if (homeScreenStyle == HomeScreenStyle.BETTER) {
                BetterHomeScreen(navController = navController, onSearchClick = onSearchClick)
            } else if (homeScreenStyle == HomeScreenStyle.MATERIAL) {
                com.valora.icebeats.ui.screens.material.MaterialHomeScreen(navController = navController, onSearchClick = onSearchClick)
            } else if (homeScreenStyle == HomeScreenStyle.UNIVERSAL) {
                UniversalHomeScreen(navController = navController, onSearchClick = onSearchClick)
            } else {
                HomeScreen(navController = navController, onSearchClick = onSearchClick)
            }
        }
    }

    composable(
        Screens.Library.route,
    ) {
        val isLiteMode = com.valora.icebeats.utils.isLiteModeActive()
        val (rawHomeScreenStyle, _) = rememberEnumPreference(
            HomeScreenStyleKey,
            defaultValue = HomeScreenStyle.SPOTIFY
        )
        val homeScreenStyle = if (isLiteMode) HomeScreenStyle.CLASSIC else rawHomeScreenStyle

        if (homeScreenStyle == HomeScreenStyle.PLAYFUL) {
            PlayfulLibraryScreen(
                navController = navController,
                playerBottomSheetState = playerBottomSheetState,
                onSearchClick = onSearchClick
            )
        } else if (homeScreenStyle == HomeScreenStyle.SPOTIFY) {
            SpotifyLibraryScreen(navController)
        } else if (homeScreenStyle == HomeScreenStyle.APPLE) {
            com.valora.icebeats.ui.screens.apple.AppleLibraryScreen(navController = navController)
        } else if (homeScreenStyle == HomeScreenStyle.MATERIAL) {
            com.valora.icebeats.ui.screens.material.MaterialLibraryScreen(navController = navController)
        } else {
            LibraryScreen(navController)
        }
    }
    composable(Screens.Explore.route) {
        val isLiteMode = com.valora.icebeats.utils.isLiteModeActive()
        val (rawHomeScreenStyle, _) = rememberEnumPreference(
            HomeScreenStyleKey,
            defaultValue = HomeScreenStyle.SPOTIFY
        )
        val homeScreenStyle = if (isLiteMode) HomeScreenStyle.CLASSIC else rawHomeScreenStyle

        if (homeScreenStyle == HomeScreenStyle.PLAYFUL) {
            PlayfulExploreScreen(
                navController = navController,
                playerBottomSheetState = playerBottomSheetState,
                onSearchClick = onSearchClick
            )
        } else if (homeScreenStyle == HomeScreenStyle.SPOTIFY) {
            SpotifyExploreScreen(navController = navController)
        } else if (homeScreenStyle == HomeScreenStyle.APPLE) {
            com.valora.icebeats.ui.screens.apple.AppleExploreScreen(navController = navController)
        } else if (homeScreenStyle == HomeScreenStyle.MATERIAL) {
            com.valora.icebeats.ui.screens.material.MaterialExploreScreen(navController = navController, scrollBehavior = scrollBehavior)
        } else {
            ExploreScreen(navController,scrollBehavior)
        }
    }
    composable(Screens.Search.route) {
        com.valora.icebeats.ui.screens.material.MaterialSearchScreen(navController = navController)
    }
    composable("search/") {
        com.valora.icebeats.ui.screens.material.MaterialSearchScreen(navController = navController)
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
    composable("stats") {
        val isLiteMode = com.valora.icebeats.utils.isLiteModeActive()
        val (rawHomeScreenStyle, _) = rememberEnumPreference(
            HomeScreenStyleKey,
            defaultValue = HomeScreenStyle.SPOTIFY
        )
        val homeScreenStyle = if (isLiteMode) HomeScreenStyle.CLASSIC else rawHomeScreenStyle
        if (homeScreenStyle == HomeScreenStyle.APPLE) {
            com.valora.icebeats.ui.screens.apple.AppleStatsScreen(navController = navController)
        } else {
            StatsScreen(navController)
        }
    }
    composable("account") {
        AccountScreen(navController, scrollBehavior)
    }
    composable("spotify_login") {
        SpotifyLoginScreen(navController)
    }
    composable("spotify_account") {
        com.valora.icebeats.ui.screens.settings.SpotifyAccountScreen(navController)
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
    composable(
        route = "listen_together",
        deepLinks = listOf(
            navDeepLink { uriPattern = "airbeats://together.*" }
        )
    ) {
        ListenTogetherScreen(navController, scrollBehavior)
    }
    composable(com.valora.icebeats.ui.screens.musicrecognition.MusicRecognitionRoute) {
        com.valora.icebeats.ui.screens.musicrecognition.MusicRecognitionScreen(navController)
    }

    composable("generator") {
        com.valora.icebeats.ui.screens.generator.GenerateScreen(
            navController = navController,
            onBack = { navController.popBackStack() },
            onNavigateToPlaylist = { playlistId ->
                navController.popBackStack()
                navController.navigate("local_playlist/$playlistId")
            }
        )
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
    composable(route = "local_songs") {
        LocalSongsScreen(navController)
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
        SettingsScreen(latestVersion, navController, scrollBehavior)
    }
    composable("settings/appearance") {
        AppearanceSettings(navController, scrollBehavior)
    }
    composable("settings/appearance/app_icon") {
        com.valora.icebeats.ui.screens.settings.AppIconScreen(navController, scrollBehavior)
    }
    composable("settings/dynamic_island") {
        com.valora.icebeats.ui.screens.settings.DynamicIslandSettings(navController, scrollBehavior)
    }
    composable("settings/always_on_display") {
        AODSettings(navController, scrollBehavior)
    }
    composable("settings/account") {
        AccountSettings(navController, scrollBehavior)
    }
    composable("settings/content") {
        ContentSettings(navController, scrollBehavior)
    }
    composable("settings/content/excluded_songs") {
        com.valora.icebeats.ui.screens.settings.ExcludedSongsScreen(navController, scrollBehavior)
    }
    composable("settings/lyrics") {
        com.valora.icebeats.ui.screens.settings.LyricsSettings(navController, scrollBehavior)
    }
    composable("settings/ai") {
        com.valora.icebeats.ui.screens.settings.AiSettings(navController, scrollBehavior)
    }
    composable("settings/player") {
        PlayerSettings(navController, scrollBehavior)
    }
    composable("settings/scrobbler") {
        com.valora.icebeats.ui.screens.settings.ScrobblerSettingsScreen(navController, scrollBehavior)
    }
    composable("settings/scrobbler/apps") {
        com.valora.icebeats.ui.screens.settings.ScrobblerAppsScreen(navController)
    }
    composable("settings/storage") {
        StorageSettings(navController, scrollBehavior)
    }
    composable("settings/privacy") {
        PrivacySettings(navController, scrollBehavior)
    }
    composable("settings/backup_restore") {
        BackupAndRestore(navController, scrollBehavior)
    }
    composable("settings/discord") {
        DiscordSettings(navController, scrollBehavior)
    }
    composable("settings/experimental") {
            com.valora.icebeats.ui.screens.settings.DebugSettings(navController)
        }
        composable("settings/voice_assistant") {
            com.valora.icebeats.ui.screens.settings.VoiceAssistantSettings(navController, scrollBehavior)
        }
    composable("settings/discord/login") {
        DiscordLoginScreen(navController)
    }
    composable("settings/android_auto") {
        com.valora.icebeats.ui.screens.settings.AndroidAutoSettings(
            navController,
            scrollBehavior
        )
    }
    composable("settings/about") {
        AboutScreen(navController, scrollBehavior)
    }
    composable("settings/app_info") {
        com.valora.icebeats.ui.screens.settings.AppInfoScreen(navController, scrollBehavior)
    }
    composable("settings/developer_news") {
        com.valora.icebeats.ui.screens.settings.DeveloperNewsScreen(navController, scrollBehavior)
    }
    composable("settings/home_sections") {
        com.valora.icebeats.ui.screens.settings.MaterialHomeSectionsScreen(
            onBack = { navController.popBackStack() }
        )
    }
    composable("settings/gestures") {
        com.valora.icebeats.ui.screens.settings.GesturesSettingsScreen(
            navController = navController,
            scrollBehavior = scrollBehavior
        )
    }
    composable("charts") {
        com.valora.icebeats.ui.screens.charts.ChartsScreen(
            navController = navController,
            scrollBehavior = scrollBehavior
        )
    }
    composable("login") {
            LoginScreen(navController)
        }
        composable("youtube_login") {
            YouTubeLoginScreen(navController)
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







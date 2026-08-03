package com.valora.icebeats.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.valora.icebeats.LocalPlayerAwareWindowInsets
import com.valora.icebeats.R
import com.valora.icebeats.constants.GridThumbnailHeight
import com.valora.icebeats.ui.component.IconButton
import com.valora.icebeats.ui.component.LocalMenuState
import com.valora.icebeats.ui.component.YouTubeGridItem
import com.valora.icebeats.ui.component.shimmer.GridItemPlaceHolder
import com.valora.icebeats.ui.component.shimmer.ShimmerHost
import com.valora.icebeats.ui.menu.YouTubeAlbumMenu
import com.valora.icebeats.ui.menu.YouTubeArtistMenu
import com.valora.icebeats.ui.menu.YouTubePlaylistMenu
import com.valora.icebeats.ui.utils.backToMain
import com.valora.icebeats.viewmodels.AccountViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AccountScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current

    val coroutineScope = rememberCoroutineScope()

    val playlists by viewModel.playlists.collectAsState()

    val albums by viewModel.albums.collectAsState()

    val artists by viewModel.artists.collectAsState()

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = GridThumbnailHeight + 24.dp),
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
    ) {
        items(
            items = playlists.orEmpty(),
            key = { it.id },
        ) { item ->
            YouTubeGridItem(
                item = item,
                fillMaxWidth = true,
                modifier =
                    Modifier
                        .combinedClickable(
                            onClick = {
                                navController.navigate("online_playlist/${item.id}")
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    YouTubePlaylistMenu(
                                        playlist = item,
                                        coroutineScope = coroutineScope,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                        ),
            )
        }

        items(
            items = albums.orEmpty(),
            key = { it.id }
        ) { item ->
            YouTubeGridItem(
                item = item,
                fillMaxWidth = true,
                modifier = Modifier
                    .combinedClickable(
                        onClick = {
                            navController.navigate("album/${item.id}")
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                YouTubeAlbumMenu(
                                    albumItem = item,
                                    navController = navController,
                                    onDismiss = menuState::dismiss
                                )
                            }
                        }
                    )
            )
        }

        items(
            items = artists.orEmpty(),
            key = { it.id }
        ) { item ->
            YouTubeGridItem(
                item = item,
                fillMaxWidth = true,
                modifier = Modifier
                    .combinedClickable(
                        onClick = {
                            navController.navigate("artist/${item.id}")
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                YouTubeArtistMenu(
                                    artist = item,
                                    onDismiss = menuState::dismiss
                                )
                            }
                        }
                    )
            )
        }

        if (playlists == null) {
            items(8) {
                ShimmerHost {
                    GridItemPlaceHolder(fillMaxWidth = true)
                }
            }
        }
    }

    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.account),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                    tint = Color.White
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color(0xFF0F0F0F),
            scrolledContainerColor = Color(0xFF0F0F0F)
        ),
        scrollBehavior = scrollBehavior
    )
}

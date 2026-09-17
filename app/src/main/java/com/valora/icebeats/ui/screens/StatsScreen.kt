package com.valora.icebeats.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.clickable
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.shape.GenericShape
import kotlin.math.cos
import kotlin.math.sin
import com.valora.icebeats.db.entities.Artist
import com.valora.icebeats.db.entities.Song
import com.valora.icebeats.db.entities.SongWithStats
import coil.compose.AsyncImage
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.valora.icebeats.innertube.models.WatchEndpoint
import com.valora.icebeats.LocalPlayerAwareWindowInsets
import com.valora.icebeats.LocalPlayerConnection
import com.valora.icebeats.R
import com.valora.icebeats.constants.StatPeriod
import com.valora.icebeats.extensions.toMediaItem
import com.valora.icebeats.extensions.togglePlayPause
import com.valora.icebeats.models.toMediaMetadata
import com.valora.icebeats.playback.queues.ListQueue
import com.valora.icebeats.playback.queues.YouTubeQueue
import com.valora.icebeats.ui.component.ChoiceChipsRow
import com.valora.icebeats.ui.component.HideOnScrollFAB
import com.valora.icebeats.ui.component.IconButton
import com.valora.icebeats.ui.component.LocalAlbumsGrid
import com.valora.icebeats.ui.component.LocalArtistsGrid
import com.valora.icebeats.ui.component.LocalMenuState
import com.valora.icebeats.ui.component.LocalSongsGrid
import com.valora.icebeats.ui.component.NavigationTitle
import com.valora.icebeats.ui.menu.AlbumMenu
import com.valora.icebeats.ui.menu.ArtistMenu
import com.valora.icebeats.ui.menu.SongMenu
import com.valora.icebeats.ui.utils.backToMain
import com.valora.icebeats.utils.joinByBullet
import com.valora.icebeats.utils.makeTimeString
import com.valora.icebeats.utils.GlobalStatsUser
import com.valora.icebeats.viewmodels.GlobalStatsUiState
import com.valora.icebeats.viewmodels.StatsViewModel
import com.valora.icebeats.ui.component.RankBadge
import com.valora.icebeats.ui.component.icebeatsRank
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StatsScreen(
    navController: NavController,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val context = LocalContext.current

    val supabaseAuthManager = remember { com.valora.icebeats.supabase.SupabaseAuthManager.getInstance(context) }
    val isSupabaseLoggedIn by supabaseAuthManager.isLoggedIn.collectAsState()
    val (innerTubeCookie) = com.valora.icebeats.utils.rememberPreference(com.valora.icebeats.constants.InnerTubeCookieKey, "")
    val (accountEmail) = com.valora.icebeats.utils.rememberPreference(com.valora.icebeats.constants.AccountEmailKey, "")
    val isInnerTubeLoggedIn = remember(innerTubeCookie) {
        innerTubeCookie.isNotEmpty() && "SAPISID" in com.valora.icebeats.innertube.utils.parseCookieString(innerTubeCookie)
    }
    val isUserLoggedIn = isSupabaseLoggedIn || isInnerTubeLoggedIn || accountEmail.isNotBlank()

    val indexChips by viewModel.indexChips.collectAsState()
    val mostPlayedSongs by viewModel.mostPlayedSongs.collectAsState()
    val mostPlayedSongsStats by viewModel.mostPlayedSongsStats.collectAsState()
    val mostPlayedArtists by viewModel.mostPlayedArtists.collectAsState()
    val mostPlayedAlbums by viewModel.mostPlayedAlbums.collectAsState()
    val firstEvent by viewModel.firstEvent.collectAsState()
    val currentDate = LocalDateTime.now()

    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val selectedOption by viewModel.selectedOption.collectAsState()
    val globalStats by viewModel.globalStats.collectAsState()

    // BottomSheet para Insight
    var showInsightBottomSheet by remember { mutableStateOf(false) }
    var showWeeklyGlobalStats by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(globalStats.board.updatedAt, globalStats.board.users.size, isUserLoggedIn) {
        if (isUserLoggedIn && globalStats.board.users.isNotEmpty() && viewModel.shouldShowWeeklyPopup()) {
            showWeeklyGlobalStats = true
        }
    }

    val weeklyDates =
        if (currentDate != null && firstEvent != null) {
            generateSequence(currentDate) { it.minusWeeks(1) }
                .takeWhile { it.isAfter(firstEvent?.event?.timestamp?.minusWeeks(1)) }
                .mapIndexed { index, date ->
                    val endDate = date.plusWeeks(1).minusDays(1).coerceAtMost(currentDate)
                    val formatter = DateTimeFormatter.ofPattern("dd MMM")

                    val startDateFormatted = formatter.format(date)
                    val endDateFormatted = formatter.format(endDate)

                    val startMonth = date.month
                    val endMonth = endDate.month
                    val startYear = date.year
                    val endYear = endDate.year

                    val text =
                        when {
                            startYear != currentDate.year -> "$startDateFormatted, $startYear - $endDateFormatted, $endYear"
                            startMonth != endMonth -> "$startDateFormatted - $endDateFormatted"
                            else -> "${date.dayOfMonth} - $endDateFormatted"
                        }
                    Pair(index, text)
                }.toList()
        } else {
            emptyList()
        }

    val monthlyDates =
        if (currentDate != null && firstEvent != null) {
            generateSequence(
                currentDate.plusMonths(1).withDayOfMonth(1).minusDays(1)
            ) { it.minusMonths(1) }
                .takeWhile {
                    it.isAfter(
                        firstEvent
                            ?.event
                            ?.timestamp
                            ?.withDayOfMonth(1),
                    )
                }.mapIndexed { index, date ->
                    val formatter = DateTimeFormatter.ofPattern("MMM")
                    val formattedDate = formatter.format(date)
                    val text =
                        if (date.year != currentDate.year) {
                            "$formattedDate ${date.year}"
                        } else {
                            formattedDate
                        }
                    Pair(index, text)
                }.toList()
        } else {
            emptyList()
        }

    val yearlyDates =
        if (currentDate != null && firstEvent != null) {
            generateSequence(
                currentDate
                    .plusYears(1)
                    .withDayOfYear(1)
                    .minusDays(1),
            ) { it.minusYears(1) }
                .takeWhile {
                    it.isAfter(
                        firstEvent
                            ?.event
                            ?.timestamp,
                    )
                }.mapIndexed { index, date ->
                    Pair(index, "${date.year}")
                }.toList()
        } else {
            emptyList()
        }

    Box(modifier = Modifier.fillMaxSize()) {
        val artworkUrl = mediaMetadata?.thumbnailUrl
        artworkUrl?.let { imageUrl ->
            com.valora.icebeats.ui.component.BlurredBackground(
                model = imageUrl
            )
            val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
            val overlayBrush = if (isDarkTheme) {
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.2f),
                        Color.Black.copy(alpha = 0.5f),
                        Color.Black.copy(alpha = 0.85f)
                    )
                )
            } else {
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.2f),
                        Color.White.copy(alpha = 0.5f),
                        Color.White.copy(alpha = 0.85f)
                    )
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(overlayBrush)
            )
        }

        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                .asPaddingValues(),
            modifier = Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)
            )
        ) {
            if (!isUserLoggedIn) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.login),
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Masuk untuk Melihat Statistik",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Total waktu mendengarkan, lagu favorit, dan artis teratas hanya direkap setelah Anda login. Masuk ke akun Anda untuk mulai merekap data.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(onClick = { navController.navigate("login") }) {
                                Text("Masuk / Login")
                            }
                        }
                    }
                }

                item {
                    GlobalStatsBoardCard(
                        state = globalStats,
                        onRefresh = viewModel::refreshGlobalStats,
                        isUserLoggedIn = false,
                        onLoginClick = { navController.navigate("login") }
                    )
                }
            } else {
                item {
                    ChoiceChipsRow(
                    chips =
                        when (selectedOption) {
                            OptionStats.WEEKS -> weeklyDates
                            OptionStats.MONTHS -> monthlyDates
                            OptionStats.YEARS -> yearlyDates
                            OptionStats.CONTINUOUS -> {
                                listOf(
                                    StatPeriod.WEEK_1.ordinal to pluralStringResource(
                                        R.plurals.n_week,
                                        1,
                                        1
                                    ),
                                    StatPeriod.MONTH_1.ordinal to pluralStringResource(
                                        R.plurals.n_month,
                                        1,
                                        1
                                    ),
                                    StatPeriod.MONTH_3.ordinal to pluralStringResource(
                                        R.plurals.n_month,
                                        3,
                                        3
                                    ),
                                    StatPeriod.MONTH_6.ordinal to pluralStringResource(
                                        R.plurals.n_month,
                                        6,
                                        6
                                    ),
                                    StatPeriod.YEAR_1.ordinal to pluralStringResource(
                                        R.plurals.n_year,
                                        1,
                                        1
                                    ),
                                    StatPeriod.ALL.ordinal to stringResource(R.string.filter_all),
                                )
                            }
                        },
                    options =
                        listOf(
                            OptionStats.CONTINUOUS to stringResource(id = R.string.continuous),
                            OptionStats.WEEKS to stringResource(R.string.weeks),
                            OptionStats.MONTHS to stringResource(R.string.months),
                            OptionStats.YEARS to stringResource(R.string.years),
                        ),
                    selectedOption = selectedOption,
                    onSelectionChange = {
                        viewModel.selectedOption.value = it
                        viewModel.indexChips.value = 0
                    },
                    currentValue = indexChips,
                    onValueUpdate = { viewModel.indexChips.value = it },
                )
            }

            item {
                val totalTime = mostPlayedSongsStats.sumOf { it.timeListened?.toLong() ?: 0L }
                val totalSongs = mostPlayedSongsStats.size
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Total Time", style = MaterialTheme.typography.labelMedium)
                            Text(text = makeTimeString(totalTime), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Unique Songs", style = MaterialTheme.typography.labelMedium)
                            Text(text = "$totalSongs", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item {
                GlobalStatsBoardCard(
                    state = globalStats,
                    onRefresh = viewModel::refreshGlobalStats,
                    isUserLoggedIn = true,
                    onLoginClick = null
                )
            }

            item {
                StatsHighlightsSection(
                    topArtist = mostPlayedArtists.firstOrNull(),
                    topSong = mostPlayedSongsStats.firstOrNull(),
                    topSongEntity = mostPlayedSongs.firstOrNull(),
                    navController = navController,
                )
            }

            item(key = "artistPieChart") {
                if (mostPlayedArtists.isNotEmpty()) {
                    val overallTotalTime = mostPlayedSongsStats.sumOf { it.timeListened?.toLong() ?: 0L }
                    Spacer(modifier = Modifier.size(16.dp))
                    ArtistPieChart(
                        artists = mostPlayedArtists.take(5),
                        totalTime = overallTotalTime,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .animateItem()
                    )
                    Spacer(modifier = Modifier.size(16.dp))
                }
            }

            item(key = "mostPlayedSongs") {
                NavigationTitle(
                    title = "${mostPlayedSongsStats.size} ${stringResource(id = R.string.songs)}",
                    modifier = Modifier.animateItem(),
                )

                LazyRow(
                    modifier = Modifier.animateItem(),
                ) {
                    itemsIndexed(
                        items = mostPlayedSongsStats,
                        key = { _, song -> song.id },
                    ) { index, song ->
                        LocalSongsGrid(
                            title = "${index + 1}. ${song.title}",
                            subtitle =
                                joinByBullet(
                                    pluralStringResource(
                                        R.plurals.n_time,
                                        song.songCountListened,
                                        song.songCountListened,
                                    ),
                                    makeTimeString(song.timeListened),
                                ),
                            thumbnailUrl = song.thumbnailUrl,
                            isActive = song.id == mediaMetadata?.id,
                            isPlaying = isPlaying,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            if (song.id == mediaMetadata?.id) {
                                                playerConnection.player.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(
                                                    YouTubeQueue(
                                                        endpoint = WatchEndpoint(song.id),
                                                        preloadItem = mostPlayedSongs[index].toMediaMetadata(),
                                                    ),
                                                )
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                SongMenu(
                                                    originalSong = mostPlayedSongs[index],
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        },
                                    )
                                    .animateItem(),
                        )
                    }
                }
            }

            item(key = "mostPlayedArtists") {
                NavigationTitle(
                    title = "${mostPlayedArtists.size} ${stringResource(id = R.string.artists)}",
                    modifier = Modifier.animateItem(),
                )

                LazyRow(
                    modifier = Modifier.animateItem(),
                ) {
                    itemsIndexed(
                        items = mostPlayedArtists,
                        key = { _, artist -> artist.id },
                    ) { index, artist ->
                        LocalArtistsGrid(
                            title = "${index + 1}. ${artist.artist.name}",
                            subtitle =
                                joinByBullet(
                                    pluralStringResource(
                                        R.plurals.n_time,
                                        artist.songCount,
                                        artist.songCount
                                    ),
                                    makeTimeString(artist.timeListened?.toLong()),
                                ),
                            thumbnailUrl = artist.artist.thumbnailUrl,
                            modifier =
                                Modifier
                                    .combinedClickable(
                                        onClick = {
                                            navController.navigate("artist/${artist.id}")
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                ArtistMenu(
                                                    originalArtist = artist,
                                                    coroutineScope = coroutineScope,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        },
                                    )
                                    .animateItem(),
                        )
                    }
                }
            }

            item(key = "mostPlayedAlbums") {
                NavigationTitle(
                    title = "${mostPlayedAlbums.size} ${stringResource(id = R.string.albums)}",
                    modifier = Modifier.animateItem(),
                )

                if (mostPlayedAlbums.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.animateItem(),
                    ) {
                        itemsIndexed(
                            items = mostPlayedAlbums,
                            key = { _, album -> album.id },
                        ) { index, album ->
                            LocalAlbumsGrid(
                                title = "${index + 1}. ${album.album.title}",
                                subtitle =
                                    joinByBullet(
                                        pluralStringResource(
                                            R.plurals.n_time,
                                            album.songCountListened!!,
                                            album.songCountListened
                                        ),
                                        makeTimeString(album.timeListened?.toLong()),
                                    ),
                                thumbnailUrl = album.album.thumbnailUrl,
                                isActive = album.id == mediaMetadata?.album?.id,
                                isPlaying = isPlaying,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                navController.navigate("album/${album.id}")
                                            },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                menuState.show {
                                                    AlbumMenu(
                                                        originalAlbum = album,
                                                        navController = navController,
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }
                                            },
                                        )
                                        .animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }

        // FAB to shuffle most played songs
        if (isUserLoggedIn && mostPlayedSongs.isNotEmpty()) {
            HideOnScrollFAB(
                visible = true,
                lazyListState = lazyListState,
                icon = R.drawable.shuffle,
                onClick = {
                    playerConnection.playQueue(
                        ListQueue(
                            title = context.getString(R.string.most_played_songs),
                            items = mostPlayedSongs.map { it.toMediaMetadata().toMediaItem() }
                                .shuffled()
                        )
                    )
                }
            )
        }

        TopAppBar(
            title = { Text(stringResource(R.string.stats)) },
            navigationIcon = {
                IconButton(
                    onClick = navController::navigateUp,
                    onLongClick = navController::backToMain,
                ) {
                    Icon(
                        painterResource(R.drawable.arrow_back),
                        contentDescription = null,
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent
            ),
            actions = {
                IconButton(
                    onClick = { navController.navigate("year_in_music") },
                    modifier = Modifier.size(48.dp),
                    enabled = true,
                    onLongClick = {}
                ) {
                    Icon(
                        painter = painterResource(R.drawable.stats),
                        contentDescription = "icebeats Insights",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        )
    }

    // BottomSheet de Insight
    if (showInsightBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showInsightBottomSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            InsightBottomSheetContent(
                onNavigateToFullInsight = {
                    coroutineScope.launch {
                        sheetState.hide()
                        showInsightBottomSheet = false
                    }
                    navController.navigate("insight")
                },
                onDismiss = {
                    coroutineScope.launch {
                        sheetState.hide()
                        showInsightBottomSheet = false
                    }
                }
            )
        }
    }

    if (showWeeklyGlobalStats) {
        val weeklyUsers = remember(globalStats.board.users) {
            val active = globalStats.board.users.filter { it.weeklyListenMs > 0 }
            val list = if (active.isNotEmpty()) active else globalStats.board.users
            list.sortedByDescending { if (active.isNotEmpty()) it.weeklyListenMs else it.totalListenMs }
                .mapIndexed { index, user -> user.copy(rank = index + 1) }
        }
        WeeklyGlobalStatsSheet(
            users = weeklyUsers,
            currentUserId = globalStats.currentUserId,
            onDismiss = {
                viewModel.markWeeklyPopupSeen()
                showWeeklyGlobalStats = false
            },
        )
    }
}

@Composable
private fun GlobalStatsBoardCard(
    state: GlobalStatsUiState,
    onRefresh: () -> Unit,
    isUserLoggedIn: Boolean = true,
    onLoginClick: (() -> Unit)? = null,
) {
    val users = state.board.users
    val topUser = users.firstOrNull()
    val currentUser = if (isUserLoggedIn) users.firstOrNull { it.id == state.currentUserId } else null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Global Stats",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = topUser?.let { "Most listened: ${it.name} • Total Users: ${users.size}" } ?: "Waiting for daily cloud stats",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Button(onClick = onRefresh, enabled = !state.isLoading) {
                    Text(if (state.isLoading) "Syncing" else "Refresh")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GlobalStatPill(
                    label = "Top listener",
                    value = topUser?.let { formatListenHours(it.totalListenMs) } ?: "--",
                    modifier = Modifier.weight(1f),
                )
                GlobalStatPill(
                    label = "Your rank",
                    value = if (isUserLoggedIn) (currentUser?.rank?.let { "#$it" } ?: "--") else "Belum Login",
                    modifier = Modifier.weight(1f),
                )
            }

            if (!isUserLoggedIn) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Login untuk masuk ke Global Stats",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { onLoginClick?.invoke() }
                        ) {
                            Text("Login")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val top20Users = users.take(20)
            val isCurrentUserInTop20 = isUserLoggedIn && top20Users.any { it.id == state.currentUserId }

            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                top20Users.forEach { user ->
                    GlobalUserRankRow(
                        user = user,
                        isCurrentUser = isUserLoggedIn && user.id == state.currentUserId,
                    )
                }

                if (isUserLoggedIn && !isCurrentUserInTop20 && currentUser != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "• • •",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }

                    GlobalUserRankRow(
                        user = currentUser,
                        isCurrentUser = true,
                    )
                }
            }

            state.error?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun GlobalStatPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        Text(value, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GlobalUserRankRow(
    user: GlobalStatsUser,
    isCurrentUser: Boolean,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .background(
                    if (isCurrentUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) 
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    RoundedCornerShape(12.dp),
                )
                .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "#${user.rank}",
            modifier = Modifier.width(42.dp),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.bodyMedium,
        )
        ProfileBubble(user.profileUrl, user.name)
        Spacer(modifier = Modifier.width(10.dp))
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = user.name,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (isCurrentUser) FontWeight.Black else FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
            )
            val userHours = user.totalListenMs.toDouble() / (3600.0 * 1000.0)
            val userRank = if (userHours >= 1.0) icebeatsRank.fromHours(userHours.toInt()) else null
            userRank?.let { rank ->
                Spacer(modifier = Modifier.width(6.dp))
                RankBadge(rank = rank, displayedRank = null, size = 18.dp)
            }
        }
        Text(
            text = formatListenHours(user.totalListenMs),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeeklyGlobalStatsSheet(
    users: List<GlobalStatsUser>,
    currentUserId: String,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF1DB954).copy(alpha = 0.35f),
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.surface,
                            ),
                        ),
                    )
                    .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            Column {
                Text(
                    text = "Weekly Global Stats",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = "Total Users: ${users.size} • Only names and listened hours are shown.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                )
                Spacer(modifier = Modifier.height(18.dp))
                LazyColumn(
                    modifier = Modifier
                        .weight(1f, fill = false)
                ) {
                    items(users, key = { it.id }) { user ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp)
                                    .background(
                                        if (user.id == currentUserId) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                                        RoundedCornerShape(14.dp),
                                    )
                                    .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "#${user.rank}",
                                modifier = Modifier.width(46.dp),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                            )
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = user.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.Bold,
                                )
                                val userHours = user.totalListenMs.toDouble() / (3600.0 * 1000.0)
                                val userRank = if (userHours >= 1.0) icebeatsRank.fromHours(userHours.toInt()) else null
                                userRank?.let { rank ->
                                    Spacer(modifier = Modifier.width(6.dp))
                                    RankBadge(rank = rank, displayedRank = null, size = 18.dp)
                                }
                            }
                            Text(
                                text = formatListenHours(user.weeklyListenMs),
                                fontWeight = FontWeight.Black,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(stringResource(R.string.done))
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ProfileBubble(
    profileUrl: String?,
    name: String,
) {
    val validProfileUrl = profileUrl?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
    var imageLoadFailed by remember(validProfileUrl) { mutableStateOf(false) }

    if (validProfileUrl != null && !imageLoadFailed) {
        AsyncImage(
            model = validProfileUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onError = { imageLoadFailed = true },
            modifier =
                Modifier
                    .size(34.dp)
                    .clip(CircleShape),
        )
    } else {
        Box(
            modifier =
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "A",
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

private fun formatListenHours(milliseconds: Long): String {
    val hours = milliseconds / 3_600_000.0
    return if (hours >= 10) {
        "${hours.toInt()}h"
    } else {
        String.format(Locale.US, "%.1fh", hours)
    }
}

@Composable
fun InsightBottomSheetContent(
    onNavigateToFullInsight: () -> Unit,
    onDismiss: () -> Unit
) {
    val currentYear = LocalDateTime.now().year
    val gradientColors = listOf(
        Color(0xFF1DB954),
        Color(0xFF1ED760),
        Color(0xFF191414)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp, start = 16.dp, end = 16.dp, top = 8.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.auto_awesome), // o bar_chart
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = Color(0xFF1DB954)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "icebeats Insight",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Discover your musical year",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }

        // Card principal
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(gradientColors)
                    )
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Your Musical Year",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Column {
                        Text(
                            text = "$currentYear",
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Text(
                            text = "Tap to view your full statistics",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Caracter•sticas
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            InsightFeatureItem(
                iconRes = R.drawable.music_note,
                label = "Top\nSongs"
            )
            InsightFeatureItem(
                iconRes = R.drawable.person,
                label = "Top\nArtists"
            )
            InsightFeatureItem(
                iconRes = R.drawable.equalizer,
                label = "Full\nStatistics"
            )
            InsightFeatureItem(
                iconRes = R.drawable.download,
                label = "Download\nReport"
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Bot•n para ver completo
        Button(
            onClick = onNavigateToFullInsight,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "View Full icebeats Insight",
                modifier = Modifier.padding(8.dp),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun InsightFeatureItem(
    iconRes: Int,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(80.dp)
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = Color(0xFF1DB954)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            fontSize = 11.sp,
            lineHeight = 14.sp
        )
    }
}

enum class OptionStats { WEEKS, MONTHS, YEARS, CONTINUOUS }

@Composable
fun ArtistPieChart(
    artists: List<Artist>,
    totalTime: Long = artists.sumOf { it.timeListened?.toLong() ?: 0L },
    modifier: Modifier = Modifier
) {
    val effectiveTotalTime = if (totalTime > 0L) totalTime else artists.sumOf { it.timeListened?.toLong() ?: 0L }
    if (effectiveTotalTime == 0L) return

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Box(
            modifier = Modifier.size(160.dp),
            contentAlignment = Alignment.Center
        ) {
            var startAngle = -90f

            artists.forEach { artist ->
                val time = artist.timeListened?.toLong() ?: 0L
                val sweepAngle = (time.toFloat() / effectiveTotalTime) * 360f
                
                if (sweepAngle > 1f) {
                    AsyncImage(
                        model = artist.artist.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(PieSliceShape(startAngle, sweepAngle))
                    )
                    startAngle += sweepAngle
                }
            }
        }

        Column {
            Text(
                text = "Total Time Listened",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = makeTimeString(effectiveTotalTime),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

fun PieSliceShape(startAngle: Float, sweepAngle: Float): GenericShape {
    return GenericShape { size, _ ->
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.width / 2
        
        moveTo(center.x, center.y)
        
        val startRad = Math.toRadians(startAngle.toDouble())
        
        lineTo(
            (center.x + radius * cos(startRad)).toFloat(),
            (center.y + radius * sin(startRad)).toFloat()
        )
        
        arcTo(
            rect = androidx.compose.ui.geometry.Rect(
                center = center,
                radius = radius
            ),
            startAngleDegrees = startAngle,
            sweepAngleDegrees = sweepAngle,
            forceMoveTo = false
        )
        
        lineTo(center.x, center.y)
        close()
    }
}

@Composable
fun StatsHighlightsSection(
    topArtist: Artist?,
    topSong: SongWithStats?,
    topSongEntity: com.valora.icebeats.db.entities.Song?,
    navController: NavController
) {
    if (topArtist == null && topSong == null) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (topArtist != null) {
            StatsHighlightCard(
                title = "Your Favourite Artist",
                mainText = topArtist.artist.name,
                subText = "${topArtist.songCount} songs played • ${makeTimeString(topArtist.timeListened?.toLong())}",
                imageUrl = topArtist.artist.thumbnailUrl,
                onClick = { navController.navigate("artist/${topArtist.id}") }
            )
        }

        if (topSong != null && topSongEntity != null) {
            StatsHighlightCard(
                title = "Your Favourite Song",
                mainText = topSong.title,
                subText = "${topSong.songCountListened} plays • ${makeTimeString(topSong.timeListened)}",
                imageUrl = topSong.thumbnailUrl,
                onClick = { }
            )
        }
    }
}

@Composable
fun StatsHighlightCard(
    title: String,
    mainText: String,
    subText: String,
    imageUrl: String?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
            )

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = mainText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

package com.valora.icebeats.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.valora.icebeats.LocalDatabase
import com.valora.icebeats.LocalPlayerConnection
import com.valora.icebeats.R
import com.valora.icebeats.constants.SongSortType
import com.valora.icebeats.supabase.ChatMessage
import com.valora.icebeats.supabase.ChatSharedMedia
import com.valora.icebeats.supabase.ChatUser
import com.valora.icebeats.supabase.SupabaseClient
import com.valora.icebeats.ui.component.ChatMusicCard
import com.valora.icebeats.ui.component.MasterProfileBorder
import com.valora.icebeats.ui.component.NamePreferenceManager
import com.valora.icebeats.ui.component.RankBadge
import com.valora.icebeats.utils.IceBeatsStatsCloudSync
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    navController: NavController,
    conversationId: String,
    otherUserId: String,
    otherUserName: String
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()
    val supabaseClient = remember { SupabaseClient(context) }
    val namePreferenceManager = remember { NamePreferenceManager(context) }
    val playerConnection = LocalPlayerConnection.current
    val currentPlayingMedia by playerConnection?.mediaMetadata?.collectAsState() ?: remember { mutableStateOf(null) }

    var currentUserId by remember { mutableStateOf("") }
    var otherUser by remember { mutableStateOf<ChatUser?>(null) }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var showShareMusicSheet by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Resolve current user ID
    LaunchedEffect(Unit) {
        val uid = IceBeatsStatsCloudSync.resolveStableUserId(context, namePreferenceManager)
        currentUserId = uid
        // Ambil info detail otherUser jika memungkinkan
        val searchRes = supabaseClient.searchChatUsers(otherUserName, uid).getOrNull()
        otherUser = searchRes?.find { it.id == otherUserId } ?: ChatUser(otherUserId, otherUserName)
    }

    // Polling berkala untuk pesan baru (setiap 3,5 detik tanpa memberatkan HP)
    LaunchedEffect(conversationId, currentUserId) {
        if (conversationId.isBlank()) return@LaunchedEffect
        while (isActive) {
            val res = supabaseClient.getChatMessages(conversationId).getOrNull()
            if (res != null) {
                val previousSize = messages.size
                messages = res
                isLoading = false
                if (currentUserId.isNotBlank()) {
                    supabaseClient.markChatAsRead(conversationId, currentUserId)
                }
                if (res.size > previousSize && res.isNotEmpty()) {
                    listState.animateScrollToItem(res.size - 1)
                }
            }
            delay(3500)
        }
    }

    val songsFlow = remember(database) {
        database.likedSongs(SongSortType.CREATE_DATE, descending = true)
    }
    val likedSongs by songsFlow.collectAsState(initial = emptyList())

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = "Kembali",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Avatar lawan bicara dengan Master Profile Border
                MasterProfileBorder(
                    avatarSize = 40.dp,
                    userRank = otherUser?.rank,
                    totalListenMs = otherUser?.totalListenMs
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!otherUser?.profileUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = otherUser?.profileUrl,
                                contentDescription = otherUserName,
                                modifier = Modifier.size(40.dp),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = otherUserName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "U",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = otherUserName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        otherUser?.rank?.let { rank ->
                            Spacer(modifier = Modifier.width(6.dp))
                            RankBadge(rank = rank, displayedRank = null, size = 18.dp)
                        }
                    }
                    Text(
                        text = "IceBeats Live Chat",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                }
            }

            // Pesan Obrolan List
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = painterResource(R.drawable.chat),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Belum ada pesan. Mulai obrolan atau bagikan musik!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    contentPadding = PaddingValues(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        val isMe = msg.senderId == currentUserId
                        ChatBubbleItem(message = msg, isMe = isMe)
                    }
                }
            }

            // Input Bottom Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tombol Bagikan Musik
                IconButton(
                    onClick = { showShareMusicSheet = true },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                ) {
                    Icon(
                        painter = painterResource(R.drawable.music_note),
                        contentDescription = "Bagikan Musik",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Kotak Input Teks
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Ketik pesan...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Tombol Kirim
                IconButton(
                    onClick = {
                        val toSend = inputText.trim()
                        if (toSend.isNotBlank() && !isSending) {
                            isSending = true
                            inputText = ""
                            scope.launch {
                                supabaseClient.sendChatMessage(
                                    conversationId = conversationId,
                                    senderId = currentUserId,
                                    receiverId = otherUserId,
                                    content = toSend
                                )
                                val updated = supabaseClient.getChatMessages(conversationId).getOrNull()
                                if (updated != null) {
                                    messages = updated
                                    listState.animateScrollToItem(updated.size - 1)
                                }
                                isSending = false
                            }
                        }
                    },
                    enabled = inputText.isNotBlank() && !isSending,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_forward),
                        contentDescription = "Kirim",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Bottom Sheet: Pilihan Bagikan Musik
        if (showShareMusicSheet) {
            ModalBottomSheet(
                onDismissRequest = { showShareMusicSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Bagikan Musik ke $otherUserName",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Opsi 1: Lagu yang sedang diputar
                    currentPlayingMedia?.let { media ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .clickable {
                                    showShareMusicSheet = false
                                    scope.launch {
                                        val shared = ChatSharedMedia(
                                            songId = media.id,
                                            title = media.title,
                                            artistName = media.artists.joinToString(", ") { it.name },
                                            albumName = media.album?.title,
                                            thumbnailUrl = media.thumbnailUrl,
                                            duration = media.duration
                                        )
                                        supabaseClient.sendSharedMusic(
                                            conversationId = conversationId,
                                            senderId = currentUserId,
                                            receiverId = otherUserId,
                                            media = shared
                                        )
                                        val updated = supabaseClient.getChatMessages(conversationId).getOrNull()
                                        if (updated != null) messages = updated
                                    }
                                }
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(R.drawable.play),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Sedang Diputar: ${media.title}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = media.artists.joinToString(", ") { it.name },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    Text(
                        text = "Lagu Favorit Anda",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (likedSongs.isEmpty()) {
                        Text(
                            text = "Belum ada lagu favorit di library.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(likedSongs.take(30)) { songItem ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            showShareMusicSheet = false
                                            scope.launch {
                                                val shared = ChatSharedMedia(
                                                    songId = songItem.song.id,
                                                    title = songItem.song.title,
                                                    artistName = songItem.artists.joinToString(", ") { it.name },
                                                    albumName = songItem.song.albumName,
                                                    thumbnailUrl = songItem.song.thumbnailUrl,
                                                    duration = songItem.song.duration
                                                )
                                                supabaseClient.sendSharedMusic(
                                                    conversationId = conversationId,
                                                    senderId = currentUserId,
                                                    receiverId = otherUserId,
                                                    media = shared
                                                )
                                                val updated = supabaseClient.getChatMessages(conversationId).getOrNull()
                                                if (updated != null) messages = updated
                                            }
                                        }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = songItem.song.thumbnailUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(6.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = songItem.song.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = songItem.artists.joinToString(", ") { it.name },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                }
            }
        }
    }
}

@Composable
private fun ChatBubbleItem(
    message: ChatMessage,
    isMe: Boolean
) {
    val bubbleColor = if (isMe) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    }

    val alignment = if (isMe) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isMe) 16.dp else 4.dp,
                        bottomEnd = if (isMe) 4.dp else 16.dp
                    )
                )
                .background(bubbleColor)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Column {
                if (message.messageType == "music" && message.mediaData != null) {
                    if (message.content.isNotBlank() && !message.content.startsWith("🎵 Berbagi lagu")) {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    ChatMusicCard(media = message.mediaData, isFromMe = isMe)
                } else {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Status dibaca (Read Checkmark) untuk pesan pengirim
        if (isMe) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.done_all),
                    contentDescription = if (message.isRead) "Dibaca" else "Terkirim",
                    tint = if (message.isRead) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

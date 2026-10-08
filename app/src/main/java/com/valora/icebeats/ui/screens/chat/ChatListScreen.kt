package com.valora.icebeats.ui.screens.chat

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.valora.icebeats.R
import com.valora.icebeats.supabase.ChatConversation
import com.valora.icebeats.supabase.ChatUser
import com.valora.icebeats.supabase.SupabaseClient
import com.valora.icebeats.ui.component.BorderPreferenceManager
import com.valora.icebeats.ui.component.MasterBorderStyle
import com.valora.icebeats.ui.component.MasterProfileBorder
import com.valora.icebeats.ui.component.NamePreferenceManager
import com.valora.icebeats.ui.component.RankBadge
import com.valora.icebeats.ui.component.RankPreferenceManager
import com.valora.icebeats.ui.component.icebeatsRank
import com.valora.icebeats.utils.IceBeatsStatsCloudSync
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val supabaseClient = remember { SupabaseClient(context) }
    val namePreferenceManager = remember { NamePreferenceManager(context) }
    val borderPrefManager = remember { BorderPreferenceManager(context) }
    val rankPrefManager = remember { RankPreferenceManager(context) }

    val selectedBorder by borderPrefManager.selectedBorder.collectAsState(initial = MasterBorderStyle.ROYAL_CROWN)
    val myRank by rankPrefManager.displayedRank.collectAsState(initial = null)
    val myName by namePreferenceManager.userName.collectAsState(initial = "")
    val myEmail by namePreferenceManager.accountEmail.collectAsState(initial = "")

    val vipManager = remember { com.valora.icebeats.ui.component.VipSubscriptionManager(context) }
    val isVip by vipManager.isVip.collectAsState(initial = false)
    var showVipDialog by remember { mutableStateOf(false) }

    var currentUserId by remember { mutableStateOf("") }
    var conversations by remember { mutableStateOf<List<ChatConversation>>(emptyList()) }
    var friendsList by remember { mutableStateOf<List<ChatUser>>(emptyList()) }
    var pendingRequests by remember { mutableStateOf<List<ChatUser>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Obrolan, 1 = Teman & Permintaan

    var showSearchSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<ChatUser>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    var selectedUserForProfile by remember { mutableStateOf<ChatUser?>(null) }
    var showMyProfileSheet by remember { mutableStateOf(false) }

    var selectedConversationForActions by remember { mutableStateOf<ChatConversation?>(null) }
    var selectedFriendForActions by remember { mutableStateOf<ChatUser?>(null) }
    var showDeleteChatConfirm by remember { mutableStateOf<ChatConversation?>(null) }
    var showDeleteFriendConfirm by remember { mutableStateOf<ChatUser?>(null) }

    // Hitung data profil saya sendiri
    val statsPrefs = remember { context.getSharedPreferences(IceBeatsStatsCloudSync.PREFERENCES_NAME, android.content.Context.MODE_PRIVATE) }
    val myListenMs = remember(currentUserId, myName, myEmail) {
        if (currentUserId.isNotBlank()) {
            statsPrefs.getLong("saved_max_total_listen_ms_$currentUserId", statsPrefs.getLong("saved_max_total_listen_ms", 0L))
        } else 0L
    }

    val myChatUser = remember(currentUserId, myName, myEmail, myListenMs, myRank, selectedBorder) {
        ChatUser(
            id = currentUserId,
            name = myName.ifBlank { myEmail.substringBefore("@").ifBlank { "Saya" } },
            profileUrl = null,
            totalListenMs = myListenMs,
            rank = myRank ?: icebeatsRank.fromHours((myListenMs / 3600000L).toInt()),
            borderStyle = selectedBorder.id
        )
    }

    // Refresh semua data (Cache-First: tampilkan lokal instan, lalu sync dari cloud)
    fun refreshData(uid: String) {
        if (uid.isBlank()) return
        scope.launch {
            // Tampilkan cache lokal terlebih dahulu jika belum ada di state
            if (conversations.isEmpty()) {
                val cached = com.valora.icebeats.supabase.ChatLocalCache.getConversations(context, uid)
                if (cached.isNotEmpty()) {
                    conversations = cached
                    isLoading = false
                }
            }

            // Sinkronkan data terbaru dari server
            val convsResult = supabaseClient.getChatConversations(uid)
            val friendsResult = supabaseClient.getFriendsList(uid)
            val reqsResult = supabaseClient.getPendingFriendRequests(uid)

            convsResult.getOrNull()?.let { convs ->
                conversations = convs
                com.valora.icebeats.supabase.ChatLocalCache.saveConversations(context, uid, convs)
            }
            friendsResult.getOrNull()?.let { friends ->
                friendsList = friends
            }
            reqsResult.getOrNull()?.let { reqs ->
                pendingRequests = reqs
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        val uid = IceBeatsStatsCloudSync.resolveStableUserId(context, namePreferenceManager)
        currentUserId = uid
        // Muat seketika riwayat chat dari cache lokal agar tidak pernah hilang
        val cached = com.valora.icebeats.supabase.ChatLocalCache.getConversations(context, uid)
        if (cached.isNotEmpty()) {
            conversations = cached
            isLoading = false
        }
        refreshData(uid)
    }

    // Auto-search user saat query berubah di Search Sheet
    LaunchedEffect(searchQuery, currentUserId, showSearchSheet) {
        if (!showSearchSheet || currentUserId.isBlank()) return@LaunchedEffect
        isSearching = true
        val res = supabaseClient.searchChatUsers(searchQuery, currentUserId).getOrNull() ?: emptyList()
        searchResults = res
        isSearching = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        if (!isVip) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFD700).copy(alpha = 0.15f))
                        .border(2.dp, Color(0xFFFFD700), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_vip_crown),
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "IceBeats Chat Khusus VIP",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFFD700),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Fitur chat real-time, berbagi musik, dan pertemanan adalah fitur eksklusif untuk member VIP IceBeats.\n\nNikmati obrolan seru dan bagikan lagu favoritmu sekarang!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = { showVipDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFD700),
                        contentColor = Color.Black
                    )
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.ic_vip_crown),
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Aktifkan VIP (Mulai Rp 5.000)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(text = "Kembali ke Beranda")
                }
            }

            if (showVipDialog) {
                com.valora.icebeats.ui.component.VipSubscriptionDialog(
                    onDismiss = { showVipDialog = false }
                )
            }
            return@Box
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Header Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = "Kembali",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = "IceBeats Chat",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                // Tombol Profil Saya
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { showMyProfileSheet = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MasterProfileBorder(
                            avatarSize = 24.dp,
                            userRank = myChatUser.rank,
                            totalListenMs = myChatUser.totalListenMs,
                            borderStyle = selectedBorder,
                            isSelf = true
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = myChatUser.name.take(1).uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Profil Saya",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Tombol Cari Pengguna
                IconButton(
                    onClick = { showSearchSheet = true },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Icon(
                        painter = painterResource(R.drawable.search),
                        contentDescription = "Cari Pengguna",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Tabs: Obrolan vs Teman & Permintaan
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "Obrolan (${conversations.size})",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )

                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Teman (${friendsList.size})",
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                            )
                            if (pendingRequests.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${pendingRequests.size}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                )
            }

            // Konten Sesuai Tab
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (selectedTab == 0) {
                // TAB 0: DAFTAR OBROLAN
                if (conversations.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.chat),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Belum Ada Percakapan",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Ajak teman untuk mulai mengobrol & berbagi musik favorit!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            Button(
                                onClick = { showSearchSheet = true },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.search),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Cari Teman Sekarang")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(conversations, key = { it.id }) { conv ->
                            ConversationItem(
                                conversation = conv,
                                onClick = {
                                    val encodedName = URLEncoder.encode(conv.otherUser.name, StandardCharsets.UTF_8.name())
                                    navController.navigate("chat/${conv.id}/${conv.otherUser.id}/$encodedName")
                                },
                                onLongClick = {
                                    selectedConversationForActions = conv
                                },
                                onAvatarClick = {
                                    selectedUserForProfile = conv.otherUser
                                }
                            )
                        }
                    }
                }
            } else {
                // TAB 1: DAFTAR TEMAN & PERMINTAAN PERTEMANAN
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Bagian Permintaan Pertemanan Masuk
                    if (pendingRequests.isNotEmpty()) {
                        item {
                            Text(
                                text = "Permintaan Pertemanan Masuk (${pendingRequests.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        items(pendingRequests, key = { "req_${it.id}" }) { reqUser ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier.size(54.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        MasterProfileBorder(
                                            avatarSize = 34.dp,
                                            userRank = reqUser.rank,
                                            totalListenMs = reqUser.totalListenMs,
                                            borderStyle = reqUser.borderStyle?.let { MasterBorderStyle.fromIdOrNull(it) },
                                            isSelf = false
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                if (!reqUser.profileUrl.isNullOrBlank()) {
                                                    AsyncImage(
                                                        model = reqUser.profileUrl,
                                                        contentDescription = null,
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                } else {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = reqUser.name.take(1).uppercase(),
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { selectedUserForProfile = reqUser }
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = reqUser.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val verType = com.valora.icebeats.ui.component.VerificationHelper.parseVerificationType(
                                                verificationBadge = reqUser.verificationBadge
                                            )
                                            if (verType != com.valora.icebeats.ui.component.VerificationType.NONE) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                com.valora.icebeats.ui.component.VerificationBadge(type = verType, size = 13.dp)
                                            }
                                        }
                                        Text(
                                            text = "Level ${reqUser.rank?.name ?: "Echo"} • Ketuk untuk lihat profil",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Button(
                                        onClick = {
                                            scope.launch {
                                                supabaseClient.acceptFriendRequest(currentUserId, reqUser.id)
                                                refreshData(currentUserId)
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text("Terima", style = MaterialTheme.typography.labelSmall)
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                supabaseClient.rejectOrRemoveFriend(currentUserId, reqUser.id)
                                                refreshData(currentUserId)
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Text("Tolak", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }

                    // Bagian Daftar Teman
                    item {
                        Text(
                            text = "Semua Teman (${friendsList.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    if (friendsList.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Belum memiliki teman. Gunakan fitur pencarian untuk mengajak teman!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        items(friendsList, key = { "friend_${it.id}" }) { friend ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = { selectedUserForProfile = friend },
                                            onLongClick = { selectedFriendForActions = friend }
                                        )
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier.size(54.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        MasterProfileBorder(
                                            avatarSize = 34.dp,
                                            userRank = friend.rank,
                                            totalListenMs = friend.totalListenMs,
                                            borderStyle = friend.borderStyle?.let { MasterBorderStyle.fromIdOrNull(it) },
                                            isSelf = false
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                if (!friend.profileUrl.isNullOrBlank()) {
                                                    AsyncImage(
                                                        model = friend.profileUrl,
                                                        contentDescription = null,
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                } else {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = friend.name.take(1).uppercase(),
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = friend.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            friend.rank?.let { r ->
                                                Spacer(modifier = Modifier.width(6.dp))
                                                RankBadge(rank = r, displayedRank = null, size = 16.dp)
                                            }
                                        }
                                        Text(
                                            text = "${(friend.totalListenMs / 3600000)} Jam didengar • Ketuk profil",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                val convId = supabaseClient.getOrCreateConversation(currentUserId, friend.id).getOrNull()
                                                if (!convId.isNullOrBlank()) {
                                                    val encodedName = URLEncoder.encode(friend.name, StandardCharsets.UTF_8.name())
                                                    navController.navigate("chat/$convId/${friend.id}/$encodedName")
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.chat),
                                            contentDescription = "Chat",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Action Button: Mulai Chat / Cari Teman Baru
        FloatingActionButton(
            onClick = { showSearchSheet = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            shape = CircleShape
        ) {
            Icon(
                painter = painterResource(R.drawable.chat),
                contentDescription = "Mulai Obrolan",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(24.dp)
            )
        }

        // Modal Bottom Sheet: Pencarian Pengguna & Profil
        if (showSearchSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSearchSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Cari Pengguna IceBeats",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Ketik nama atau email...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.search),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    if (isSearching) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    } else if (searchResults.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Pengguna tidak ditemukan.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(340.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(searchResults, key = { it.id }) { user ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            selectedUserForProfile = user
                                        },
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier.size(54.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            MasterProfileBorder(
                                                avatarSize = 34.dp,
                                                userRank = user.rank,
                                                totalListenMs = user.totalListenMs,
                                                borderStyle = user.borderStyle?.let { MasterBorderStyle.fromIdOrNull(it) },
                                                isSelf = false
                                            ) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                    modifier = Modifier.size(34.dp)
                                                ) {
                                                    if (!user.profileUrl.isNullOrBlank()) {
                                                        AsyncImage(
                                                            model = user.profileUrl,
                                                            contentDescription = null,
                                                            modifier = Modifier.fillMaxSize(),
                                                            contentScale = ContentScale.Crop
                                                        )
                                                    } else {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Text(
                                                                text = user.name.take(1).uppercase(),
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = user.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                val verType = com.valora.icebeats.ui.component.VerificationHelper.parseVerificationType(
                                                    verificationBadge = user.verificationBadge
                                                )
                                                if (verType != com.valora.icebeats.ui.component.VerificationType.NONE) {
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    com.valora.icebeats.ui.component.VerificationBadge(type = verType, size = 13.dp)
                                                }
                                                user.rank?.let { r ->
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    RankBadge(rank = r, displayedRank = null, size = 16.dp)
                                                }
                                            }
                                            Text(
                                                text = "${(user.totalListenMs / 3600000)} Jam didengar • Ketuk lihat profil",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                selectedUserForProfile = user
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text("Profil", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // Tampilan Profil Pengguna Lain
        if (selectedUserForProfile != null) {
            UserProfileSheet(
                targetUser = selectedUserForProfile!!,
                currentUserId = currentUserId,
                isSelf = false,
                onDismiss = {
                    selectedUserForProfile = null
                    refreshData(currentUserId)
                },
                onStartChat = { u ->
                    selectedUserForProfile = null
                    showSearchSheet = false
                    scope.launch {
                        val convId = supabaseClient.getOrCreateConversation(currentUserId, u.id).getOrNull()
                        if (!convId.isNullOrBlank()) {
                            val encodedName = URLEncoder.encode(u.name, StandardCharsets.UTF_8.name())
                            navController.navigate("chat/$convId/${u.id}/$encodedName")
                        }
                    }
                }
            )
        }

        // Tampilan Profil Saya Sendiri
        if (showMyProfileSheet) {
            UserProfileSheet(
                targetUser = myChatUser,
                currentUserId = currentUserId,
                isSelf = true,
                onDismiss = { showMyProfileSheet = false },
                onOpenAccountSettings = {
                    showMyProfileSheet = false
                    navController.navigate("settings/account")
                }
            )
        }

        // Modal Bottom Sheet Aksi Obrolan (Tekan Lama / Long Press di Home Chat)
        if (selectedConversationForActions != null) {
            val conv = selectedConversationForActions!!
            val other = conv.otherUser
            ModalBottomSheet(
                onDismissRequest = { selectedConversationForActions = null },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .navigationBarsPadding()
                ) {
                    // Header Info Lawan Bicara
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(54.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            MasterProfileBorder(
                                avatarSize = 34.dp,
                                userRank = other.rank,
                                totalListenMs = other.totalListenMs,
                                borderStyle = other.borderStyle?.let { MasterBorderStyle.fromIdOrNull(it) },
                                isSelf = false
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    if (!other.profileUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = other.profileUrl,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = other.name.take(1).uppercase(),
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = other.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val verType = com.valora.icebeats.ui.component.VerificationHelper.parseVerificationType(
                                    verificationBadge = other.verificationBadge
                                )
                                if (verType != com.valora.icebeats.ui.component.VerificationType.NONE) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    com.valora.icebeats.ui.component.VerificationBadge(type = verType, size = 15.dp)
                                }
                                other.rank?.let { r ->
                                    Spacer(modifier = Modifier.width(6.dp))
                                    RankBadge(rank = r, displayedRank = null, size = 18.dp)
                                }
                            }
                            Text(
                                text = conv.lastMessage.ifBlank { "Mulai obrolan..." },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    // 1. LIHAT PROFIL
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                val u = other
                                selectedConversationForActions = null
                                selectedUserForProfile = u
                            },
                        color = Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Lihat Profil",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Lihat jam didengarkan, rank, dan playlist",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 2. HAPUS PERTEMANAN
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                val u = other
                                showDeleteFriendConfirm = u
                            },
                        color = Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Hapus Pertemanan",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "Hapus ${other.name} dari daftar teman Anda",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 3. HAPUS CHAT
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                val c = conv
                                showDeleteChatConfirm = c
                            },
                        color = Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Hapus Obrolan",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "Hapus seluruh riwayat pesan obrolan ini",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // Modal Bottom Sheet Aksi Teman (Tekan Lama / Long Press di Tab Teman)
        if (selectedFriendForActions != null) {
            val friend = selectedFriendForActions!!
            ModalBottomSheet(
                onDismissRequest = { selectedFriendForActions = null },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .navigationBarsPadding()
                ) {
                    // Header Info Teman
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(54.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            MasterProfileBorder(
                                avatarSize = 34.dp,
                                userRank = friend.rank,
                                totalListenMs = friend.totalListenMs,
                                borderStyle = friend.borderStyle?.let { MasterBorderStyle.fromIdOrNull(it) },
                                isSelf = false
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    if (!friend.profileUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = friend.profileUrl,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = friend.name.take(1).uppercase(),
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = friend.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val verType = com.valora.icebeats.ui.component.VerificationHelper.parseVerificationType(
                                    verificationBadge = friend.verificationBadge
                                )
                                if (verType != com.valora.icebeats.ui.component.VerificationType.NONE) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    com.valora.icebeats.ui.component.VerificationBadge(type = verType, size = 15.dp)
                                }
                                friend.rank?.let { r ->
                                    Spacer(modifier = Modifier.width(6.dp))
                                    RankBadge(rank = r, displayedRank = null, size = 18.dp)
                                }
                            }
                            Text(
                                text = "${friend.totalListenMs / 3600000} Jam didengarkan",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    // 1. LIHAT PROFIL
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                val u = friend
                                selectedFriendForActions = null
                                selectedUserForProfile = u
                            },
                        color = Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Lihat Profil",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Lihat jam didengarkan, rank, dan playlist",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 2. HAPUS PERTEMANAN
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                val u = friend
                                showDeleteFriendConfirm = u
                            },
                        color = Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Hapus Pertemanan",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "Hapus ${friend.name} dari daftar teman Anda",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // Dialog Konfirmasi Hapus Pertemanan
        if (showDeleteFriendConfirm != null) {
            val target = showDeleteFriendConfirm!!
            AlertDialog(
                onDismissRequest = { showDeleteFriendConfirm = null },
                title = { Text("Hapus Pertemanan?", fontWeight = FontWeight.Bold) },
                text = { Text("Apakah Anda yakin ingin menghapus pertemanan dengan ${target.name}? Anda harus berteman kembali sebelum dapat saling mengirim pesan obrolan.") },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                supabaseClient.rejectOrRemoveFriend(currentUserId, target.id)
                                refreshData(currentUserId)
                                showDeleteFriendConfirm = null
                                selectedConversationForActions = null
                                selectedFriendForActions = null
                                Toast.makeText(context, "Pertemanan dengan ${target.name} dihapus", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Hapus", color = MaterialTheme.colorScheme.onError)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showDeleteFriendConfirm = null }) {
                        Text("Batal")
                    }
                }
            )
        }

        // Dialog Konfirmasi Hapus Obrolan
        if (showDeleteChatConfirm != null) {
            val targetConv = showDeleteChatConfirm!!
            AlertDialog(
                onDismissRequest = { showDeleteChatConfirm = null },
                title = { Text("Hapus Obrolan?", fontWeight = FontWeight.Bold) },
                text = { Text("Seluruh riwayat pesan percakapan dengan ${targetConv.otherUser.name} akan dihapus secara permanen.") },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                supabaseClient.deleteConversation(targetConv.id)
                                refreshData(currentUserId)
                                showDeleteChatConfirm = null
                                selectedConversationForActions = null
                                Toast.makeText(context, "Obrolan berhasil dihapus", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Hapus Obrolan", color = MaterialTheme.colorScheme.onError)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showDeleteChatConfirm = null }) {
                        Text("Batal")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationItem(
    conversation: ChatConversation,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onAvatarClick: (() -> Unit)? = null
) {
    val other = conversation.otherUser

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar dengan Master Profile Border (diperkecil seimbang & rapi agar pas di home chat)
        Box(
            modifier = Modifier
                .size(54.dp)
                .clickable { onAvatarClick?.invoke() },
            contentAlignment = Alignment.Center
        ) {
            MasterProfileBorder(
                avatarSize = 34.dp,
                userRank = other.rank,
                totalListenMs = other.totalListenMs,
                borderStyle = other.borderStyle?.let { MasterBorderStyle.fromIdOrNull(it) },
                isSelf = false
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(34.dp)
                ) {
                    if (!other.profileUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = other.profileUrl,
                            contentDescription = other.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = other.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "U",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = other.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                other.rank?.let { rank ->
                    Spacer(modifier = Modifier.width(6.dp))
                    RankBadge(rank = rank, displayedRank = null, size = 18.dp)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = conversation.lastMessage.ifBlank { "Mulai obrolan..." },
                style = MaterialTheme.typography.bodyMedium,
                color = if (conversation.unreadCount > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (conversation.unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (conversation.unreadCount > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${conversation.unreadCount}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

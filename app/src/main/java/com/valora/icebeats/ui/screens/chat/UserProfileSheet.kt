package com.valora.icebeats.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.valora.icebeats.LocalDatabase
import com.valora.icebeats.R
import com.valora.icebeats.constants.PlaylistSortType
import com.valora.icebeats.supabase.ChatUser
import com.valora.icebeats.supabase.FriendshipStatus
import com.valora.icebeats.supabase.SupabaseClient
import com.valora.icebeats.supabase.UserPublicPlaylist
import com.valora.icebeats.ui.component.MasterBorderSelectorSheet
import com.valora.icebeats.ui.component.MasterBorderStyle
import com.valora.icebeats.ui.component.MasterProfileBorder
import com.valora.icebeats.ui.component.RankBadge
import com.valora.icebeats.ui.component.icebeatsRank
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Bottom Sheet Tampilan Profil Pengguna (Melihat Profil Orang Lain atau Profil Saya Sendiri)
 * v7.1.3
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileSheet(
    targetUser: ChatUser,
    currentUserId: String,
    isSelf: Boolean = false,
    onDismiss: () -> Unit,
    onStartChat: ((ChatUser) -> Unit)? = null,
    onOpenAccountSettings: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()
    val supabaseClient = remember { SupabaseClient(context) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var friendshipStatus by remember { mutableStateOf(if (isSelf) FriendshipStatus.FRIENDS else FriendshipStatus.NONE) }
    var isFriendActionLoading by remember { mutableStateOf(false) }
    var playlists by remember { mutableStateOf<List<UserPublicPlaylist>>(emptyList()) }
    var isLoadingPlaylists by remember { mutableStateOf(true) }
    var showBorderSelector by remember { mutableStateOf(false) }

    val vipManager = remember { com.valora.icebeats.ui.component.VipSubscriptionManager(context) }
    val isVip by vipManager.isVip.collectAsState(initial = false)
    var showVipDialog by remember { mutableStateOf(false) }

    val bannerPrefManager = remember { com.valora.icebeats.ui.component.BannerPreferenceManager(context) }
    val myBannerUrl by bannerPrefManager.bannerUrl.collectAsState(initial = null)
    val displayBannerUrl = if (isSelf) myBannerUrl else targetUser.bannerUrl

    // Hitung jam dengar & rank
    val totalHours = (targetUser.totalListenMs / (1000 * 3600)).coerceAtLeast(0L)
    val userRank = targetUser.rank ?: icebeatsRank.fromHours(totalHours.toInt())
    val verType = com.valora.icebeats.ui.component.VerificationHelper.parseVerificationType(
        verificationBadge = targetUser.verificationBadge
    )
    val isTargetVip = if (isSelf) isVip else (verType != com.valora.icebeats.ui.component.VerificationType.NONE)
    val borderStyle = if (isTargetVip) MasterBorderStyle.fromIdOrNull(targetUser.borderStyle) else null

    // Muat data status pertemanan & playlist
    LaunchedEffect(targetUser.id, currentUserId) {
        if (!isSelf && currentUserId.isNotBlank() && targetUser.id.isNotBlank()) {
            val status = supabaseClient.getFriendshipStatus(currentUserId, targetUser.id).getOrDefault(FriendshipStatus.NONE)
            friendshipStatus = status
        }

        if (isSelf) {
            // Muat playlist lokal pengguna sendiri
            isLoadingPlaylists = true
            scope.launch {
                runCatching {
                    val localPlaylists = database.playlistsByNameAsc().first()
                    playlists = localPlaylists.map {
                        UserPublicPlaylist(
                            playlistId = it.id,
                            name = it.playlist.name,
                            songCount = it.songCount,
                            updatedAt = ""
                        )
                    }
                }
                isLoadingPlaylists = false
            }
        } else {
            // Muat playlist publik pengguna lain dari cloud
            isLoadingPlaylists = true
            scope.launch {
                val cloudPlaylists = supabaseClient.getUserPublicPlaylists(targetUser.id).getOrDefault(emptyList())
                playlists = cloudPlaylists
                isLoadingPlaylists = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 0. HEADER PROFIL (BANNER + AVATAR DENGAN MASTER BORDER)
            item {
                if (!displayBannerUrl.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        // Banner Animasi Latar Belakang (tanpa garis kuning)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                        ) {
                            AsyncImage(
                                model = displayBannerUrl,
                                contentDescription = "Banner Animasi ${targetUser.name}",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            // Gradien halus di bagian bawah agar menyatu secara transparan dengan background tanpa garis kuning
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colorStops = arrayOf(
                                                0.0f to Color.Transparent,
                                                0.35f to Color.Transparent,
                                                0.70f to MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                                                1.0f to MaterialTheme.colorScheme.surface
                                            )
                                        )
                                    )
                            )
                        }

                        // Avatar dengan Master Border tepat di tengah batas bawah banner
                        Box(
                            modifier = Modifier
                                .padding(top = 70.dp)
                                .size(140.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            MasterProfileBorder(
                                avatarSize = 70.dp,
                                userRank = userRank,
                                totalListenMs = targetUser.totalListenMs,
                                borderStyle = if (isSelf) null else borderStyle,
                                isSelf = isSelf
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(70.dp)
                                ) {
                                    if (!targetUser.profileUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = targetUser.profileUrl,
                                            contentDescription = targetUser.name,
                                            modifier = Modifier.size(70.dp),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(70.dp)
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(
                                                            MaterialTheme.colorScheme.primary,
                                                            MaterialTheme.colorScheme.tertiary
                                                        )
                                                    )
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = targetUser.name.take(1).uppercase(),
                                                style = MaterialTheme.typography.headlineMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Tampilan avatar normal jika tidak ada banner
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .padding(top = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        MasterProfileBorder(
                            avatarSize = 70.dp,
                            userRank = userRank,
                            totalListenMs = targetUser.totalListenMs,
                            borderStyle = if (isSelf) null else borderStyle,
                            isSelf = isSelf
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(70.dp)
                            ) {
                                if (!targetUser.profileUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = targetUser.profileUrl,
                                        contentDescription = targetUser.name,
                                        modifier = Modifier.size(70.dp),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(70.dp)
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(
                                                        MaterialTheme.colorScheme.primary,
                                                        MaterialTheme.colorScheme.tertiary
                                                    )
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = targetUser.name.take(1).uppercase(),
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // NAMA & TIER BADGE
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = targetUser.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val verType = com.valora.icebeats.ui.component.VerificationHelper.parseVerificationType(
                        verificationBadge = targetUser.verificationBadge
                    )
                    if (verType != com.valora.icebeats.ui.component.VerificationType.NONE) {
                        Spacer(modifier = Modifier.width(6.dp))
                        com.valora.icebeats.ui.component.VerificationBadge(type = verType, size = 18.dp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    RankBadge(
                        rank = userRank,
                        displayedRank = userRank,
                        size = 22.dp
                    )
                }

                Text(
                    text = if (isSelf) "Akun Anda • Level ${userRank.name}" else "Level ${userRank.name} • IceBeats User",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(18.dp))
            }

            // 2. KARTU STATISTIK MENDENGARKAN
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Total Jam Dengar
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.schedule),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "$totalHours Jam",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Total Didengar",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Rank & Gelar
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = userRank.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (userRank == icebeatsRank.Master) "Border Aktif" else "Tier Saat Ini",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
            }

            // 3. TOMBOL AKSI PERTEMANAN / EDIT PROFIL
            item {
                if (isSelf) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (isVip) {
                                    showBorderSelector = true
                                } else {
                                    showVipDialog = true
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Border Master")
                        }

                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                onOpenAccountSettings?.invoke()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.settings),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pengaturan")
                        }
                    }
                } else {
                    // Tindakan Berteman untuk User Lain
                    when (friendshipStatus) {
                        FriendshipStatus.NONE -> {
                            Button(
                                onClick = {
                                    scope.launch {
                                        isFriendActionLoading = true
                                        val success = supabaseClient.sendFriendRequest(currentUserId, targetUser.id).getOrDefault(false)
                                        if (success) friendshipStatus = FriendshipStatus.PENDING_SENT
                                        isFriendActionLoading = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isFriendActionLoading
                            ) {
                                if (isFriendActionLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        painter = painterResource(R.drawable.person),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Ajak Berteman (Tambah Teman)")
                                }
                            }
                        }

                        FriendshipStatus.PENDING_SENT -> {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        isFriendActionLoading = true
                                        supabaseClient.rejectOrRemoveFriend(currentUserId, targetUser.id)
                                        friendshipStatus = FriendshipStatus.NONE
                                        isFriendActionLoading = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isFriendActionLoading
                            ) {
                                Text("⏳ Permintaan Terkirim (Batalkan)")
                            }
                        }

                        FriendshipStatus.PENDING_RECEIVED -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            isFriendActionLoading = true
                                            val ok = supabaseClient.acceptFriendRequest(currentUserId, targetUser.id).getOrDefault(false)
                                            if (ok) friendshipStatus = FriendshipStatus.FRIENDS
                                            isFriendActionLoading = false
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = !isFriendActionLoading
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Terima Teman")
                                }

                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            isFriendActionLoading = true
                                            supabaseClient.rejectOrRemoveFriend(currentUserId, targetUser.id)
                                            friendshipStatus = FriendshipStatus.NONE
                                            isFriendActionLoading = false
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = !isFriendActionLoading
                                ) {
                                    Text("Tolak")
                                }
                            }
                        }

                        FriendshipStatus.FRIENDS -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (isVip) {
                                            onDismiss()
                                            onStartChat?.invoke(targetUser)
                                        } else {
                                            showVipDialog = true
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.chat),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Mulai Obrolan")
                                }

                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            isFriendActionLoading = true
                                            supabaseClient.rejectOrRemoveFriend(currentUserId, targetUser.id)
                                            friendshipStatus = FriendshipStatus.NONE
                                            isFriendActionLoading = false
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = !isFriendActionLoading
                                ) {
                                    Text("Hapus Teman")
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            // 4. DAFTAR PLAYLIST
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.queue_music),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isSelf) "Playlist Saya" else "Playlist Pengguna",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            if (isLoadingPlaylists) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            } else if (playlists.isEmpty()) {
                item {
                    Text(
                        text = if (isSelf) "Belum ada playlist yang dibuat." else "Pengguna ini belum membagikan playlist.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                items(playlists) { playlist ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(R.drawable.music_note),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = playlist.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${playlist.songCount} lagu",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }

    if (showBorderSelector) {
        MasterBorderSelectorSheet(
            onDismiss = { showBorderSelector = false },
            userRank = userRank,
            totalListenMs = targetUser.totalListenMs
        )
    }

    if (showVipDialog) {
        com.valora.icebeats.ui.component.VipSubscriptionDialog(
            onDismiss = { showVipDialog = false }
        )
    }
}

package com.valora.icebeats.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.valora.icebeats.R
import com.valora.icebeats.supabase.ChatUser
import com.valora.icebeats.ui.screens.chat.UserProfileSheet
import com.valora.icebeats.utils.GlobalStatsUser
import com.valora.icebeats.utils.IceBeatsStatsCloudSync
import com.valora.icebeats.utils.icebeatsStatsCloudClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Singleton Manager untuk caching dan fetching Top 1 Listener secara otomatis.
 * Mencegah fetch berulang saat berpindah-pindah tab / tema.
 */
object TopListenerManager {
    private val _topUser = MutableStateFlow<GlobalStatsUser?>(null)
    val topUser: StateFlow<GlobalStatsUser?> = _topUser.asStateFlow()

    private var lastFetchTime = 0L
    private val cloudClient = icebeatsStatsCloudClient()

    suspend fun refreshIfNeeded(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && _topUser.value != null && (now - lastFetchTime) < 5 * 60 * 1000L) {
            return
        }
        withContext(Dispatchers.IO) {
            runCatching {
                cloudClient.readBoard().onSuccess { board ->
                    val first = board.users.firstOrNull()
                    if (first != null) {
                        _topUser.value = first
                        lastFetchTime = now
                    }
                }
            }
        }
    }
}

/**
 * Komponen Kartu Ringkas Top 1 Listener di Halaman Home (Semua Tema).
 * "jangan besar-besar yaa" -> Kompak, tinggi sekitar 56dp, elegan, informatif.
 */
@Composable
fun TopListenerHomeSection(
    modifier: Modifier = Modifier,
    navController: NavController? = null
) {
    val context = LocalContext.current
    val topUser by TopListenerManager.topUser.collectAsState()
    var selectedUserForSheet by remember { mutableStateOf<ChatUser?>(null) }

    LaunchedEffect(Unit) {
        TopListenerManager.refreshIfNeeded()
    }

    val currentTopUser = topUser ?: return

    val totalHours = (currentTopUser.totalListenMs / (1000 * 3600)).coerceAtLeast(0L)
    val hoursText = if (totalHours >= 1L) {
        "$totalHours Jam"
    } else {
        val minutes = (currentTopUser.totalListenMs / (1000 * 60)).coerceAtLeast(1L)
        "$minutes Menit"
    }

    val userRank = if (totalHours >= 1L) icebeatsRank.fromHours(totalHours.toInt()) else null
    val verType = VerificationHelper.parseVerificationType(verificationBadge = currentTopUser.verificationBadge)
    val isVipListener = verType != VerificationType.NONE
    val borderStyle = if (isVipListener) MasterBorderStyle.fromIdOrNull(currentTopUser.borderStyle) else null

    val targetChatUser = remember(currentTopUser, userRank, isVipListener) {
        ChatUser(
            id = currentTopUser.id,
            name = currentTopUser.name,
            profileUrl = currentTopUser.profileUrl,
            totalListenMs = currentTopUser.totalListenMs,
            rank = userRank,
            borderStyle = if (isVipListener) currentTopUser.borderStyle else null,
            verificationBadge = currentTopUser.verificationBadge
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { selectedUserForSheet = targetChatUser },
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF141316).copy(alpha = 0.92f),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.horizontalGradient(
                listOf(
                    Color(0xFFFFD700).copy(alpha = 0.45f),
                    Color(0xFFB8860B).copy(alpha = 0.25f),
                    Color(0xFF1E1E22).copy(alpha = 0.3f)
                )
            )
        ),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // AVATAR DENGAN MASTER PROFILE BORDER
            Box(
                modifier = Modifier.size(46.dp),
                contentAlignment = Alignment.Center
            ) {
                MasterProfileBorder(
                    avatarSize = 26.dp,
                    userRank = userRank,
                    totalListenMs = currentTopUser.totalListenMs,
                    borderStyle = borderStyle,
                    forceShowMaster = false,
                    isSelf = false
                ) {
                    if (!currentTopUser.profileUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = currentTopUser.profileUrl,
                            contentDescription = currentTopUser.name,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFFFFD700), Color(0xFFE65100))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = currentTopUser.name.take(1).uppercase(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // INFO NAMA & JAM & RANK
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                // Header Label + Nama
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Badge Mahkota Mini #1
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFFFD700).copy(alpha = 0.18f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(R.drawable.ic_vip_crown),
                                contentDescription = null,
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "#1 TOP LISTENER",
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFFFD700),
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = currentTopUser.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (verType != VerificationType.NONE) {
                        Spacer(modifier = Modifier.width(4.dp))
                        VerificationBadge(type = verType, size = 13.dp)
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Jumlah Jam & Rank Tier
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = hoursText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFFFD700).copy(alpha = 0.9f)
                    )

                    Text(
                        text = " • ",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.4f)
                    )

                    if (userRank != null) {
                        RankBadge(rank = userRank, displayedRank = null, size = 14.dp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = userRank.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    } else {
                        Text(
                            text = "Pendengar Baru",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // TOMBOL "LIHAT PROFIL"
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFFD700).copy(alpha = 0.14f),
                border = BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.45f)),
                modifier = Modifier.clickable { selectedUserForSheet = targetChatUser }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Profil",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        painter = painterResource(R.drawable.chevron_right),
                        contentDescription = "Lihat Profil",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }

    // Modal Bottom Sheet Tampilan Profil Top 1 Listener
    selectedUserForSheet?.let { chatUser ->
        val namePreferenceManager = remember { NamePreferenceManager(context) }
        val currentUserId = remember {
            IceBeatsStatsCloudSync.resolveStableUserIdBlocking(context, namePreferenceManager)
        }

        UserProfileSheet(
            targetUser = chatUser,
            currentUserId = currentUserId,
            isSelf = chatUser.id == currentUserId,
            onDismiss = { selectedUserForSheet = null },
            onOpenAccountSettings = {
                selectedUserForSheet = null
                navController?.navigate("settings/account")
            }
        )
    }
}

package com.valora.icebeats.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Komponen Border Profil Khusus Level Master v7.0.9
 * 
 * Fitur:
 * 1. Pilihan 4 Tema Border Mahkota & Sayap (Royal Crown, Crimson Wings, Fire Flame, Golden Champion).
 * 2. Posisi lingkaran border 100% pas dan presisi dengan lingkaran avatar.
 * 3. Layer border berada di atas (overlay z-index teratas) mengelilingi foto profil.
 * 4. Efek animasi ambient shimmer glow yang halus dan hemat baterai/GPU.
 * 5. Fallback aman tanpa crash jika aset tidak ditemukan.
 */
@Composable
fun MasterProfileBorder(
    avatarSize: Dp,
    modifier: Modifier = Modifier,
    userRank: icebeatsRank? = null,
    totalListenMs: Long? = null,
    forceShowMaster: Boolean = false,
    borderStyle: MasterBorderStyle? = null,
    isSelf: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val vipManager = remember { VipSubscriptionManager(context) }
    val isLocalVip by vipManager.isVip.collectAsState(initial = false)

    val borderPreferenceManager = remember { BorderPreferenceManager(context) }
    val userSavedStyle by borderPreferenceManager.selectedBorder.collectAsState(initial = MasterBorderStyle.ROYAL_CROWN)

    // Logika Border Premium: Hanya yang membeli VIP/Premium yang berhak menggunakan border.
    // Untuk orang lain di leaderboard/chat: hanya tampil jika orang tersebut punya borderStyle sendiri dari server.
    val effectiveStyle: MasterBorderStyle? = when {
        forceShowMaster -> borderStyle ?: userSavedStyle
        isSelf -> if (isLocalVip) (borderStyle ?: userSavedStyle) else null
        else -> borderStyle // Orang lain: pakai borderStyle miliknya sendiri, jangan fallback ke userSavedStyle
    }

    if (effectiveStyle == null) {
        // Pengguna tanpa border: tampilkan avatar normal tanpa border
        Box(
            modifier = modifier.size(avatarSize),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
        return
    }

    val activeStyle = effectiveStyle

    // Cek resource drawable untuk style aktif, fallback ke master_profile_border
    val borderResId = remember(activeStyle) {
        val primaryId = context.resources.getIdentifier(activeStyle.drawableResName, "drawable", context.packageName)
        if (primaryId != 0) {
            primaryId
        } else {
            val fallbackId = context.resources.getIdentifier("master_profile_border", "drawable", context.packageName)
            if (fallbackId != 0) fallbackId else null
        }
    }

    // Animasi Pulse Shimmer Halus
    val infiniteTransition = rememberInfiniteTransition(label = "MasterBorderAnim")

    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseGlow"
    )

    // Skala dimensi presisi sesuai diameter lingkaran cutout
    val borderOverlaySize = avatarSize * activeStyle.scaleMultiplier

    Box(
        modifier = modifier.size(avatarSize),
        contentAlignment = Alignment.Center
    ) {
        // 1. LAYER BAWAH (z-index: 1): FOTO PROFIL / AVATAR PENGGUNA
        // Berada di belakang ornamen, terlihat melalui lubang lingkaran transparan di tengah border
        Box(
            modifier = Modifier
                .size(avatarSize)
                .clip(CircleShape)
                .zIndex(1f),
            contentAlignment = Alignment.Center
        ) {
            content()
        }

        // 2. LAYER ATAS (z-index: 99f): BORDER / ORNAMEN OVERLAY
        // Ornamen berada di layer paling atas mengelilingi dan menutupi tepi avatar
        if (borderResId != null) {
            Image(
                painter = painterResource(borderResId),
                contentDescription = activeStyle.title,
                modifier = Modifier
                    .requiredSize(borderOverlaySize)
                    .zIndex(99f)
                    .alpha(pulseGlow)
            )
        } else {
            // Fallback cincin emas tipis di atas avatar jika aset PNG belum dimuat
            val borderWidth = (avatarSize.value * 0.05f).coerceIn(1.5f, 3.dp.value).dp
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .border(borderWidth, Color(0xFFFFD700).copy(alpha = pulseGlow), CircleShape)
                    .zIndex(99f)
            )
        }
    }
}

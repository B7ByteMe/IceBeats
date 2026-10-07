package com.valora.icebeats.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
    content: @Composable () -> Unit
) {
    val isMaster = forceShowMaster || remember(userRank, totalListenMs) {
        val rankQualified = userRank != null && userRank.ordinal >= icebeatsRank.Master.ordinal
        val hoursQualified = totalListenMs != null && totalListenMs >= (150L * 3600L * 1000L)
        rankQualified || hoursQualified
    }

    if (!isMaster) {
        // Pengguna non-Master: tampilkan avatar normal tanpa border
        Box(
            modifier = modifier.size(avatarSize),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
        return
    }

    val context = LocalContext.current
    val borderPreferenceManager = remember { BorderPreferenceManager(context) }
    val userSavedStyle by borderPreferenceManager.selectedBorder.collectAsState(initial = MasterBorderStyle.ROYAL_CROWN)
    val activeStyle = borderStyle ?: userSavedStyle

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

    val shimmerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerRotation"
    )

    // Skala dimensi presisi
    val borderOverlaySize = avatarSize * activeStyle.scaleMultiplier
    val offsetY = avatarSize * activeStyle.offsetYRatio
    val borderWidth = (avatarSize.value * 0.045f).coerceIn(1.5f, 3.dp.value).dp

    Box(
        modifier = modifier.size(avatarSize),
        contentAlignment = Alignment.Center
    ) {
        // 1. AURA AMBIENT GLOW DI BELAKANG AVATAR
        val auraBrush = remember(activeStyle) {
            when (activeStyle) {
                MasterBorderStyle.FIRE_FLAME -> Brush.sweepGradient(
                    listOf(
                        Color(0xFFFF4500).copy(alpha = 0.6f),
                        Color(0xFFFFD700).copy(alpha = 0.3f),
                        Color(0xFFFF0000).copy(alpha = 0.7f),
                        Color(0xFFFF8C00).copy(alpha = 0.4f),
                        Color(0xFFFF4500).copy(alpha = 0.6f)
                    )
                )
                MasterBorderStyle.CRIMSON_WING -> Brush.sweepGradient(
                    listOf(
                        Color(0xFFDC143C).copy(alpha = 0.6f),
                        Color(0xFFFFD700).copy(alpha = 0.4f),
                        Color(0xFF8B0000).copy(alpha = 0.7f),
                        Color(0xFFFFA500).copy(alpha = 0.3f),
                        Color(0xFFDC143C).copy(alpha = 0.6f)
                    )
                )
                else -> Brush.sweepGradient(
                    listOf(
                        Color(0xFFFFD700).copy(alpha = 0.6f),
                        Color(0xFFFF8C00).copy(alpha = 0.3f),
                        Color(0xFFFFA500).copy(alpha = 0.7f),
                        Color(0xFFFFD700).copy(alpha = 0.2f),
                        Color(0xFFFF8C00).copy(alpha = 0.5f),
                        Color(0xFFFFD700).copy(alpha = 0.6f)
                    )
                )
            }
        }

        Box(
            modifier = Modifier
                .size(avatarSize * 1.05f)
                .rotate(shimmerRotation)
                .alpha(pulseGlow * 0.4f)
                .clip(CircleShape)
                .border(borderWidth, auraBrush, CircleShape)
        )

        // 2. FOTO PROFIL / AVATAR PENGGUNA (DI BAWAH BORDER)
        Box(
            modifier = Modifier
                .size(avatarSize)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            content()
        }

        // 3. BORDER OVERLAY (DI ATAS FOTO PROFIL - Z-INDEX TERATAS)
        if (borderResId != null) {
            val painterResult = runCatching { painterResource(borderResId) }
            if (painterResult.isSuccess) {
                Image(
                    painter = painterResult.getOrThrow(),
                    contentDescription = activeStyle.title,
                    modifier = Modifier
                        .size(borderOverlaySize)
                        .offset(y = offsetY)
                        .alpha(pulseGlow)
                )
            }
        } else {
            // Fallback elegan cincin emas jika PNG tidak ditemukan
            Box(
                modifier = Modifier
                    .size(avatarSize * 1.04f)
                    .clip(CircleShape)
                    .border(borderWidth * 1.2f, Color(0xFFFFD700).copy(alpha = pulseGlow), CircleShape)
            )
        }
    }
}

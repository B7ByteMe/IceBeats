package com.valora.icebeats.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class VerificationType {
    NONE,
    PREMIUM,   // Biru (Blue) - Akun Member VIP / Premium
    DEVELOPER  // Merah (Red) - Khusus Developer
}

object VerificationHelper {
    /**
     * Parsing tipe verifikasi berdasarkan plan dan status akun
     * - Merah (DEVELOPER): jika planName mengandung "developer" atau role == "developer"
     * - Biru (PREMIUM): jika user berlangganan VIP / Premium aktif
     */
    fun parseVerificationType(
        planName: String? = null,
        role: String? = null,
        isVip: Boolean = false,
        verificationBadge: String? = null
    ): VerificationType {
        val badge = verificationBadge?.trim()?.lowercase()
        if (badge == "developer" || badge == "red") return VerificationType.DEVELOPER
        if (badge == "premium" || badge == "blue" || badge == "vip") return VerificationType.PREMIUM

        val r = role?.trim()?.lowercase()
        val plan = planName?.trim()?.lowercase()

        if (r == "developer" || plan?.contains("developer") == true) {
            return VerificationType.DEVELOPER
        }

        if (isVip || (plan != null && plan.isNotBlank() && plan != "gratis" && plan != "none" && plan != "null")) {
            return VerificationType.PREMIUM
        }

        return VerificationType.NONE
    }
}

/**
 * Komponen icon verifikasi resmi (Centang Terverifikasi)
 * - Warna Biru: Member VIP / Premium
 * - Warna Merah: Khusus Developer
 */
@Composable
fun VerificationBadge(
    type: VerificationType,
    modifier: Modifier = Modifier,
    size: Dp = 15.dp
) {
    if (type == VerificationType.NONE) return

    val badgeColor = when (type) {
        VerificationType.DEVELOPER -> Color(0xFFE53935) // Merah Cerah Developer
        VerificationType.PREMIUM -> Color(0xFF1D9BF0)   // Biru Resmi Verified (Twitter/Telegram)
        VerificationType.NONE -> Color.Transparent
    }

    Box(
        modifier = modifier
            .size(size)
            .background(badgeColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = if (type == VerificationType.DEVELOPER) "Developer Terverifikasi" else "VIP Member Terverifikasi",
            tint = Color.White,
            modifier = Modifier.size(size * 0.68f)
        )
    }
}

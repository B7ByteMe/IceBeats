package com.valora.icebeats.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * Wrapper avatar tanpa dekorasi border (fitur border profil telah dihapus).
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
    // Border feature removed: render avatar content directly without decorative frame
    Box(
        modifier = modifier.size(avatarSize),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

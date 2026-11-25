package com.valora.icebeats.db.entities

import androidx.compose.runtime.Immutable

@Immutable
data class SongWithStats(
    val id: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val songCountListened: Int,
    val timeListened: Long?,
)

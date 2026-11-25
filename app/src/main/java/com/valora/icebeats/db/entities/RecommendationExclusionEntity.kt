package com.valora.icebeats.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "recommendation_exclusions")
data class RecommendationExclusionEntity(
    @PrimaryKey val songId: String,
    val title: String = "",
    val artist: String = "",
    val thumbnailUrl: String? = null,
    val excludedAt: Long = System.currentTimeMillis(),
)

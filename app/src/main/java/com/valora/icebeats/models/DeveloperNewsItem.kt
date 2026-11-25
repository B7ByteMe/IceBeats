package com.valora.icebeats.models

import androidx.annotation.Keep

@Keep
data class DeveloperNewsItem(
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val author: String? = null,
    val imageUrl: String? = null,
    val actionUrl: String? = null,
    val actionText: String? = null,
    val tag: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val showPopup: Boolean = false,
    val priority: Int = 0,
    val active: Boolean = true
)

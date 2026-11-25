package com.valora.icebeats.ui.component

import androidx.compose.ui.Modifier
import com.valora.icebeats.ui.screens.Screens

/**
 * Legacy tab swipe gesture stub. Top-level tabs are now driven directly
 * by Compose's native HorizontalPager for 1:1 fluid tracking and snap physics.
 */
fun Modifier.tabSwipeGesture(
    enabled: Boolean = true,
    currentRoute: String? = null,
    navigationItems: List<Screens> = emptyList(),
    onNavigateToRoute: (String) -> Unit = {},
    edgeExcludePx: Float = 48f,
): Modifier = this

package com.valora.icebeats.ui.component

data class CurvedBottomNavigationItem(
    val iconInactive: Int,
    val iconActive: Int,
    val titleId: Int = 0
)

typealias BottomNavigationItem = CurvedBottomNavigationItem

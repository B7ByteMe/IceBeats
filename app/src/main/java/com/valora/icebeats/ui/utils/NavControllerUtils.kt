package com.valora.icebeats.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.util.fastAny
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import com.valora.icebeats.ui.screens.Screens

val NavController.canNavigateUp: Boolean
    get() = currentBackStackEntry?.destination?.parent?.route != null

fun NavController.backToMain() {
    try {
        val mainRoutes = setOf(
            Screens.Home.route,
            Screens.Search.route,
            Screens.Explore.route,
            Screens.Library.route,
            Screens.Stats.route
        )
        val mainDestination = currentBackStack.value.lastOrNull { entry ->
            val route = entry.destination.route
            route != null && route in mainRoutes
        }?.destination?.route ?: graph.startDestinationRoute ?: Screens.Home.route

        val popped = popBackStack(mainDestination, inclusive = false)
        if (!popped) {
            navigate(Screens.Home.route) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    } catch (e: Exception) {
        timber.log.Timber.e(e, "Error navigating back to main")
        try {
            navigate(Screens.Home.route) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        } catch (_: Exception) {}
    }
}

@Composable
inline fun <reified VM : ViewModel> safeHiltViewModel(): VM? {
    val owner = LocalViewModelStoreOwner.current ?: return null
    if (owner is NavBackStackEntry) {
        if (owner.lifecycle.currentState == Lifecycle.State.DESTROYED) {
            return null
        }
    }
    return androidx.hilt.navigation.compose.hiltViewModel<VM>()
}

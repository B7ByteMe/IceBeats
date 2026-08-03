package com.valora.icebeats.ui.screens

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.valora.icebeats.ui.screens.onboarding.OnboardingScreen

@Composable
fun LoginScreen(navController: NavController) {
    OnboardingScreen(navController = navController)
}

package com.valora.icebeats.ui.screens.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.valora.icebeats.R
import com.valora.icebeats.ui.component.AvatarSelector
import com.valora.icebeats.ui.component.NamePreferenceManager
import com.valora.icebeats.ui.component.bitChordSettingsBackground
import com.valora.icebeats.ui.component.bitChordSettingsCardColor
import com.valora.icebeats.ui.component.bitChordSettingsOutlineColor
import com.valora.icebeats.ui.component.bitChordSettingsSecondaryTextColor
import com.valora.icebeats.ui.component.isBitChordDarkTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuestProfileSetupScreen(navController: NavController) {
    val context = LocalContext.current
    val namePrefManager = remember { NamePreferenceManager(context) }
    var name by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    val dark = isBitChordDarkTheme()
    val bgColor = bitChordSettingsBackground()
    val cardColor = bitChordSettingsCardColor()
    val outlineColor = bitChordSettingsOutlineColor()
    val secondaryColor = bitChordSettingsSecondaryTextColor()
    val primaryTextColor = if (dark) Color.White else Color.Black

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        // Clean BitChord-style Back Button
        Box(
            modifier = Modifier
                .statusBarsPadding()
                .padding(16.dp)
                .align(Alignment.TopStart)
                .size(40.dp)
                .clip(CircleShape)
                .background(cardColor)
                .clickable {
                    if (!navController.popBackStack()) {
                        navController.navigate("onboarding") {
                            popUpTo("guest_profile_setup") { inclusive = true }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.arrow_back),
                contentDescription = "Back",
                tint = primaryTextColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(R.drawable.icebeats_logo),
                contentDescription = "IceBeats Logo",
                modifier = Modifier
                    .size(68.dp)
                    .padding(bottom = 10.dp)
            )

            Text(
                text = "IceBeats",
                color = primaryTextColor,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Text(
                text = "Continue as Guest",
                color = secondaryColor,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            AvatarSelector(useLiquidSilverTheme = true)

            Spacer(modifier = Modifier.height(18.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.what_should_we_call_you),
                    color = secondaryColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = {
                        Text(
                            text = "Nama panggilan kamu...",
                            color = secondaryColor
                        )
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.person),
                            contentDescription = null,
                            tint = secondaryColor,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = primaryTextColor,
                        unfocusedTextColor = primaryTextColor,
                        focusedBorderColor = primaryTextColor.copy(alpha = 0.65f),
                        unfocusedBorderColor = outlineColor,
                        cursorColor = primaryTextColor,
                        focusedContainerColor = cardColor,
                        unfocusedContainerColor = cardColor
                    ),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            val isContinueEnabled = name.isNotBlank()
            val btnContainer = if (isContinueEnabled) primaryTextColor else cardColor
            val btnContent = if (isContinueEnabled) bgColor else secondaryColor

            Button(
                onClick = {
                    if (isContinueEnabled) {
                        coroutineScope.launch {
                            namePrefManager.saveUserName(name.trim())
                            navController.navigate("home") {
                                popUpTo("onboarding") { inclusive = true }
                            }
                        }
                    }
                },
                enabled = isContinueEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = btnContainer,
                    contentColor = btnContent,
                    disabledContainerColor = cardColor,
                    disabledContentColor = secondaryColor
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = "Continue",
                    color = btnContent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

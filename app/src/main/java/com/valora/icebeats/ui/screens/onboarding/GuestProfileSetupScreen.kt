package com.valora.icebeats.ui.screens.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.valora.icebeats.ui.component.NamePreferenceManager
import com.valora.icebeats.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuestProfileSetupScreen(navController: NavController) {
    val context = LocalContext.current
    val namePrefManager = remember { NamePreferenceManager(context) }
    var name by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    val darkGrayBg = Color(0xFF0F0F0F)

    val neonGreenColorScheme = remember {
        darkColorScheme(
            primary = Color(0xFFCCFF00),
            onPrimary = Color.Black,
            secondary = Color(0xFFCCFF00),
            onSecondary = Color.Black,
            background = darkGrayBg,
            onBackground = Color.White,
            surface = darkGrayBg,
            onSurface = Color.White,
            surfaceVariant = Color(0xFF161616),
            onSurfaceVariant = Color.White.copy(alpha = 0.7f),
            outline = Color.White.copy(alpha = 0.15f)
        )
    }

    MaterialTheme(colorScheme = neonGreenColorScheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(darkGrayBg)
                .systemBarsPadding()
        ) {
            // Top back button
            IconButton(
                onClick = {
                    if (!navController.popBackStack()) {
                        navController.navigate("onboarding")
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back),
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Logo
                Image(
                    painter = painterResource(R.drawable.icebeats_logo),
                    contentDescription = "IceBeats Logo",
                    modifier = Modifier
                        .size(80.dp)
                        .padding(bottom = 12.dp)
                )

                Text(
                    text = "IceBeats",
                    color = Color(0xFFCCFF00),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Text(
                    text = "Continue as Guest",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 28.dp)
                )

                com.valora.icebeats.ui.component.AvatarSelector()

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.what_should_we_call_you), color = Color(0xFFCCFF00).copy(alpha = 0.7f)) },
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White.copy(alpha = 0.8f),
                        focusedIndicatorColor = Color(0xFFCCFF00),
                        unfocusedIndicatorColor = Color.White.copy(alpha = 0.2f),
                        cursorColor = Color(0xFFCCFF00),
                        focusedContainerColor = Color.White.copy(alpha = 0.07f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.04f)
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            coroutineScope.launch {
                                namePrefManager.saveUserName(name)
                                navController.navigate("home") {
                                    popUpTo("onboarding") { inclusive = true }
                                }
                            }
                        }
                    },
                    enabled = name.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFCCFF00),
                        contentColor = Color.Black,
                        disabledContainerColor = Color.White.copy(alpha = 0.1f),
                        disabledContentColor = Color.White.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(
                        text = "Continue",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

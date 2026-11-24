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
import com.valora.icebeats.ui.component.drawBackdropCustomShape
import com.valora.icebeats.ui.component.layerBackdrop
import com.valora.icebeats.ui.component.rememberBackdrop
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuestProfileSetupScreen(navController: NavController) {
    val context = LocalContext.current
    val namePrefManager = remember { NamePreferenceManager(context) }
    var name by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    val backdrop = rememberBackdrop()
    val glassLayer = rememberGraphicsLayer()

    val darkObsidianBg = Color(0xFF06070A)
    val silverAccent = Color(0xFFE2E8F0)

    val liquidSilverColorScheme = remember {
        darkColorScheme(
            primary = silverAccent,
            onPrimary = Color(0xFF0C1016),
            secondary = silverAccent,
            onSecondary = Color(0xFF0C1016),
            background = darkObsidianBg,
            onBackground = Color.White,
            surface = Color(0xFF10141C),
            onSurface = Color.White,
            surfaceVariant = Color(0xFF161B24),
            onSurfaceVariant = silverAccent.copy(alpha = 0.75f),
            outline = Color.White.copy(alpha = 0.22f)
        )
    }

    MaterialTheme(colorScheme = liquidSilverColorScheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(darkObsidianBg)
        ) {
            // Subtle monochrome silver-titanium spatial depth for glass refraction
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
            ) {
                drawRect(darkObsidianBg)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF94A3B8).copy(alpha = 0.15f),
                            Color(0xFF475569).copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.18f, size.height * 0.80f),
                        radius = size.width * 0.85f
                    ),
                    center = Offset(size.width * 0.18f, size.height * 0.80f),
                    radius = size.width * 0.85f
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFCBD5E1).copy(alpha = 0.11f),
                            Color(0xFF64748B).copy(alpha = 0.04f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.85f, size.height * 0.45f),
                        radius = size.width * 0.75f
                    ),
                    center = Offset(size.width * 0.85f, size.height * 0.45f),
                    radius = size.width * 0.75f
                )
            }

            // Top-left iOS Liquid Glass Back Button
            Box(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(16.dp)
                    .align(Alignment.TopStart)
                    .size(44.dp)
                    .clip(CircleShape)
                    .drawBackdropCustomShape(
                        backdrop = backdrop,
                        layer = glassLayer,
                        luminanceAnimation = 0.35f,
                        shape = CircleShape
                    )
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.18f),
                                Color.White.copy(alpha = 0.06f)
                            )
                        ),
                        shape = CircleShape
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = 0.52f),
                                Color.White.copy(alpha = 0.12f),
                                Color.White.copy(alpha = 0.28f)
                            )
                        ),
                        shape = CircleShape
                    )
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
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Clean app logo (no glow/shadow behind it)
                Image(
                    painter = painterResource(R.drawable.icebeats_logo),
                    contentDescription = "IceBeats Logo",
                    modifier = Modifier
                        .size(72.dp)
                        .padding(bottom = 10.dp)
                )

                // Pure white IceBeats title
                Text(
                    text = "IceBeats",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Text(
                    text = "Continue as Guest",
                    color = silverAccent.copy(alpha = 0.72f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // Liquid Glass Avatar Selector
                AvatarSelector(useLiquidSilverTheme = true)

                Spacer(modifier = Modifier.height(18.dp))

                // Clean & simple Liquid Glass Name Input
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.what_should_we_call_you),
                        color = silverAccent.copy(alpha = 0.78f),
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
                                color = Color.White.copy(alpha = 0.42f)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.person),
                                contentDescription = null,
                                tint = silverAccent.copy(alpha = 0.68f),
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = silverAccent.copy(alpha = 0.85f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                            cursorColor = silverAccent,
                            focusedContainerColor = Color.White.copy(alpha = 0.11f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.06f)
                        ),
                        shape = RoundedCornerShape(18.dp),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Silver Liquid Glass Continue Button
                val isContinueEnabled = name.isNotBlank()
                val silverGradient = remember {
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFF8FAFC),
                            Color(0xFFD8E0EA),
                            Color(0xFFB4BFC9)
                        )
                    )
                }
                val silverBorder = remember {
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.92f),
                            Color(0xFF8C98A8).copy(alpha = 0.55f)
                        )
                    )
                }

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
                        containerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent
                    ),
                    contentPadding = PaddingValues(),
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(CircleShape)
                        .then(
                            if (isContinueEnabled) {
                                Modifier
                                    .background(brush = silverGradient, shape = CircleShape)
                                    .border(width = 1.dp, brush = silverBorder, shape = CircleShape)
                            } else {
                                Modifier
                                    .background(Color.White.copy(alpha = 0.08f), shape = CircleShape)
                                    .border(
                                        width = 1.dp,
                                        color = Color.White.copy(alpha = 0.16f),
                                        shape = CircleShape
                                    )
                            }
                        )
                ) {
                    Text(
                        text = "Continue",
                        color = if (isContinueEnabled) Color(0xFF0C1016) else Color.White.copy(alpha = 0.40f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

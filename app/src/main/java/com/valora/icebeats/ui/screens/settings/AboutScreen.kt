// icebeats - About Screen (Premium Redesign)
package com.valora.icebeats.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.valora.icebeats.BuildConfig
import com.valora.icebeats.LocalPlayerAwareWindowInsets
import com.valora.icebeats.R
import com.valora.icebeats.ui.component.IconButton

// ==================== INFO ROW ITEM ====================

@Composable
private fun InfoRow(
    label: String,
    value: String,
    isLast: Boolean = false
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.5f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
        if (!isLast) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .padding(horizontal = 20.dp)
                    .background(Color.White.copy(alpha = 0.06f))
            )
        }
    }
}

// ==================== SOCIAL LINK BUTTON ====================

@Composable
private fun SocialLinkButton(
    label: String,
    iconRes: Int,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Color(0xFF1F251A), shape = RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFFCCFF00).copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = Color(0xFFCCFF00),
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )
        Icon(
            painter = painterResource(R.drawable.arrow_forward),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.25f),
            modifier = Modifier.size(16.dp)
        )
    }
}

// ==================== MAIN ABOUT SCREEN ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val uriHandler = LocalUriHandler.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.about),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                painter = painterResource(R.drawable.arrow_back),
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF0F0F0F),
                        scrolledContainerColor = Color(0xFF0F0F0F)
                    ),
                    scrollBehavior = scrollBehavior
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                        )
                    )
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(24.dp))

                // App logo
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFF161616))
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(22.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.icebeats_monochrome),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(Color(0xFFCCFF00), BlendMode.SrcIn),
                        modifier = Modifier
                            .size(52.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // App name
                Text(
                    text = "IceBeats",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "Advanced YouTube Music Client",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.45f)
                )

                Spacer(Modifier.height(8.dp))

                // Version badge
                Box(
                    modifier = Modifier
                        .background(Color(0xFF1F251A), shape = RoundedCornerShape(20.dp))
                        .border(1.dp, Color(0xFFCCFF00).copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "v${BuildConfig.VERSION_NAME}${if (BuildConfig.DEBUG) " · DEBUG" else ""}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFCCFF00),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.height(28.dp))

                // ─── App Info Card ────────────────────────────────────────
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        InfoRow(label = "Developer", value = "Valora · Zyxone")
                        InfoRow(label = "Version", value = BuildConfig.VERSION_NAME)
                        InfoRow(label = "Build", value = if (BuildConfig.DEBUG) "Debug" else "Release")
                        InfoRow(label = "Package", value = "com.valora.icebeats", isLast = true)
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ─── Social Links Card ──────────────────────────────────
                Text(
                    text = "LINKS",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = Color(0xFFCCFF00).copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.Start)
                        .padding(start = 4.dp, bottom = 8.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        SocialLinkButton(
                            label = "Website",
                            iconRes = R.drawable.resource_public,
                            onClick = { uriHandler.openUri("https://icebeats.pages.dev/") }
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .padding(start = 72.dp, end = 20.dp)
                                .background(Color.White.copy(alpha = 0.06f))
                        )
                        SocialLinkButton(
                            label = "GitHub",
                            iconRes = R.drawable.github,
                            onClick = { uriHandler.openUri("https://github.com/d0x-dev") }
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .padding(start = 72.dp, end = 20.dp)
                                .background(Color.White.copy(alpha = 0.06f))
                        )
                        SocialLinkButton(
                            label = "Telegram",
                            iconRes = R.drawable.telegram,
                            onClick = { uriHandler.openUri("https://t.me/songpy") }
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .padding(start = 72.dp, end = 20.dp)
                                .background(Color.White.copy(alpha = 0.06f))
                        )
                        SocialLinkButton(
                            label = "Instagram",
                            iconRes = R.drawable.instagram,
                            onClick = { uriHandler.openUri("https://instagram.com/dark__336") }
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))

                // Footer
                Text(
                    text = "Made with ♥ by Valora & Zyxone",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.25f)
                )

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

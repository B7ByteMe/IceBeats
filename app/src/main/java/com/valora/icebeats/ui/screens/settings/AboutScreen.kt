// IceBeats - About and Contributors Screen
package com.valora.icebeats.ui.screens.settings

import androidx.compose.animation.core.*
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.valora.icebeats.BuildConfig
import com.valora.icebeats.LocalPlayerAwareWindowInsets
import com.valora.icebeats.LocalPlayerConnection
import com.valora.icebeats.R
import com.valora.icebeats.ui.component.IconButton
import com.valora.icebeats.ui.component.isFrostedGlassUiEnabled
import com.valora.icebeats.ui.component.settingsCardContainerColor
import com.valora.icebeats.ui.component.settingsCardBorder

// ==================== SHIMMER EFFECT ====================

@Composable
fun shimmerEffect(): Brush {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    )

    val transition = rememberInfiniteTransition(label = "shimmerEffect")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "shimmerEffect"
    )

    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnim.value, y = translateAnim.value)
    )
}

// ==================== USER CARD ====================

@Composable
fun SocialIconBadge(
    iconRes: Int,
    onClick: () -> Unit
) {
    val isFrosted = isFrostedGlassUiEnabled()
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(
                if (isFrosted) Color.White.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            )
            .border(
                1.dp,
                if (isFrosted) Color.White.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = if (isFrosted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun UserCard(
    imageUrl: String,
    name: String,
    role: String,
    commits: Int? = null,
    githubUrl: String? = null,
    telegramUrl: String? = null,
    instagramUrl: String? = null,
    websiteUrl: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val isFrosted = isFrostedGlassUiEnabled()

    val borderBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f)
        )
    )

    val cardBorder = if (isFrosted) {
        settingsCardBorder(true) ?: androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    } else {
        androidx.compose.foundation.BorderStroke(1.dp, borderBrush)
    }

    Card(
        modifier = modifier
            .padding(horizontal = 6.dp, vertical = 8.dp)
            .height(240.dp)
            .scale(if (isPressed) 0.98f else 1f)
            .then(
                if (isFrosted) {
                    Modifier
                } else {
                    Modifier.shadow(
                        elevation = 16.dp,
                        shape = RoundedCornerShape(24.dp),
                        ambientColor = MaterialTheme.colorScheme.primary,
                        spotColor = MaterialTheme.colorScheme.primary
                    )
                }
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isPressed = true
                onClick()
                isPressed = false
            },
        shape = RoundedCornerShape(24.dp),
        border = cardBorder,
        colors = CardDefaults.cardColors(
            containerColor = if (isFrosted) settingsCardContainerColor(true) else MaterialTheme.colorScheme.surfaceContainer,
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Avatar - Centered at top
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(
                            if (isFrosted) {
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.12f),
                                        Color.White.copy(alpha = 0.04f)
                                    )
                                )
                            } else {
                                Brush.radialGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                                    )
                                )
                            }
                        )
                        .then(
                            if (isFrosted) {
                                Modifier.border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                            } else {
                                Modifier.border(1.5.dp, borderBrush, CircleShape)
                            }
                        )
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Text - Centered
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                )

                Spacer(modifier = Modifier.height(2.dp))

                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isFrosted) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    border = if (isFrosted) androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)) else null
                ) {
                    Text(
                        text = role,
                        modifier = Modifier.padding(
                            horizontal = 10.dp,
                            vertical = 4.dp
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isFrosted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (commits != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isFrosted) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        border = if (isFrosted) androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)) else null
                    ) {
                        Text(
                            text = "$commits Commits",
                            modifier = Modifier.padding(
                                horizontal = 10.dp,
                                vertical = 4.dp
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isFrosted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Social Badges Row at the bottom of the card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val contextUriHandler = LocalUriHandler.current
                if (githubUrl != null) {
                    SocialIconBadge(
                        iconRes = R.drawable.github,
                        onClick = { contextUriHandler.openUri(githubUrl) }
                    )
                }
                if (telegramUrl != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    SocialIconBadge(
                        iconRes = R.drawable.telegram,
                        onClick = { contextUriHandler.openUri(telegramUrl) }
                    )
                }
                if (instagramUrl != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    SocialIconBadge(
                        iconRes = R.drawable.instagram,
                        onClick = { contextUriHandler.openUri(instagramUrl) }
                    )
                }
                if (websiteUrl != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    SocialIconBadge(
                        iconRes = R.drawable.resource_public,
                        onClick = { contextUriHandler.openUri(websiteUrl) }
                    )
                }
            }
        }
    }
}

// ==================== SOCIAL ICON ROW ====================

@Composable
fun SocialIconRow(uriHandler: UriHandler) {
    val isFrosted = isFrostedGlassUiEnabled()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(4.dp, RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = settingsCardContainerColor(isFrosted)
        ),
        border = settingsCardBorder(isFrosted)
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { uriHandler.openUri("https://www.facebook.com/venom.digital.creator") }
            ) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    painter = painterResource(R.drawable.facebook),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(
                onClick = { uriHandler.openUri("https://www.instagram.com/Dark__336/") }
            ) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    painter = painterResource(R.drawable.instagram),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(
                onClick = { uriHandler.openUri("https://github.com/B7ByteMe") }
            ) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    painter = painterResource(R.drawable.github),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(
                onClick = { uriHandler.openUri("https://g.dev/Darkboy336") }
            ) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    painter = painterResource(R.drawable.google),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(
                onClick = { uriHandler.openUri("https://IceBeats.stormx.pw/") }
            ) {
                Icon(
                    modifier = Modifier.size(22.dp),
                    painter = painterResource(R.drawable.resource_public),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ==================== MAIN ABOUT SCREEN ====================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AboutScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val shimmerBrush = shimmerEffect()
    var logoTapCount by remember { mutableIntStateOf(0) }
    var versionTapCount by remember { mutableIntStateOf(0) }

    val infiniteTransition = rememberInfiniteTransition(label = "")
    val logoScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500),
            repeatMode = RepeatMode.Reverse
        ),
        label = ""
    )

    // Get player connection for album artwork
    val playerConnection = LocalPlayerConnection.current
    val mediaMetadata by playerConnection?.mediaMetadata?.collectAsState()
        ?: remember { mutableStateOf(null) }

    Box(modifier = Modifier.fillMaxSize().background(com.valora.icebeats.ui.component.bitChordSettingsBackground())) {
// Main Scaffold with new TopAppBar
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = com.valora.icebeats.ui.component.bitChordSettingsBackground(),
            topBar = {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        androidx.compose.material3.IconButton(onClick = { navController.navigateUp() }) {
                            Icon(
                                painter = painterResource(R.drawable.arrow_back),
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = com.valora.icebeats.ui.component.bitChordSettingsBackground(),
                        scrolledContainerColor = com.valora.icebeats.ui.component.bitChordSettingsBackground()
                    ),
                    scrollBehavior = scrollBehavior
                )
            }
        ) { innerPadding ->
            // Content with proper padding from Scaffold
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                        )
                    )
                    .padding(innerPadding) // This pushes content below the top bar
            ) {
                com.valora.icebeats.ui.component.BitChordSettingsPageTitle(stringResource(R.string.about))

                val cardColor = com.valora.icebeats.ui.component.bitChordSettingsCardColor()
                val outlineColor = com.valora.icebeats.ui.component.bitChordSettingsOutlineColor()
                val secondaryColor = com.valora.icebeats.ui.component.bitChordSettingsSecondaryTextColor()
                val primaryTextColor = MaterialTheme.colorScheme.onBackground

                val gifImageLoader = remember(context) {
                    coil.ImageLoader.Builder(context)
                        .components {
                            if (android.os.Build.VERSION.SDK_INT >= 28) {
                                add(coil.decode.ImageDecoderDecoder.Factory())
                            } else {
                                add(coil.decode.GifDecoder.Factory())
                            }
                        }
                        .build()
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 1. App Header Card (matching reference)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(com.valora.icebeats.ui.component.BitChordGroupShape)
                            .background(cardColor)
                            .clickable {
                                logoTapCount++
                                if (logoTapCount >= 7) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "IceBeats • Valora Project",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                    logoTapCount = 0
                                }
                            }
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(R.drawable.icebeats_logo),
                            contentDescription = "IceBeats",
                            modifier = Modifier
                                .size(50.dp)
                                .scale(logoScale)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "IceBeats",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.4).sp
                                ),
                                color = primaryTextColor
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(outlineColor)
                                        .clickable { navController.navigate("settings/app_info") }
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = BuildConfig.VERSION_NAME,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = primaryTextColor
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(outlineColor)
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "UNIVERSAL",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = secondaryColor
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2. Developer Banner + Profile Card (matching reference)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(cardColor)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(250.dp)
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data("https://imagepaste.org/i/j5tevf2p.gif")
                                        .crossfade(true)
                                        .build(),
                                    imageLoader = gifImageLoader,
                                    contentDescription = "Developer Banner",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(110.dp)
                                        .align(Alignment.BottomCenter)
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    cardColor.copy(alpha = 0.85f),
                                                    cardColor
                                                )
                                            )
                                        )
                                )
                            }
                            Spacer(modifier = Modifier.height(52.dp))
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 20.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "aprizall_febrian",
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = (-0.5).sp
                                    ),
                                    color = primaryTextColor
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "PENGEMBANG",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        letterSpacing = 0.8.sp
                                    ),
                                    fontWeight = FontWeight.SemiBold,
                                    color = secondaryColor
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data("https://imagepaste.org/i/sxqwaxdm.jpg")
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "aprizall_febrian",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(CircleShape)
                                    .border(3.dp, cardColor, CircleShape)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 3. MEDIA SOSIAL section
                    Text(
                        text = "MEDIA SOSIAL",
                        style = MaterialTheme.typography.labelMedium.copy(
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = secondaryColor
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val socialLinks = listOf(
                        Triple(R.drawable.telegram, "Telegram · @Riz_BuyX", "https://t.me/Riz_BuyX"),
                        Triple(R.drawable.resource_public, "Website · valoraproject.my.id", "https://valoraproject.my.id"),
                        Triple(R.drawable.ic_whatsapp, "Saluran WhatsApp · VALORA PROJECT", "https://whatsapp.com/channel/0029Vb8V2qh8V0tpL0VDky0M")
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        socialLinks.forEach { (iconRes, label, url) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(CircleShape)
                                    .background(cardColor)
                                    .border(0.5.dp, outlineColor, CircleShape)
                                    .clickable { runCatching { uriHandler.openUri(url) } }
                                    .padding(horizontal = 20.dp, vertical = 15.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    painter = painterResource(iconRes),
                                    contentDescription = null,
                                    tint = primaryTextColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = primaryTextColor
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun AboutScreenPreview() {
    com.valora.icebeats.ui.theme.IceBeatsTheme {
        androidx.compose.runtime.CompositionLocalProvider(
            com.valora.icebeats.LocalPlayerAwareWindowInsets provides androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
        ) {
            val scrollBehavior = androidx.compose.material3.TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
            AboutScreen(
                navController = androidx.navigation.compose.rememberNavController(),
                scrollBehavior = scrollBehavior
            )
        }
    }
}

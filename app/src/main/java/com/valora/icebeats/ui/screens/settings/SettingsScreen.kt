package com.valora.icebeats.ui.screens.settings

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.valora.icebeats.innertube.utils.parseCookieString
import com.valora.icebeats.BuildConfig
import com.valora.icebeats.LocalPlayerAwareWindowInsets
import com.valora.icebeats.LocalPlayerConnection
import com.valora.icebeats.R
import com.valora.icebeats.constants.AccountNameKey
import com.valora.icebeats.constants.AppModeKey
import com.valora.icebeats.constants.APP_MODE_LITE
import com.valora.icebeats.constants.APP_MODE_NORMAL
import com.valora.icebeats.constants.InnerTubeCookieKey
import com.valora.icebeats.ui.component.AppleSegmentedSwitch
import com.valora.icebeats.ui.component.AvatarPreferenceManager
import com.valora.icebeats.ui.component.AvatarSelection
import com.valora.icebeats.ui.component.BitChordSettingsGroup
import com.valora.icebeats.ui.component.BitChordSettingsPageTitle
import com.valora.icebeats.ui.component.BitChordSettingsRow
import com.valora.icebeats.ui.component.BitChordSettingsSearchField
import com.valora.icebeats.ui.component.BitChordRowDivider
import com.valora.icebeats.ui.component.bitChordSettingsBackground
import com.valora.icebeats.ui.component.bitChordSettingsCardColor
import com.valora.icebeats.ui.component.bitChordSettingsSecondaryTextColor
import com.valora.icebeats.ui.component.ChangelogScreen
import com.valora.icebeats.ui.component.UpdateAvailableDialog
import com.valora.icebeats.ui.component.isFrostedGlassUiEnabled
import com.valora.icebeats.ui.component.settingsCardContainerColor
import com.valora.icebeats.ui.component.settingsCardBorder
import com.valora.icebeats.utils.RemoteConfigManager
import com.valora.icebeats.utils.UpdateInfo
import com.valora.icebeats.utils.Updater
import com.valora.icebeats.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

// ==================== DIVIDER COMPONENT ====================

@Composable
fun SettingsDivider(
    modifier: Modifier = Modifier,
    color: Color = Color.White.copy(alpha = 0.1f),
    thickness: Dp = 0.5.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(thickness)
            .background(color)
    )
}

// ==================== SETTINGS CATEGORY COMPONENTS ====================

data class SettingsCategoryItem(
    val icon: androidx.compose.ui.graphics.painter.Painter,
    val title: @Composable () -> Unit,
    val trailingContent: @Composable (() -> Unit)? = null,
    val onClick: () -> Unit
)

@Composable
fun SettingsCategory(
    title: String,
    items: List<SettingsCategoryItem>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            ),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
        )

        val isFrosted = isFrostedGlassUiEnabled()
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = settingsCardContainerColor(isFrosted)
            ),
            border = settingsCardBorder(isFrosted),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                items.forEachIndexed { index, item ->
                    SettingsCategoryItemContent(
                        item = item,
                        isLast = index == items.lastIndex
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsCategoryItemContent(
    item: SettingsCategoryItem,
    isLast: Boolean
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(if (isPressed) 0.98f else 1f)
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (isPressed) {
                    Modifier.background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.1f),
                                Color.White.copy(alpha = 0.05f)
                            )
                        )
                    )
                } else Modifier
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isPressed = true
                item.onClick()
                isPressed = false
            }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon container
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = item.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Title
            Box(modifier = Modifier.weight(1f)) {
                item.title()
            }

            // Trailing content
            if (item.trailingContent != null) {
                item.trailingContent()
            } else {
                Icon(
                    painter = painterResource(R.drawable.arrow_forward),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    if (!isLast) {
        SettingsDivider(
            modifier = Modifier.padding(start = 72.dp, end = 16.dp)
        )
    }
}

// ==================== GLASS CARD COMPONENT ====================

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val isFrosted = isFrostedGlassUiEnabled()
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = settingsCardContainerColor(isFrosted)
        ),
        border = settingsCardBorder(isFrosted),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        content()
    }
}

// ==================== WATER DROP BUTTON COMPONENTS ====================

@Composable
fun WaterDropButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null,
    text: String? = null,
    colors: List<Color> = listOf(
        Color(0xFF6C5CE7),
        Color(0xFFA463F5),
        Color(0xFFC45AF0)
    )
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(30.dp))
            .scale(if (isPressed) 0.95f else 1f)
            .shadow(
                elevation = if (isPressed) 2.dp else 8.dp,
                shape = RoundedCornerShape(30.dp),
                clip = false
            )
            .background(
                brush = Brush.linearGradient(
                    colors = colors,
                    start = Offset(0f, 0f),
                    end = Offset(100f, 100f)
                ),
                shape = RoundedCornerShape(30.dp)
            )
            .drawBehind {
                // Water drop effect - bottom highlight
                drawRect(
                    color = Color.White.copy(alpha = 0.2f),
                    topLeft = Offset(size.width * 0.2f, size.height * 0.7f),
                    size = Size(size.width * 0.6f, size.height * 0.3f),
                    blendMode = BlendMode.Screen
                )
                // Top highlight
                drawCircle(
                    color = Color.White.copy(alpha = 0.3f),
                    radius = size.minDimension * 0.15f,
                    center = Offset(size.width * 0.3f, size.height * 0.3f),
                    blendMode = BlendMode.Screen
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isPressed = true
                onClick()
                isPressed = false
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                icon()
                if (text != null) Spacer(modifier = Modifier.width(8.dp))
            }
            if (text != null) {
                Text(
                    text = text,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
fun WaterDropIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .scale(if (isPressed) 0.9f else 1f)
            .shadow(
                elevation = if (isPressed) 2.dp else 6.dp,
                shape = CircleShape,
                clip = false
            )
            .background(
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.8f),
                CircleShape
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                shape = CircleShape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isPressed = true
                onClick()
                isPressed = false
            },
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

// ==================== ORIGINAL FUNCTIONS ====================

fun getAppVersion(context: Context): String {
    return try {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(
                context.packageName,
                0
            )
        }
        packageInfo.versionName ?: "Unknown"
    } catch (e: PackageManager.NameNotFoundException) {
        "Unknown"
    }
}

@Composable
fun VersionCard(uriHandler: UriHandler, navController: NavController? = null) {
    val context = LocalContext.current
    val appVersion = remember { getAppVersion(context) }

    BitChordSettingsGroup(header = stringResource(R.string.app_info)) {
        BitChordSettingsRow(
            iconPainter = painterResource(R.drawable.info),
            title = stringResource(R.string.Version),
            value = appVersion,
            onClick = {
                if (navController != null) {
                    navController.navigate("settings/app_info")
                } else {
                    uriHandler.openUri(com.valora.icebeats.utils.RemoteConfigManager.getLatestReleasePageUrl())
                }
            }
        )
        BitChordRowDivider()
        BitChordSettingsRow(
            iconPainter = painterResource(R.drawable.resource_public),
            title = stringResource(R.string.ui_text_official_website),
            onClick = {
                val url = com.valora.icebeats.utils.RemoteConfigManager.websiteUrl
                val safeUrl = if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) url else "https://$url"
                runCatching { uriHandler.openUri(safeUrl) }
            }
        )
    }
}

@Composable
fun UpdateCard(latestVersion: String = "") {
    val context = LocalContext.current
    var showUpdateCard by remember { mutableStateOf(false) }
    var currentLatestVersion by remember { mutableStateOf(latestVersion) }
    var updateInfoState by remember { mutableStateOf<UpdateInfo?>(null) }
    var showDownloadDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        Updater.getLatestUpdateInfo().onSuccess { info ->
            if (info.versionName.isNotBlank() && isNewerVersion(info.versionName, BuildConfig.VERSION_NAME)) {
                showUpdateCard = true
                currentLatestVersion = info.versionName
                updateInfoState = info
            }
        }.onFailure {
            val newVersion = checkForUpdates()
            if (newVersion != null && isNewerVersion(newVersion, BuildConfig.VERSION_NAME)) {
                showUpdateCard = true
                currentLatestVersion = newVersion
                updateInfoState = UpdateInfo(versionName = newVersion)
            }
        }
    }

    if (showDownloadDialog) {
        updateInfoState?.let { info ->
            UpdateAvailableDialog(
                updateInfo = info,
                onDismiss = { showDownloadDialog = false }
            )
        } ?: UpdateDownloadDialog(
            latestVersion = currentLatestVersion,
            onDismiss = { showDownloadDialog = false }
        )
    }

    if (showUpdateCard) {
        BitChordSettingsGroup(topSpacing = 16.dp) {
            BitChordSettingsRow(
                iconPainter = painterResource(R.drawable.update),
                title = stringResource(R.string.NewVersion) + ": $currentLatestVersion",
                subtitle = stringResource(R.string.tap_to_update),
                badge = "NEW",
                onClick = { showDownloadDialog = true }
            )
        }
    }
}

@Composable
fun UpdateDownloadDialog(
    latestVersion: String,
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    var downloadStatus by remember { mutableStateOf(DownloadStatus.NOT_STARTED) }

    Dialog(onDismissRequest = {
        if (downloadStatus != DownloadStatus.REDIRECTING) {
            onDismiss()
        }
    }) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(id = R.string.update_version, latestVersion),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                when (downloadStatus) {
                    DownloadStatus.NOT_STARTED -> {
                        Text(
                            stringResource(R.string.download_question),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            WaterDropButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                text = stringResource(R.string.cancel),
                                colors = listOf(
                                    Color(0xFF2A2A2A),
                                    Color(0xFF3A3A3A),
                                    Color(0xFF2A2A2A)
                                )
                            )

                            WaterDropButton(
                                onClick = {
                                    downloadStatus = DownloadStatus.REDIRECTING
                                    val downloadUrl = com.valora.icebeats.utils.RemoteConfigManager.getApkDownloadUrl(
                                        latestVersion,
                                        com.valora.icebeats.BuildConfig.IS_NIGHTLY
                                    )
                                    uriHandler.openUri(downloadUrl)
                                    downloadStatus = DownloadStatus.COMPLETED
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f),
                                text = stringResource(R.string.download),
                                colors = listOf(
                                    Color(0xFF6C5CE7),
                                    Color(0xFFA463F5),
                                    Color(0xFFC45AF0)
                                )
                            )
                        }
                    }

                    DownloadStatus.REDIRECTING -> {
                        Text(
                            stringResource(R.string.opening_browser),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 4.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    DownloadStatus.COMPLETED -> {
                        Text(
                            stringResource(R.string.download_started),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        WaterDropButton(
                            onClick = onDismiss,
                            text = stringResource(R.string.close),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    DownloadStatus.ERROR -> {
                        Text(
                            stringResource(R.string.download_errorup),
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        WaterDropButton(
                            onClick = onDismiss,
                            text = stringResource(R.string.close),
                            modifier = Modifier.fillMaxWidth(),
                            colors = listOf(
                                Color(0xFFCF6679),
                                Color(0xFFB0003A),
                                Color(0xFFCF6679)
                            )
                        )
                    }
                }
            }
        }
    }
}

enum class DownloadStatus {
    NOT_STARTED,
    REDIRECTING,
    COMPLETED,
    ERROR
}

suspend fun checkForUpdates(): String? = withContext(Dispatchers.IO) {
    try {
        if (com.valora.icebeats.BuildConfig.IS_NIGHTLY) {
            val url = java.net.URL(com.valora.icebeats.utils.RemoteConfigManager.getLatestReleaseApiUrl(isNightly = true))
            val connection = url.openConnection()
            connection.connect()
            val json = connection.getInputStream().bufferedReader().use { it.readText() }
            val jsonArray = org.json.JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                val release = jsonArray.getJSONObject(i)
                if (release.getBoolean("prerelease")) {
                    val tagName = release.getString("tag_name")
                    return@withContext tagName.removePrefix("v").removeSuffix("-nightly").trim()
                }
            }
            return@withContext null
        } else {
            val url = java.net.URL(com.valora.icebeats.utils.RemoteConfigManager.getLatestReleaseApiUrl(isNightly = false))
            val connection = url.openConnection()
            connection.connect()
            val json = connection.getInputStream().bufferedReader().use { it.readText() }
            val jsonObject = org.json.JSONObject(json)
            val tagName = jsonObject.getString("tag_name")
            return@withContext tagName.removePrefix("v").trim()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return@withContext null
    }
}

fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean {
    fun normalize(version: String): List<Int> {
        return version
            .replace(Regex("[^0-9.]"), "")
            .split(".")
            .map { it.toIntOrNull() ?: 0 }
    }

    val remote = normalize(remoteVersion)
    val current = normalize(currentVersion)

    for (i in 0 until maxOf(remote.size, current.size)) {
        val r = remote.getOrNull(i) ?: 0
        val c = current.getOrNull(i) ?: 0
        if (r > c) return true
        if (r < c) return false
    }

    return false
}

// ==================== MAIN SETTINGS SCREEN ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    latestVersion: Long,
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val uriHandler = LocalUriHandler.current
    var showTranslateDialog by remember { mutableStateOf(false) }
    var showChangelogSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val bgColor = bitChordSettingsBackground()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = bgColor,
            topBar = {
                TopAppBar(
                    title = {},
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = bgColor,
                        scrolledContainerColor = bgColor
                    ),
                    scrollBehavior = scrollBehavior
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                        )
                    )
            ) {
                BitChordSettingsPageTitle(stringResource(R.string.settings))

                BitChordSettingsSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = stringResource(R.string.search),
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
                )

                val context = LocalContext.current
                val avatarManager = remember { AvatarPreferenceManager(context) }
                val currentSelection by avatarManager.getAvatarSelection.collectAsState(initial = AvatarSelection.Default)
                val accountName by rememberPreference(AccountNameKey, "")
                val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
                val isLoggedIn = remember(innerTubeCookie) {
                    "SAPISID" in parseCookieString(innerTubeCookie)
                }

                val (appMode, _) = rememberPreference(AppModeKey, defaultValue = APP_MODE_LITE)
                val isLiteMode = appMode == APP_MODE_LITE

                val needle = searchQuery.trim()
                fun matches(vararg words: String): Boolean =
                    needle.isEmpty() || words.any { it.contains(needle, ignoreCase = true) }

                // Account & Listen Together group (top card, matching BitChord)
                val accountTitle = stringResource(R.string.account)
                val listenTogetherTitle = stringResource(R.string.listen_together)
                val showAccountRow = matches(accountTitle, accountName, "profile", "login", "supabase")
                val showListenRow = matches(listenTogetherTitle, "jam", "party", "sync")

                if (showAccountRow || showListenRow) {
                    BitChordSettingsGroup(topSpacing = 16.dp) {
                        if (showAccountRow) {
                            BitChordSettingsRow(
                                iconPainter = painterResource(R.drawable.person),
                                iconContent = {
                                    BitChordProfileAvatarSmall(
                                        isLoggedIn = isLoggedIn,
                                        accountName = accountName,
                                        currentSelection = currentSelection
                                    )
                                },
                                title = if (accountName.isNotBlank()) accountName.replace("@", "") else accountTitle,
                                subtitle = if (isLoggedIn) stringResource(R.string.account) else "IceBeats • Valora Official",
                                onClick = { navController.navigate("settings/account") }
                            )
                        }
                        if (showAccountRow && showListenRow) {
                            BitChordRowDivider()
                        }
                        if (showListenRow) {
                            BitChordSettingsRow(
                                iconPainter = painterResource(R.drawable.group),
                                title = listenTogetherTitle,
                                onClick = { navController.navigate("listen_together") }
                            )
                        }
                    }
                }

                // General & Customization group
                val appearanceTitle = stringResource(R.string.appearance)
                val aodTitle = stringResource(R.string.always_on_display)
                val contentTitle = stringResource(R.string.content)
                val lyricsTitle = stringResource(R.string.lyrics)
                val aiTitle = stringResource(R.string.ai_integration)
                val playerTitle = stringResource(R.string.player_and_audio)
                val gesturesTitle = stringResource(R.string.ui_text_gestures)
                val scrobblerTitle = stringResource(R.string.ui_text_scrobbler)
                val androidAutoTitle = stringResource(R.string.android_auto)
                val storageTitle = stringResource(R.string.storage)
                val privacyTitle = stringResource(R.string.privacy)
                val backupTitle = stringResource(R.string.backup_restore)
                val experimentalTitle = stringResource(R.string.ui_text_experimental_settings)

                data class MenuRowSpec(
                    val title: String,
                    val iconRes: Int,
                    val route: String,
                )

                val mainRows = buildList {
                    if (matches(appearanceTitle, "theme", "dark", "light", "font", "icon", "navbar")) {
                        add(MenuRowSpec(appearanceTitle, R.drawable.palette, "settings/appearance"))
                    }
                    if (!isLiteMode && matches(aodTitle, "always on", "display", "screen")) {
                        add(MenuRowSpec(aodTitle, R.drawable.schedule, "settings/always_on_display"))
                    }
                    if (matches(contentTitle, "language", "country", "youtube")) {
                        add(MenuRowSpec(contentTitle, R.drawable.language, "settings/content"))
                    }
                    if (!isLiteMode && matches(lyricsTitle, "translate", "lrc", "provider")) {
                        add(MenuRowSpec(lyricsTitle, R.drawable.lyrics, "settings/lyrics"))
                    }
                    if (!isLiteMode && matches(aiTitle, "gemini", "recommendation")) {
                        add(MenuRowSpec(aiTitle, R.drawable.ic_gen_ai, "settings/ai"))
                    }
                    if (matches(playerTitle, "audio", "equalizer", "crossfade", "quality", "playback")) {
                        add(MenuRowSpec(playerTitle, R.drawable.play, "settings/player"))
                    }
                    if (!isLiteMode && matches(gesturesTitle, "swipe", "gesture")) {
                        add(MenuRowSpec(gesturesTitle, R.drawable.swipe, "settings/gestures"))
                    }
                    if (!isLiteMode && matches(scrobblerTitle, "last.fm", "lastfm", "scrobble")) {
                        add(MenuRowSpec(scrobblerTitle, R.drawable.graphic_eq, "settings/scrobbler"))
                    }
                    if (!isLiteMode && matches(androidAutoTitle, "car", "auto")) {
                        add(MenuRowSpec(androidAutoTitle, R.drawable.play, "settings/android_auto"))
                    }
                }

                if (mainRows.isNotEmpty()) {
                    BitChordSettingsGroup(header = stringResource(R.string.general_settings)) {
                        mainRows.forEachIndexed { idx, row ->
                            if (idx > 0) BitChordRowDivider()
                            BitChordSettingsRow(
                                iconPainter = painterResource(row.iconRes),
                                title = row.title,
                                onClick = { navController.navigate(row.route) }
                            )
                        }
                    }
                }

                val dataRows = buildList {
                    if (matches(storageTitle, "cache", "download", "space")) {
                        add(MenuRowSpec(storageTitle, R.drawable.storage, "settings/storage"))
                    }
                    if (matches(privacyTitle, "history", "search", "incognito")) {
                        add(MenuRowSpec(privacyTitle, R.drawable.security, "settings/privacy"))
                    }
                    if (matches(backupTitle, "drive", "restore", "export", "import")) {
                        add(MenuRowSpec(backupTitle, R.drawable.restore, "settings/backup_restore"))
                    }
                    if (matches(experimentalTitle, "debug", "experimental")) {
                        add(MenuRowSpec(experimentalTitle, R.drawable.bug_report, "settings/experimental"))
                    }
                }

                if (dataRows.isNotEmpty()) {
                    BitChordSettingsGroup(header = stringResource(R.string.storage)) {
                        dataRows.forEachIndexed { idx, row ->
                            if (idx > 0) BitChordRowDivider()
                            BitChordSettingsRow(
                                iconPainter = painterResource(row.iconRes),
                                title = row.title,
                                onClick = { navController.navigate(row.route) }
                            )
                        }
                    }
                }

                // Community & Info group
                val newsTitle = stringResource(R.string.ui_text_news_from_developers_2)
                val aboutTitle = stringResource(R.string.about)
                val translateTitle = stringResource(R.string.ui_text_help_translate)
                val changelogTitle = stringResource(R.string.Changelog)
                val telegramTitle = stringResource(R.string.ui_text_telegram)

                data class ActionRowSpec(
                    val title: String,
                    val iconRes: Int,
                    val onClick: () -> Unit,
                )

                val communityRows = buildList {
                    if (matches(newsTitle, "news", "developer")) {
                        add(ActionRowSpec(newsTitle, R.drawable.newspaper) { navController.navigate("settings/developer_news") })
                    }
                    if (matches(aboutTitle, "license", "contributor", "valora")) {
                        add(ActionRowSpec(aboutTitle, R.drawable.info) { navController.navigate("settings/about") })
                    }
                    if (matches(translateTitle, "crowdin", "language")) {
                        add(ActionRowSpec(translateTitle, R.drawable.translate) { showTranslateDialog = true })
                    }
                    if (matches(changelogTitle, "update", "notes")) {
                        add(ActionRowSpec(changelogTitle, R.drawable.schedule) { showChangelogSheet = true })
                    }
                    if (matches(telegramTitle, "community", "channel")) {
                        add(ActionRowSpec(telegramTitle, R.drawable.telegram) {
                            val telegramUrl = RemoteConfigManager.telegramUrl.ifBlank { RemoteConfigManager.DEFAULT_TELEGRAM_URL }
                            uriHandler.openUri(telegramUrl)
                        })
                    }
                }

                if (communityRows.isNotEmpty()) {
                    BitChordSettingsGroup(header = stringResource(R.string.community)) {
                        communityRows.forEachIndexed { idx, row ->
                            if (idx > 0) BitChordRowDivider()
                            BitChordSettingsRow(
                                iconPainter = painterResource(row.iconRes),
                                title = row.title,
                                onClick = row.onClick
                            )
                        }
                    }
                }

                if (needle.isEmpty()) {
                    UpdateCard()
                    VersionCard(uriHandler, navController)
                }

                Spacer(Modifier.height(32.dp))
            }
        }

        // Dialogs and Bottom Sheets
        if (showTranslateDialog) {
            Dialog(onDismissRequest = { showTranslateDialog = false }) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.Redirección),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = stringResource(R.string.poeditor_redirect),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            WaterDropButton(
                                onClick = { showTranslateDialog = false },
                                modifier = Modifier.weight(1f),
                                text = stringResource(R.string.cancel),
                                colors = listOf(
                                    Color(0xFF2A2A2A),
                                    Color(0xFF3A3A3A),
                                    Color(0xFF2A2A2A)
                                )
                            )

                            WaterDropButton(
                                onClick = {
                                    showTranslateDialog = false
                                    uriHandler.openUri("https://poeditor.com/join/project/208BwCVazA")
                                },
                                modifier = Modifier.weight(1f),
                                text = "OK",
                                colors = listOf(
                                    Color(0xFF6C5CE7),
                                    Color(0xFFA463F5),
                                    Color(0xFFC45AF0)
                                )
                            )
                        }
                    }
                }
            }
        }

        if (showChangelogSheet) {
            ModalBottomSheet(
                onDismissRequest = { showChangelogSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                    )
                }
            ) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.Changelog),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        ChangelogScreen()

                        Spacer(Modifier.height(24.dp))

                        WaterDropButton(
                            onClick = { showChangelogSheet = false },
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(R.string.close),
                            colors = listOf(
                                Color(0xFF6C5CE7),
                                Color(0xFFA463F5),
                                Color(0xFFC45AF0)
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileSection(
    isLoggedIn: Boolean,
    accountName: String,
    currentSelection: AvatarSelection
) {
    val isLiteMode = com.valora.icebeats.utils.isLiteModeActive()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 0.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isLoggedIn) {
            var imageLoadError by remember { mutableStateOf(false) }
            var isImageLoading by remember { mutableStateOf(false) }

            // Avatar with glow effect
            Box(
                contentAlignment = Alignment.Center
            ) {
                // Outer glow
                if (!isLiteMode) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .blur(20.dp)
                    )
                }

                // Avatar container
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainer
                        )
                        .border(
                            width = 3.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)
                                )
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        currentSelection is AvatarSelection.Custom && !imageLoadError -> {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data((currentSelection as AvatarSelection.Custom).uri.toUri())
                                    .crossfade(true)
                                    .listener(
                                        onStart = { isImageLoading = true },
                                        onSuccess = { _, _ ->
                                            isImageLoading = false
                                            imageLoadError = false
                                        },
                                        onError = { _, _ ->
                                            isImageLoading = false
                                            imageLoadError = true
                                        }
                                    )
                                    .build(),
                                contentDescription = stringResource(R.string.ui_a11y_avatar_de_accountname, accountName),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )

                            if (isImageLoading) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        currentSelection is AvatarSelection.DiceBear && !imageLoadError -> {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data((currentSelection as AvatarSelection.DiceBear).url)
                                    .crossfade(true)
                                    .listener(
                                        onStart = { isImageLoading = true },
                                        onSuccess = { _, _ ->
                                            isImageLoading = false
                                            imageLoadError = false
                                        },
                                        onError = { _, _ ->
                                            isImageLoading = false
                                            imageLoadError = true
                                        }
                                    )
                                    .build(),
                                contentDescription = stringResource(R.string.ui_a11y_avatar_dicebear_de_accountname, accountName),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )

                            if (isImageLoading) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        else -> {
                            val initials = remember(accountName) {
                                val cleanName = accountName.replace("@", "").trim()
                                when {
                                    cleanName.isEmpty() -> "?"
                                    cleanName.contains(" ") -> {
                                        val parts = cleanName.split(" ")
                                        "${parts.first().firstOrNull()?.uppercase() ?: ""}${
                                            parts.last().firstOrNull()?.uppercase() ?: ""
                                        }"
                                    }
                                    else -> cleanName.take(2).uppercase()
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        brush = Brush.linearGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.tertiary
                                            ),
                                            start = Offset(0f, 0f),
                                            end = Offset(100f, 100f)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = initials,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Online indicator with pulse animation
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.5f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "alpha"
                )

                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .offset(x = 32.dp, y = 32.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF00FF00).copy(alpha = pulseAlpha),
                                    Color(0xFF00FF00).copy(alpha = 0.3f)
                                )
                            )
                        )
                        .border(
                            width = 2.dp,
                            color = Color.White,
                            shape = CircleShape
                        )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Username with animation
            AnimatedContent(
                targetState = accountName.replace("@", "").takeIf { it.isNotBlank() } ?: "",
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "username"
            ) { name ->
                Text(
                    text = name,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

        } else {
            // Not logged in state
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Logo with glow effect
                if (!isLiteMode) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .blur(20.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainer
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                )
                            ),
                            shape = CircleShape
                        )
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.airbeats_monochrome),
                        contentDescription = stringResource(R.string.ui_a11y_logo_de_airbeats),
                        modifier = Modifier.fillMaxSize(),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(
                                style = SpanStyle(
                                    color = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                append("Ice")
                            }

                            withStyle(
                                style = SpanStyle(
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            ) {
                                append("Beats")
                            }
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Valora Official",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BitChordProfileAvatarSmall(
    isLoggedIn: Boolean,
    accountName: String,
    currentSelection: AvatarSelection,
) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        when (currentSelection) {
            is AvatarSelection.Custom -> {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(currentSelection.uri.toUri())
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
            is AvatarSelection.DiceBear -> {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(currentSelection.url)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
            else -> {
                val initial = accountName.replace("@", "").trim().firstOrNull()?.uppercaseChar()?.toString()
                if (isLoggedIn && !initial.isNullOrBlank()) {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.person),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

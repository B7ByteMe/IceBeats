package com.valora.icebeats.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.valora.icebeats.innertube.YouTube
import com.valora.icebeats.innertube.utils.parseCookieString
import com.valora.icebeats.App.Companion.forgetAccount
import com.valora.icebeats.R
import com.valora.icebeats.constants.*
import com.valora.icebeats.ui.component.*
import com.valora.icebeats.utils.rememberPreference
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import android.widget.Toast
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import com.valora.icebeats.viewmodels.BackupRestoreViewModel
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import coil.compose.AsyncImage
import com.valora.icebeats.LocalPlayerAwareWindowInsets
import com.valora.icebeats.LocalPlayerConnection
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.flow.first
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.style.TextAlign

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val nameManager = remember { NamePreferenceManager(context) }
    val currentDisplayName by nameManager.userName.collectAsState(initial = "")
    val currentGoogleEmail by nameManager.accountEmail.collectAsState(initial = "")
    
    val backupViewModel: BackupRestoreViewModel = hiltViewModel()
    val avatarManager = remember { AvatarPreferenceManager(context) }

    var showEditNameDialog by remember { mutableStateOf(false) }
    var isGoogleSignInOpen by remember { mutableStateOf(false) }

    val (accountName, onAccountNameChange) = rememberPreference(AccountNameKey, "")
    val (accountEmail, onAccountEmailChange) = rememberPreference(AccountEmailKey, "")
    val (accountChannelHandle, onAccountChannelHandleChange) =
        rememberPreference(AccountChannelHandleKey, "")
    val (innerTubeCookie, onInnerTubeCookieChange) =
        rememberPreference(InnerTubeCookieKey, "")
    val (visitorData, onVisitorDataChange) =
        rememberPreference(VisitorDataKey, "")
    val (dataSyncId, onDataSyncIdChange) =
        rememberPreference(DataSyncIdKey, "")

    val database = com.valora.icebeats.LocalDatabase.current
    val supabaseAuthManager = remember { com.valora.icebeats.supabase.SupabaseAuthManager.getInstance(context) }
    val supabaseClient = remember { com.valora.icebeats.supabase.SupabaseClient(context) }
    val isSupabaseLoggedIn by supabaseAuthManager.isLoggedIn.collectAsState()
    val supabaseEmail by supabaseAuthManager.userEmail.collectAsState()
    val supabaseName by supabaseAuthManager.userName.collectAsState()
    val authProvider by supabaseAuthManager.authProvider.collectAsState()
    val lastSyncTime by supabaseAuthManager.lastSyncTime.collectAsState()
    var isSyncing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        supabaseClient.syncCurrentUserStats(context)
    }

    val isLoggedIn = remember(innerTubeCookie) {
        innerTubeCookie.isNotEmpty() &&
                "SAPISID" in parseCookieString(innerTubeCookie)
    }
    val isUserLoggedIn = isSupabaseLoggedIn || isLoggedIn || currentGoogleEmail.isNotBlank()

    val getAccountDisplayName =
        remember(accountName, accountEmail, accountChannelHandle, isLoggedIn) {
            when {
                !isLoggedIn -> ""
                accountName.isNotBlank() -> accountName
                accountEmail.isNotBlank() -> accountEmail.substringBefore("@")
                accountChannelHandle.isNotBlank() -> accountChannelHandle
                else -> "No username"
            }
        }

    val getAccountDescription =
        remember(accountEmail, accountChannelHandle, isLoggedIn) {
            when {
                !isLoggedIn -> null
                accountEmail.isNotBlank() -> accountEmail
                accountChannelHandle.isNotBlank() -> accountChannelHandle
                else -> null
            }
        }

    val (useLoginForBrowse, onUseLoginForBrowseChange) =
        rememberPreference(UseLoginForBrowse, true)
    val (ytmSync, onYtmSyncChange) =
        rememberPreference(YtmSyncKey, true)

    var showToken by remember { mutableStateOf(false) }
    var showTokenEditor by remember { mutableStateOf(false) }

    val playerConnection = LocalPlayerConnection.current
    val mediaMetadata by playerConnection?.mediaMetadata?.collectAsState()
        ?: remember { mutableStateOf(null) }

    fun generatedAvatarUrl(name: String, email: String): String {
        val seed = name.takeIf { it.isNotBlank() } ?: email
        val encodedSeed = URLEncoder.encode(seed, StandardCharsets.UTF_8.toString())
        return "https://api.dicebear.com/9.x/initials/svg?seed=$encodedSeed&backgroundType=gradientLinear"
    }

    fun displayNameFromEmail(email: String): String {
        return email
            .substringBefore("@")
            .replace('.', ' ')
            .replace('_', ' ')
            .replace('-', ' ')
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { part ->
                part.replaceFirstChar { char ->
                    if (char.isLowerCase()) char.titlecase() else char.toString()
                }
            }
            .ifBlank { "Friend" }
    }

    fun linkGoogleAccount(name: String, email: String, photoUrl: String?, idToken: String?) {
        scope.launch {
            try {
                if (!idToken.isNullOrBlank()) {
                    val supabaseResult = supabaseClient.signInWithGoogleIdToken(idToken)
                    supabaseResult.onFailure { err ->
                        Toast.makeText(context, "Gagal sinkron Google ke Supabase: ${err.message}", Toast.LENGTH_LONG).show()
                    }
                }
                nameManager.rememberGoogleLoginEmail(email)
                nameManager.saveUserName(name)
                nameManager.saveAccountEmail(email)
                if (!photoUrl.isNullOrBlank()) {
                    avatarManager.saveAvatarSelection(
                        AvatarSelection.Custom(uri = photoUrl, cloudUrl = photoUrl)
                    )
                } else {
                    avatarManager.saveAvatarSelection(
                        AvatarSelection.DiceBear(generatedAvatarUrl(name, email))
                    )
                }

                // Clear local items and restore account data from Supabase cleanly
                if (database != null) {
                    withContext(Dispatchers.IO) {
                        database.clearAllLikes()
                        database.clearUserPlaylists()
                        database.clearAllPlaylistSongs()
                        database.clearAllEvents()
                        database.clearAllArtistBookmarks()
                        database.clearAllAlbumBookmarks()
                        val res = supabaseClient.restoreUserData(database)
                        res.onSuccess { count ->
                            runCatching {
                                val now = System.currentTimeMillis()
                                val allSongs = database.mostPlayedSongsStats(0L, limit = -1, toTimeStamp = now).first()
                                val restoredTotalMs = allSongs.sumOf { it.timeListened?.toLong() ?: 0L }
                                val uid = com.valora.icebeats.utils.IceBeatsStatsCloudSync.resolveStableUserIdBlocking(context, nameManager)
                                val syncPrefs = context.getSharedPreferences(
                                    com.valora.icebeats.utils.IceBeatsStatsCloudSync.PREFERENCES_NAME,
                                    android.content.Context.MODE_PRIVATE
                                )
                                val globalPrefs = context.getSharedPreferences("icebeats_global_stats", android.content.Context.MODE_PRIVATE)
                                syncPrefs.edit().putLong("saved_max_total_listen_ms_$uid", restoredTotalMs).apply()
                                globalPrefs.edit().putLong("saved_max_total_listen_ms_$uid", restoredTotalMs).apply()
                            }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Berhasil memulihkan $count data akun dari Cloud!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.google_account_linked_cloud_sync_failed,
                        e.message.orEmpty()
                    ),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val googleSignInClient = remember {
        com.valora.icebeats.utils.GoogleAuthManager(context).getSignInClient()
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                .getResult(ApiException::class.java)
            val email = account.email.orEmpty()
            if (email.isBlank()) {
                Toast.makeText(context, context.getString(R.string.google_email_missing), Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }
            val name = account.displayName
                ?.takeIf { it.isNotBlank() }
                ?: account.givenName
                ?: displayNameFromEmail(email)

            isGoogleSignInOpen = false
            linkGoogleAccount(name, email, account.photoUrl?.toString(), account.idToken)
        } catch (e: ApiException) {
            e.printStackTrace()
            val message = when (e.statusCode) {
                GoogleSignInStatusCodes.SIGN_IN_CANCELLED ->
                    context.getString(R.string.google_sign_in_cancelled)
                GoogleSignInStatusCodes.SIGN_IN_CURRENTLY_IN_PROGRESS ->
                    context.getString(R.string.google_sign_in_in_progress)
                GoogleSignInStatusCodes.SIGN_IN_FAILED ->
                    context.getString(R.string.google_sign_in_failed_oauth)
                else -> context.getString(
                    R.string.google_sign_in_failed_with_status,
                    e.statusCode,
                    e.message.orEmpty()
                )
            }
            isGoogleSignInOpen = false
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            isGoogleSignInOpen = false
            Toast.makeText(
                context,
                context.getString(R.string.google_sign_in_failed_message, e.message.orEmpty()),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun requestGoogleSignIn() {
        if (isGoogleSignInOpen) return
        isGoogleSignInOpen = true
        googleSignInClient.revokeAccess().addOnCompleteListener {
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        }
    }

    var isLoggingOut by remember { mutableStateOf(false) }
    var logoutStatusText by remember { mutableStateOf("Mencadangkan data ke Cloud...") }

    val performLogout: (String) -> Unit = { successMessage ->
        if (!isLoggingOut) {
            isLoggingOut = true
            logoutStatusText = "Mencadangkan playlist, favorit, dan riwayat ke Cloud..."
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        if (database != null) {
                            runCatching {
                                supabaseClient.syncUserData(database)
                            }.onFailure {
                                it.printStackTrace()
                            }
                            runCatching {
                                com.valora.icebeats.utils.icebeatsStatsCloudSync.syncDaily(context, database, nameManager)
                            }.onFailure {
                                it.printStackTrace()
                            }
                        }
                        withContext(Dispatchers.Main) {
                            logoutStatusText = "Menyelesaikan sesi akun..."
                        }
                        supabaseClient.signOut()
                        supabaseAuthManager.clearSession()

                        if (database != null) {
                            database.clearAllLikes()
                            database.clearUserPlaylists()
                            database.clearAllPlaylistSongs()
                            database.clearAllEvents()
                            database.clearAllArtistBookmarks()
                            database.clearAllAlbumBookmarks()
                        }

                        forgetAccount(context)
                        playerConnection?.player?.stop()
                    }

                    onInnerTubeCookieChange("")
                    onAccountNameChange("")
                    onAccountEmailChange("")
                    onAccountChannelHandleChange("")
                    onVisitorDataChange("")
                    onDataSyncIdChange("")
                    nameManager.clearGoogleLoginLock()
                    nameManager.clearUser()
                    avatarManager.saveAvatarSelection(AvatarSelection.Default)
                    RankPreferenceManager(context).resetAll()
                    com.valora.icebeats.ui.component.BorderPreferenceManager(context).saveSelectedBorder(
                        com.valora.icebeats.ui.component.MasterBorderStyle.ROYAL_CROWN
                    )
                    com.valora.icebeats.utils.IceBeatsStatsCloudSync.clearUserSessionStats(context)
                    context.getSharedPreferences("icebeats_global_stats", android.content.Context.MODE_PRIVATE).edit().clear().apply()
                    context.getSharedPreferences(com.valora.icebeats.utils.IceBeatsStatsCloudSync.PREFERENCES_NAME, android.content.Context.MODE_PRIVATE).edit().clear().apply()
                    com.valora.icebeats.ui.component.VipSubscriptionManager(context).resetVipState()
                    com.valora.icebeats.ui.component.BannerPreferenceManager(context).clearBannerUrl()

                    Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
                    navController.navigate("onboarding") {
                        popUpTo(0) { inclusive = true }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isLoggingOut = false
                }
            }
        }
    }

    val vipManager = remember { com.valora.icebeats.ui.component.VipSubscriptionManager(context) }
    val isVip by vipManager.isVip.collectAsState(initial = false)
    val currentVipPlan by vipManager.vipPlan.collectAsState(initial = "Gratis")
    val myVerificationType = remember(isVip, currentVipPlan) {
        com.valora.icebeats.ui.component.VerificationHelper.parseVerificationType(
            planName = currentVipPlan,
            isVip = isVip
        )
    }

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
                            text = stringResource(R.string.account),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(
                                painter = painterResource(R.drawable.arrow_back),
                                contentDescription = stringResource(R.string.back),
                                tint = Color.White
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
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                        )
                    )
            ) {
            // Main content with horizontal padding
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                // ☁️ ICEBEATS CLOUD (SUPABASE)
                SettingsGeneralCategory(
                    title = "IceBeats Cloud (Supabase)",
                    items = listOfNotNull(
                        {
                            PreferenceEntry(
                                title = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            if (isSupabaseLoggedIn) {
                                                supabaseName.ifBlank { supabaseEmail.substringBefore("@") }
                                            } else {
                                                "Masuk / Buat Akun Cloud"
                                            }
                                        )
                                        if (isSupabaseLoggedIn && myVerificationType != com.valora.icebeats.ui.component.VerificationType.NONE) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            com.valora.icebeats.ui.component.VerificationBadge(type = myVerificationType, size = 16.dp)
                                        }
                                    }
                                },
                                description = if (isSupabaseLoggedIn) {
                                    if (lastSyncTime.isNotBlank()) "Email: $supabaseEmail • Terakhir sinkron: $lastSyncTime"
                                    else "Email: $supabaseEmail • Belum pernah disinkronkan"
                                } else {
                                    "Sinkronkan playlist & lagu favorit otomatis, fitur lupa password & backup"
                                },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.sync),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                trailingContent = {
                                    if (isSupabaseLoggedIn) {
                                        OutlinedButton(
                                            onClick = {
                                                performLogout("Berhasil keluar dari Akun Cloud")
                                            }
                                        ) {
                                            Text("Keluar")
                                        }
                                    } else {
                                        Button(
                                            onClick = { navController.navigate("login") }
                                        ) {
                                            Text("Masuk / Daftar")
                                        }
                                    }
                                },
                                onClick = {
                                    if (!isSupabaseLoggedIn) {
                                        navController.navigate("login")
                                    }
                                }
                            )
                        },
                        if (isSupabaseLoggedIn && database != null) {
                            {
                                PreferenceEntry(
                                    title = { Text("Sinkronkan Sekarang") },
                                    description = "Kirim lagu favorit dan playlist lokal ke cloud Supabase",
                                    icon = {
                                        if (isSyncing) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(
                                                painter = painterResource(R.drawable.favorite),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    },
                                    onClick = {
                                        if (!isSyncing) {
                                            isSyncing = true
                                            scope.launch {
                                                supabaseClient.syncCurrentUserStats(context)
                                                val res = supabaseClient.syncUserData(database)
                                                isSyncing = false
                                                res.onSuccess { msg ->
                                                    Toast.makeText(context, "$msg (Level & Rank tersinkron)", Toast.LENGTH_LONG).show()
                                                }.onFailure { err ->
                                                    Toast.makeText(context, "Gagal sinkron: ${err.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        } else null,
                        if (isSupabaseLoggedIn) {
                            {
                                PreferenceEntry(
                                    title = { Text("Tautkan ke Windows Desktop") },
                                    description = "Pindai kode QR di layar laptop/PC Windows",
                                    icon = {
                                        Icon(
                                            painter = painterResource(R.drawable.sync),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    onClick = {
                                        val scanner = com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(context)
                                        scanner.startScan()
                                            .addOnSuccessListener { barcode ->
                                                val raw = barcode.rawValue.orEmpty()
                                                val token = if (raw.contains("token")) {
                                                    try {
                                                        val json = org.json.JSONObject(raw)
                                                        json.optString("token", raw)
                                                    } catch (e: Exception) {
                                                        if (raw.contains("token=")) {
                                                            raw.substringAfter("token=").substringBefore("&")
                                                        } else raw
                                                    }
                                                } else raw

                                                if (token.isNotBlank()) {
                                                    scope.launch {
                                                        val res = supabaseClient.approveDesktopQrSession(token)
                                                        if (res.isSuccess) {
                                                            Toast.makeText(context, "Berhasil masuk ke IceBeats Desktop!", Toast.LENGTH_LONG).show()
                                                        } else {
                                                            Toast.makeText(context, "Gagal menautkan: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                                        }
                                                    }
                                                }
                                            }
                                            .addOnFailureListener { e ->
                                                Toast.makeText(context, "Pemindaian dibatalkan: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                    }
                                )
                            }
                        } else null
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 🏆 LEVEL & RANK BADGE PENGGUNA (v7.0.9)
                val rankPrefManager = remember { com.valora.icebeats.ui.component.RankPreferenceManager(context) }
                val displayedRank by rankPrefManager.displayedRank.collectAsState(initial = null)
                val highestEarnedRank by rankPrefManager.highestEarnedRank.collectAsState(initial = null)
                val statsPrefs = remember { context.getSharedPreferences(com.valora.icebeats.utils.IceBeatsStatsCloudSync.PREFERENCES_NAME, android.content.Context.MODE_PRIVATE) }
                val currentAccountEmail by nameManager.accountEmail.collectAsState(initial = "")
                val savedListenMs = remember(currentAccountEmail, isSupabaseLoggedIn) {
                    val uid = com.valora.icebeats.utils.IceBeatsStatsCloudSync.resolveStableUserIdBlocking(context, nameManager)
                    if (!uid.startsWith("device-")) {
                        statsPrefs.getLong("saved_max_total_listen_ms_$uid", 0L)
                    } else {
                        statsPrefs.getLong("saved_max_total_listen_ms_$uid", statsPrefs.getLong("saved_max_total_listen_ms", 0L))
                    }
                }
                val totalHours = remember(savedListenMs) { (savedListenMs / (1000 * 3600)).toInt() }
                val earnedRank = if (totalHours >= 1) com.valora.icebeats.ui.component.icebeatsRank.fromHours(totalHours) else null
                val actualHighestRank = listOfNotNull(earnedRank, highestEarnedRank).maxByOrNull { it.ordinal }
                val currentRank = displayedRank ?: actualHighestRank
                val isMaster = (actualHighestRank != null && actualHighestRank.ordinal >= com.valora.icebeats.ui.component.icebeatsRank.Master.ordinal) || totalHours >= 150

                SettingsGeneralCategory(
                    title = "Level & Badge Akun (v${com.valora.icebeats.BuildConfig.VERSION_NAME})",
                    items = listOf(
                        {
                            PreferenceEntry(
                                title = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val tierLabel = if (displayedRank != null && actualHighestRank != null && displayedRank != actualHighestRank) {
                                            "Badge: ${displayedRank?.name} (Level ${actualHighestRank.name})"
                                        } else {
                                            "Tier: ${actualHighestRank?.name ?: "Echo"}"
                                        }
                                        Text(tierLabel)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        com.valora.icebeats.ui.component.RankBadge(
                                            rank = actualHighestRank ?: com.valora.icebeats.ui.component.icebeatsRank.Echo,
                                            displayedRank = displayedRank,
                                            size = 22.dp
                                        )
                                    }
                                },
                                description = if (isMaster) "$totalHours Jam Mendengarkan • Level Master Aktif"
                                else "$totalHours Jam Mendengarkan • Butuh ${maxOf(0, 150 - totalHours)} jam lagi untuk Level Master",
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.auto_awesome),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                trailingContent = {
                                    Text(
                                        text = if (isMaster) "MASTER" else "#LEVEL",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isMaster) Color(0xFFFFD700) else MaterialTheme.colorScheme.primary
                                    )
                                }
                            )
                        }
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // BORDER & ANIMASI PROFIL PREMIUM
                var showVipDialog by remember { mutableStateOf(false) }
                val borderPrefManager = remember { com.valora.icebeats.ui.component.BorderPreferenceManager(context) }
                val selectedBorder by borderPrefManager.selectedBorder.collectAsState(initial = com.valora.icebeats.ui.component.MasterBorderStyle.ROYAL_CROWN)
                var showBorderSelectorSheet by remember { mutableStateOf(false) }

                val avatarManager = remember { com.valora.icebeats.ui.component.AvatarPreferenceManager(context) }
                val currentAvatarSelection by avatarManager.getAvatarSelection.collectAsState(initial = com.valora.icebeats.ui.component.AvatarSelection.Default)
                val bannerPrefManager = remember { com.valora.icebeats.ui.component.BannerPreferenceManager(context) }
                val myBannerUrl by bannerPrefManager.bannerUrl.collectAsState(initial = null)
                var showAvatarGifDialog by remember { mutableStateOf(false) }
                var showBannerGifDialog by remember { mutableStateOf(false) }

                SettingsGeneralCategory(
                    title = "IceBeats Premium Fitur",
                    items = listOf(
                        {
                            PreferenceEntry(
                                title = {
                                    Text(
                                        if (isVip) "Gaya Border: ${selectedBorder.title}"
                                        else "Border Profil Avatar (Khusus Premium)"
                                    )
                                },
                                description = if (isVip) selectedBorder.description
                                else "Beli paket IceBeats Premium untuk membuka dan mengganti 14 pilihan border animasi eksklusif!",
                                icon = {
                                    Icon(
                                        painter = painterResource(if (isVip) R.drawable.ic_vip_crown else R.drawable.lock),
                                        contentDescription = null,
                                        tint = if (isVip) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingContent = {
                                    Box(
                                        modifier = Modifier.size(50.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        com.valora.icebeats.ui.component.MasterProfileBorder(
                                            avatarSize = 28.dp,
                                            forceShowMaster = isVip,
                                            borderStyle = selectedBorder,
                                            isSelf = true
                                        ) {
                                            com.valora.icebeats.ui.component.AvatarDisplay(
                                                size = 28.dp,
                                                showBorder = false
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    if (isVip) {
                                        showBorderSelectorSheet = true
                                    } else {
                                        showVipDialog = true
                                    }
                                }
                            )
                        },
                        {
                            PreferenceEntry(
                                title = {
                                    Text(
                                        if (isVip) "Avatar Profil Animasi (GIF)"
                                        else "Avatar Animasi GIF (Khusus Premium)"
                                    )
                                },
                                description = if (isVip) {
                                    if (currentAvatarSelection is com.valora.icebeats.ui.component.AvatarSelection.Gif)
                                        "Status: Link GIF Aktif • Klik untuk mengganti link"
                                    else
                                        "Pasang tautan GIF animasi agar foto profilmu bergerak"
                                } else {
                                    "Beli paket IceBeats Premium untuk memasang link GIF bergerak pada avatar profil!"
                                },
                                icon = {
                                    Icon(
                                        painter = painterResource(if (isVip) R.drawable.ic_vip_crown else R.drawable.lock),
                                        contentDescription = null,
                                        tint = if (isVip) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingContent = {
                                    Box(
                                        modifier = Modifier.size(50.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isVip && currentAvatarSelection is com.valora.icebeats.ui.component.AvatarSelection.Gif) {
                                            AsyncImage(
                                                model = (currentAvatarSelection as com.valora.icebeats.ui.component.AvatarSelection.Gif).url,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .border(1.dp, Color(0xFFFFD700), CircleShape),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(
                                                painter = painterResource(if (isVip) R.drawable.image else R.drawable.lock),
                                                contentDescription = null,
                                                tint = if (isVip) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    if (isVip) {
                                        showAvatarGifDialog = true
                                    } else {
                                        showVipDialog = true
                                    }
                                }
                            )
                        },
                        {
                            PreferenceEntry(
                                title = {
                                    Text(
                                        if (isVip) "Banner Profil Animasi (GIF)"
                                        else "Banner Animasi GIF (Khusus Premium)"
                                    )
                                },
                                description = if (isVip) {
                                    if (!myBannerUrl.isNullOrBlank())
                                        "Status: Banner GIF Aktif • Klik untuk mengganti link banner"
                                    else
                                        "Pasang tautan GIF banner latar belakang profil yang bergerak"
                                } else {
                                    "Beli paket IceBeats Premium untuk memasang banner profil bergerak (GIF)!"
                                },
                                icon = {
                                    Icon(
                                        painter = painterResource(if (isVip) R.drawable.ic_vip_crown else R.drawable.lock),
                                        contentDescription = null,
                                        tint = if (isVip) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingContent = {
                                    Box(
                                        modifier = Modifier.size(50.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isVip && !myBannerUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = myBannerUrl,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(44.dp, 28.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .border(1.dp, Color(0xFFFFD700), RoundedCornerShape(6.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(
                                                painter = painterResource(if (isVip) R.drawable.link else R.drawable.lock),
                                                contentDescription = null,
                                                tint = if (isVip) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    if (isVip) {
                                        showBannerGifDialog = true
                                    } else {
                                        showVipDialog = true
                                    }
                                }
                            )
                        }
                    )
                )

                if (showBorderSelectorSheet) {
                    com.valora.icebeats.ui.component.MasterBorderSelectorSheet(
                        onDismiss = { showBorderSelectorSheet = false },
                        userRank = actualHighestRank ?: currentRank,
                        totalListenMs = savedListenMs
                    )
                }

                if (showAvatarGifDialog) {
                    com.valora.icebeats.ui.component.AnimatedAvatarGifDialog(
                        onDismiss = { showAvatarGifDialog = false }
                    )
                }

                if (showBannerGifDialog) {
                    com.valora.icebeats.ui.component.AnimatedBannerGifDialog(
                        onDismiss = { showBannerGifDialog = false }
                    )
                }

                if (showVipDialog) {
                    com.valora.icebeats.ui.component.VipSubscriptionDialog(
                        onDismiss = { showVipDialog = false }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                SettingsGeneralCategory(
                    title = stringResource(R.string.google),
                    items = listOf(

                        // 🔹 EDIT DISPLAY NAME
                        {
                            PreferenceEntry(
                                title = { Text(stringResource(R.string.edit_display_name)) },
                                description = if (currentDisplayName.isNotBlank() && !currentDisplayName.equals("Hai, selamat datang di IceBeats", ignoreCase = true))
                                    stringResource(R.string.current_value, currentDisplayName)
                                else
                                    "Hai, selamat datang di IceBeats (Default)",
                                icon = { Icon(painterResource(R.drawable.person), null) },
                                onClick = { showEditNameDialog = true }
                            )
                        },

                        // 🔹 STATUS AKUN / AKUN TERHUBUNG
                        {
                            val connectedEmail = when {
                                supabaseEmail.isNotBlank() -> supabaseEmail
                                currentGoogleEmail.isNotBlank() -> currentGoogleEmail
                                accountEmail.isNotBlank() -> accountEmail
                                currentDisplayName.isNotBlank() -> currentDisplayName
                                else -> ""
                            }
                            val providerDesc = when {
                                authProvider.isNotBlank() -> "Login menggunakan $authProvider"
                                isSupabaseLoggedIn -> "Login menggunakan IceBeats Cloud"
                                isLoggedIn -> "Login menggunakan Akun Google / YouTube"
                                currentGoogleEmail.isNotBlank() -> "Login menggunakan Google"
                                else -> "Belum ada akun yang terhubung"
                            }
                            val providerIcon = when {
                                authProvider.equals("GitHub", ignoreCase = true) -> R.drawable.github
                                authProvider.equals("GitLab", ignoreCase = true) -> R.drawable.gitlab
                                authProvider.equals("Google", ignoreCase = true) || currentGoogleEmail.isNotBlank() -> R.drawable.google
                                else -> R.drawable.person
                            }

                            PreferenceEntry(
                                title = {
                                    Text(if (isUserLoggedIn && connectedEmail.isNotBlank()) connectedEmail else "Status Akun")
                                },
                                description = providerDesc,
                                icon = {
                                    Icon(
                                        painter = painterResource(providerIcon),
                                        contentDescription = null,
                                        tint = if (providerIcon == R.drawable.google) androidx.compose.ui.graphics.Color.Unspecified else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {}
                            )
                        },

                        // 🔹 LOGIN / LOGOUT
                        {
                            if (!isUserLoggedIn) {
                                PreferenceEntry(
                                    title = { Text(stringResource(R.string.login)) },
                                    description = "Masuk ke akun IceBeats",
                                    icon = { Icon(painterResource(R.drawable.login), null) },
                                    trailingContent = {
                                        Button(onClick = { navController.navigate("login") }) {
                                            Text(stringResource(R.string.login))
                                        }
                                    },
                                    onClick = { navController.navigate("login") }
                                )
                            } else {
                                PreferenceEntry(
                                    title = { Text("Logout Akun") },
                                    description = "Keluar dari sesi saat ini dan kembali ke mode Guest",
                                    icon = { Icon(painterResource(R.drawable.logout), null) },
                                    trailingContent = {
                                        OutlinedButton(onClick = {
                                            performLogout("Berhasil logout")
                                        }) {
                                            Text(stringResource(R.string.logout))
                                        }
                                    },
                                    onClick = {
                                        performLogout("Berhasil logout")
                                    }
                                )
                            }
                        },

                        // 🔹 ADVANCED LOGIN
                        {
                            PreferenceEntry(
                                title = {
                                    Text(
                                        if (!isLoggedIn)
                                            stringResource(R.string.advanced_login)
                                        else if (showToken)
                                            stringResource(R.string.token_shown)
                                        else
                                            stringResource(R.string.token_hidden)
                                    )
                                },
                                icon = { Icon(painterResource(R.drawable.token), null) },
                                onClick = {
                                    if (!isLoggedIn) {
                                        showTokenEditor = true
                                    } else {
                                        if (!showToken)
                                            showToken = true
                                        else
                                            showTokenEditor = true
                                    }
                                }
                            )
                        },

                        // 🔹 USE LOGIN FOR BROWSE
                        {
                            if (isLoggedIn) {
                                SwitchPreference(
                                    title = {
                                        Text(stringResource(R.string.use_login_for_browse))
                                    },
                                    description = stringResource(R.string.use_login_for_browse_desc),
                                    icon = {
                                        Icon(painterResource(R.drawable.person), null)
                                    },
                                    checked = useLoginForBrowse,
                                    onCheckedChange = {
                                        YouTube.useLoginForBrowse = it
                                        onUseLoginForBrowseChange(it)
                                    }
                                )
                            }
                        },

                        // 🔹 YTM SYNC
                        {
                            if (isLoggedIn) {
                                SwitchPreference(
                                    title = { Text(stringResource(R.string.ytm_sync)) },
                                    icon = {
                                        Icon(painterResource(R.drawable.cached), null)
                                    },
                                    checked = ytmSync,
                                    onCheckedChange = onYtmSyncChange
                                )
                            }
                        },
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 🔥 AVATAR SELECTOR
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    AvatarSelector(modifier = Modifier.padding(vertical = 8.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 🔥 RANK BADGE SELECTOR
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    RankBadgeSelector(modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
    }

    // 🔥 EDIT NAME DIALOG
    if (showEditNameDialog) {
        var newName by remember {
            val initial = if (currentDisplayName.equals("Hai, selamat datang di IceBeats", ignoreCase = true)) "" else currentDisplayName
            mutableStateOf(TextFieldValue(initial))
        }

        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text(stringResource(R.string.edit_display_name)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = {
                            if (it.text.length <= 30)
                                newName = it
                        },
                        label = { Text(stringResource(R.string.your_name)) },
                        supportingText = {
                            Text("${newName.text.length} / 30")
                        },
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Changes will be applied after restarting the app",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val savedName = newName.text.trim()
                            nameManager.saveUserName(if (savedName.isNotBlank()) savedName else "Hai, selamat datang di IceBeats")
                        }
                        showEditNameDialog = false
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showEditNameDialog = false }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showTokenEditor) {
        var cookieValue by remember { mutableStateOf(TextFieldValue(innerTubeCookie)) }
        var visitorDataValue by remember { mutableStateOf(TextFieldValue(visitorData)) }

        AlertDialog(
            onDismissRequest = { showTokenEditor = false },
            icon = { Icon(painterResource(R.drawable.token), null) },
            title = { Text(stringResource(R.string.advanced_login)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = cookieValue,
                        onValueChange = { cookieValue = it },
                        label = { Text(stringResource(R.string.inner_tube_cookie)) },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = visitorDataValue,
                        onValueChange = { visitorDataValue = it },
                        label = { Text(stringResource(R.string.visitor_data)) },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onInnerTubeCookieChange(cookieValue.text)
                        onVisitorDataChange(visitorDataValue.text)
                        showTokenEditor = false
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showTokenEditor = false }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (isLoggingOut) {
        Dialog(
            onDismissRequest = { /* Menunggu sampai backup & logout selesai */ },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1E1E1E),
                tonalElevation = 8.dp,
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Sedang Logout",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = logoutStatusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

}

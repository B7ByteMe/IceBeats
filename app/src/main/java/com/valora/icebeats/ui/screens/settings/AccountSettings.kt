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

                // Clear cloud-synced items and restore account data from Supabase without wiping local playback history
                if (database != null) {
                    withContext(Dispatchers.IO) {
                        database.clearAllLikes()
                        database.clearUserPlaylists()
                        database.clearAllPlaylistSongs()
                        // Keep local playback events intact; restoreUserData will merge any missing cloud events
                        database.clearAllArtistBookmarks()
                        database.clearAllAlbumBookmarks()
                        val res = supabaseClient.restoreUserData(database)
                        res.onSuccess { count ->
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
                    }

                    onInnerTubeCookieChange("")
                    onAccountNameChange("")
                    onAccountEmailChange("")
                    onAccountChannelHandleChange("")
                    onVisitorDataChange("")
                    onDataSyncIdChange("")
                    nameManager.clearGoogleLoginLock()
                    nameManager.saveUserName("Hai, selamat datang di IceBeats")
                    nameManager.saveAccountEmail("")
                    avatarManager.saveAvatarSelection(AvatarSelection.Default)
                    RankPreferenceManager(context).saveDisplayedRank(null)
                    com.valora.icebeats.utils.icebeatsStatsCloudSync.clearCachedUserId(context)

                    Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isLoggingOut = false
                }
            }
        }
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
                                    Text(
                                        if (isSupabaseLoggedIn) {
                                            supabaseName.ifBlank { supabaseEmail.substringBefore("@") }
                                        } else {
                                            "Masuk / Buat Akun Cloud"
                                        }
                                    )
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
                                                val res = supabaseClient.syncUserData(database)
                                                isSyncing = false
                                                res.onSuccess { msg ->
                                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                                }.onFailure { err ->
                                                    Toast.makeText(context, "Gagal sinkron: ${err.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        } else null
                    )
                )

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

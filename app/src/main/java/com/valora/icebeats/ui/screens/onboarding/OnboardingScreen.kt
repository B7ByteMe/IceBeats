package com.valora.icebeats.ui.screens.onboarding

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.valora.icebeats.R
import com.valora.icebeats.ui.component.AvatarPreferenceManager
import com.valora.icebeats.ui.component.AvatarSelection
import com.valora.icebeats.ui.component.NamePreferenceManager
import com.valora.icebeats.ui.component.drawBackdropCustomShape
import com.valora.icebeats.ui.component.layerBackdrop
import com.valora.icebeats.ui.component.rememberBackdrop
import com.valora.icebeats.utils.AuthApiClient
import com.valora.icebeats.utils.AuthResult
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import android.net.Uri
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

enum class SyncState {
    IDLE,
    CHECKING,
    RESTORING,
    RESTORED,
    CREATING_BACKUP,
    NEW_USER
}

enum class AuthMode {
    LOGIN,
    SIGNUP,
    VERIFY_SIGNUP_OTP,
    FORGOT_PASSWORD,
    VERIFY_RECOVERY_OTP
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun OnboardingScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val database = com.valora.icebeats.LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
    val namePrefManager = remember { NamePreferenceManager(context) }
    val avatarPrefManager = remember { AvatarPreferenceManager(context) }
    val backupViewModel: com.valora.icebeats.viewmodels.BackupRestoreViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val authClient = remember { AuthApiClient() }
    val supabaseClient = remember { com.valora.icebeats.supabase.SupabaseClient(context) }
    val supabaseAuthManager = remember { com.valora.icebeats.supabase.SupabaseAuthManager.getInstance(context) }
    val isSupabaseLoggedIn by supabaseAuthManager.isLoggedIn.collectAsState()

    val initialIsLoggedIn = remember { isSupabaseLoggedIn }
    LaunchedEffect(Unit) {
        if (initialIsLoggedIn) {
            navController.navigate("home") {
                popUpTo("login") { inclusive = true }
                popUpTo("onboarding") { inclusive = true }
                launchSingleTop = true
            }
        }
    }
    
    var syncState by remember { mutableStateOf(SyncState.IDLE) }
    var authMode by remember { mutableStateOf(AuthMode.LOGIN) }
    var currentUserName by remember { mutableStateOf("") }
    var currentUserEmail by remember { mutableStateOf("") }
    var featureStep by remember { mutableStateOf(0) }
    var isGoogleSignInOpen by remember { mutableStateOf(false) }

    // Input States
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var otpInput by remember { mutableStateOf("") }
    var newPasswordInput by remember { mutableStateOf("") }
    var confirmNewPasswordInput by remember { mutableStateOf("") }
    var isEmailProcessing by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var confirmNewPasswordVisible by remember { mutableStateOf(false) }
    var otpCountdownSeconds by remember { mutableIntStateOf(0) }
    var showCancelSignupDialog by remember { mutableStateOf(false) }

    LaunchedEffect(otpCountdownSeconds) {
        if (otpCountdownSeconds > 0) {
            delay(1000L)
            otpCountdownSeconds -= 1
        }
    }

    fun stepBackAuthMode() {
        when (authMode) {
            AuthMode.VERIFY_SIGNUP_OTP -> showCancelSignupDialog = true
            AuthMode.VERIFY_RECOVERY_OTP -> authMode = AuthMode.FORGOT_PASSWORD
            AuthMode.FORGOT_PASSWORD -> authMode = AuthMode.LOGIN
            AuthMode.SIGNUP -> authMode = AuthMode.LOGIN
            AuthMode.LOGIN -> Unit
        }
    }

    BackHandler(enabled = syncState == SyncState.IDLE) {
        if (authMode != AuthMode.LOGIN) {
            stepBackAuthMode()
        } else {
            (context as? Activity)?.finish()
        }
    }

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

    fun continueToHome() {
        coroutineScope.launch {
            syncState = SyncState.IDLE
            navController.navigate("home") {
                popUpTo("onboarding") {
                    inclusive = true
                }
                popUpTo("login") {
                    inclusive = true
                }
                launchSingleTop = true
            }
        }
    }

    fun saveProfileAndSync(name: String, email: String, photoUrl: String? = null, isNewSignup: Boolean = false) {
        coroutineScope.launch {
            currentUserName = name
            currentUserEmail = email
            namePrefManager.rememberGoogleLoginEmail(email)
            namePrefManager.saveAccountEmail(email)
            if (isNewSignup) {
                namePrefManager.saveUserName(name)
                supabaseClient.updateUserProfileFields(name = name)
            } else {
                val currentName = namePrefManager.userName.first()
                if (currentName.isBlank() || currentName.startsWith("Hai,")) {
                    namePrefManager.saveUserName(name)
                }
            }
            if (!photoUrl.isNullOrBlank()) {
                avatarPrefManager.saveAvatarSelection(
                    AvatarSelection.Custom(uri = photoUrl, cloudUrl = photoUrl)
                )
            } else {
                avatarPrefManager.saveAvatarSelection(
                    AvatarSelection.DiceBear(generatedAvatarUrl(name, email))
                )
            }

            withContext(Dispatchers.IO) {
                com.valora.icebeats.utils.IceBeatsStatsCloudSync.clearUserSessionStats(context)
                com.valora.icebeats.ui.component.RankPreferenceManager(context).saveDisplayedRank(null)
                com.valora.icebeats.ui.component.BorderPreferenceManager(context).saveSelectedBorder(
                    com.valora.icebeats.ui.component.MasterBorderStyle.ROYAL_CROWN
                )
                database.clearAllLikes()
                database.clearUserPlaylists()
                database.clearAllPlaylistSongs()
                database.clearAllEvents()
                database.clearAllArtistBookmarks()
                database.clearAllAlbumBookmarks()
            }

            if (isNewSignup) {
                syncState = SyncState.NEW_USER
                continueToHome()
            } else {
                syncState = SyncState.RESTORING
                supabaseClient.syncCurrentUserStats(context)
                val restoreResult = supabaseClient.restoreUserData(database)
                restoreResult.onFailure { err ->
                    Toast.makeText(context, "Sinkronisasi Cloud: ${err.message}", Toast.LENGTH_SHORT).show()
                }
                syncState = SyncState.RESTORED
                continueToHome()
            }
        }
    }

    fun handleEmailAuth() {
        if (emailInput.isBlank() || passwordInput.isBlank() || (authMode == AuthMode.SIGNUP && nameInput.isBlank())) {
            Toast.makeText(context, "Mohon lengkapi semua data yang diperlukan", Toast.LENGTH_SHORT).show()
            return
        }
        isEmailProcessing = true
        coroutineScope.launch {
            if (authMode == AuthMode.SIGNUP) {
                val result = supabaseClient.signUp(emailInput, passwordInput, nameInput)
                result.onSuccess { msg ->
                    if (msg.contains("konfirmasi", ignoreCase = true) || msg.contains("email", ignoreCase = true)) {
                        Toast.makeText(context, "Kode OTP pendaftaran telah dikirim ke $emailInput", Toast.LENGTH_LONG).show()
                        otpInput = ""
                        otpCountdownSeconds = 60
                        authMode = AuthMode.VERIFY_SIGNUP_OTP
                    } else {
                        val displayName = nameInput.ifBlank { displayNameFromEmail(emailInput) }
                        saveProfileAndSync(displayName, emailInput, photoUrl = null, isNewSignup = true)
                    }
                }.onFailure { err ->
                    Toast.makeText(context, err.localizedMessage ?: "Pendaftaran akun gagal", Toast.LENGTH_LONG).show()
                }
            } else {
                val result = supabaseClient.signIn(emailInput, passwordInput)
                result.onSuccess {
                    val displayName = displayNameFromEmail(emailInput)
                    saveProfileAndSync(displayName, emailInput, photoUrl = null, isNewSignup = false)
                }.onFailure { err ->
                    val errMsg = err.localizedMessage ?: "Login gagal"
                    if (errMsg.contains("Email not confirmed", ignoreCase = true)) {
                        Toast.makeText(context, "Email belum diverifikasi. Silakan masukkan kode OTP.", Toast.LENGTH_LONG).show()
                        otpInput = ""
                        otpCountdownSeconds = 60
                        authMode = AuthMode.VERIFY_SIGNUP_OTP
                    } else {
                        Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
                    }
                }
            }
            isEmailProcessing = false
        }
    }

    fun handleVerifySignupOtp() {
        if (otpInput.trim().length < 6) {
            Toast.makeText(context, "Masukkan 6 digit kode OTP", Toast.LENGTH_SHORT).show()
            return
        }
        isEmailProcessing = true
        coroutineScope.launch {
            val result = supabaseClient.verifySignupOtp(emailInput, otpInput.trim())
            result.onSuccess { msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                val displayName = nameInput.ifBlank { displayNameFromEmail(emailInput) }
                saveProfileAndSync(displayName, emailInput, photoUrl = null, isNewSignup = true)
            }.onFailure { err ->
                Toast.makeText(context, err.localizedMessage ?: "Verifikasi OTP gagal", Toast.LENGTH_LONG).show()
            }
            isEmailProcessing = false
        }
    }

    fun handleResendSignupOtp() {
        if (emailInput.isBlank() || otpCountdownSeconds > 0) return
        isEmailProcessing = true
        coroutineScope.launch {
            val result = supabaseClient.resendSignupOtp(emailInput)
            result.onSuccess { msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                otpCountdownSeconds = 60
            }.onFailure { err ->
                Toast.makeText(context, err.localizedMessage ?: "Gagal mengirim ulang OTP", Toast.LENGTH_LONG).show()
            }
            isEmailProcessing = false
        }
    }

    fun handleSendPasswordReset(stayOnRecoveryScreen: Boolean = false) {
        if (emailInput.isBlank() || !emailInput.contains("@")) {
            Toast.makeText(context, "Masukkan alamat email yang valid", Toast.LENGTH_SHORT).show()
            return
        }
        if (stayOnRecoveryScreen && otpCountdownSeconds > 0) return
        isEmailProcessing = true
        coroutineScope.launch {
            val result = supabaseClient.sendPasswordReset(emailInput)
            result.onSuccess { msg ->
                Toast.makeText(context, "Kode OTP pemulihan telah dikirim ke $emailInput", Toast.LENGTH_LONG).show()
                otpCountdownSeconds = 60
                if (!stayOnRecoveryScreen) {
                    otpInput = ""
                    newPasswordInput = ""
                    confirmNewPasswordInput = ""
                    authMode = AuthMode.VERIFY_RECOVERY_OTP
                }
            }.onFailure { err ->
                Toast.makeText(context, err.localizedMessage ?: "Gagal mengirim link reset password", Toast.LENGTH_LONG).show()
            }
            isEmailProcessing = false
        }
    }

    fun handleVerifyRecoveryOtp() {
        if (otpInput.trim().length < 6) {
            Toast.makeText(context, "Masukkan 6 digit kode OTP", Toast.LENGTH_SHORT).show()
            return
        }
        if (newPasswordInput.length < 6) {
            Toast.makeText(context, "Password baru minimal 6 karakter", Toast.LENGTH_SHORT).show()
            return
        }
        if (newPasswordInput != confirmNewPasswordInput) {
            Toast.makeText(context, "Konfirmasi password tidak cocok", Toast.LENGTH_SHORT).show()
            return
        }
        isEmailProcessing = true
        coroutineScope.launch {
            val result = supabaseClient.verifyResetPasswordOtp(emailInput, otpInput.trim(), newPasswordInput)
            result.onSuccess { msg ->
                Toast.makeText(context, "Password berhasil diperbarui! Silakan masuk.", Toast.LENGTH_LONG).show()
                passwordInput = newPasswordInput
                authMode = AuthMode.LOGIN
            }.onFailure { err ->
                Toast.makeText(context, err.localizedMessage ?: "Gagal memperbarui password", Toast.LENGTH_LONG).show()
            }
            isEmailProcessing = false
        }
    }

    fun showSignInError(message: String) {
        isGoogleSignInOpen = false
        syncState = SyncState.IDLE
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun googleSignInErrorMessage(error: ApiException): String {
        return when (error.statusCode) {
            GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> "Google sign in was cancelled."
            GoogleSignInStatusCodes.SIGN_IN_CURRENTLY_IN_PROGRESS -> "Google sign in is already open."
            GoogleSignInStatusCodes.SIGN_IN_FAILED -> "Google sign in failed. Check the Android OAuth client package and SHA-1."
            else -> "Google sign in failed (${error.statusCode}): ${error.message.orEmpty()}"
        }
    }

    val googleSignInClient = remember {
        com.valora.icebeats.utils.GoogleAuthManager(context).getSignInClient()
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        coroutineScope.launch {
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java)
                val email = account.email.orEmpty()
                if (email.isBlank()) {
                    showSignInError("Google did not return an email for this account.")
                    return@launch
                }
                val name = account.displayName
                    ?.takeIf { it.isNotBlank() }
                    ?: account.givenName
                    ?: displayNameFromEmail(email)

                // Sign in directly to Supabase with Google ID token
                val idToken = account.idToken
                if (!idToken.isNullOrBlank()) {
                    val supabaseResult = supabaseClient.signInWithGoogleIdToken(idToken)
                    supabaseResult.onFailure { err ->
                        showSignInError("Gagal autentikasi Google ke Supabase: ${err.message}")
                        return@launch
                    }
                }

                isGoogleSignInOpen = false
                saveProfileAndSync(name, email, account.photoUrl?.toString(), isNewSignup = false)
            } catch (e: ApiException) {
                e.printStackTrace()
                showSignInError(googleSignInErrorMessage(e))
            } catch (e: Exception) {
                e.printStackTrace()
                showSignInError("Google sign in failed: ${e.message}")
            }
        }
    }

    val onGoogleSignInClick: () -> Unit = {
        if (!isGoogleSignInOpen) {
            isGoogleSignInOpen = true
            googleSignInClient.revokeAccess().addOnCompleteListener {
                googleSignInLauncher.launch(googleSignInClient.signInIntent)
            }
        }
    }

    val hazeState = remember { HazeState() }
    val backdrop = rememberBackdrop()
    val glassLayer = rememberGraphicsLayer()

    val silverAccent = Color(0xFFE2E8F0)
    val liquidCardShape = RoundedCornerShape(34.dp)
    val liquidCardFill = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.14f),
                Color(0xFFD6E0EC).copy(alpha = 0.05f),
                Color(0xFF0A0D12).copy(alpha = 0.52f)
            )
        )
    }
    val liquidCardBorder = remember {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.50f),
                Color(0xFFD0D9E4).copy(alpha = 0.18f),
                Color.White.copy(alpha = 0.06f),
                Color(0xFFE2E8F0).copy(alpha = 0.30f)
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF06070A))
    ) {
        // Subtle monochrome titanium-slate spatial depth behind glass (no glow on logos)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .haze(hazeState)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(Color(0xFF06070A))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF94A3B8).copy(alpha = 0.16f),
                            Color(0xFF475569).copy(alpha = 0.06f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.18f, size.height * 0.84f),
                        radius = size.width * 0.85f
                    ),
                    center = Offset(size.width * 0.18f, size.height * 0.84f),
                    radius = size.width * 0.85f
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFCBD5E1).copy(alpha = 0.12f),
                            Color(0xFF64748B).copy(alpha = 0.04f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.86f, size.height * 0.52f),
                        radius = size.width * 0.75f
                    ),
                    center = Offset(size.width * 0.86f, size.height * 0.52f),
                    radius = size.width * 0.75f
                )
            }
        }

        // Top-left iOS 26 Liquid Glass Back Button (hidden on main Login screen so entry is only via Login or Continue as Guest)
        if (authMode != AuthMode.LOGIN && syncState == SyncState.IDLE) {
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
                    .clickable { stepBackAuthMode() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back),
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Floating iOS 26 Liquid Glass Login Sheet
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .fillMaxWidth()
                .fillMaxHeight(if (authMode == AuthMode.SIGNUP || authMode == AuthMode.VERIFY_RECOVERY_OTP) 0.78f else 0.70f)
                .clip(liquidCardShape)
                .hazeChild(
                    state = hazeState,
                    style = HazeMaterials.ultraThin()
                )
                .background(
                    brush = liquidCardFill,
                    shape = liquidCardShape
                )
                .border(
                    width = 1.2.dp,
                    brush = liquidCardBorder,
                    shape = liquidCardShape
                )
        ) {
            // Top subtle Liquid Glass grabber pill
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
                    .width(38.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.32f))
            )

            val scrollState = rememberScrollState()
            Crossfade(targetState = syncState, label = "OnboardingState") { state ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(start = 24.dp, end = 24.dp, top = 26.dp, bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    when (state) {
                        SyncState.IDLE -> {
                            val textFieldColors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = silverAccent.copy(alpha = 0.85f),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.20f),
                                focusedContainerColor = Color.White.copy(alpha = 0.11f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = silverAccent
                            )

                            when (authMode) {
                                AuthMode.LOGIN, AuthMode.SIGNUP -> {
                                    // Clean Logo (no glow or shadow behind it)
                                    androidx.compose.foundation.Image(
                                        painter = painterResource(R.drawable.icebeats_logo),
                                        contentDescription = "IceBeats Logo",
                                        modifier = Modifier.size(68.dp).padding(bottom = 8.dp)
                                    )
                                    Text(
                                        text = if (authMode == AuthMode.SIGNUP) "Get Started Free" else "Welcome Back",
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = (-0.4).sp
                                        ),
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (authMode == AuthMode.SIGNUP) "Free Forever. No Ads, No Subscription" else "Welcome back we missed you",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = silverAccent.copy(alpha = 0.72f),
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))

                                    if (authMode == AuthMode.SIGNUP) {
                                        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                                            Text("Your name", color = silverAccent.copy(alpha = 0.75f), fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp, start = 4.dp))
                                            OutlinedTextField(
                                                value = nameInput,
                                                onValueChange = { nameInput = it },
                                                placeholder = { Text("@yourname", color = Color.White.copy(alpha = 0.45f)) },
                                                leadingIcon = { Icon(painterResource(R.drawable.person), contentDescription = null, tint = silverAccent.copy(alpha = 0.65f)) },
                                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                                singleLine = true,
                                                shape = RoundedCornerShape(18.dp),
                                                colors = textFieldColors
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                                        Text(if (authMode == AuthMode.SIGNUP) "Email address" else "Username / Email", color = silverAccent.copy(alpha = 0.75f), fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp, start = 4.dp))
                                        OutlinedTextField(
                                            value = emailInput,
                                            onValueChange = { emailInput = it },
                                            placeholder = { Text("yourname@gmail.com", color = Color.White.copy(alpha = 0.45f)) },
                                            leadingIcon = { Icon(painterResource(if (authMode == AuthMode.SIGNUP) R.drawable.email else R.drawable.person), contentDescription = null, tint = silverAccent.copy(alpha = 0.65f)) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                            modifier = Modifier.fillMaxWidth().height(52.dp),
                                            singleLine = true,
                                            shape = RoundedCornerShape(18.dp),
                                            colors = textFieldColors
                                        )
                                    }

                                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                                        Text("Password", color = silverAccent.copy(alpha = 0.75f), fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp, start = 4.dp))
                                        OutlinedTextField(
                                            value = passwordInput,
                                            onValueChange = { passwordInput = it },
                                            placeholder = { Text("••••••••", color = Color.White.copy(alpha = 0.45f)) },
                                            leadingIcon = { Icon(painterResource(R.drawable.lock), contentDescription = null, tint = silverAccent.copy(alpha = 0.65f), modifier = Modifier.size(20.dp)) },
                                            trailingIcon = {
                                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                                    Icon(painterResource(if (passwordVisible) R.drawable.visibility else R.drawable.visibility_off), contentDescription = null, tint = silverAccent.copy(alpha = 0.65f), modifier = Modifier.size(20.dp))
                                                }
                                            },
                                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                            modifier = Modifier.fillMaxWidth().height(52.dp),
                                            singleLine = true,
                                            shape = RoundedCornerShape(18.dp),
                                            colors = textFieldColors
                                        )
                                    }

                                    if (authMode == AuthMode.LOGIN) {
                                        Text(
                                            text = "Forgot Password?",
                                            color = silverAccent.copy(alpha = 0.78f),
                                            fontSize = 12.sp,
                                            modifier = Modifier
                                                .align(Alignment.End)
                                                .padding(top = 2.dp, bottom = 14.dp)
                                                .clickable {
                                                    authMode = AuthMode.FORGOT_PASSWORD
                                                }
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.height(14.dp))
                                    }

                                    // Silver Liquid Glass Action Button
                                    SilverLiquidGlassButton(
                                        text = if (authMode == AuthMode.SIGNUP) "Sign up" else "Login",
                                        isLoading = isEmailProcessing,
                                        onClick = { handleEmailAuth() }
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Or continue with divider
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        HorizontalDivider(modifier = Modifier.weight(1f), color = Color.White.copy(alpha = 0.16f))
                                        Text(
                                            text = if (authMode == AuthMode.SIGNUP) " Or sign up with " else " Or continue with ",
                                            color = silverAccent.copy(alpha = 0.55f),
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        )
                                        HorizontalDivider(modifier = Modifier.weight(1f), color = Color.White.copy(alpha = 0.16f))
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    // Liquid Glass Social Icons Row
                                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                        LiquidGlassSocialTile(
                                            backdrop = backdrop,
                                            glassLayer = glassLayer,
                                            onClick = { onGoogleSignInClick() }
                                        ) {
                                            Icon(painterResource(R.drawable.ic_google_logo), contentDescription = "Google", tint = Color.Unspecified)
                                        }
                                        LiquidGlassSocialTile(
                                            backdrop = backdrop,
                                            glassLayer = glassLayer,
                                            onClick = {
                                                val oauthUrl = supabaseClient.getOAuthAuthorizeUrl("facebook")
                                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(oauthUrl))
                                                context.startActivity(intent)
                                            }
                                        ) {
                                            Icon(painterResource(R.drawable.facebook), contentDescription = "Facebook", tint = Color(0xFF1877F2))
                                        }
                                        LiquidGlassSocialTile(
                                            backdrop = backdrop,
                                            glassLayer = glassLayer,
                                            onClick = {
                                                val oauthUrl = supabaseClient.getOAuthAuthorizeUrl("github")
                                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(oauthUrl))
                                                context.startActivity(intent)
                                            }
                                        ) {
                                            Icon(painterResource(R.drawable.github), contentDescription = "Github", tint = Color.White)
                                        }
                                        LiquidGlassSocialTile(
                                            backdrop = backdrop,
                                            glassLayer = glassLayer,
                                            onClick = {
                                                val oauthUrl = supabaseClient.getOAuthAuthorizeUrl("gitlab")
                                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(oauthUrl))
                                                context.startActivity(intent)
                                            }
                                        ) {
                                            Icon(painterResource(R.drawable.gitlab), contentDescription = "GitLab", tint = Color.Unspecified)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(20.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(onClick = { navController.navigate("guest_profile_setup") }) {
                                            Text("Continue as Guest", color = silverAccent.copy(alpha = 0.78f), fontSize = 13.sp)
                                        }
                                        TextButton(onClick = { authMode = if (authMode == AuthMode.LOGIN) AuthMode.SIGNUP else AuthMode.LOGIN }) {
                                            Text(
                                                text = if (authMode == AuthMode.LOGIN) "Sign up" else "Login",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                }

                                AuthMode.VERIFY_SIGNUP_OTP -> {
                                    androidx.compose.foundation.Image(
                                        painter = painterResource(R.drawable.icebeats_logo),
                                        contentDescription = "IceBeats Logo",
                                        modifier = Modifier.size(68.dp).padding(bottom = 8.dp)
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = "Verifikasi Akun Baru",
                                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Masukkan 6-digit kode OTP pendaftaran yang telah dikirim ke:\n$emailInput",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = silverAccent.copy(alpha = 0.85f),
                                        textAlign = TextAlign.Center
                                    )

                                    Spacer(modifier = Modifier.height(14.dp))

                                    OtpCountdownBadge(
                                        countdownSeconds = otpCountdownSeconds,
                                        silverAccent = silverAccent
                                    )

                                    Spacer(modifier = Modifier.height(14.dp))

                                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                                        Text("Kode OTP (6 Digit)", color = silverAccent.copy(alpha = 0.75f), fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp, start = 4.dp))
                                        OutlinedTextField(
                                            value = otpInput,
                                            onValueChange = { if (it.length <= 8) otpInput = it },
                                            placeholder = { Text("Contoh: 123456", color = Color.White.copy(alpha = 0.45f)) },
                                            leadingIcon = { Icon(painterResource(R.drawable.lock), contentDescription = null, tint = silverAccent.copy(alpha = 0.65f), modifier = Modifier.size(20.dp)) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.fillMaxWidth().height(52.dp),
                                            singleLine = true,
                                            shape = RoundedCornerShape(18.dp),
                                            colors = textFieldColors
                                        )
                                    }

                                    SilverLiquidGlassButton(
                                        text = "Verifikasi & Masuk",
                                        isLoading = isEmailProcessing,
                                        onClick = { handleVerifySignupOtp() }
                                    )

                                    Spacer(modifier = Modifier.height(14.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OtpResendTextButton(
                                            countdownSeconds = otpCountdownSeconds,
                                            isProcessing = isEmailProcessing,
                                            silverAccent = silverAccent,
                                            onResend = { handleResendSignupOtp() }
                                        )
                                        TextButton(onClick = { showCancelSignupDialog = true }) {
                                            Text("← Ganti Email", color = silverAccent.copy(alpha = 0.72f), fontSize = 13.sp)
                                        }
                                    }
                                }

                                AuthMode.FORGOT_PASSWORD -> {
                                    androidx.compose.foundation.Image(
                                        painter = painterResource(R.drawable.icebeats_logo),
                                        contentDescription = "IceBeats Logo",
                                        modifier = Modifier.size(68.dp).padding(bottom = 8.dp)
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = "Lupa Password",
                                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Masukkan alamat email akun Anda untuk menerima kode OTP pemulihan password.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = silverAccent.copy(alpha = 0.85f),
                                        textAlign = TextAlign.Center
                                    )

                                    Spacer(modifier = Modifier.height(20.dp))

                                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                        Text("Alamat Email Akun", color = silverAccent.copy(alpha = 0.75f), fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp, start = 4.dp))
                                        OutlinedTextField(
                                            value = emailInput,
                                            onValueChange = { emailInput = it },
                                            placeholder = { Text("yourname@gmail.com", color = Color.White.copy(alpha = 0.45f)) },
                                            leadingIcon = { Icon(painterResource(R.drawable.email), contentDescription = null, tint = silverAccent.copy(alpha = 0.65f)) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                            modifier = Modifier.fillMaxWidth().height(52.dp),
                                            singleLine = true,
                                            shape = RoundedCornerShape(18.dp),
                                            colors = textFieldColors
                                        )
                                    }

                                    SilverLiquidGlassButton(
                                        text = "Kirim Kode OTP",
                                        isLoading = isEmailProcessing,
                                        onClick = { handleSendPasswordReset() }
                                    )

                                    Spacer(modifier = Modifier.height(14.dp))

                                    TextButton(onClick = { authMode = AuthMode.LOGIN }) {
                                        Text("← Kembali ke Login", color = silverAccent.copy(alpha = 0.72f), fontSize = 13.sp)
                                    }
                                }

                                AuthMode.VERIFY_RECOVERY_OTP -> {
                                    androidx.compose.foundation.Image(
                                        painter = painterResource(R.drawable.icebeats_logo),
                                        contentDescription = "IceBeats Logo",
                                        modifier = Modifier.size(68.dp).padding(bottom = 8.dp)
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = "Buat Password Baru",
                                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Masukkan kode OTP 6-digit dari email $emailInput dan tentukan password baru.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = silverAccent.copy(alpha = 0.85f),
                                        textAlign = TextAlign.Center
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    OtpCountdownBadge(
                                        countdownSeconds = otpCountdownSeconds,
                                        silverAccent = silverAccent
                                    )

                                    Spacer(modifier = Modifier.height(14.dp))

                                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                                        Text("Kode OTP (6 Digit)", color = silverAccent.copy(alpha = 0.75f), fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp, start = 4.dp))
                                        OutlinedTextField(
                                            value = otpInput,
                                            onValueChange = { if (it.length <= 8) otpInput = it },
                                            placeholder = { Text("Contoh: 123456", color = Color.White.copy(alpha = 0.45f)) },
                                            leadingIcon = { Icon(painterResource(R.drawable.lock), contentDescription = null, tint = silverAccent.copy(alpha = 0.65f), modifier = Modifier.size(20.dp)) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.fillMaxWidth().height(52.dp),
                                            singleLine = true,
                                            shape = RoundedCornerShape(18.dp),
                                            colors = textFieldColors
                                        )
                                    }

                                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                                        Text("Password Baru (Min. 6 Karakter)", color = silverAccent.copy(alpha = 0.75f), fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp, start = 4.dp))
                                        OutlinedTextField(
                                            value = newPasswordInput,
                                            onValueChange = { newPasswordInput = it },
                                            placeholder = { Text("••••••••", color = Color.White.copy(alpha = 0.45f)) },
                                            leadingIcon = { Icon(painterResource(R.drawable.lock), contentDescription = null, tint = silverAccent.copy(alpha = 0.65f), modifier = Modifier.size(20.dp)) },
                                            trailingIcon = {
                                                IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                                                    Icon(painterResource(if (newPasswordVisible) R.drawable.visibility else R.drawable.visibility_off), contentDescription = null, tint = silverAccent.copy(alpha = 0.65f), modifier = Modifier.size(20.dp))
                                                }
                                            },
                                            visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                            modifier = Modifier.fillMaxWidth().height(52.dp),
                                            singleLine = true,
                                            shape = RoundedCornerShape(18.dp),
                                            colors = textFieldColors
                                        )
                                    }

                                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                                        Text("Konfirmasi Password Baru", color = silverAccent.copy(alpha = 0.75f), fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp, start = 4.dp))
                                        OutlinedTextField(
                                            value = confirmNewPasswordInput,
                                            onValueChange = { confirmNewPasswordInput = it },
                                            placeholder = { Text("••••••••", color = Color.White.copy(alpha = 0.45f)) },
                                            leadingIcon = { Icon(painterResource(R.drawable.lock), contentDescription = null, tint = silverAccent.copy(alpha = 0.65f), modifier = Modifier.size(20.dp)) },
                                            trailingIcon = {
                                                IconButton(onClick = { confirmNewPasswordVisible = !confirmNewPasswordVisible }) {
                                                    Icon(painterResource(if (confirmNewPasswordVisible) R.drawable.visibility else R.drawable.visibility_off), contentDescription = null, tint = silverAccent.copy(alpha = 0.65f), modifier = Modifier.size(20.dp))
                                                }
                                            },
                                            visualTransformation = if (confirmNewPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                            modifier = Modifier.fillMaxWidth().height(52.dp),
                                            singleLine = true,
                                            shape = RoundedCornerShape(18.dp),
                                            colors = textFieldColors
                                        )
                                    }

                                    SilverLiquidGlassButton(
                                        text = "Simpan Password Baru",
                                        isLoading = isEmailProcessing,
                                        onClick = { handleVerifyRecoveryOtp() }
                                    )

                                    Spacer(modifier = Modifier.height(14.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OtpResendTextButton(
                                            countdownSeconds = otpCountdownSeconds,
                                            isProcessing = isEmailProcessing,
                                            silverAccent = silverAccent,
                                            onResend = { handleSendPasswordReset(stayOnRecoveryScreen = true) }
                                        )
                                        TextButton(onClick = { authMode = AuthMode.FORGOT_PASSWORD }) {
                                            Text("← Batal", color = silverAccent.copy(alpha = 0.72f), fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }

                        SyncState.CHECKING -> {
                            CircularProgressIndicator(color = silverAccent, modifier = Modifier.padding(bottom = 24.dp))
                            Text(
                                text = "Looking for your cloud backup...",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            Text(stringResource(R.string.please_wait), color = silverAccent.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                        }

                        SyncState.RESTORING -> {
                            CircularProgressIndicator(color = silverAccent, modifier = Modifier.padding(bottom = 24.dp))
                            Text(
                                text = "Restoring your cloud backup...",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            Text(stringResource(R.string.please_wait), color = silverAccent.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                        }

                        SyncState.RESTORED -> {
                            Icon(painter = painterResource(R.drawable.check_circle), contentDescription = "Success", tint = silverAccent, modifier = Modifier.size(64.dp).padding(bottom = 16.dp))
                            Text(
                                text = "Hi $currentUserName,",
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = "Welcome again back to IceBeats",
                                color = silverAccent.copy(alpha = 0.75f),
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center
                            )
                        }

                        SyncState.CREATING_BACKUP -> {
                            CircularProgressIndicator(color = silverAccent, modifier = Modifier.padding(bottom = 24.dp))
                            Text(
                                text = "Creating your cloud backup...",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            Text(stringResource(R.string.uploading_first_backup), color = silverAccent.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                        }

                        SyncState.NEW_USER -> {
                            val featureTitles = listOf("Discover Music", "Create Playlists", "Offline Mode")
                            val featureDesc = listOf("Find millions of songs matching your mood.", "Curate your perfect listening experience.", "Download your favorites and listen anywhere.")

                            Text(
                                text = if (featureStep == 0) "Hi $currentUserName, welcome!" else featureTitles[featureStep - 1],
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = if (featureStep == 0) "Looking like you are new to IceBeats" else featureDesc[featureStep - 1],
                                color = silverAccent.copy(alpha = 0.75f),
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(bottom = 32.dp)
                            )
                            SilverLiquidGlassButton(
                                text = if (featureStep == featureTitles.size) "Get Started" else "Next",
                                isLoading = false,
                                onClick = {
                                    if (featureStep < featureTitles.size) featureStep++ else continueToHome()
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showCancelSignupDialog) {
            CancelSignupLiquidGlassDialog(
                silverAccent = silverAccent,
                onDismiss = { showCancelSignupDialog = false },
                onConfirmCancel = {
                    showCancelSignupDialog = false
                    otpInput = ""
                    otpCountdownSeconds = 0
                    authMode = AuthMode.SIGNUP
                }
            )
        }
    }
}

/**
 * Primary metallic silver pill button styled for iOS Liquid Glass.
 */
@Composable
private fun SilverLiquidGlassButton(
    text: String,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(CircleShape)
            .background(brush = silverGradient, shape = CircleShape)
            .border(width = 1.dp, brush = silverBorder, shape = CircleShape),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent
        ),
        contentPadding = PaddingValues(),
        enabled = !isLoading
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = Color(0xFF0C1016),
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(22.dp)
            )
        } else {
            Text(
                text = text,
                color = Color(0xFF0C1016),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

/**
 * Frosted Liquid Glass tile used for third-party OAuth providers.
 */
@Composable
private fun LiquidGlassSocialTile(
    backdrop: com.valora.icebeats.ui.component.PlatformBackdrop,
    glassLayer: androidx.compose.ui.graphics.layer.GraphicsLayer,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(shape)
            .drawBackdropCustomShape(
                backdrop = backdrop,
                layer = glassLayer,
                luminanceAnimation = 0.35f,
                shape = shape
            )
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.16f),
                        Color.White.copy(alpha = 0.05f)
                    )
                ),
                shape = shape
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.48f),
                        Color.White.copy(alpha = 0.10f),
                        Color.White.copy(alpha = 0.25f)
                    )
                ),
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(11.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * Liquid Glass pill showing remaining OTP cooldown or readiness to resend.
 */
@Composable
private fun OtpCountdownBadge(
    countdownSeconds: Int,
    silverAccent: Color
) {
    val label = if (countdownSeconds > 0) {
        val mins = countdownSeconds / 60
        val secs = countdownSeconds % 60
        "Kirim ulang tersedia dalam %02d:%02d".format(mins, secs)
    } else {
        "Kode OTP belum masuk? Bisa minta kode baru"
    }
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                color = Color.White.copy(alpha = if (countdownSeconds > 0) 0.08f else 0.12f),
                shape = CircleShape
            )
            .border(
                width = 1.dp,
                color = silverAccent.copy(alpha = if (countdownSeconds > 0) 0.22f else 0.40f),
                shape = CircleShape
            )
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = silverAccent.copy(alpha = if (countdownSeconds > 0) 0.85f else 0.95f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Resend OTP button locked with countdown until cooldown expires.
 */
@Composable
private fun OtpResendTextButton(
    countdownSeconds: Int,
    isProcessing: Boolean,
    silverAccent: Color,
    onResend: () -> Unit
) {
    val canResend = countdownSeconds <= 0 && !isProcessing
    val text = if (countdownSeconds > 0) {
        val mins = countdownSeconds / 60
        val secs = countdownSeconds % 60
        "Kirim Ulang (%02d:%02d)".format(mins, secs)
    } else {
        "Kirim Ulang OTP"
    }
    TextButton(
        onClick = onResend,
        enabled = canResend
    ) {
        Text(
            text = text,
            color = if (canResend) silverAccent else silverAccent.copy(alpha = 0.42f),
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )
    }
}

/**
 * Confirmation modal styled in iOS Liquid Glass when cancelling active new account creation.
 */
@Composable
private fun CancelSignupLiquidGlassDialog(
    silverAccent: Color,
    onDismiss: () -> Unit,
    onConfirmCancel: () -> Unit
) {
    val shape = RoundedCornerShape(28.dp)
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1E242E).copy(alpha = 0.96f),
                            Color(0xFF0E1218).copy(alpha = 0.98f)
                        )
                    ),
                    shape = shape
                )
                .border(
                    width = 1.2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.48f),
                            Color.White.copy(alpha = 0.12f),
                            silverAccent.copy(alpha = 0.28f)
                        )
                    ),
                    shape = shape
                )
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.icebeats_logo),
                    contentDescription = "IceBeats Logo",
                    modifier = Modifier.size(48.dp).padding(bottom = 10.dp)
                )
                Text(
                    text = "Batalkan Pembuatan Akun?",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Anda sedang dalam proses pembuatan akun baru dan menunggu verifikasi OTP. Apakah Anda yakin ingin membatalkannya?",
                    color = silverAccent.copy(alpha = 0.80f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onConfirmCancel,
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = CircleShape,
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.28f)
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White.copy(alpha = 0.06f),
                            contentColor = silverAccent
                        )
                    ) {
                        Text("Ya, Batalkan", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.verticalGradient(
                                    listOf(
                                        Color(0xFFF8FAFC),
                                        Color(0xFFD8E0EA),
                                        Color(0xFFB4BFC9)
                                    )
                                ),
                                shape = CircleShape
                            ),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues()
                    ) {
                        Text("Lanjutkan", color = Color(0xFF0C1016), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

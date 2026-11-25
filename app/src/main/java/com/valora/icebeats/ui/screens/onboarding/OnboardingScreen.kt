package com.valora.icebeats.ui.screens.onboarding

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.valora.icebeats.utils.AutoBackupManager
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
import com.valora.icebeats.ui.component.bitChordSettingsBackground
import com.valora.icebeats.ui.component.bitChordSettingsCardColor
import com.valora.icebeats.ui.component.bitChordSettingsOutlineColor
import com.valora.icebeats.ui.component.bitChordSettingsSecondaryTextColor
import com.valora.icebeats.ui.component.isBitChordDarkTheme
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
            AutoBackupManager.markSetupCompleted(context)
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

    val openedFromSettings = remember { navController.previousBackStackEntry != null }
    var onboardingStep by rememberSaveable { mutableIntStateOf(if (openedFromSettings) 2 else 0) }

    BackHandler(enabled = onboardingStep > 0 && !openedFromSettings && authMode == AuthMode.LOGIN && syncState == SyncState.IDLE) {
        onboardingStep--
    }

    val dark = isBitChordDarkTheme()
    val bgColor = bitChordSettingsBackground()
    val cardColor = bitChordSettingsCardColor()
    val outlineColor = bitChordSettingsOutlineColor()
    val secondaryColor = bitChordSettingsSecondaryTextColor()
    val primaryTextColor = if (dark) Color.White else Color.Black
    val silverAccent = secondaryColor
    val groupShape = RoundedCornerShape(14.dp)

    Crossfade(targetState = onboardingStep, label = "onboarding_step_crossfade") { step ->
        when (step) {
            0 -> WelcomeStep(
                bgColor = bgColor,
                cardColor = cardColor,
                outlineColor = outlineColor,
                primaryTextColor = primaryTextColor,
                secondaryColor = secondaryColor,
                dark = dark,
                onNext = { onboardingStep = 1 }
            )

            1 -> PermissionsStep(
                bgColor = bgColor,
                cardColor = cardColor,
                outlineColor = outlineColor,
                primaryTextColor = primaryTextColor,
                secondaryColor = secondaryColor,
                dark = dark,
                onBack = { onboardingStep = 0 },
                onNext = { onboardingStep = 2 }
            )

            else -> Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        // Clean BitChord-style Back Button (hidden on main Login screen)
        if (authMode != AuthMode.LOGIN && syncState == SyncState.IDLE) {
            Box(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(16.dp)
                    .align(Alignment.TopStart)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(cardColor)
                    .clickable { stepBackAuthMode() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back),
                    contentDescription = "Back",
                    tint = primaryTextColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Clean BitChord Settings-style Auth Container
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
        ) {
            val scrollState = rememberScrollState()
            Crossfade(targetState = syncState, label = "OnboardingState") { state ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    when (state) {
                        SyncState.IDLE -> {
                            val textFieldColors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primaryTextColor.copy(alpha = 0.65f),
                                unfocusedBorderColor = outlineColor,
                                focusedContainerColor = cardColor,
                                unfocusedContainerColor = cardColor,
                                focusedTextColor = primaryTextColor,
                                unfocusedTextColor = primaryTextColor,
                                cursorColor = primaryTextColor
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
                                                placeholder = { Text("@yourname", color = secondaryColor) },
                                                leadingIcon = { Icon(painterResource(R.drawable.person), contentDescription = null, tint = silverAccent.copy(alpha = 0.65f)) },
                                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                                singleLine = true,
                                                shape = RoundedCornerShape(14.dp),
                                                colors = textFieldColors
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                                        Text(if (authMode == AuthMode.SIGNUP) "Email address" else "Username / Email", color = silverAccent.copy(alpha = 0.75f), fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp, start = 4.dp))
                                        OutlinedTextField(
                                            value = emailInput,
                                            onValueChange = { emailInput = it },
                                            placeholder = { Text("yourname@gmail.com", color = secondaryColor) },
                                            leadingIcon = { Icon(painterResource(if (authMode == AuthMode.SIGNUP) R.drawable.email else R.drawable.person), contentDescription = null, tint = silverAccent.copy(alpha = 0.65f)) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                            modifier = Modifier.fillMaxWidth().height(52.dp),
                                            singleLine = true,
                                            shape = RoundedCornerShape(14.dp),
                                            colors = textFieldColors
                                        )
                                    }

                                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                                        Text("Password", color = silverAccent.copy(alpha = 0.75f), fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp, start = 4.dp))
                                        OutlinedTextField(
                                            value = passwordInput,
                                            onValueChange = { passwordInput = it },
                                            placeholder = { Text("••••••••", color = secondaryColor) },
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
                                            shape = RoundedCornerShape(14.dp),
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
                                        HorizontalDivider(modifier = Modifier.weight(1f), color = outlineColor)
                                        Text(
                                            text = if (authMode == AuthMode.SIGNUP) " Or sign up with " else " Or continue with ",
                                            color = silverAccent.copy(alpha = 0.55f),
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        )
                                        HorizontalDivider(modifier = Modifier.weight(1f), color = outlineColor)
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    // Liquid Glass Social Icons Row -> BitChord Clean Card Tiles
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        BitChordSocialTile(
                                            cardColor = cardColor,
                                            outlineColor = outlineColor,
                                            onClick = { onGoogleSignInClick() }
                                        ) {
                                            Icon(painterResource(R.drawable.ic_google_logo), contentDescription = "Google", tint = Color.Unspecified)
                                        }
                                        BitChordSocialTile(
                                            cardColor = cardColor,
                                            outlineColor = outlineColor,
                                            onClick = {
                                                val oauthUrl = supabaseClient.getOAuthAuthorizeUrl("facebook")
                                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(oauthUrl))
                                                context.startActivity(intent)
                                            }
                                        ) {
                                            Icon(painterResource(R.drawable.facebook), contentDescription = "Facebook", tint = Color(0xFF1877F2))
                                        }
                                        BitChordSocialTile(
                                            cardColor = cardColor,
                                            outlineColor = outlineColor,
                                            onClick = {
                                                val oauthUrl = supabaseClient.getOAuthAuthorizeUrl("github")
                                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(oauthUrl))
                                                context.startActivity(intent)
                                            }
                                        ) {
                                            Icon(painterResource(R.drawable.github), contentDescription = "Github", tint = primaryTextColor)
                                        }
                                        BitChordSocialTile(
                                            cardColor = cardColor,
                                            outlineColor = outlineColor,
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
                                            placeholder = { Text("Contoh: 123456", color = secondaryColor) },
                                            leadingIcon = { Icon(painterResource(R.drawable.lock), contentDescription = null, tint = silverAccent.copy(alpha = 0.65f), modifier = Modifier.size(20.dp)) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.fillMaxWidth().height(52.dp),
                                            singleLine = true,
                                            shape = RoundedCornerShape(14.dp),
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
                                            placeholder = { Text("yourname@gmail.com", color = secondaryColor) },
                                            leadingIcon = { Icon(painterResource(R.drawable.email), contentDescription = null, tint = silverAccent.copy(alpha = 0.65f)) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                            modifier = Modifier.fillMaxWidth().height(52.dp),
                                            singleLine = true,
                                            shape = RoundedCornerShape(14.dp),
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
                                            placeholder = { Text("Contoh: 123456", color = secondaryColor) },
                                            leadingIcon = { Icon(painterResource(R.drawable.lock), contentDescription = null, tint = silverAccent.copy(alpha = 0.65f), modifier = Modifier.size(20.dp)) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.fillMaxWidth().height(52.dp),
                                            singleLine = true,
                                            shape = RoundedCornerShape(14.dp),
                                            colors = textFieldColors
                                        )
                                    }

                                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                                        Text("Password Baru (Min. 6 Karakter)", color = silverAccent.copy(alpha = 0.75f), fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp, start = 4.dp))
                                        OutlinedTextField(
                                            value = newPasswordInput,
                                            onValueChange = { newPasswordInput = it },
                                            placeholder = { Text("••••••••", color = secondaryColor) },
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
                                            shape = RoundedCornerShape(14.dp),
                                            colors = textFieldColors
                                        )
                                    }

                                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                                        Text("Konfirmasi Password Baru", color = silverAccent.copy(alpha = 0.75f), fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp, start = 4.dp))
                                        OutlinedTextField(
                                            value = confirmNewPasswordInput,
                                            onValueChange = { confirmNewPasswordInput = it },
                                            placeholder = { Text("••••••••", color = secondaryColor) },
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
                                            shape = RoundedCornerShape(14.dp),
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
    }
}

/**
 * Clean primary action button styled like BitChord Settings.
 */
@Composable
private fun SilverLiquidGlassButton(
    text: String,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dark = isBitChordDarkTheme()
    val containerColor = if (dark) Color.White else Color.Black
    val contentColor = if (dark) Color.Black else Color.White

    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.5f),
            disabledContentColor = contentColor
        ),
        enabled = !isLoading
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = contentColor,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(22.dp)
            )
        } else {
            Text(
                text = text,
                color = contentColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
        }
    }
}

/**
 * Clean grouped card tile used for third-party OAuth providers in BitChord style.
 */
@Composable
private fun BitChordSocialTile(
    cardColor: Color,
    outlineColor: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(shape)
            .background(cardColor, shape)
            .border(width = 0.5.dp, color = outlineColor, shape = shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
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

@Composable
private fun OnboardingStepHeader(
    currentStep: Int,
    totalSteps: Int,
    showBackButton: Boolean,
    cardColor: Color,
    outlineColor: Color,
    primaryTextColor: Color,
    secondaryColor: Color,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .height(40.dp)
    ) {
        if (showBackButton) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(cardColor)
                    .border(1.dp, outlineColor, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back),
                    contentDescription = "Back",
                    tint = primaryTextColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(totalSteps) { index ->
                val isActive = index == currentStep
                val isCompleted = index < currentStep
                val pillWidth by animateDpAsState(
                    targetValue = if (isActive) 26.dp else 8.dp,
                    animationSpec = tween(durationMillis = 250),
                    label = "onboarding_pill_width"
                )
                val pillColor by animateColorAsState(
                    targetValue = when {
                        isActive -> primaryTextColor
                        isCompleted -> primaryTextColor.copy(alpha = 0.45f)
                        else -> outlineColor
                    },
                    animationSpec = tween(durationMillis = 250),
                    label = "onboarding_pill_color"
                )
                Box(
                    modifier = Modifier
                        .height(8.dp)
                        .width(pillWidth)
                        .clip(CircleShape)
                        .background(pillColor)
                )
            }
        }

        Text(
            text = "${currentStep + 1}/$totalSteps",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = secondaryColor,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

@Composable
private fun WelcomeStep(
    bgColor: Color,
    cardColor: Color,
    outlineColor: Color,
    primaryTextColor: Color,
    secondaryColor: Color,
    dark: Boolean,
    onNext: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .navigationBarsPadding()
    ) {
        OnboardingStepHeader(
            currentStep = 0,
            totalSteps = 3,
            showBackButton = false,
            cardColor = cardColor,
            outlineColor = outlineColor,
            primaryTextColor = primaryTextColor,
            secondaryColor = secondaryColor,
            onBack = {}
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(cardColor)
                    .border(1.dp, outlineColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.headphones),
                    contentDescription = null,
                    tint = primaryTextColor,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = "Welcome to IceBeats",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                color = primaryTextColor,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.ui_sweep_your_private_sanctuary_for_pure_high_fidelity_music_streaming_and),
                fontSize = 14.sp,
                color = secondaryColor,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(26.dp))

            WelcomeFeatureCard(
                iconRes = R.drawable.headphones,
                title = stringResource(R.string.ui_heading_lossless_audio_streaming),
                subtitle = stringResource(R.string.ui_heading_enjoy_pristine_audio_quality_with_dynamic_str),
                cardColor = cardColor,
                outlineColor = outlineColor,
                primaryTextColor = primaryTextColor,
                secondaryColor = secondaryColor,
                dark = dark
            )

            Spacer(modifier = Modifier.height(12.dp))

            WelcomeFeatureCard(
                iconRes = R.drawable.mic,
                title = stringResource(R.string.music_recognition),
                subtitle = stringResource(R.string.ui_heading_instantly_identify_songs_playing_around_you_w),
                cardColor = cardColor,
                outlineColor = outlineColor,
                primaryTextColor = primaryTextColor,
                secondaryColor = secondaryColor,
                dark = dark
            )

            Spacer(modifier = Modifier.height(12.dp))

            WelcomeFeatureCard(
                iconRes = R.drawable.backup,
                title = stringResource(R.string.ui_heading_safe_local_backups),
                subtitle = stringResource(R.string.ui_heading_keep_your_playlists_history_and_stats_securel),
                cardColor = cardColor,
                outlineColor = outlineColor,
                primaryTextColor = primaryTextColor,
                secondaryColor = secondaryColor,
                dark = dark
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            OnboardingActionButton(
                text = stringResource(R.string.ui_text_get_started),
                primaryTextColor = primaryTextColor,
                dark = dark,
                onClick = onNext
            )
        }
    }
}

@Composable
private fun WelcomeFeatureCard(
    iconRes: Int,
    title: String,
    subtitle: String,
    cardColor: Color,
    outlineColor: Color,
    primaryTextColor: Color,
    secondaryColor: Color,
    dark: Boolean
) {
    val iconBg = if (dark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = cardColor,
        border = BorderStroke(1.dp, outlineColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = primaryTextColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryTextColor
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    color = secondaryColor
                )
            }
        }
    }
}

@Composable
private fun PermissionsStep(
    bgColor: Color,
    cardColor: Color,
    outlineColor: Color,
    primaryTextColor: Color,
    secondaryColor: Color,
    dark: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scrollState = rememberScrollState()

    var hasNotificationPermission by remember { mutableStateOf(isNotificationPermissionGranted(context)) }
    var hasStoragePermission by remember { mutableStateOf(AutoBackupManager.hasStoragePermission(context)) }
    var hasMicPermission by remember { mutableStateOf(isMicPermissionGranted(context)) }
    var hasBluetoothPermission by remember { mutableStateOf(isBluetoothPermissionGranted(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationPermission = isNotificationPermissionGranted(context)
                hasStoragePermission = AutoBackupManager.hasStoragePermission(context)
                hasMicPermission = isMicPermissionGranted(context)
                hasBluetoothPermission = isBluetoothPermissionGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted || isNotificationPermissionGranted(context)
    }

    val legacyStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasStoragePermission = granted || AutoBackupManager.hasStoragePermission(context)
    }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted || isMicPermissionGranted(context)
    }

    val bluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasBluetoothPermission = granted || isBluetoothPermissionGranted(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .navigationBarsPadding()
    ) {
        OnboardingStepHeader(
            currentStep = 1,
            totalSteps = 3,
            showBackButton = true,
            cardColor = cardColor,
            outlineColor = outlineColor,
            primaryTextColor = primaryTextColor,
            secondaryColor = secondaryColor,
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.ui_text_app_permissions),
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                color = primaryTextColor,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "IceBeats requests a few permissions to power music playback and recognition. All permissions are optional.",
                fontSize = 13.5.sp,
                color = secondaryColor,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp
            )

            Spacer(modifier = Modifier.height(22.dp))

            OnboardingPermissionCard(
                iconRes = R.drawable.notification,
                title = stringResource(R.string.ui_heading_notifications_controls),
                subtitle = stringResource(R.string.ui_extra_shows_media_controls_in_notification_center_and_lock_screen),
                isGranted = hasNotificationPermission,
                cardColor = cardColor,
                outlineColor = outlineColor,
                primaryTextColor = primaryTextColor,
                secondaryColor = secondaryColor,
                dark = dark,
                onRequest = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        hasNotificationPermission = true
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            OnboardingPermissionCard(
                iconRes = R.drawable.folder,
                title = stringResource(R.string.ui_heading_full_storage_backup_access),
                subtitle = stringResource(R.string.ui_extra_grants_direct_storage_access_to_discover_offline_music_and_automatic),
                isGranted = hasStoragePermission,
                cardColor = cardColor,
                outlineColor = outlineColor,
                primaryTextColor = primaryTextColor,
                secondaryColor = secondaryColor,
                dark = dark,
                onRequest = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        AutoBackupManager.requestStoragePermission(context)
                    } else {
                        legacyStorageLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            OnboardingPermissionCard(
                iconRes = R.drawable.mic,
                title = stringResource(R.string.ui_heading_audio_recognition),
                subtitle = stringResource(R.string.ui_extra_used_only_when_you_tap_the_mic_to_identify_music_playing_nearby),
                isGranted = hasMicPermission,
                cardColor = cardColor,
                outlineColor = outlineColor,
                primaryTextColor = primaryTextColor,
                secondaryColor = secondaryColor,
                dark = dark,
                onRequest = {
                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            OnboardingPermissionCard(
                iconRes = R.drawable.ic_bluetooth,
                title = stringResource(R.string.ui_heading_bluetooth_devices),
                subtitle = stringResource(R.string.ui_extra_displays_connected_wireless_headphone_names_and_battery_status),
                isGranted = hasBluetoothPermission,
                cardColor = cardColor,
                outlineColor = outlineColor,
                primaryTextColor = primaryTextColor,
                secondaryColor = secondaryColor,
                dark = dark,
                onRequest = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        bluetoothLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    } else {
                        hasBluetoothPermission = true
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.ui_sweep_you_can_grant_or_change_these_anytime_in_android_app_settings),
                fontSize = 12.sp,
                color = secondaryColor,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(20.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            OnboardingActionButton(
                text = stringResource(R.string.continue_button),
                primaryTextColor = primaryTextColor,
                dark = dark,
                onClick = onNext
            )
        }
    }
}

@Composable
private fun OnboardingPermissionCard(
    iconRes: Int,
    title: String,
    subtitle: String,
    isGranted: Boolean,
    cardColor: Color,
    outlineColor: Color,
    primaryTextColor: Color,
    secondaryColor: Color,
    dark: Boolean,
    onRequest: () -> Unit
) {
    val iconBg = if (dark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
    val grantedBg = if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = cardColor,
        border = BorderStroke(1.dp, outlineColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = primaryTextColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryTextColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = secondaryColor
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            if (isGranted) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = grantedBg,
                    border = BorderStroke(1.dp, outlineColor)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.check),
                            contentDescription = null,
                            tint = primaryTextColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Granted",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryTextColor
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = primaryTextColor,
                    modifier = Modifier.clickable(onClick = onRequest)
                ) {
                    Text(
                        text = "Allow",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (dark) Color.Black else Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingActionButton(
    text: String,
    primaryTextColor: Color,
    dark: Boolean,
    onClick: () -> Unit
) {
    val contentColor = if (dark) Color.Black else Color.White
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = primaryTextColor,
            contentColor = contentColor
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = text,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                painter = painterResource(R.drawable.arrow_forward),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun isNotificationPermissionGranted(context: android.content.Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

private fun isMicPermissionGranted(context: android.content.Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
}

private fun isBluetoothPermissionGranted(context: android.content.Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

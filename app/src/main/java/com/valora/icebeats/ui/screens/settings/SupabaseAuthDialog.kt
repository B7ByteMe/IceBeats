package com.valora.icebeats.ui.screens.settings

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.valora.icebeats.R
import com.valora.icebeats.supabase.SupabaseClient
import kotlinx.coroutines.launch

enum class AuthDialogMode {
    SIGN_IN,
    SIGN_UP,
    SIGN_UP_OTP,
    FORGOT_PASSWORD,
    FORGOT_PASSWORD_VERIFY
}

@Composable
fun SupabaseAuthDialog(
    onDismiss: () -> Unit,
    onSuccess: (String) -> Unit,
    onGoogleSignInClick: (() -> Unit)? = null,
    initialMode: AuthDialogMode = AuthDialogMode.SIGN_IN
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val supabaseClient = remember { SupabaseClient(context) }

    var mode by remember(initialMode) { mutableStateOf(initialMode) }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }

    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = { if (!isLoading) onDismiss() }) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Title
                Text(
                    text = when (mode) {
                        AuthDialogMode.SIGN_IN -> "Masuk ke Akun Cloud"
                        AuthDialogMode.SIGN_UP -> "Buat Akun IceBeats"
                        AuthDialogMode.SIGN_UP_OTP -> "Verifikasi Kode OTP"
                        AuthDialogMode.FORGOT_PASSWORD -> "Lupa Password"
                        AuthDialogMode.FORGOT_PASSWORD_VERIFY -> "Masukkan Kode OTP & Password Baru"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = when (mode) {
                        AuthDialogMode.SIGN_IN -> "Sinkronkan playlist & favorit Anda di cloud"
                        AuthDialogMode.SIGN_UP -> "Daftar untuk backup otomatis lintas perangkat"
                        AuthDialogMode.SIGN_UP_OTP -> "Masukkan kode 6-digit yang dikirim ke $email"
                        AuthDialogMode.FORGOT_PASSWORD -> "Masukkan email untuk menerima kode OTP pemulihan"
                        AuthDialogMode.FORGOT_PASSWORD_VERIFY -> "Cek kode 6-digit di email $email lalu buat password baru"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                // Tab Mode Selector (Sign In vs Sign Up)
                if (mode == AuthDialogMode.SIGN_IN || mode == AuthDialogMode.SIGN_UP) {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        SegmentedButton(
                            selected = mode == AuthDialogMode.SIGN_IN,
                            onClick = {
                                mode = AuthDialogMode.SIGN_IN
                                errorMessage = null
                                successMessage = null
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) {
                            Text("Masuk")
                        }
                        SegmentedButton(
                            selected = mode == AuthDialogMode.SIGN_UP,
                            onClick = {
                                mode = AuthDialogMode.SIGN_UP
                                errorMessage = null
                                successMessage = null
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) {
                            Text("Daftar")
                        }
                    }
                }

                // Error Banner
                AnimatedVisibility(visible = errorMessage != null) {
                    errorMessage?.let { msg ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Text(
                                text = msg,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Success Banner
                AnimatedVisibility(visible = successMessage != null) {
                    successMessage?.let { msg ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Text(
                                text = msg,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Display Name field (only for Sign Up)
                if (mode == AuthDialogMode.SIGN_UP) {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Nama Pengguna") },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null)
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    )
                }

                // Email field (Sign In, Sign Up, Forgot Password)
                if (mode != AuthDialogMode.SIGN_UP_OTP && mode != AuthDialogMode.FORGOT_PASSWORD_VERIFY) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        leadingIcon = {
                            Icon(Icons.Default.Email, contentDescription = null)
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = if (mode == AuthDialogMode.FORGOT_PASSWORD) ImeAction.Done else ImeAction.Next
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    )
                }

                // Password field (for Sign In & Sign Up)
                if (mode == AuthDialogMode.SIGN_IN || mode == AuthDialogMode.SIGN_UP) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null)
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = if (mode == AuthDialogMode.SIGN_IN) ImeAction.Done else ImeAction.Next
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    )
                }

                // Confirm Password field (only for Sign Up)
                if (mode == AuthDialogMode.SIGN_UP) {
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Konfirmasi Password") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null)
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    )
                }

                // OTP Code field (For SIGN_UP_OTP or FORGOT_PASSWORD_VERIFY)
                if (mode == AuthDialogMode.SIGN_UP_OTP || mode == AuthDialogMode.FORGOT_PASSWORD_VERIFY) {
                    OutlinedTextField(
                        value = otpCode,
                        onValueChange = { if (it.length <= 8) otpCode = it },
                        label = { Text("Kode OTP") },
                        leadingIcon = {
                            Icon(Icons.Default.Key, contentDescription = null)
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = if (mode == AuthDialogMode.FORGOT_PASSWORD_VERIFY) ImeAction.Next else ImeAction.Done
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    )
                }

                // New Password field (For FORGOT_PASSWORD_VERIFY)
                if (mode == AuthDialogMode.FORGOT_PASSWORD_VERIFY) {
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("Password Baru (Min. 6 Karakter)") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null)
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    )
                }

                // "Lupa Password?" button (only in Sign In mode)
                if (mode == AuthDialogMode.SIGN_IN) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                mode = AuthDialogMode.FORGOT_PASSWORD
                                errorMessage = null
                                successMessage = null
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = "Lupa Password?",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Action Submit Button
                Button(
                    onClick = {
                        errorMessage = null
                        successMessage = null

                        when (mode) {
                            AuthDialogMode.SIGN_IN -> {
                                if (email.isBlank() || !email.contains("@")) {
                                    errorMessage = "Mohon masukkan alamat email yang valid"
                                    return@Button
                                }
                                if (password.isBlank()) {
                                    errorMessage = "Password tidak boleh kosong"
                                    return@Button
                                }
                                isLoading = true
                                scope.launch {
                                    val result = supabaseClient.signIn(email, password)
                                    isLoading = false
                                    result.onSuccess { msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        onSuccess(msg)
                                        onDismiss()
                                    }.onFailure { err ->
                                        errorMessage = err.localizedMessage ?: "Gagal masuk"
                                    }
                                }
                            }

                            AuthDialogMode.SIGN_UP -> {
                                if (email.isBlank() || !email.contains("@")) {
                                    errorMessage = "Mohon masukkan alamat email yang valid"
                                    return@Button
                                }
                                if (password.length < 6) {
                                    errorMessage = "Password minimal 6 karakter"
                                    return@Button
                                }
                                if (password != confirmPassword) {
                                    errorMessage = "Konfirmasi password tidak cocok"
                                    return@Button
                                }
                                isLoading = true
                                scope.launch {
                                    val name = displayName.ifBlank { email.substringBefore("@") }
                                    val result = supabaseClient.signUp(email, password, name)
                                    isLoading = false
                                    result.onSuccess { msg ->
                                        if (msg.contains("konfirmasi", ignoreCase = true) || msg.contains("email", ignoreCase = true)) {
                                            successMessage = "Kode OTP telah dikirim ke $email"
                                            mode = AuthDialogMode.SIGN_UP_OTP
                                        } else {
                                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                            onSuccess(msg)
                                            onDismiss()
                                        }
                                    }.onFailure { err ->
                                        errorMessage = err.localizedMessage ?: "Gagal mendaftar"
                                    }
                                }
                            }

                            AuthDialogMode.SIGN_UP_OTP -> {
                                if (otpCode.length < 6) {
                                    errorMessage = "Masukkan kode OTP dari email"
                                    return@Button
                                }
                                isLoading = true
                                scope.launch {
                                    val result = supabaseClient.verifySignupOtp(email, otpCode)
                                    isLoading = false
                                    result.onSuccess { msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                        onSuccess(msg)
                                        onDismiss()
                                    }.onFailure { err ->
                                        errorMessage = err.localizedMessage ?: "Verifikasi gagal"
                                    }
                                }
                            }

                            AuthDialogMode.FORGOT_PASSWORD -> {
                                if (email.isBlank() || !email.contains("@")) {
                                    errorMessage = "Mohon masukkan alamat email yang valid"
                                    return@Button
                                }
                                isLoading = true
                                scope.launch {
                                    val result = supabaseClient.sendPasswordReset(email)
                                    isLoading = false
                                    result.onSuccess { _ ->
                                        successMessage = "Kode OTP telah dikirim ke $email"
                                        mode = AuthDialogMode.FORGOT_PASSWORD_VERIFY
                                    }.onFailure { err ->
                                        errorMessage = err.localizedMessage ?: "Gagal mengirim kode OTP"
                                    }
                                }
                            }

                            AuthDialogMode.FORGOT_PASSWORD_VERIFY -> {
                                if (otpCode.length < 6) {
                                    errorMessage = "Masukkan kode OTP dari email"
                                    return@Button
                                }
                                if (newPassword.length < 6) {
                                    errorMessage = "Password baru minimal 6 karakter"
                                    return@Button
                                }
                                isLoading = true
                                scope.launch {
                                    val result = supabaseClient.verifyResetPasswordOtp(email, otpCode, newPassword)
                                    isLoading = false
                                    result.onSuccess { msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                        mode = AuthDialogMode.SIGN_IN
                                        successMessage = "Password berhasil diubah. Silakan masuk!"
                                        otpCode = ""
                                        newPassword = ""
                                    }.onFailure { err ->
                                        errorMessage = err.localizedMessage ?: "Gagal memperbarui password"
                                    }
                                }
                            }
                        }
                    },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Text(
                            text = when (mode) {
                                AuthDialogMode.SIGN_IN -> "Masuk"
                                AuthDialogMode.SIGN_UP -> "Daftar Akun Baru"
                                AuthDialogMode.SIGN_UP_OTP -> "Verifikasi Akun"
                                AuthDialogMode.FORGOT_PASSWORD -> "Kirim Kode OTP ke Email"
                                AuthDialogMode.FORGOT_PASSWORD_VERIFY -> "Simpan Password Baru"
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (mode == AuthDialogMode.SIGN_IN || mode == AuthDialogMode.SIGN_UP) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f))
                        Text(
                            text = "  atau  ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(14.dp))

                    // Google Button
                    Button(
                        onClick = {
                            if (onGoogleSignInClick != null) {
                                onGoogleSignInClick()
                            } else {
                                val oauthUrl = supabaseClient.getOAuthAuthorizeUrl("google")
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(oauthUrl))
                                context.startActivity(intent)
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF1F1F1F)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_google_logo),
                                contentDescription = null,
                                tint = Color.Unspecified,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Lanjutkan dengan Google",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Facebook Button
                    Button(
                        onClick = {
                            val oauthUrl = supabaseClient.getOAuthAuthorizeUrl("facebook")
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(oauthUrl))
                            context.startActivity(intent)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1877F2),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.facebook),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Lanjutkan dengan Facebook",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // GitHub Button
                    Button(
                        onClick = {
                            val oauthUrl = supabaseClient.getOAuthAuthorizeUrl("github")
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(oauthUrl))
                            context.startActivity(intent)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF24292E),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.github),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Lanjutkan dengan GitHub",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // GitLab Button
                    Button(
                        onClick = {
                            val oauthUrl = supabaseClient.getOAuthAuthorizeUrl("gitlab")
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(oauthUrl))
                            context.startActivity(intent)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFC6D26),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.gitlab),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Lanjutkan dengan GitLab",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Back / Cancel Buttons
                if (mode != AuthDialogMode.SIGN_IN && mode != AuthDialogMode.SIGN_UP) {
                    TextButton(
                        onClick = {
                            mode = AuthDialogMode.SIGN_IN
                            errorMessage = null
                            successMessage = null
                        },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("Kembali ke Halaman Masuk")
                    }
                } else {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("Batal")
                    }
                }
            }
        }
    }
}

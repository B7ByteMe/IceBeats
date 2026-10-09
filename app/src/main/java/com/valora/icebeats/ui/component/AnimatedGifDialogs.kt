package com.valora.icebeats.ui.component

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.valora.icebeats.R
import kotlinx.coroutines.launch

/**
 * Dialog Bottom Sheet untuk Menyetel Link GIF Avatar Profil Animasi
 * Khusus Member IceBeats Premium / Developer
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimatedAvatarGifDialog(
    onDismiss: () -> Unit,
    onAvatarApplied: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val avatarManager = remember { AvatarPreferenceManager(context) }
    val currentSelection by avatarManager.getAvatarSelection.collectAsState(initial = AvatarSelection.Default)

    var inputUrl by remember {
        mutableStateOf(
            if (currentSelection is AvatarSelection.Gif) (currentSelection as AvatarSelection.Gif).url else ""
        )
    }
    var previewUrl by remember { mutableStateOf(inputUrl) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF141416),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_vip_crown),
                    contentDescription = null,
                    tint = Color(0xFFFFD700),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Avatar Profil Animasi (GIF)",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Fitur Eksklusif Premium: Masukkan tautan link GIF animasi untuk avatar profil bergerak.",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.65f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Live Preview Avatar
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF222226))
                    .border(2.dp, Color(0xFFFFD700).copy(alpha = 0.8f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (previewUrl.isNotBlank()) {
                    SubcomposeAsyncImage(
                        model = previewUrl,
                        contentDescription = "Preview Avatar GIF",
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    ) {
                        val state = painter.state
                        if (state is AsyncImagePainter.State.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = Color(0xFFFFD700),
                                strokeWidth = 2.dp
                            )
                        } else if (state is AsyncImagePainter.State.Error) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Gagal memuat",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(32.dp)
                            )
                        } else {
                            SubcomposeAsyncImageContent()
                        }
                    }
                } else {
                    Icon(
                        painter = painterResource(R.drawable.image),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(42.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Input URL Field
            OutlinedTextField(
                value = inputUrl,
                onValueChange = {
                    inputUrl = it
                    if (it.startsWith("http://") || it.startsWith("https://")) {
                        previewUrl = it.trim()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://example.com/avatar.gif", color = Color.White.copy(alpha = 0.4f)) },
                label = { Text("URL Link GIF") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFFD700),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                trailingIcon = {
                    if (inputUrl.isNotBlank()) {
                        IconButton(onClick = {
                            inputUrl = ""
                            previewUrl = ""
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Hapus", tint = Color.White.copy(alpha = 0.5f))
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            avatarManager.saveAvatarSelection(AvatarSelection.Default)
                            Toast.makeText(context, "Avatar direset ke bawaan", Toast.LENGTH_SHORT).show()
                            onAvatarApplied?.invoke()
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Reset Default")
                }

                Button(
                    onClick = {
                        val clean = inputUrl.trim()
                        if (clean.isNotBlank() && (clean.startsWith("http://") || clean.startsWith("https://"))) {
                            scope.launch {
                                avatarManager.saveAvatarSelection(AvatarSelection.Gif(clean))
                                Toast.makeText(context, "Avatar Animasi GIF berhasil diterapkan!", Toast.LENGTH_SHORT).show()
                                onAvatarApplied?.invoke()
                                onDismiss()
                            }
                        } else {
                            Toast.makeText(context, "Harap masukkan tautan URL HTTP/HTTPS yang valid", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFD700),
                        contentColor = Color.Black
                    )
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Terapkan GIF", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Dialog Bottom Sheet untuk Menyetel Link GIF Banner Profil Animasi
 * Khusus Member IceBeats Premium / Developer
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimatedBannerGifDialog(
    onDismiss: () -> Unit,
    onBannerApplied: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bannerManager = remember { BannerPreferenceManager(context) }
    val currentBanner by bannerManager.bannerUrl.collectAsState(initial = null)

    var inputUrl by remember { mutableStateOf(currentBanner ?: "") }
    var previewUrl by remember { mutableStateOf(inputUrl) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF141416),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_vip_crown),
                    contentDescription = null,
                    tint = Color(0xFFFFD700),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Banner Profil Animasi (GIF)",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Fitur Eksklusif Premium: Pasang tautan GIF bergerak sebagai banner latar belakang profil Anda.",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.65f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Live Banner Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF2B2206),
                                Color(0xFF1A1A1E),
                                Color(0xFF121214)
                            )
                        )
                    )
                    .border(1.5.dp, Color(0xFFFFD700).copy(alpha = 0.7f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (previewUrl.isNotBlank()) {
                    SubcomposeAsyncImage(
                        model = previewUrl,
                        contentDescription = "Preview Banner GIF",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentScale = ContentScale.Crop
                    ) {
                        val state = painter.state
                        if (state is AsyncImagePainter.State.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = Color(0xFFFFD700),
                                strokeWidth = 2.dp
                            )
                        } else if (state is AsyncImagePainter.State.Error) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Clear, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Text("Gagal memuat banner", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                            }
                        } else {
                            SubcomposeAsyncImageContent()
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = painterResource(R.drawable.image),
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Pratinjau Banner Profil", fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Input URL Field
            OutlinedTextField(
                value = inputUrl,
                onValueChange = {
                    inputUrl = it
                    if (it.startsWith("http://") || it.startsWith("https://")) {
                        previewUrl = it.trim()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://example.com/banner.gif", color = Color.White.copy(alpha = 0.4f)) },
                label = { Text("URL Link Banner GIF") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFFD700),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                trailingIcon = {
                    if (inputUrl.isNotBlank()) {
                        IconButton(onClick = {
                            inputUrl = ""
                            previewUrl = ""
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Hapus", tint = Color.White.copy(alpha = 0.5f))
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            bannerManager.clearBannerUrl()
                            runCatching {
                                com.valora.icebeats.utils.IceBeatsStatsCloudSync.uploadCurrentStats(context)
                            }
                            Toast.makeText(context, "Banner profil dihapus", Toast.LENGTH_SHORT).show()
                            onBannerApplied?.invoke()
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Hapus Banner")
                }

                Button(
                    onClick = {
                        val clean = inputUrl.trim()
                        if (clean.isNotBlank() && (clean.startsWith("http://") || clean.startsWith("https://"))) {
                            scope.launch {
                                bannerManager.saveBannerUrl(clean)
                                runCatching {
                                    com.valora.icebeats.utils.IceBeatsStatsCloudSync.uploadCurrentStats(context)
                                }
                                Toast.makeText(context, "Banner Profil Animasi berhasil diterapkan!", Toast.LENGTH_SHORT).show()
                                onBannerApplied?.invoke()
                                onDismiss()
                            }
                        } else {
                            Toast.makeText(context, "Harap masukkan tautan URL HTTP/HTTPS yang valid", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFD700),
                        contentColor = Color.Black
                    )
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Terapkan Banner", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

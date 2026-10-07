package com.valora.icebeats.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.valora.icebeats.R
import kotlinx.coroutines.launch

/**
 * Bottom Sheet Pemilihan Tema Border Profil Master (v7.0.9)
 * Memberikan 4 pilihan desain border mentahan premium dari user:
 * 1. Royal Crown
 * 2. Crimson Wings
 * 3. Fire Flame Ring
 * 4. Golden Champion Shield
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasterBorderSelectorSheet(
    onDismiss: () -> Unit,
    userRank: icebeatsRank? = null,
    totalListenMs: Long? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val borderPreferenceManager = remember { BorderPreferenceManager(context) }
    val currentSavedStyle by borderPreferenceManager.selectedBorder.collectAsState(initial = MasterBorderStyle.ROYAL_CROWN)

    var previewStyle by remember(currentSavedStyle) { mutableStateOf(currentSavedStyle) }

    val isMaster = remember(userRank, totalListenMs) {
        val rankQualified = userRank != null && userRank.ordinal >= icebeatsRank.Master.ordinal
        val hoursQualified = totalListenMs != null && totalListenMs >= (150L * 3600L * 1000L)
        rankQualified || hoursQualified
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Pilih Border Profil Master",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (isMaster) {
                Text(
                    text = "Pilih gaya border mahkota atau sayap favorit Anda",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "🔒 Terkunci • Capai Level Master (150 Jam) untuk membuka!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Live Preview Avatar dengan Border di Atasnya
            Box(
                modifier = Modifier
                    .size(150.dp),
                contentAlignment = Alignment.Center
            ) {
                MasterProfileBorder(
                    avatarSize = 64.dp,
                    forceShowMaster = true,
                    borderStyle = previewStyle
                ) {
                    AvatarDisplay(
                        size = 64.dp,
                        showBorder = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = previewStyle.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = previewStyle.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 4 Pilihan Border
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(MasterBorderStyle.entries) { style ->
                    val isSelected = previewStyle == style
                    val cardBorder = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    val cardBg = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(cardBg)
                            .border(if (isSelected) 2.dp else 1.dp, cardBorder, RoundedCornerShape(14.dp))
                            .clickable(enabled = isMaster) {
                                previewStyle = style
                            }
                            .padding(12.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier.size(72.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                MasterProfileBorder(
                                    avatarSize = 32.dp,
                                    forceShowMaster = true,
                                    borderStyle = style
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.secondaryContainer)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = style.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )

                            if (!isMaster) {
                                Text(
                                    text = "🔒 Terkunci",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Tombol Simpan
            Button(
                onClick = {
                    if (isMaster) {
                        scope.launch {
                            borderPreferenceManager.saveSelectedBorder(previewStyle)
                            onDismiss()
                        }
                    }
                },
                enabled = isMaster,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Icon(
                    painter = painterResource(if (isMaster) R.drawable.check else R.drawable.lock),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isMaster) "Gunakan Border Ini" else "🔒 Terkunci (Perlu Level Master 150 Jam)")
            }

            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

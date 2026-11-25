package com.valora.icebeats.ui.component

import androidx.compose.ui.res.stringResource

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.valora.icebeats.R
import com.valora.icebeats.utils.AppUpdateService
import com.valora.icebeats.utils.UpdateDownloadState
import com.valora.icebeats.utils.UpdateInfo
import java.util.Locale

@Composable
fun UpdateAvailableDialog(
    updateInfo: UpdateInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isWhatIsNewExpanded by remember { mutableStateOf(false) }
    val downloadState by AppUpdateService.downloadState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var waitingForInstallPermission by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, downloadState) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (waitingForInstallPermission && downloadState is UpdateDownloadState.Completed) {
                    val canInstall = AppUpdateService.canRequestPackageInstalls(context)
                    if (canInstall) {
                        waitingForInstallPermission = false
                        val apkFile = (downloadState as UpdateDownloadState.Completed).apkFile
                        AppUpdateService.openInstaller(context, apkFile)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val arrowRotation by animateFloatAsState(
        targetValue = if (isWhatIsNewExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "arrowRotation"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val dark = isBitChordDarkTheme()
    val cardColor = bitChordSettingsCardColor()
    val innerCardColor = bitChordSettingsBackground()
    val outlineColor = bitChordSettingsOutlineColor()
    val secondaryColor = bitChordSettingsSecondaryTextColor()
    val primaryTextColor = if (dark) Color.White else Color.Black
    val badgeBg = if (dark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            shape = RoundedCornerShape(22.dp),
            color = cardColor,
            border = BorderStroke(
                width = 1.dp,
                color = outlineColor
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // App Icon Badge
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(64.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(badgeBg)
                        .border(1.dp, outlineColor, CircleShape)
                ) {
                    Image(
                        painter = painterResource(R.drawable.icebeats_logo),
                        contentDescription = "IceBeats",
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Title
                Text(
                    text = "Update IceBeats",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = primaryTextColor,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Version Badge Tag
                Surface(
                    shape = RoundedCornerShape(50),
                    color = badgeBg,
                    border = BorderStroke(1.dp, outlineColor)
                ) {
                    Text(
                        text = "Version v${updateInfo.versionName}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = primaryTextColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "A new version of IceBeats is ready to install with latest features and enhancements.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryColor,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Expandable What's New Accordion Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = innerCardColor
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = outlineColor
                    )
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Header Row (Click to Expand / Collapse)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { isWhatIsNewExpanded = !isWhatIsNewExpanded }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(badgeBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.info),
                                        contentDescription = null,
                                        tint = primaryTextColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Text(
                                    text = stringResource(R.string.ui_sweep_what_s_new),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = primaryTextColor
                                )
                            }

                            Icon(
                                painter = painterResource(R.drawable.expand_more),
                                contentDescription = if (isWhatIsNewExpanded) "Collapse" else "Expand",
                                tint = secondaryColor,
                                modifier = Modifier
                                    .size(24.dp)
                                    .rotate(arrowRotation)
                            )
                        }

                        // Expanded Release Notes Content
                        AnimatedVisibility(
                            visible = isWhatIsNewExpanded,
                            enter = expandVertically(animationSpec = tween(300)) + fadeIn(),
                            exit = shrinkVertically(animationSpec = tween(300)) + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 14.dp)
                            ) {
                                HorizontalDivider(
                                    color = outlineColor,
                                    modifier = Modifier.padding(bottom = 10.dp)
                                )

                                val notesList = remember(updateInfo.releaseNotes) {
                                    formatReleaseNotes(updateInfo.releaseNotes)
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 180.dp)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (notesList.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.ui_sweep_performance_improvements_and_bug_fixes),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = secondaryColor
                                        )
                                    } else {
                                        notesList.forEach { note ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.Top
                                            ) {
                                                Text(
                                                    text = "•",
                                                    color = primaryTextColor,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp
                                                )
                                                Text(
                                                    text = note,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = secondaryColor,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Real-time Progress / Status Card
                AnimatedVisibility(
                    visible = downloadState !is UpdateDownloadState.Idle,
                    enter = expandVertically(animationSpec = tween(300)) + fadeIn(),
                    exit = shrinkVertically(animationSpec = tween(300)) + fadeOut()
                ) {
                    when (val state = downloadState) {
                        is UpdateDownloadState.Downloading -> {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = innerCardColor
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    outlineColor
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(R.string.downloadingup),
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            color = primaryTextColor
                                        )
                                        if (state.progress >= 0f) {
                                            Text(
                                                text = "${(state.progress * 100).toInt()}%",
                                                style = MaterialTheme.typography.labelLarge.copy(
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = primaryTextColor
                                            )
                                        }
                                    }

                                    if (state.progress >= 0f) {
                                        LinearProgressIndicator(
                                            progress = { state.progress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(8.dp)
                                                .clip(RoundedCornerShape(4.dp)),
                                            color = primaryTextColor,
                                            trackColor = badgeBg
                                        )
                                    } else {
                                        LinearProgressIndicator(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(8.dp)
                                                .clip(RoundedCornerShape(4.dp)),
                                            color = primaryTextColor,
                                            trackColor = badgeBg
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val downloadedMb = state.downloadedBytes / (1024f * 1024f)
                                        val totalMb = state.totalBytes / (1024f * 1024f)
                                        val bytesText = if (state.totalBytes > 0) {
                                            String.format(Locale.getDefault(), "%.1f MB / %.1f MB", downloadedMb, totalMb)
                                        } else if (state.downloadedBytes > 0) {
                                            String.format(Locale.getDefault(), "%.1f MB", downloadedMb)
                                        } else {
                                            "Starting download…"
                                        }
                                        Text(
                                            text = bytesText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = secondaryColor
                                        )
                                    }
                                }
                            }
                        }
                        is UpdateDownloadState.Completed -> {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = innerCardColor
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    outlineColor
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(badgeBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.check_circle),
                                            contentDescription = null,
                                            tint = primaryTextColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = stringResource(R.string.ui_text_download_complete),
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = primaryTextColor
                                        )
                                        Text(
                                            text = stringResource(R.string.ui_text_tap_install_to_update_now),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = secondaryColor
                                        )
                                    }
                                }
                            }
                        }
                        is UpdateDownloadState.Failed -> {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.info),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Column {
                                        Text(
                                            text = stringResource(R.string.ui_text_download_failed),
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        Text(
                                            text = state.error.ifBlank { "Could not complete update download." },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }
                        }
                        UpdateDownloadState.Idle -> Unit
                    }
                }

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, outlineColor)
                    ) {
                        Text(
                            text = stringResource(R.string.ui_text_later),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = secondaryColor
                        )
                    }

                    val isDownloading = downloadState is UpdateDownloadState.Downloading
                    val isCompleted = downloadState is UpdateDownloadState.Completed
                    val isFailed = downloadState is UpdateDownloadState.Failed
                    val actionContentColor = if (dark) Color.Black else Color.White

                    Button(
                        onClick = {
                            when (val state = downloadState) {
                                is UpdateDownloadState.Completed -> {
                                    if (!AppUpdateService.canRequestPackageInstalls(context)) {
                                        waitingForInstallPermission = true
                                    }
                                    AppUpdateService.openInstaller(context, state.apkFile)
                                }
                                else -> {
                                    val directApkUrl = updateInfo.apkDownloadUrl.ifBlank {
                                        updateInfo.releaseUrl.ifBlank {
                                            com.valora.icebeats.utils.RemoteConfigManager.getLatestReleasePageUrl()
                                        }
                                    }
                                    AppUpdateService.start(context, directApkUrl)
                                }
                            }
                        },
                        enabled = !isDownloading,
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryTextColor,
                            contentColor = actionContentColor,
                            disabledContainerColor = primaryTextColor.copy(alpha = 0.5f),
                            disabledContentColor = actionContentColor
                        )
                    ) {
                        val iconRes = when {
                            isCompleted -> R.drawable.deployed_code_update
                            isFailed -> R.drawable.refresh
                            else -> R.drawable.download
                        }
                        val buttonText = when {
                            isDownloading -> "Downloading…"
                            isCompleted -> "Install"
                            isFailed -> "Retry"
                            else -> "Download"
                        }

                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = buttonText,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }
}

private fun formatReleaseNotes(rawBody: String): List<String> {
    if (rawBody.isBlank()) return emptyList()

    return rawBody
        .lines()
        .map { line ->
            line.trim()
                .removePrefix("##")
                .removePrefix("#")
                .removePrefix("*")
                .removePrefix("-")
                .removePrefix("•")
                .trim()
        }
        .filter { it.isNotBlank() && !it.startsWith("What's Changed", ignoreCase = true) && !it.startsWith("Full Changelog", ignoreCase = true) }
}

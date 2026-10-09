package com.valora.icebeats.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valora.icebeats.R
import com.valora.icebeats.constants.AccountEmailKey
import com.valora.icebeats.constants.AccountNameKey
import com.valora.icebeats.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLEncoder

private val GoldPrimary = Color(0xFFFFD700)
private val GoldSecondary = Color(0xFFD4AF37)
private val GoldDark = Color(0xFFB8860B)
private val DarkCardBg = Color(0xFF161618)
private val DarkCardBorder = Color(0xFF2A2A2E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VipSubscriptionDialog(
    onDismiss: () -> Unit,
    initialSelectedPlan: VipPlan = VipPlan.TWO_MONTHS
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vipManager = remember { VipSubscriptionManager(context) }
    val isVipActive by vipManager.isVip.collectAsState(initial = false)
    val vipExpiresAt by vipManager.vipExpiresAt.collectAsState(initial = 0L)
    val currentPlanName by vipManager.vipPlan.collectAsState(initial = "")

    val (userName) = rememberPreference(AccountNameKey, defaultValue = "")
    val (userEmail) = rememberPreference(AccountEmailKey, defaultValue = "")

    var selectedPlan by remember { mutableStateOf(initialSelectedPlan) }
    var showConfirmationDialog by remember { mutableStateOf(false) }
    var showQrisStep by remember { mutableStateOf(false) }
    var voucherCode by remember { mutableStateOf("") }
    var showVoucherField by remember { mutableStateOf(false) }
    var isRedeeming by remember { mutableStateOf(false) }
    var isCheckingStatus by remember { mutableStateOf(false) }
    var isApprovedBanner by remember { mutableStateOf(false) }

    val adminWaNumber = "6288983660479"
    val adminWaDisplay = "088983660479"

    fun openUrl(url: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label disalin: $text", Toast.LENGTH_SHORT).show()
    }

    fun submitAndOpenWhatsApp(plan: VipPlan) {
        scope.launch {
            // Catat order pending ke database Supabase
            vipManager.submitPendingPayment(plan)

            val userIdentifier = if (userName.isNotBlank()) userName else if (userEmail.isNotBlank()) userEmail else "IceBeats User"
            val message = """
Halo Admin Valora / IceBeats, saya sudah melakukan pembayaran langganan VIP:
• Paket: ${plan.title}
• Nominal: ${plan.formattedPrice}
• Nama / Email Akun: $userIdentifier
• Tujuan Admin: $adminWaDisplay

Berikut saya lampirkan bukti screenshot (SS) transfernya. Mohon bantu proses verifikasi dan aktivasi akun VIP saya ya. Terima kasih!
            """.trimIndent()

            val encodedMessage = URLEncoder.encode(message, "UTF-8")
            val waUrl = "https://api.whatsapp.com/send?phone=$adminWaNumber&text=$encodedMessage"
            openUrl(waUrl)
        }
    }

    // Auto-polling cek status verifikasi jika di halaman QRIS
    LaunchedEffect(showQrisStep) {
        if (showQrisStep && !isVipActive) {
            while (!isVipActive) {
                delay(6000)
                val check = vipManager.checkCloudSubscriptionStatus().getOrDefault(false)
                if (check) {
                    isApprovedBanner = true
                    Toast.makeText(context, "Selamat! Pembayaran terverifikasi. Paket VIP Anda telah aktif!", Toast.LENGTH_LONG).show()
                    break
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF101012),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Close button & Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showQrisStep) {
                    IconButton(
                        onClick = { showQrisStep = false },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = "Kembali",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(32.dp))
                }

                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(GoldPrimary.copy(alpha = 0.25f), GoldDark.copy(alpha = 0.15f))
                            )
                        )
                        .border(
                            width = 1.5.dp,
                            brush = Brush.linearGradient(listOf(GoldPrimary, GoldDark)),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_vip_crown),
                        contentDescription = "VIP Crown",
                        tint = GoldPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = "Tutup",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (showQrisStep) "Pembayaran QRIS Valora" else "IceBeats VIP Member",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    brush = Brush.linearGradient(
                        listOf(Color(0xFFFFF099), GoldPrimary, Color(0xFFFFB300))
                    )
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (showQrisStep)
                    "Scan QRIS & transfer persis nominal paket, lalu kirim screenshot ke admin untuk di-ACC"
                else
                    "Buka semua tema kustom, Dynamic Island, chat eksklusif, dan border animasi profil tanpa batas!",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Banner jika status VIP aktif / baru saja di-ACC
            if (isVipActive || isApprovedBanner) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(GoldPrimary.copy(alpha = 0.12f))
                        .border(1.dp, GoldPrimary.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.check_circle),
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "VIP Anda Sedang Aktif: $currentPlanName",
                                fontWeight = FontWeight.Bold,
                                color = GoldPrimary,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Berakhir: ${VipSubscriptionManager.formatExpiryDate(vipExpiresAt)}",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ==================== TAMPILAN HALAMAN QRIS ====================
            if (showQrisStep) {
                // Card Gambar QRIS Resmi Valora Store
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Image(
                            painter = painterResource(R.drawable.qris_valora),
                            contentDescription = "QRIS Valora Store",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 340.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Card Nominal yang Harus Dibayar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1C1A14))
                        .border(1.5.dp, GoldPrimary.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "TOTAL YANG HARUS DITRANSFER",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.65f),
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = selectedPlan.formattedPrice,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = GoldPrimary
                            )
                            Text(
                                text = "Paket ${selectedPlan.title}",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }

                        Button(
                            onClick = {
                                copyToClipboard("Nominal Transfer", selectedPlan.priceRupiah.toString())
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GoldPrimary.copy(alpha = 0.2f),
                                contentColor = GoldPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Salin Rp", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Panduan Langkah Transfer & ACC
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkCardBg)
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "CARA BAYAR & AKTIVASI OTOMATIS:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldPrimary,
                            letterSpacing = 0.5.sp
                        )
                        QrisStepItem(number = "1", text = "Screenshot (SS) layar QRIS di atas ini.")
                        QrisStepItem(number = "2", text = "Buka Bank/e-Wallet (BCA, Dana, GoPay, OVO, ShopeePay, dll) & scan QRIS.")
                        QrisStepItem(number = "3", text = "Transfer tepat sesuai nominal: ${selectedPlan.formattedPrice}.")
                        QrisStepItem(number = "4", text = "Klik tombol di bawah untuk kirim bukti SS transfer ke WhatsApp Admin ($adminWaDisplay).")
                        QrisStepItem(number = "5", text = "Admin akan memverifikasi bukti transfer, dan akun VIP langsung aktif otomatis!")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Card Nomor WhatsApp Admin
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF102416))
                        .border(1.dp, Color(0xFF25D366).copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(R.drawable.ic_whatsapp),
                                contentDescription = "WhatsApp",
                                tint = Color(0xFF25D366),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "WhatsApp Konfirmasi Admin:",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = adminWaDisplay,
                                    fontSize = 15.sp,
                                    color = Color(0xFF25D366),
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        Button(
                            onClick = { copyToClipboard("Nomor WhatsApp", adminWaDisplay) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF25D366).copy(alpha = 0.2f),
                                contentColor = Color(0xFF25D366)
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Salin", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tombol Kirim Bukti ke WhatsApp Admin
                Button(
                    onClick = {
                        submitAndOpenWhatsApp(selectedPlan)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(16.dp),
                            ambientColor = GoldPrimary,
                            spotColor = GoldPrimary
                        ),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldPrimary,
                        contentColor = Color.Black
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_whatsapp),
                            contentDescription = "WhatsApp",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Kirim Bukti ke WhatsApp ($adminWaDisplay)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.Black
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tombol Cek Status VIP (Prem)
                OutlinedButton(
                    onClick = {
                        isCheckingStatus = true
                        scope.launch {
                            val ok = vipManager.checkCloudSubscriptionStatus().getOrDefault(false)
                            isCheckingStatus = false
                            if (ok) {
                                isApprovedBanner = true
                                Toast.makeText(context, "Selamat! Pembayaran berhasil diverifikasi. Paket VIP Anda telah aktif!", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Sedang diverifikasi oleh admin. Pastikan bukti transfer sudah dikirim ke WhatsApp ya!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !isCheckingStatus,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.cached),
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isCheckingStatus) "Memeriksa Status..." else "Cek Status VIP",
                            color = GoldPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tombol Batalkan Pembelian (Kembali ke menu paket 1, 2, 5 bulan)
                OutlinedButton(
                    onClick = {
                        showQrisStep = false
                        Toast.makeText(context, "Pembelian dibatalkan. Silakan pilih paket lainnya.", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFFF5252)
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = null,
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Batalkan Pembelian",
                            color = Color(0xFFFF5252),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

            } else {
                // ==================== TAMPILAN PILIH PAKET LANGGANAN ====================
                Text(
                    text = "PILIH PAKET LANGGANAN",
                    style = MaterialTheme.typography.labelSmall,
                    color = GoldPrimary.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    VipPlan.entries.forEach { plan ->
                        val isSelected = selectedPlan == plan
                        val isThisPlanActive = isVipActive && (
                            currentPlanName.contains(plan.title, ignoreCase = true) ||
                            (plan == VipPlan.FIVE_MONTHS && currentPlanName.contains("5", ignoreCase = true)) ||
                            (plan == VipPlan.TWO_MONTHS && currentPlanName.contains("2", ignoreCase = true)) ||
                            (plan == VipPlan.ONE_MONTH && currentPlanName.contains("1", ignoreCase = true))
                        )

                        val targetBorderColor = when {
                            isThisPlanActive -> Color(0xFF25D366) // Hijau neon jika paket aktif
                            isSelected -> GoldPrimary
                            else -> DarkCardBorder
                        }
                        val cardBorderColor by animateColorAsState(
                            targetValue = targetBorderColor,
                            label = "card_border"
                        )

                        val targetBgColor = when {
                            isThisPlanActive && isSelected -> Color(0xFF142B1A)
                            isThisPlanActive -> Color(0xFF0E2214)
                            isSelected -> Color(0xFF221F14)
                            else -> DarkCardBg
                        }
                        val cardBgColor by animateColorAsState(
                            targetValue = targetBgColor,
                            label = "card_bg"
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(cardBgColor)
                                .border(
                                    width = if (isThisPlanActive || isSelected) 2.dp else 1.dp,
                                    color = cardBorderColor,
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .clickable {
                                    selectedPlan = plan
                                    showConfirmationDialog = true
                                }
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = plan.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp,
                                            color = when {
                                                isThisPlanActive -> Color(0xFF25D366)
                                                isSelected -> GoldPrimary
                                                else -> Color.White
                                            }
                                        )

                                        if (isThisPlanActive) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0xFF25D366))
                                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "SEDANG BERLANGGANAN",
                                                    color = Color.Black,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                            }
                                        } else if (plan.badge != null) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(
                                                        if (plan == VipPlan.FIVE_MONTHS) Color(0xFFE50914) else GoldDark
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = plan.badge,
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = plan.description,
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.65f)
                                    )

                                    if (isThisPlanActive) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Masa aktif: Hingga ${VipSubscriptionManager.formatExpiryDate(vipExpiresAt)}",
                                            fontSize = 11.sp,
                                            color = Color(0xFF25D366),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = plan.formattedPrice,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp,
                                        color = when {
                                            isThisPlanActive -> Color(0xFF25D366)
                                            isSelected -> GoldPrimary
                                            else -> Color.White
                                        }
                                    )

                                    Icon(
                                        painter = painterResource(
                                            if (isThisPlanActive) R.drawable.check_circle
                                            else if (isSelected) R.drawable.radio_button_checked
                                            else R.drawable.radio_button_unchecked
                                        ),
                                        contentDescription = null,
                                        tint = when {
                                            isThisPlanActive -> Color(0xFF25D366)
                                            isSelected -> GoldPrimary
                                            else -> Color.White.copy(alpha = 0.3f)
                                        },
                                        modifier = Modifier
                                            .padding(top = 4.dp)
                                            .size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Tombol Beli / Konfirmasi Paket Terpilih
                Button(
                    onClick = { showConfirmationDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(16.dp),
                            ambientColor = GoldPrimary,
                            spotColor = GoldPrimary
                        ),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldPrimary,
                        contentColor = Color.Black
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.donate),
                            contentDescription = "Beli Paket",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Beli Paket ${selectedPlan.title} (${selectedPlan.formattedPrice})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.Black
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Daftar Benefit VIP
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF141416))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                        .padding(16.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "KEUNTUNGAN MEMBER VIP",
                            style = MaterialTheme.typography.labelSmall,
                            color = GoldPrimary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        VipBenefitItem(
                            iconRes = R.drawable.palette,
                            title = "Bebas Semua Tema Home Screen",
                            desc = "Akses Playful, Neon, Spotify, Apple (Gratis hanya Classic)"
                        )
                        VipBenefitItem(
                            iconRes = R.drawable.nav_bar,
                            title = "Bebas Semua Tema Nav Bar",
                            desc = "Akses Liquid Glass, Spotify, Apple, Neon, New Classic (Gratis hanya Classic)"
                        )
                        VipBenefitItem(
                            iconRes = R.drawable.music_note,
                            title = "Dynamic Island Mini Player",
                            desc = "Island melayang interaktif di atas layar ala iOS"
                        )
                        VipBenefitItem(
                            iconRes = R.drawable.chat,
                            title = "IceBeats Chat & Kirim Lagu",
                            desc = "Ngobrol bersama teman dan bagikan musik favorit"
                        )
                        VipBenefitItem(
                            iconRes = R.drawable.auto_awesome,
                            title = "Semua Border Animasi Profil Master",
                            desc = "Royal Crown, Crimson Wings, Flame Ring & Golden Shield"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Voucher Redeem Section
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showVoucherField = !showVoucherField }
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.token),
                        contentDescription = null,
                        tint = GoldSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (showVoucherField) "Tutup Kode Voucher" else "Punya Kode Voucher VIP? Klaim di Sini",
                        color = GoldSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                AnimatedVisibility(visible = showVoucherField) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = voucherCode,
                                onValueChange = { voucherCode = it.uppercase() },
                                placeholder = { Text("Contoh: VIP1BULAN", fontSize = 13.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GoldPrimary,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )

                            Button(
                                onClick = {
                                    if (voucherCode.isBlank()) return@Button
                                    isRedeeming = true
                                    scope.launch {
                                        val result = vipManager.redeemVoucher(voucherCode)
                                        isRedeeming = false
                                        result.onSuccess { message ->
                                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                            voucherCode = ""
                                        }.onFailure { err ->
                                            Toast.makeText(context, err.message ?: "Gagal klaim", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                enabled = !isRedeeming && voucherCode.isNotBlank(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = GoldSecondary,
                                    contentColor = Color.Black
                                ),
                                modifier = Modifier.height(54.dp)
                            ) {
                                Text(text = "Klaim", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Dialog Konfirmasi Pembelian Paket
    if (showConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmationDialog = false },
            containerColor = Color(0xFF18181C),
            shape = RoundedCornerShape(24.dp),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(GoldPrimary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_vip_crown),
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        text = "Konfirmasi Pembelian",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Apakah Anda ingin melanjutkan pembelian paket langganan ini?",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )

                    // Card Rincian Paket
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF221F14))
                            .border(1.5.dp, GoldPrimary.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Paket ${selectedPlan.title}",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp,
                                    color = GoldPrimary
                                )
                                Text(
                                    text = selectedPlan.formattedPrice,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = "Durasi Akses: ${selectedPlan.durationDays} Hari (${selectedPlan.description})",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }

                    // Card Info Pembayaran & Bukti WA
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Alur Pembayaran & Konfirmasi:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = GoldSecondary
                            )
                            Text(
                                text = "1. Anda akan diarahkan ke layar QRIS Valora Store.\n2. Lakukan transfer sesuai nominal (${selectedPlan.formattedPrice}).\n3. Kirim bukti screenshot ke WhatsApp Admin ($adminWaDisplay) untuk aktivasi otomatis.",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.75f),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmationDialog = false
                        showQrisStep = true
                        scope.launch {
                            vipManager.submitPendingPayment(selectedPlan)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldPrimary,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Lanjut Pembayaran",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showConfirmationDialog = false },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                ) {
                    Text(
                        text = "Batal",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp
                    )
                }
            }
        )
    }
}

@Composable
private fun QrisStepItem(number: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(GoldPrimary.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = GoldPrimary
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.85f),
            lineHeight = 16.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun VipBenefitItem(
    iconRes: Int,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(GoldPrimary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = GoldPrimary,
                modifier = Modifier.size(15.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color.White
            )
            Text(
                text = desc,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.65f),
                lineHeight = 15.sp
            )
        }
    }
}

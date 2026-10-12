package com.valora.icebeats.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
    initialSelectedPlan: VipPlan = VipPlan.FIVE_MONTHS
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vipManager = remember { VipSubscriptionManager(context) }

    val isVipActive by vipManager.isVip.collectAsState(initial = false)
    val vipExpiresAt by vipManager.vipExpiresAt.collectAsState(initial = 0L)
    val vipStartsAt by vipManager.vipStartsAt.collectAsState(initial = 0L)
    val currentPlanName by vipManager.vipPlan.collectAsState(initial = "")
    val pendingTx by vipManager.pendingTransaction.collectAsState(initial = null)

    val (userName) = rememberPreference(AccountNameKey, defaultValue = "")
    val (userEmail) = rememberPreference(AccountEmailKey, defaultValue = "")

    var selectedPlan by remember { mutableStateOf(initialSelectedPlan) }
    var showConfirmationDialog by remember { mutableStateOf(false) }
    var showCancelPendingDialog by remember { mutableStateOf(false) }
    var showCancelActiveSubDialog by remember { mutableStateOf(false) }

    var voucherCode by remember { mutableStateOf("") }
    var showVoucherField by remember { mutableStateOf(false) }
    var isRedeeming by remember { mutableStateOf(false) }
    var isCheckingStatus by remember { mutableStateOf(false) }
    var isSubmittingOrder by remember { mutableStateOf(false) }

    val adminWaNumber = "6288983660479"
    val adminWaDisplay = "088983660479"

    // Real-time server-synced timer ticker
    var currentTimeMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(isVipActive) {
        if (isVipActive) {
            while (true) {
                delay(1000)
                currentTimeMillis = System.currentTimeMillis()
            }
        }
    }

    // Periksa status terbaru dari cloud saat dialog dibuka
    LaunchedEffect(Unit) {
        vipManager.checkCloudSubscriptionStatus()
    }

    // Auto-polling saat user memiliki transaksi pending (setiap 5 detik)
    LaunchedEffect(pendingTx, isVipActive) {
        if (pendingTx != null && !isVipActive) {
            while (pendingTx != null && !isVipActive) {
                delay(5000)
                val isApproved = vipManager.checkCloudSubscriptionStatus().getOrDefault(false)
                if (isApproved) {
                    Toast.makeText(context, "Selamat! Transaksi Anda telah disetujui. VIP aktif!", Toast.LENGTH_LONG).show()
                    break
                }
            }
        }
    }

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

    fun sendWhatsAppConfirmation(txId: String, planTitle: String, priceFormatted: String) {
        val userIdentifier = if (userName.isNotBlank()) userName else if (userEmail.isNotBlank()) userEmail else "Pengguna IceBeats"
        val message = """
Halo Admin Valora / IceBeats, saya telah melakukan transfer pembayaran langganan VIP:
• ID Transaksi: $txId
• Paket: $planTitle
• Nominal: $priceFormatted
• Akun Pengguna: $userIdentifier
• Tujuan Admin: $adminWaDisplay

Berikut saya lampirkan bukti transfernya. Mohon bantu verifikasi dan aktivasi VIP di aplikasi IceBeats saya. Terima kasih!
        """.trimIndent()

        val encodedMessage = URLEncoder.encode(message, "UTF-8")
        val waUrl = "https://api.whatsapp.com/send?phone=$adminWaNumber&text=$encodedMessage"
        openUrl(waUrl)
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
            // Header Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.size(32.dp))

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
                        tint = if (isVipActive) Color(0xFF25D366) else GoldPrimary,
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

            // ==============================================================================
            // ROUTING SCREEN SESUAI STATUS LANGGANAN
            // 1. isVipActive == true         -> SCREEN C (LANGGANAN VIP AKTIF)
            // 2. pendingTx != null           -> SCREEN B (DETAIL TRANSAKSI MENUNGGU ACC)
            // 3. else                        -> SCREEN A (PILIH 3 PAKET LANGGANAN)
            // ==============================================================================

            if (isVipActive) {
                // ==============================================================================
                // SCREEN C: LANGGANAN AKTIF (USER SEDANG BERLANGGANAN)
                // ==============================================================================
                val remainingText = remember(currentTimeMillis, vipExpiresAt) {
                    VipSubscriptionManager.formatRemainingTime(currentTimeMillis, vipExpiresAt)
                }

                Text(
                    text = "Langganan VIP Aktif",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        brush = Brush.linearGradient(
                            listOf(Color(0xFF86EFAC), Color(0xFF22C55E), Color(0xFF15803D))
                        )
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Akun Anda memiliki akses eksklusif ke semua fitur premium IceBeats.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Card Rincian Paket Aktif
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF0F2314))
                        .border(1.5.dp, Color(0xFF22C55E).copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                        .padding(18.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "PAKET LANGGANAN",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF86EFAC),
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = currentPlanName.ifBlank { "VIP 5 Bulan" },
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF22C55E))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "VIP AKTIF",
                                    color = Color.Black,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        Divider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Mulai Berlangganan", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                            Text(
                                text = VipSubscriptionManager.formatIndoDateTime(vipStartsAt),
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Tanggal Berakhir", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                            Text(
                                text = VipSubscriptionManager.formatExpiryDate(vipExpiresAt),
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Sisa Masa Aktif Real-time
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF14381E))
                                .border(1.dp, Color(0xFF22C55E).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = painterResource(R.drawable.cached),
                                        contentDescription = null,
                                        tint = Color(0xFF86EFAC),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Sisa Masa Aktif:",
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Text(
                                    text = remainingText,
                                    fontSize = 13.sp,
                                    color = Color(0xFF86EFAC),
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Card Blokir Pergantian Paket Saat VIP Masih Aktif
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF1A1A14))
                        .border(1.dp, GoldPrimary.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_vip_crown),
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Langganan VIP kamu masih aktif. Kamu dapat memilih paket lain setelah masa aktif ini berakhir.",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            lineHeight = 17.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Benefit yang sedang dinikmati
                VipBenefitsCard()

                Spacer(modifier = Modifier.height(16.dp))

                // Tombol Batalkan Langganan
                OutlinedButton(
                    onClick = { showCancelActiveSubDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.6f)),
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
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Batalkan Langganan",
                            color = Color(0xFFFF5252),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

            } else if (pendingTx != null) {
                // ==============================================================================
                // SCREEN B: TRANSAKSI MENUNGGU PERSETUJUAN ADMIN (PENDING)
                // ==============================================================================
                val tx = pendingTx!!

                Text(
                    text = "Detail Transaksi VIP",
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
                    text = "VIP akan aktif otomatis setelah bukti transfer disetujui oleh admin.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Status Badge Menunggu Persetujuan
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF2A200B))
                        .border(1.dp, GoldPrimary.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.cached),
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Status: Menunggu Persetujuan Admin",
                            color = GoldPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Card Rincian Transaksi
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(DarkCardBg)
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(18.dp))
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("ID Transaksi", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = tx.id,
                                    fontSize = 12.sp,
                                    color = GoldPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    painter = painterResource(R.drawable.music_note),
                                    contentDescription = "Salin",
                                    tint = GoldPrimary,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable { copyToClipboard("ID Transaksi", tx.id) }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Paket yang Dibeli", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                            Text(
                                text = tx.planTitle,
                                fontSize = 12.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total Tagihan", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                            Text(
                                text = "Rp ${NumberFormat(tx.price)}",
                                fontSize = 15.sp,
                                color = GoldPrimary,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Tanggal Transaksi", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                            Text(
                                text = tx.createdAt,
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Metode Pembayaran", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                            Text(
                                text = "QRIS Valora Store",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.8f),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Card QRIS Valora Store
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
                                .heightIn(max = 300.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // WhatsApp Confirmation Box
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
                                    text = "WhatsApp Verifikasi Admin:",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = adminWaDisplay,
                                    fontSize = 14.sp,
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

                // Tombol Kirim Bukti ke WhatsApp
                Button(
                    onClick = {
                        sendWhatsAppConfirmation(tx.id, tx.planTitle, "Rp ${NumberFormat(tx.price)}")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .shadow(elevation = 6.dp, shape = RoundedCornerShape(16.dp), ambientColor = GoldPrimary, spotColor = GoldPrimary),
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
                            text = "Kirim Bukti ke WhatsApp Admin",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.Black
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tombol Cek Status Persetujuan
                OutlinedButton(
                    onClick = {
                        isCheckingStatus = true
                        scope.launch {
                            val ok = vipManager.checkCloudSubscriptionStatus().getOrDefault(false)
                            isCheckingStatus = false
                            if (ok) {
                                Toast.makeText(context, "Selamat! Pembayaran berhasil diverifikasi. VIP telah aktif!", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Masih menunggu persetujuan admin. Silakan pastikan bukti transfer sudah dikirim ke WhatsApp ya!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !isCheckingStatus,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f))
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
                            text = if (isCheckingStatus) "Memeriksa Status..." else "Cek Status Persetujuan",
                            color = GoldPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tombol Batalkan Transaksi Pending
                OutlinedButton(
                    onClick = { showCancelPendingDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f)),
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
                            text = "Batalkan Transaksi",
                            color = Color(0xFFFF5252),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

            } else {
                // ==============================================================================
                // SCREEN A: PILIHAN 3 PAKET LANGGANAN VIP (TIDAK ADA VIP / TRANSAKSI PENDING)
                // ==============================================================================
                Text(
                    text = "IceBeats VIP Member",
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
                    text = "Buka semua tema kustom, Dynamic Island, chat eksklusif, dan border animasi profil tanpa batas!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "PILIH PAKET LANGGANAN",
                    style = MaterialTheme.typography.labelSmall,
                    color = GoldPrimary.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Kartu 3 Paket Langganan Asli
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    VipPlan.entries.forEach { plan ->
                        val isSelected = selectedPlan == plan
                        val targetBorderColor = if (isSelected) GoldPrimary else DarkCardBorder
                        val cardBorderColor by animateColorAsState(targetValue = targetBorderColor, label = "card_border")

                        val targetBgColor = if (isSelected) Color(0xFF221F14) else DarkCardBg
                        val cardBgColor by animateColorAsState(targetValue = targetBgColor, label = "card_bg")

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(cardBgColor)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = cardBorderColor,
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .clickable {
                                    selectedPlan = plan
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
                                            color = if (isSelected) GoldPrimary else Color.White
                                        )

                                        if (plan.badge != null) {
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
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = plan.formattedPrice,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp,
                                        color = if (isSelected) GoldPrimary else Color.White
                                    )

                                    Icon(
                                        painter = painterResource(
                                            if (isSelected) R.drawable.radio_button_checked
                                            else R.drawable.radio_button_unchecked
                                        ),
                                        contentDescription = null,
                                        tint = if (isSelected) GoldPrimary else Color.White.copy(alpha = 0.3f),
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

                // Tombol Beli Paket
                Button(
                    onClick = { showConfirmationDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp), ambientColor = GoldPrimary, spotColor = GoldPrimary),
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
                VipBenefitsCard()

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
                                placeholder = { Text("Contoh: VIP5BULAN", fontSize = 13.sp) },
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
                                            Toast.makeText(context, err.message ?: "Gagal klaim voucher", Toast.LENGTH_SHORT).show()
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

    // ==============================================================================
    // DIALOG KONFIRMASI PEMBELIAN PAKET (SCREEN A -> SCREEN B)
    // ==============================================================================
    if (showConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSubmittingOrder) showConfirmationDialog = false },
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
                                text = "Durasi Akses: ${selectedPlan.durationMonths} Bulan Kalender (${selectedPlan.description})",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }

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
                                text = "Alur Pembayaran & Persetujuan Admin:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = GoldSecondary
                            )
                            Text(
                                text = "1. Transaksi akan dibuat dengan status 'Menunggu Persetujuan'.\n2. Anda akan diarahkan ke layar QRIS & WhatsApp Admin.\n3. Setelah admin menyetujui, akun VIP langsung aktif otomatis.",
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
                        isSubmittingOrder = true
                        scope.launch {
                            val res = vipManager.submitPendingPayment(selectedPlan)
                            isSubmittingOrder = false
                            showConfirmationDialog = false
                            res.onSuccess { tx ->
                                Toast.makeText(context, "Transaksi berhasil dibuat! Silakan transfer QRIS dan kirim bukti ke admin.", Toast.LENGTH_LONG).show()
                                sendWhatsAppConfirmation(tx.id, tx.planTitle, "Rp ${NumberFormat(tx.price)}")
                            }.onFailure { err ->
                                Toast.makeText(context, err.message ?: "Gagal membuat transaksi", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !isSubmittingOrder,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldPrimary,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (isSubmittingOrder) "Memproses..." else "Lanjut Pembayaran",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showConfirmationDialog = false },
                    enabled = !isSubmittingOrder,
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

    // ==============================================================================
    // DIALOG KONFIRMASI PEMBATALAN TRANSAKSI PENDING
    // ==============================================================================
    if (showCancelPendingDialog) {
        AlertDialog(
            onDismissRequest = { showCancelPendingDialog = false },
            containerColor = Color(0xFF18181C),
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "Batalkan Transaksi Ini?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = "Transaksi pembelian ${pendingTx?.planTitle ?: "VIP"} yang sedang menunggu persetujuan akan dibatalkan. Anda dapat memilih kembali paket langganan lain setelah pembatalan ini.",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCancelPendingDialog = false
                        scope.launch {
                            vipManager.cancelPendingTransaction()
                            Toast.makeText(context, "Transaksi berhasil dibatalkan. Silakan pilih paket baru.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF5252),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Ya, Batalkan Transaksi", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showCancelPendingDialog = false },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                ) {
                    Text("Kembali", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                }
            }
        )
    }

    // ==============================================================================
    // DIALOG KONFIRMASI PEMBATALAN LANGGANAN VIP AKTIF
    // ==============================================================================
    if (showCancelActiveSubDialog) {
        AlertDialog(
            onDismissRequest = { showCancelActiveSubDialog = false },
            containerColor = Color(0xFF18181C),
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "Batalkan Langganan VIP?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color(0xFFFF5252)
                )
            },
            text = {
                Text(
                    text = "Perhatian: Jika dibatalkan, akun Anda akan kembali ke status gratis. Akses ke semua tema kustom, Dynamic Island, dan border animasi profil akan langsung dinonaktifkan.",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCancelActiveSubDialog = false
                        scope.launch {
                            vipManager.cancelActiveSubscription()
                            Toast.makeText(context, "Langganan VIP telah dinonaktifkan.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF5252),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Ya, Batalkan VIP", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showCancelActiveSubDialog = false },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                ) {
                    Text("Pertahankan VIP", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                }
            }
        )
    }
}

private fun NumberFormat(amount: Int): String {
    return String.format("%,d", amount).replace(',', '.')
}

@Composable
private fun VipBenefitsCard() {
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

package com.valora.icebeats.ui.component

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.valora.icebeats.constants.HomeScreenStyle
import com.valora.icebeats.constants.HomeScreenStyleKey
import com.valora.icebeats.constants.NavBarStyle
import com.valora.icebeats.constants.NavBarStyleKey
import com.valora.icebeats.constants.DynamicIslandKey
import com.valora.icebeats.constants.VipExpiresAtKey
import com.valora.icebeats.constants.VipStartsAtKey
import com.valora.icebeats.constants.VipPlanKey
import com.valora.icebeats.constants.VipStatusKey
import com.valora.icebeats.constants.VipSignatureKey
import com.valora.icebeats.constants.VipPendingTxIdKey
import com.valora.icebeats.constants.VipPendingPlanKey
import com.valora.icebeats.constants.VipPendingPriceKey
import com.valora.icebeats.constants.VipPendingCreatedAtKey
import com.valora.icebeats.constants.AccountEmailKey
import com.valora.icebeats.constants.AccountNameKey
import java.security.MessageDigest
import com.valora.icebeats.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class VipPlan(
    val id: String,
    val title: String,
    val durationMonths: Int,
    val durationDays: Int,
    val priceRupiah: Int,
    val formattedPrice: String,
    val badge: String? = null,
    val description: String = ""
) {
    ONE_MONTH(
        id = "vip_1_month",
        title = "1 Bulan",
        durationMonths = 1,
        durationDays = 30,
        priceRupiah = 5000,
        formattedPrice = "Rp 5.000",
        badge = null,
        description = "Akses VIP 1 Bulan Kalender"
    ),
    TWO_MONTHS(
        id = "vip_2_months",
        title = "2 Bulan",
        durationMonths = 2,
        durationDays = 60,
        priceRupiah = 7000,
        formattedPrice = "Rp 7.000",
        badge = "Paling Populer",
        description = "Hemat Rp 3.000 (Hanya Rp 3.500/bln)"
    ),
    FIVE_MONTHS(
        id = "vip_5_months",
        title = "5 Bulan",
        durationMonths = 5,
        durationDays = 150,
        priceRupiah = 12000,
        formattedPrice = "Rp 12.000",
        badge = "Paling Hemat",
        description = "Hemat Rp 13.000 (Cuma Rp 2.400/bln)"
    );

    companion object {
        fun fromId(id: String?): VipPlan {
            return entries.find { it.id == id } ?: ONE_MONTH
        }

        fun fromTitle(title: String?): VipPlan {
            val t = title.orEmpty().lowercase(Locale.ROOT)
            return when {
                t.contains("5") || t.contains("lima") -> FIVE_MONTHS
                t.contains("2") || t.contains("dua") -> TWO_MONTHS
                t.contains("1") || t.contains("satu") -> ONE_MONTH
                else -> entries.find { it.title.equals(title, ignoreCase = true) } ?: ONE_MONTH
            }
        }
    }
}

data class PendingVipTransaction(
    val id: String,
    val planTitle: String,
    val price: Int,
    val createdAt: String
)

@Singleton
class VipSubscriptionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private fun generateSecuritySignature(userId: String, expiresAt: Long, planTitle: String): String {
        val raw = "IB_VIP_SIG:${userId}:${expiresAt}:${planTitle}:VALORA_SECURE_SALT_98472849"
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(raw.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun getEffectiveUserIdentity(): Triple<String, String, String> {
        val authManager = com.valora.icebeats.supabase.SupabaseAuthManager.getInstance(context)
        val sbUid = authManager.userId.value.trim().ifBlank {
            runCatching {
                context.dataStore.data.first()[androidx.datastore.preferences.core.stringPreferencesKey("supabase_user_id")]?.trim().orEmpty()
            }.getOrDefault("")
        }
        val prefs = context.dataStore.data.first()
        val email = (prefs[AccountEmailKey] ?: "").trim()
        val userName = (prefs[AccountNameKey] ?: "").trim()

        val finalUid = when {
            sbUid.isNotBlank() -> sbUid
            email.isNotBlank() -> {
                val md = MessageDigest.getInstance("SHA-256")
                "user-" + md.digest(email.lowercase(Locale.ROOT).toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
            }
            else -> com.valora.icebeats.utils.IceBeatsStatsCloudSync.stableUserId(context)
        }
        return Triple(finalUid, email, userName)
    }

    suspend fun resetVipState() {
        context.dataStore.edit { prefs ->
            prefs[VipStatusKey] = false
            prefs[VipExpiresAtKey] = 0L
            prefs[VipStartsAtKey] = 0L
            prefs[VipPlanKey] = "Gratis"
            prefs[VipSignatureKey] = ""
            prefs[DynamicIslandKey] = false
        }
    }

    val isVip: Flow<Boolean> = context.dataStore.data.map { prefs ->
        val active = prefs[VipStatusKey] ?: false
        val expiresAt = prefs[VipExpiresAtKey] ?: 0L
        val plan = prefs[VipPlanKey] ?: ""
        val token = prefs[VipSignatureKey] ?: ""

        if (!active) {
            false
        } else {
            val authMgr = com.valora.icebeats.supabase.SupabaseAuthManager.getInstance(context)
            val sbUid = authMgr.userId.value.trim().ifBlank {
                prefs[androidx.datastore.preferences.core.stringPreferencesKey("supabase_user_id")]?.trim().orEmpty()
            }
            val email = (prefs[AccountEmailKey] ?: "").trim()
            val emailUid = if (email.isNotBlank()) {
                val md = MessageDigest.getInstance("SHA-256")
                "user-" + md.digest(email.lowercase(Locale.ROOT).toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
            } else ""
            val deviceId = com.valora.icebeats.utils.IceBeatsStatsCloudSync.stableUserId(context)

            val validSigs = listOfNotNull(
                if (sbUid.isNotBlank()) generateSecuritySignature(sbUid, expiresAt, plan) else null,
                if (emailUid.isNotBlank()) generateSecuritySignature(emailUid, expiresAt, plan) else null,
                generateSecuritySignature(deviceId, expiresAt, plan)
            )

            if (token !in validSigs) {
                // Modifikasi ilegal / cracker terdeteksi!
                false
            } else {
                val now = System.currentTimeMillis()
                expiresAt <= 0L || now < expiresAt
            }
        }
    }

    val vipExpiresAt: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[VipExpiresAtKey] ?: 0L
    }

    val vipStartsAt: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[VipStartsAtKey] ?: 0L
    }

    val vipPlan: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[VipPlanKey] ?: "Gratis"
    }

    val pendingTransaction: Flow<PendingVipTransaction?> = context.dataStore.data.map { prefs ->
        val txId = prefs[VipPendingTxIdKey] ?: ""
        if (txId.isBlank()) {
            null
        } else {
            PendingVipTransaction(
                id = txId,
                planTitle = prefs[VipPendingPlanKey] ?: "VIP 5 Bulan",
                price = prefs[VipPendingPriceKey] ?: 12000,
                createdAt = prefs[VipPendingCreatedAtKey] ?: ""
            )
        }
    }

    suspend fun getVipStatusSync(): Boolean {
        val prefs = context.dataStore.data.first()
        val active = prefs[VipStatusKey] ?: false
        val expiresAt = prefs[VipExpiresAtKey] ?: 0L
        val plan = prefs[VipPlanKey] ?: ""
        val token = prefs[VipSignatureKey] ?: ""

        if (!active) return false
        val (userId, _, _) = getEffectiveUserIdentity()
        val deviceId = com.valora.icebeats.utils.IceBeatsStatsCloudSync.stableUserId(context)
        val validSigs = listOf(
            generateSecuritySignature(userId, expiresAt, plan),
            generateSecuritySignature(deviceId, expiresAt, plan)
        )

        return if (token !in validSigs) false else (expiresAt <= 0L || System.currentTimeMillis() < expiresAt)
    }

    suspend fun activatePlan(plan: VipPlan) {
        val now = System.currentTimeMillis()
        val currentExpiry = context.dataStore.data.first()[VipExpiresAtKey] ?: 0L
        val baseStart = if (currentExpiry > now) currentExpiry else now
        val newExpiry = calculateCalendarExpiry(baseStart, plan.durationMonths, plan.durationDays)

        val (userId, _, _) = getEffectiveUserIdentity()
        val signature = generateSecuritySignature(userId, newExpiry, plan.title)

        context.dataStore.edit { prefs ->
            prefs[VipStatusKey] = true
            prefs[VipStartsAtKey] = now
            prefs[VipExpiresAtKey] = newExpiry
            prefs[VipPlanKey] = plan.title
            prefs[VipSignatureKey] = signature
            // Bersihkan transaksi pending
            prefs.remove(VipPendingTxIdKey)
            prefs.remove(VipPendingPlanKey)
            prefs.remove(VipPendingPriceKey)
            prefs.remove(VipPendingCreatedAtKey)
        }

        syncToSupabase(plan.title, plan.priceRupiah, now, newExpiry, status = "approved", isActive = true)
    }

    suspend fun activateCustomDays(days: Int, planTitle: String) {
        val now = System.currentTimeMillis()
        val durationMillis = days * 24L * 60L * 60L * 1000L
        val currentExpiry = context.dataStore.data.first()[VipExpiresAtKey] ?: 0L

        val newExpiry = if (currentExpiry > now) {
            currentExpiry + durationMillis
        } else {
            now + durationMillis
        }

        val (userId, _, _) = getEffectiveUserIdentity()
        val signature = generateSecuritySignature(userId, newExpiry, planTitle)

        context.dataStore.edit { prefs ->
            prefs[VipStatusKey] = true
            prefs[VipStartsAtKey] = now
            prefs[VipExpiresAtKey] = newExpiry
            prefs[VipPlanKey] = planTitle
            prefs[VipSignatureKey] = signature
            prefs.remove(VipPendingTxIdKey)
            prefs.remove(VipPendingPlanKey)
            prefs.remove(VipPendingPriceKey)
            prefs.remove(VipPendingCreatedAtKey)
        }

        syncToSupabase(planTitle, 0, now, newExpiry, status = "approved", isActive = true)
    }

    suspend fun redeemVoucher(rawCode: String): Result<String> {
        val code = rawCode.trim().uppercase(Locale.ROOT)
        return when (code) {
            "VIP1BULAN", "ICEBEATS5K" -> {
                activatePlan(VipPlan.ONE_MONTH)
                val expiry = calculateCalendarExpiry(System.currentTimeMillis(), 1, 30)
                Result.success("Berhasil! VIP 1 Bulan aktif hingga ${formatExpiryDate(expiry)}")
            }
            "VIP2BULAN", "ICEBEATS7K" -> {
                activatePlan(VipPlan.TWO_MONTHS)
                val expiry = calculateCalendarExpiry(System.currentTimeMillis(), 2, 60)
                Result.success("Berhasil! VIP 2 Bulan aktif hingga ${formatExpiryDate(expiry)}")
            }
            "VIP5BULAN", "ICEBEATS12K" -> {
                activatePlan(VipPlan.FIVE_MONTHS)
                val expiry = calculateCalendarExpiry(System.currentTimeMillis(), 5, 150)
                Result.success("Selamat! VIP 5 Bulan aktif hingga ${formatExpiryDate(expiry)}")
            }
            "VALORAVIP", "ADMIN123", "SUPERVIP" -> {
                activateCustomDays(365, "VIP 1 Tahun Spesial")
                Result.success("Luar biasa! VIP Spesial 1 Tahun (365 Hari) telah diaktifkan!")
            }
            else -> {
                Result.failure(IllegalArgumentException("Kode voucher tidak valid atau telah kadaluarsa."))
            }
        }
    }

    /**
     * Mengirim catatan order ke database Supabase saat user transfer QRIS.
     * Status order: 'pending' (menunggu ACC admin di dashboard).
     * Jika user masih memiliki VIP aktif, pembelian paket baru dicegah!
     */
    suspend fun submitPendingPayment(plan: VipPlan): Result<PendingVipTransaction> = withContext(Dispatchers.IO) {
        runCatching {
            // Cegah penggantian paket saat VIP masih aktif
            if (getVipStatusSync()) {
                throw IllegalStateException("Langganan VIP kamu masih aktif. Kamu dapat memilih paket lain setelah masa aktif ini berakhir.")
            }

            val (userId, email, currentUserName) = getEffectiveUserIdentity()
            val anonKey = com.valora.icebeats.supabase.SupabaseConfig.SUPABASE_ANON_KEY
            val baseUrl = com.valora.icebeats.supabase.SupabaseConfig.SUPABASE_URL
            val authManager = com.valora.icebeats.supabase.SupabaseAuthManager.getInstance(context)
            val token = authManager.accessToken ?: anonKey

            val generatedTxId = "TRX-" + UUID.randomUUID().toString().take(8).uppercase(Locale.ROOT)
            val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date())

            val bodyJson = JSONObject().apply {
                put("user_id", userId)
                put("user_name", currentUserName.ifBlank { email.substringBefore("@").ifBlank { "User IceBeats" } })
                put("email", email)
                put("plan_name", plan.title)
                put("price", plan.priceRupiah)
                put("status", "pending")
                put("is_active", false)
                put("starts_at", nowIso)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = bodyJson.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url("$baseUrl/rest/v1/user_subscriptions?on_conflict=user_id")
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .header("Prefer", "resolution=merge-duplicates,return=representation")
                .post(requestBody)
                .build()

            val response = OkHttpClient().newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()
            val isSuccess = response.isSuccessful
            response.close()

            if (!isSuccess) {
                throw IllegalStateException("Gagal membuat transaksi: HTTP ${response.code}")
            }

            var finalTxId = generatedTxId
            var finalCreatedAt = formatIndoDateTime(System.currentTimeMillis())

            runCatching {
                val resArr = JSONArray(responseBody)
                if (resArr.length() > 0) {
                    val row = resArr.getJSONObject(0)
                    val rowId = row.optString("id", "")
                    if (rowId.isNotBlank()) finalTxId = rowId
                    val createdIso = row.optString("created_at", "")
                    val parsedCreated = parseIsoTimestamp(createdIso)
                    if (parsedCreated > 0L) {
                        finalCreatedAt = formatIndoDateTime(parsedCreated)
                    }
                }
            }

            // Simpan state pending ke DataStore lokal agar persisten saat app direstart
            context.dataStore.edit { prefs ->
                prefs[VipPendingTxIdKey] = finalTxId
                prefs[VipPendingPlanKey] = plan.title
                prefs[VipPendingPriceKey] = plan.priceRupiah
                prefs[VipPendingCreatedAtKey] = finalCreatedAt
            }

            PendingVipTransaction(
                id = finalTxId,
                planTitle = plan.title,
                price = plan.priceRupiah,
                createdAt = finalCreatedAt
            )
        }
    }

    /**
     * Memeriksa apakah admin di dashboard Supabase sudah me-ACC (mengaktifkan) paket langganan.
     * Mengembalikan true jika VIP aktif, false jika pending/belum aktif/ditolak.
     */
    suspend fun checkCloudSubscriptionStatus(): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val (userId, email, _) = getEffectiveUserIdentity()
            val anonKey = com.valora.icebeats.supabase.SupabaseConfig.SUPABASE_ANON_KEY
            val baseUrl = com.valora.icebeats.supabase.SupabaseConfig.SUPABASE_URL
            val authManager = com.valora.icebeats.supabase.SupabaseAuthManager.getInstance(context)
            val token = authManager.accessToken ?: anonKey

            val filter = if (email.isNotBlank() && userId.isNotBlank() && !userId.startsWith("device-")) {
                val encEmail = java.net.URLEncoder.encode(email, java.nio.charset.StandardCharsets.UTF_8.name())
                "or=(user_id.eq.$userId,email.eq.$encEmail)&order=created_at.desc&limit=1"
            } else if (email.isNotBlank()) {
                val encEmail = java.net.URLEncoder.encode(email, java.nio.charset.StandardCharsets.UTF_8.name())
                "email=eq.$encEmail&order=created_at.desc&limit=1"
            } else {
                "user_id=eq.$userId&order=created_at.desc&limit=1"
            }

            val request = Request.Builder()
                .url("$baseUrl/rest/v1/user_subscriptions?$filter")
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $token")
                .header("Cache-Control", "no-cache")
                .get()
                .build()

            val response = OkHttpClient().newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return@runCatching false
            }

            val responseBody = response.body?.string().orEmpty()
            response.close()

            val jsonArray = JSONArray(responseBody)
            if (jsonArray.length() == 0) {
                // Tidak ada data transaksi di server
                resetVipState()
                context.dataStore.edit { prefs ->
                    prefs.remove(VipPendingTxIdKey)
                    prefs.remove(VipPendingPlanKey)
                    prefs.remove(VipPendingPriceKey)
                    prefs.remove(VipPendingCreatedAtKey)
                }
                return@runCatching false
            }

            val subObject = jsonArray.getJSONObject(0)
            val isActive = subObject.optBoolean("is_active", false)
            val status = subObject.optString("status", "pending")
            val planName = subObject.optString("plan_name", "1 Bulan")
            val startsAtStr = subObject.optString("starts_at", "")
            val expiresAtStr = subObject.optString("expires_at", "")

            if (isActive || status.equals("approved", ignoreCase = true)) {
                // Transaksi sudah di-ACC oleh admin! Aktifkan VIP!
                val isDeveloperPlan = planName.contains("developer", ignoreCase = true)
                val matchedPlan = VipPlan.fromTitle(planName)
                val finalPlanTitle = if (isDeveloperPlan) "Developer" else matchedPlan.title

                var expiryMillis = parseIsoTimestamp(expiresAtStr)
                var startMillis = parseIsoTimestamp(startsAtStr)
                if (startMillis <= 0L) startMillis = System.currentTimeMillis()

                if (expiryMillis <= System.currentTimeMillis()) {
                    expiryMillis = if (isDeveloperPlan) {
                        System.currentTimeMillis() + (36500L * 24L * 60L * 60L * 1000L) // 100 Tahun Developer
                    } else {
                        calculateCalendarExpiry(startMillis, matchedPlan.durationMonths, matchedPlan.durationDays)
                    }
                }

                if (!isDeveloperPlan && expiryMillis <= System.currentTimeMillis()) {
                    // Masa aktif telah berakhir
                    resetVipState()
                    context.dataStore.edit { prefs ->
                        prefs.remove(VipPendingTxIdKey)
                        prefs.remove(VipPendingPlanKey)
                        prefs.remove(VipPendingPriceKey)
                        prefs.remove(VipPendingCreatedAtKey)
                    }
                    return@runCatching false
                }

                val signature = generateSecuritySignature(userId, expiryMillis, finalPlanTitle)

                context.dataStore.edit { prefs ->
                    prefs[VipStatusKey] = true
                    prefs[VipStartsAtKey] = startMillis
                    prefs[VipExpiresAtKey] = expiryMillis
                    prefs[VipPlanKey] = finalPlanTitle
                    prefs[VipSignatureKey] = signature
                    // Bersihkan pending state saat VIP aktif
                    prefs.remove(VipPendingTxIdKey)
                    prefs.remove(VipPendingPlanKey)
                    prefs.remove(VipPendingPriceKey)
                    prefs.remove(VipPendingCreatedAtKey)
                }
                return@runCatching true
            } else if (status.equals("pending", ignoreCase = true)) {
                // Masih menunggu persetujuan admin: simpan pending transaksi
                val txId = subObject.optString("id", "")
                val price = subObject.optInt("price", 12000)
                val createdIso = subObject.optString("created_at", "")
                val createdParsed = parseIsoTimestamp(createdIso)
                val createdDisplay = if (createdParsed > 0L) formatIndoDateTime(createdParsed) else formatIndoDateTime(System.currentTimeMillis())

                context.dataStore.edit { prefs ->
                    prefs[VipStatusKey] = false
                    prefs[VipExpiresAtKey] = 0L
                    prefs[VipPlanKey] = "Gratis"
                    if (txId.isNotBlank()) prefs[VipPendingTxIdKey] = txId
                    prefs[VipPendingPlanKey] = planName
                    prefs[VipPendingPriceKey] = price
                    prefs[VipPendingCreatedAtKey] = createdDisplay
                }
                return@runCatching false
            } else {
                // Status ditolak (rejected) atau dibatalkan (cancelled)
                resetVipState()
                context.dataStore.edit { prefs ->
                    prefs.remove(VipPendingTxIdKey)
                    prefs.remove(VipPendingPlanKey)
                    prefs.remove(VipPendingPriceKey)
                    prefs.remove(VipPendingCreatedAtKey)
                }
                return@runCatching false
            }
        }
    }

    /**
     * Membatalkan transaksi pending (yang belum disetujui admin)
     */
    suspend fun cancelPendingTransaction(): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val (userId, _, _) = getEffectiveUserIdentity()
            val anonKey = com.valora.icebeats.supabase.SupabaseConfig.SUPABASE_ANON_KEY
            val baseUrl = com.valora.icebeats.supabase.SupabaseConfig.SUPABASE_URL
            val authManager = com.valora.icebeats.supabase.SupabaseAuthManager.getInstance(context)
            val token = authManager.accessToken ?: anonKey

            val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date())

            val bodyJson = JSONObject().apply {
                put("status", "cancelled")
                put("is_active", false)
                put("updated_at", nowIso)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = bodyJson.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url("$baseUrl/rest/v1/user_subscriptions?user_id=eq.$userId")
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .patch(requestBody)
                .build()

            OkHttpClient().newCall(request).execute().close()

            context.dataStore.edit { prefs ->
                prefs.remove(VipPendingTxIdKey)
                prefs.remove(VipPendingPlanKey)
                prefs.remove(VipPendingPriceKey)
                prefs.remove(VipPendingCreatedAtKey)
            }
            true
        }
    }

    /**
     * Membatalkan langganan VIP aktif
     */
    suspend fun cancelActiveSubscription(): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val (userId, _, _) = getEffectiveUserIdentity()
            val anonKey = com.valora.icebeats.supabase.SupabaseConfig.SUPABASE_ANON_KEY
            val baseUrl = com.valora.icebeats.supabase.SupabaseConfig.SUPABASE_URL
            val authManager = com.valora.icebeats.supabase.SupabaseAuthManager.getInstance(context)
            val token = authManager.accessToken ?: anonKey

            val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date())

            val bodyJson = JSONObject().apply {
                put("status", "cancelled")
                put("is_active", false)
                put("updated_at", nowIso)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = bodyJson.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url("$baseUrl/rest/v1/user_subscriptions?user_id=eq.$userId")
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .patch(requestBody)
                .build()

            OkHttpClient().newCall(request).execute().close()

            resetVipState()
            context.dataStore.edit { prefs ->
                prefs.remove(VipPendingTxIdKey)
                prefs.remove(VipPendingPlanKey)
                prefs.remove(VipPendingPriceKey)
                prefs.remove(VipPendingCreatedAtKey)
            }
            true
        }
    }

    suspend fun checkExpiryAndRevertIfNeeded() {
        val prefs = context.dataStore.data.first()
        val active = prefs[VipStatusKey] ?: false
        val expiresAt = prefs[VipExpiresAtKey] ?: 0L
        val plan = prefs[VipPlanKey] ?: ""
        val token = prefs[VipSignatureKey] ?: ""
        val now = System.currentTimeMillis()

        val (userId, _, _) = getEffectiveUserIdentity()
        val deviceId = com.valora.icebeats.utils.IceBeatsStatsCloudSync.stableUserId(context)
        val validSigs = listOf(
            generateSecuritySignature(userId, expiresAt, plan),
            generateSecuritySignature(deviceId, expiresAt, plan)
        )

        // Validasi Anti-Tamper & Validasi Kadaluarsa
        if (active && (token !in validSigs || (expiresAt > 0L && now >= expiresAt))) {
            resetVipState()
        } else {
            // Verifikasi status dengan server Supabase di latar belakang
            checkCloudSubscriptionStatus()
        }
    }

    private fun syncToSupabase(
        planName: String,
        price: Int,
        startsAtMillis: Long,
        expiresAtMillis: Long,
        status: String = "approved",
        isActive: Boolean = true
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val (userId, email, currentUserName) = getEffectiveUserIdentity()
                val anonKey = com.valora.icebeats.supabase.SupabaseConfig.SUPABASE_ANON_KEY
                val baseUrl = com.valora.icebeats.supabase.SupabaseConfig.SUPABASE_URL
                val authManager = com.valora.icebeats.supabase.SupabaseAuthManager.getInstance(context)
                val token = authManager.accessToken ?: anonKey

                val startsDateStr = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date(startsAtMillis))

                val expiresDateStr = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date(expiresAtMillis))

                val bodyJson = JSONObject().apply {
                    put("user_id", userId)
                    put("user_name", currentUserName.ifBlank { email.substringBefore("@").ifBlank { "User IceBeats" } })
                    put("email", email)
                    put("plan_name", planName)
                    put("price", price)
                    put("status", status)
                    put("is_active", isActive)
                    put("starts_at", startsDateStr)
                    put("expires_at", expiresDateStr)
                }

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = bodyJson.toString().toRequestBody(mediaType)

                val request = Request.Builder()
                    .url("$baseUrl/rest/v1/user_subscriptions")
                    .header("apikey", anonKey)
                    .header("Authorization", "Bearer $token")
                    .header("Content-Type", "application/json")
                    .header("Prefer", "resolution=merge-duplicates")
                    .post(requestBody)
                    .build()

                OkHttpClient().newCall(request).execute().close()
            }
        }
    }

    companion object {
        fun parseIsoTimestamp(isoString: String): Long {
            if (isoString.isBlank() || isoString == "null") return 0L
            return runCatching {
                OffsetDateTime.parse(isoString).toInstant().toEpochMilli()
            }.recoverCatching {
                Instant.parse(isoString).toEpochMilli()
            }.recoverCatching {
                val cleaned = isoString.replace("Z", "+00:00")
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val prefix = cleaned.substringBefore(".").substringBefore("+")
                sdf.parse(prefix)?.time ?: 0L
            }.getOrDefault(0L)
        }

        fun calculateCalendarExpiry(startMillis: Long, months: Int, daysFallback: Int = 0): Long {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                timeInMillis = startMillis
            }
            if (months > 0) {
                cal.add(Calendar.MONTH, months)
            } else if (daysFallback > 0) {
                cal.add(Calendar.DAY_OF_YEAR, daysFallback)
            }
            return cal.timeInMillis
        }

        fun formatExpiryDate(millis: Long): String {
            if (millis <= 0L) return "Selamanya (Lifetime)"
            val sdf = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale("id", "ID"))
            return sdf.format(Date(millis))
        }

        fun formatIndoDateTime(millis: Long): String {
            if (millis <= 0L) return "-"
            val sdf = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale("id", "ID"))
            return sdf.format(Date(millis))
        }

        fun formatRemainingTime(nowMillis: Long, expiryMillis: Long): String {
            if (expiryMillis <= 0L) return "Selamanya (Lifetime)"
            val diffMillis = expiryMillis - nowMillis
            if (diffMillis <= 0L) return "Masa aktif telah berakhir"

            val totalSeconds = diffMillis / 1000L
            val totalMinutes = totalSeconds / 60L
            val totalHours = totalMinutes / 60L
            val totalDays = totalHours / 24L

            return when {
                totalDays >= 30 -> {
                    val months = (totalDays / 30).toInt()
                    val remainingDays = (totalDays % 30).toInt()
                    if (remainingDays > 0) {
                        "$months bulan $remainingDays hari tersisa"
                    } else {
                        "$months bulan tersisa"
                    }
                }
                totalDays > 0 -> {
                    val remainingHours = (totalHours % 24).toInt()
                    if (remainingHours > 0) {
                        "$totalDays hari $remainingHours jam tersisa"
                    } else {
                        "$totalDays hari tersisa"
                    }
                }
                totalHours > 0 -> {
                    val remainingMinutes = (totalMinutes % 60).toInt()
                    "$totalHours jam $remainingMinutes menit tersisa"
                }
                else -> {
                    val minutes = if (totalMinutes < 1) 1 else totalMinutes
                    "$minutes menit tersisa"
                }
            }
        }
    }
}

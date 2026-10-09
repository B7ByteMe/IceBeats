package com.valora.icebeats.ui.component

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.valora.icebeats.constants.HomeScreenStyle
import com.valora.icebeats.constants.HomeScreenStyleKey
import com.valora.icebeats.constants.NavBarStyle
import com.valora.icebeats.constants.NavBarStyleKey
import com.valora.icebeats.constants.DynamicIslandKey
import com.valora.icebeats.constants.VipExpiresAtKey
import com.valora.icebeats.constants.VipPlanKey
import com.valora.icebeats.constants.VipStatusKey
import com.valora.icebeats.constants.VipSignatureKey
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
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class VipPlan(
    val id: String,
    val title: String,
    val durationDays: Int,
    val priceRupiah: Int,
    val formattedPrice: String,
    val badge: String? = null,
    val description: String = ""
) {
    ONE_MONTH(
        id = "vip_1_month",
        title = "1 Bulan",
        durationDays = 30,
        priceRupiah = 5000,
        formattedPrice = "Rp 5.000",
        badge = null,
        description = "Akses VIP 30 Hari Penuh"
    ),
    TWO_MONTHS(
        id = "vip_2_months",
        title = "2 Bulan",
        durationDays = 60,
        priceRupiah = 7000,
        formattedPrice = "Rp 7.000",
        badge = "Paling Populer",
        description = "Hemat Rp 3.000 (Hanya Rp 3.500/bln)"
    ),
    FIVE_MONTHS(
        id = "vip_5_months",
        title = "5 Bulan",
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
    }
}

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
        val email = (prefs[com.valora.icebeats.constants.AccountEmailKey] ?: "").trim()
        val userName = (prefs[com.valora.icebeats.constants.AccountNameKey] ?: "").trim()

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
            prefs[VipPlanKey] = "Gratis"
            prefs[VipSignatureKey] = ""
            prefs[HomeScreenStyleKey] = HomeScreenStyle.CLASSIC.name
            prefs[NavBarStyleKey] = NavBarStyle.CLASSIC.name
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
            val email = (prefs[com.valora.icebeats.constants.AccountEmailKey] ?: "").trim()
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

    val vipPlan: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[VipPlanKey] ?: "Gratis"
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
        val durationMillis = plan.durationDays * 24L * 60L * 60L * 1000L
        val currentExpiry = context.dataStore.data.first()[VipExpiresAtKey] ?: 0L

        // Jika masih aktif, tambahkan dari expiry sebelumnya (stacking)
        val newExpiry = if (currentExpiry > now) {
            currentExpiry + durationMillis
        } else {
            now + durationMillis
        }

        val (userId, _, _) = getEffectiveUserIdentity()
        val signature = generateSecuritySignature(userId, newExpiry, plan.title)

        context.dataStore.edit { prefs ->
            prefs[VipStatusKey] = true
            prefs[VipExpiresAtKey] = newExpiry
            prefs[VipPlanKey] = plan.title
            prefs[VipSignatureKey] = signature
        }

        syncToSupabase(plan.title, plan.priceRupiah, newExpiry, status = "approved", isActive = true)
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
            prefs[VipExpiresAtKey] = newExpiry
            prefs[VipPlanKey] = planTitle
            prefs[VipSignatureKey] = signature
        }

        syncToSupabase(planTitle, 0, newExpiry, status = "approved", isActive = true)
    }

    suspend fun redeemVoucher(rawCode: String): Result<String> {
        val code = rawCode.trim().uppercase(Locale.ROOT)
        return when (code) {
            "VIP1BULAN", "ICEBEATS5K" -> {
                activatePlan(VipPlan.ONE_MONTH)
                Result.success("Berhasil! VIP 1 Bulan aktif hingga ${formatExpiryDate(System.currentTimeMillis() + 30L * 86400000L)}")
            }
            "VIP2BULAN", "ICEBEATS7K" -> {
                activatePlan(VipPlan.TWO_MONTHS)
                Result.success("Berhasil! VIP 2 Bulan aktif hingga ${formatExpiryDate(System.currentTimeMillis() + 60L * 86400000L)}")
            }
            "VIP5BULAN", "ICEBEATS12K" -> {
                activatePlan(VipPlan.FIVE_MONTHS)
                Result.success("Selamat! VIP 5 Bulan aktif hingga ${formatExpiryDate(System.currentTimeMillis() + 150L * 86400000L)}")
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
     * Mengirim catatan order ke database Supabase saat user transfer QRIS
     * Status order: 'pending' (menunggu ACC admin di dashboard)
     */
    suspend fun submitPendingPayment(plan: VipPlan): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val (userId, email, currentUserName) = getEffectiveUserIdentity()
            val anonKey = com.valora.icebeats.supabase.SupabaseConfig.SUPABASE_ANON_KEY
            val baseUrl = com.valora.icebeats.supabase.SupabaseConfig.SUPABASE_URL
            val authManager = com.valora.icebeats.supabase.SupabaseAuthManager.getInstance(context)
            val token = authManager.accessToken ?: anonKey

            val bodyJson = JSONObject().apply {
                put("user_id", userId)
                put("user_name", currentUserName.ifBlank { email.substringBefore("@").ifBlank { "User IceBeats" } })
                put("email", email)
                put("plan_name", plan.title)
                put("price", plan.priceRupiah)
                put("status", "pending")
                put("is_active", false)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = bodyJson.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url("$baseUrl/rest/v1/user_subscriptions?on_conflict=user_id")
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .header("Prefer", "resolution=merge-duplicates")
                .post(requestBody)
                .build()

            val response = OkHttpClient().newCall(request).execute()
            val isSuccess = response.isSuccessful
            response.close()
            isSuccess
        }
    }

    /**
     * Memeriksa apakah admin di dashboard Supabase sudah me-ACC (mengaktifkan) paket langganan
     * Khusus untuk akun yang sedang login! Jika akun ini tidak punya langganan, otomatis revert ke non-VIP!
     */
    suspend fun checkCloudSubscriptionStatus(): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val (userId, email, _) = getEffectiveUserIdentity()
            val anonKey = com.valora.icebeats.supabase.SupabaseConfig.SUPABASE_ANON_KEY
            val baseUrl = com.valora.icebeats.supabase.SupabaseConfig.SUPABASE_URL
            val authManager = com.valora.icebeats.supabase.SupabaseAuthManager.getInstance(context)
            val token = authManager.accessToken ?: anonKey

            // Query spesifik untuk akun yang aktif (berdasarkan Supabase UID atau email)
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
                // Tidak ada langganan untuk akun ini di database cloud!
                // Pastikan status VIP akun ini NONAKTIF (agar tidak mewarisi VIP akun sebelumnya)
                resetVipState()
                return@runCatching false
            }

            val subObject = jsonArray.getJSONObject(0)
            val isActive = subObject.optBoolean("is_active", false)
            val status = subObject.optString("status", "pending")
            val planName = subObject.optString("plan_name", "1 Bulan")
            val expiresAtStr = subObject.optString("expires_at", "")

            if (isActive || status.equals("approved", ignoreCase = true)) {
                // Admin telah ACC pesanan di dashboard / set Developer! Otomatis aktifkan di HP pengguna!
                val isDeveloperPlan = planName.contains("developer", ignoreCase = true)
                val matchedPlan = VipPlan.entries.find { it.title.equals(planName, ignoreCase = true) } ?: VipPlan.ONE_MONTH
                val finalPlanTitle = if (isDeveloperPlan) "Developer" else matchedPlan.title

                var expiryMillis = 0L

                if (expiresAtStr.isNotBlank() && expiresAtStr != "null") {
                    runCatching {
                        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                            timeZone = java.util.TimeZone.getTimeZone("UTC")
                        }
                        expiryMillis = sdf.parse(expiresAtStr.substringBefore("."))?.time ?: 0L
                    }
                }

                if (expiryMillis <= System.currentTimeMillis()) {
                    expiryMillis = if (isDeveloperPlan) {
                        System.currentTimeMillis() + (36500L * 24L * 60L * 60L * 1000L) // 100 Tahun Developer
                    } else {
                        System.currentTimeMillis() + (matchedPlan.durationDays * 24L * 60L * 60L * 1000L)
                    }
                }

                if (!isDeveloperPlan && expiryMillis <= System.currentTimeMillis()) {
                    // Paket sudah habis masa berlakunya
                    resetVipState()
                    return@runCatching false
                }

                val signature = generateSecuritySignature(userId, expiryMillis, finalPlanTitle)

                context.dataStore.edit { prefs ->
                    prefs[VipStatusKey] = true
                    prefs[VipExpiresAtKey] = expiryMillis
                    prefs[VipPlanKey] = finalPlanTitle
                    prefs[VipSignatureKey] = signature
                }
                return@runCatching true
            } else {
                // Pesanan pending / ditolak / dinonaktifkan
                resetVipState()
            }

            false
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
            // Modifikasi ilegal terdeteksi ATAU masa aktif telah habis -> reset ke gratis
            resetVipState()
        } else {
            // Selalu verifikasi status dengan server Supabase di latar belakang
            checkCloudSubscriptionStatus()
        }
    }

    private fun syncToSupabase(
        planName: String,
        price: Int,
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

                val expiresDateStr = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }.format(Date(expiresAtMillis))

                val bodyJson = JSONObject().apply {
                    put("user_id", userId)
                    put("user_name", currentUserName.ifBlank { email.substringBefore("@").ifBlank { "User IceBeats" } })
                    put("email", email)
                    put("plan_name", planName)
                    put("price", price)
                    put("status", status)
                    put("is_active", isActive)
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
        fun formatExpiryDate(millis: Long): String {
            if (millis <= 0L) return "Selamanya (Lifetime)"
            val sdf = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale("id", "ID"))
            return sdf.format(Date(millis))
        }
    }
}

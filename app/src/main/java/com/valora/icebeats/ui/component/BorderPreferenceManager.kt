package com.valora.icebeats.ui.component

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

enum class MasterBorderStyle(
    val id: String,
    val title: String,
    val description: String,
    val drawableResName: String,
    val scaleMultiplier: Float = 1.68f,
    val offsetYRatio: Float = 0f
) {
    ROYAL_CROWN(
        id = "royal_crown",
        title = "Royal Crown",
        description = "Mahkota Emas Mewah, Sayap Hitam & Permata",
        drawableResName = "border_royal_crown",
        scaleMultiplier = 1.68f,
        offsetYRatio = 0f
    ),
    CRIMSON_WING(
        id = "crimson_wing",
        title = "Crimson Wings",
        description = "Sayap Emas Elegan & Kristal Rubi Merah",
        drawableResName = "border_crimson_wing",
        scaleMultiplier = 1.68f,
        offsetYRatio = 0f
    ),
    FIRE_FLAME(
        id = "fire_flame",
        title = "Fire Flame Ring",
        description = "Cincin Api Berputar Khas Elemen Membara",
        drawableResName = "border_fire_flame",
        scaleMultiplier = 1.68f,
        offsetYRatio = 0f
    ),
    GOLDEN_SHIELD(
        id = "golden_shield",
        title = "Golden Champion",
        description = "Tameng Sayap Kejuaraan Emas Gagah",
        drawableResName = "border_golden_shield",
        scaleMultiplier = 1.62f,
        offsetYRatio = 0f
    ),
    SHADOW_MASK(
        id = "shadow_mask",
        title = "Shadow Mask",
        description = "Topeng Iblis Kuno, Api Biru & Sayap Bayangan",
        drawableResName = "border_shadow_mask",
        scaleMultiplier = 1.68f,
        offsetYRatio = 0f
    ),
    RAIDEN_SHOGUN(
        id = "raiden_shogun",
        title = "Electro Shogun",
        description = "Petir Abadi Inazuma, Kipas Sakura & Chibi Shogun",
        drawableResName = "border_raiden_shogun",
        scaleMultiplier = 1.68f,
        offsetYRatio = 0f
    ),
    ELYSIA_PINK(
        id = "elysia_pink",
        title = "Starlight Elysia",
        description = "Planet Bintang, Sayap Kristal & Chibi Peri Imut",
        drawableResName = "border_elysia_pink",
        scaleMultiplier = 1.68f,
        offsetYRatio = 0f
    ),
    FURINA_CHESS(
        id = "furina_chess",
        title = "Furina Chess",
        description = "Bidak Catur Kristal Fontaine, Mahkota & Api Dingin",
        drawableResName = "border_furina_chess",
        scaleMultiplier = 1.68f,
        offsetYRatio = 0f
    ),
    CRIMSON_BLOOM(
        id = "crimson_bloom",
        title = "Crimson Bloom",
        description = "Kelopak Bunga Darah, Kristal Suci & Chibi Karismatik",
        drawableResName = "border_crimson_bloom",
        scaleMultiplier = 1.68f,
        offsetYRatio = 0f
    ),
    DRAGON_KIRIN(
        id = "dragon_kirin",
        title = "Dragon Kirin",
        description = "Naga Oriental Sakti, Ombak Giok & Rumbai Emas",
        drawableResName = "border_dragon_kirin",
        scaleMultiplier = 1.68f,
        offsetYRatio = 0f
    ),
    PHOENIX_FEATHER(
        id = "phoenix_feather",
        title = "Phoenix Feather",
        description = "Bulu Sayap Feniks Merah Muda, Mutiara & Teratai Mekar",
        drawableResName = "border_phoenix_feather",
        scaleMultiplier = 1.68f,
        offsetYRatio = 0f
    ),
    VOID_RAVEN(
        id = "void_raven",
        title = "Void Raven Wings",
        description = "Sayap Gagak Hitam Legam, Permata Rubi & Bola Bayangan Ungu",
        drawableResName = "border_void_raven",
        scaleMultiplier = 1.68f,
        offsetYRatio = 0f
    ),
    CLOCKWORK_STAR(
        id = "clockwork_star",
        title = "Clockwork Star",
        description = "Kompas Mekanikal Merah Menyala & Roda Bintang Berputar",
        drawableResName = "border_clockwork_star",
        scaleMultiplier = 1.68f,
        offsetYRatio = 0f
    ),
    COSMIC_NEBULA(
        id = "cosmic_nebula",
        title = "Cosmic Nebula",
        description = "Orbit Planet Kristal Ungu, Cincin Galaksi & Bintang Kejora",
        drawableResName = "border_cosmic_nebula",
        scaleMultiplier = 1.68f,
        offsetYRatio = 0f
    );

    companion object {
        fun fromId(id: String?): MasterBorderStyle {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: ROYAL_CROWN
        }

        fun fromIdOrNull(id: String?): MasterBorderStyle? {
            if (id.isNullOrBlank() || id.equals("none", ignoreCase = true) || id.equals("null", ignoreCase = true)) {
                return null
            }
            return entries.find { it.id.equals(id, ignoreCase = true) }
        }
    }
}

private val Context.borderDataStore by preferencesDataStore("master_border_prefs")

@Singleton
class BorderPreferenceManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private val SELECTED_BORDER_KEY = stringPreferencesKey("selected_master_border")
    }

    val selectedBorder: Flow<MasterBorderStyle> = context.borderDataStore.data.map { prefs ->
        MasterBorderStyle.fromId(prefs[SELECTED_BORDER_KEY])
    }

    suspend fun saveSelectedBorder(style: MasterBorderStyle) {
        val isVip = VipSubscriptionManager(context).isVip.first()
        if (!isVip) return

        context.borderDataStore.edit { prefs ->
            prefs[SELECTED_BORDER_KEY] = style.id
        }

        // Sinkronkan pilihan border ke server Supabase user_stats agar unik per-akun
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching {
                val nameManager = NamePreferenceManager(context)
                val userId = com.valora.icebeats.utils.IceBeatsStatsCloudSync.resolveStableUserId(context, nameManager)
                val email = nameManager.accountEmail.first().trim()
                val anonKey = com.valora.icebeats.supabase.SupabaseConfig.SUPABASE_ANON_KEY
                val baseUrl = com.valora.icebeats.supabase.SupabaseConfig.SUPABASE_URL
                val authManager = com.valora.icebeats.supabase.SupabaseAuthManager.getInstance(context)
                val token = authManager.accessToken ?: anonKey

                val targetUrl = if (email.isNotBlank()) {
                    "$baseUrl/rest/v1/user_stats?or=(id.eq.$userId,email.eq.$email)"
                } else {
                    "$baseUrl/rest/v1/user_stats?id=eq.$userId"
                }

                val bodyJson = org.json.JSONObject().apply {
                    put("border_style", style.id)
                }

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = bodyJson.toString().toRequestBody(mediaType)

                val request = okhttp3.Request.Builder()
                    .url(targetUrl)
                    .header("apikey", anonKey)
                    .header("Authorization", "Bearer $token")
                    .header("Content-Type", "application/json")
                    .patch(requestBody)
                    .build()

                okhttp3.OkHttpClient().newCall(request).execute().close()
            }
        }
    }
}

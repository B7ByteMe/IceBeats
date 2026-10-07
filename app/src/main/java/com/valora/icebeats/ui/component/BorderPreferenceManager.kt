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
import javax.inject.Inject
import javax.inject.Singleton

enum class MasterBorderStyle(
    val id: String,
    val title: String,
    val description: String,
    val drawableResName: String,
    val scaleMultiplier: Float = 2.15f,
    val offsetYRatio: Float = 0f
) {
    ROYAL_CROWN(
        id = "royal_crown",
        title = "Royal Crown",
        description = "Mahkota Emas Mewah, Sayap Hitam & Permata",
        drawableResName = "border_royal_crown",
        scaleMultiplier = 2.15f,
        offsetYRatio = 0f
    ),
    CRIMSON_WING(
        id = "crimson_wing",
        title = "Crimson Wings",
        description = "Sayap Emas Elegan & Kristal Rubi Merah",
        drawableResName = "border_crimson_wing",
        scaleMultiplier = 2.15f,
        offsetYRatio = 0f
    ),
    FIRE_FLAME(
        id = "fire_flame",
        title = "Fire Flame Ring",
        description = "Cincin Api Berputar Khas Elemen Membara",
        drawableResName = "border_fire_flame",
        scaleMultiplier = 2.20f,
        offsetYRatio = 0f
    ),
    GOLDEN_SHIELD(
        id = "golden_shield",
        title = "Golden Champion",
        description = "Tameng Sayap Kejuaraan Emas Gagah",
        drawableResName = "border_golden_shield",
        scaleMultiplier = 2.15f,
        offsetYRatio = 0f
    );

    companion object {
        fun fromId(id: String?): MasterBorderStyle {
            return entries.find { it.id == id } ?: ROYAL_CROWN
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

                val mediaType = okhttp3.MediaType.Companion.toMediaType("application/json; charset=utf-8")
                val requestBody = okhttp3.RequestBody.Companion.toRequestBody(bodyJson.toString(), mediaType)

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

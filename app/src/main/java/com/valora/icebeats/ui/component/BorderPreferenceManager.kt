package com.valora.icebeats.ui.component

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class MasterBorderStyle(
    val id: String,
    val title: String,
    val description: String,
    val drawableResName: String,
    val scaleMultiplier: Float = 1.90f,
    val offsetYRatio: Float = 0f
) {
    ROYAL_CROWN(
        id = "royal_crown",
        title = "Royal Crown",
        description = "Mahkota Emas Mewah, Sayap Hitam & Permata",
        drawableResName = "border_royal_crown",
        scaleMultiplier = 1.95f,
        offsetYRatio = 0.005f
    ),
    CRIMSON_WING(
        id = "crimson_wing",
        title = "Crimson Wings",
        description = "Sayap Emas Elegan & Kristal Rubi Merah",
        drawableResName = "border_crimson_wing",
        scaleMultiplier = 1.88f,
        offsetYRatio = -0.015f
    ),
    FIRE_FLAME(
        id = "fire_flame",
        title = "Fire Flame Ring",
        description = "Cincin Api Berputar Khas Elemen Membara",
        drawableResName = "border_fire_flame",
        scaleMultiplier = 1.76f,
        offsetYRatio = -0.015f
    ),
    GOLDEN_SHIELD(
        id = "golden_shield",
        title = "Golden Champion",
        description = "Tameng Sayap Kejuaraan Emas Gagah",
        drawableResName = "border_golden_shield",
        scaleMultiplier = 1.88f,
        offsetYRatio = 0.01f
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
    }
}

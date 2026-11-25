package com.valora.icebeats.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaCodecList
import android.media.audiofx.AudioEffect
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import java.util.Locale

/**
 * Device audio codec inspection and system Dolby Atmos panel integration.
 */
object DeviceCodecs {

    private const val TAG = "DeviceCodecs"

    private val DOLBY_MIMES = listOf("audio/eac3-joc", "audio/eac3")

    @Volatile
    private var probed: Boolean? = null

    /**
     * Whether an E-AC-3 (JOC) or E-AC-3 stream finds a hardware/system decoder on this device.
     */
    val playsDolbyAtmos: Boolean
        get() = probed ?: probe().also { probed = it }

    fun isDolbyAtmosMime(mimeType: String?): Boolean {
        if (mimeType == null) return false
        val lower = mimeType.lowercase(Locale.ROOT)
        return DOLBY_MIMES.any { it in lower } || lower.contains("eac3") || lower.contains("dolby")
    }

    fun isDolbyAtmosFormat(format: Format?): Boolean {
        if (format == null) return false
        return isDolbyAtmosMime(format.sampleMimeType) || isDolbyAtmosMime(format.containerMimeType)
    }

    private fun probe(): Boolean {
        val viaMedia3 = media3Decoders()
        if (viaMedia3 != null) {
            Log.d(TAG, "Dolby Atmos decoders (Media3): ${viaMedia3.ifEmpty { listOf("none") }}")
            return viaMedia3.isNotEmpty()
        }
        return runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { info ->
                !info.isEncoder && info.supportedTypes.any { type ->
                    val lower = type.lowercase(Locale.ROOT)
                    DOLBY_MIMES.any { it == lower }
                }
            }.map { it.name }
        }.onSuccess {
            Log.d(TAG, "Dolby Atmos decoders (platform): ${it.ifEmpty { listOf("none") }}")
        }.map {
            it.isNotEmpty()
        }.getOrElse { error ->
            Log.w(TAG, "Could not inspect codec list; assuming Dolby Atmos playback available: ${error.message}")
            true
        }
    }

    @UnstableApi
    private fun media3Decoders(): List<String>? = runCatching {
        DOLBY_MIMES.flatMap { MediaCodecUtil.getDecoderInfos(it, false, false) }
            .map { it.name }
            .distinct()
    }.getOrElse {
        Log.w(TAG, "Media3 could not enumerate Dolby decoders: ${it.message}")
        null
    }

    /**
     * Checks if the host device has an OEM Dolby Atmos or spatial sound configuration panel.
     */
    fun hasSystemDolbyAtmos(context: Context): Boolean {
        val pm = context.packageManager
        val intents = getKnownDolbyIntents(context, 0)
        return intents.any { intent ->
            try {
                intent.resolveActivity(pm) != null
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * Attempts to open the OEM Dolby Atmos settings panel, system audio effect panel,
     * or general sound settings.
     */
    fun openDolbyAtmosSettings(context: Context, audioSessionId: Int = 0): Boolean {
        val pm = context.packageManager
        val candidateIntents = getKnownDolbyIntents(context, audioSessionId)

        for (intent in candidateIntents) {
            try {
                if (intent.resolveActivity(pm) != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to launch intent $intent: ${e.message}")
            }
        }

        // Fallback: system audio settings
        return runCatching {
            val fallback = Intent(Settings.ACTION_SOUND_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
            true
        }.getOrDefault(false)
    }

    private fun getKnownDolbyIntents(context: Context, audioSessionId: Int): List<Intent> {
        val intents = mutableListOf<Intent>()

        // 1. Standard Android AudioEffect panel
        intents.add(
            Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                if (audioSessionId > 0) putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            }
        )

        // 2. Samsung SoundAlive / Dolby Atmos
        intents.add(Intent("com.samsung.android.settings.sound.DolbyAtmosActivity"))
        intents.add(Intent().setComponent(ComponentName("com.android.settings", "com.samsung.android.settings.sound.DolbyAtmosActivity")))
        intents.add(Intent().setComponent(ComponentName("com.sec.android.app.soundalive", "com.sec.android.app.soundalive.activity.SoundAliveMainActivity")))

        // 3. Motorola / Lenovo Dolby
        intents.add(Intent().setComponent(ComponentName("com.motorola.dolby.dolbyui", "com.motorola.dolby.dolbyui.MainActivity")))
        intents.add(Intent().setComponent(ComponentName("com.lenovo.dolby.dolbyui", "com.lenovo.dolby.dolbyui.MainActivity")))

        // 4. Xiaomi / HyperOS / MIUI
        intents.add(Intent().setComponent(ComponentName("com.miui.player", "com.miui.player.ui.EqualizerActivity")))
        intents.add(Intent("miui.intent.action.DOLBY_ATMOS"))

        // 5. OnePlus / Oppo / Realme Dirac & Dolby
        intents.add(Intent("com.oplus.audio.effect.DOLBY"))
        intents.add(Intent().setComponent(ComponentName("com.oppo.music", "com.oppo.music.audioeffect.DolbyActivity")))

        // 6. Generic Dolby DAX UI
        intents.add(Intent().setComponent(ComponentName("com.dolby.daxappui", "com.dolby.daxappui.MainActivity")))
        intents.add(Intent("com.dolby.intent.action.DAXAPPUI_MAIN"))

        // 7. Sony Sound Enhancement
        intents.add(Intent().setComponent(ComponentName("com.sonyericsson.soundenhancement", "com.sonyericsson.soundenhancement.SoundEnhancementActivity")))

        return intents
    }
}

package com.valora.icebeats.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import timber.log.Timber
import java.nio.ByteBuffer

/**
 * Simultaneous Multi-Output Audio Processor for IceBeats.
 *
 * Taps into the decoded PCM audio stream in real-time and mirrors the playback
 * to a secondary hardware audio sink (e.g. Built-in Phone Speaker, Aux, or secondary Bluetooth)
 * while the primary stream plays through the user's primary output (e.g. Bluetooth earbuds).
 *
 * Runs with O(1) complexity, non-blocking AudioTrack writes, and independent volume control.
 */
@UnstableApi
class SimultaneousAudioProcessor(
    private val context: Context,
) : BaseAudioProcessor() {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Volatile
    var enabled: Boolean = false
        private set

    @Volatile
    var volume: Float = 1.0f
        private set

    @Volatile
    var targetDevice: AudioDeviceInfo? = null
        private set

    private var sampleRate: Int = 44100
    private var channelCount: Int = 2
    private var encoding: Int = C.ENCODING_PCM_16BIT

    private var secondaryAudioTrack: AudioTrack? = null
    private val trackLock = Any()

    override fun isActive(): Boolean = true

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding

        synchronized(trackLock) {
            recreateSecondaryTrackLocked()
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (enabled) {
            val duplicate = inputBuffer.duplicate()
            synchronized(trackLock) {
                val track = secondaryAudioTrack
                if (track != null && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    try {
                        track.write(duplicate, remaining, AudioTrack.WRITE_NON_BLOCKING)
                    } catch (e: Exception) {
                        Timber.w(e, "SimultaneousAudioProcessor: failed to write to secondary track")
                    }
                }
            }
        }

        // Pass through unmodified to the primary pipeline
        val outputBuffer = replaceOutputBuffer(remaining)
        outputBuffer.put(inputBuffer)
        outputBuffer.flip()
    }

    override fun onFlush() {
        synchronized(trackLock) {
            try {
                secondaryAudioTrack?.flush()
            } catch (_: Exception) {}
        }
    }

    override fun onReset() {
        synchronized(trackLock) {
            releaseTrackLocked()
        }
    }

    fun setEnabled(isEnabled: Boolean) {
        if (enabled == isEnabled) return
        enabled = isEnabled
        synchronized(trackLock) {
            if (isEnabled) {
                recreateSecondaryTrackLocked()
            } else {
                releaseTrackLocked()
            }
        }
    }

    fun setTargetDevice(device: AudioDeviceInfo?) {
        targetDevice = device
        synchronized(trackLock) {
            if (enabled) {
                recreateSecondaryTrackLocked()
            }
        }
    }

    fun setVolume(vol: Float) {
        volume = vol.coerceIn(0.0f, 1.0f)
        synchronized(trackLock) {
            try {
                secondaryAudioTrack?.setVolume(volume)
            } catch (_: Exception) {}
        }
    }

    fun onPlay() {
        synchronized(trackLock) {
            try {
                if (enabled && secondaryAudioTrack == null) {
                    recreateSecondaryTrackLocked()
                }
                secondaryAudioTrack?.play()
            } catch (e: Exception) {
                Timber.w(e, "SimultaneousAudioProcessor: error onPlay")
            }
        }
    }

    fun onPause() {
        synchronized(trackLock) {
            try {
                secondaryAudioTrack?.pause()
            } catch (_: Exception) {}
        }
    }

    private fun recreateSecondaryTrackLocked() {
        releaseTrackLocked()
        if (!enabled || sampleRate <= 0) return

        try {
            val device = targetDevice ?: getSpeakerDevice()

            // If routing to internal phone speaker while Bluetooth is connected,
            // USAGE_ALARM guarantees that Android AudioPolicy routes the stream to the physical speaker.
            val usage = if (device?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                AudioAttributes.USAGE_ALARM
            } else {
                AudioAttributes.USAGE_MEDIA
            }

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(usage)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val channelConfig = if (channelCount == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
            val audioEncoding = if (encoding == C.ENCODING_PCM_FLOAT) {
                AudioFormat.ENCODING_PCM_FLOAT
            } else {
                AudioFormat.ENCODING_PCM_16BIT
            }

            val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioEncoding)
            val bufferSize = (minBufferSize * 4).coerceAtLeast(8192)

            val track = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(audioEncoding)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && device != null) {
                track.setPreferredDevice(device)
            }

            track.setVolume(volume)
            track.play()
            secondaryAudioTrack = track
            Timber.i("SimultaneousAudioProcessor: secondary AudioTrack initialized on device: ${device?.productName ?: "Speaker"}")
        } catch (e: Exception) {
            Timber.e(e, "SimultaneousAudioProcessor: failed to initialize secondary AudioTrack")
            secondaryAudioTrack = null
        }
    }

    private fun releaseTrackLocked() {
        try {
            secondaryAudioTrack?.stop()
            secondaryAudioTrack?.release()
        } catch (_: Exception) {}
        secondaryAudioTrack = null
    }

    private fun getSpeakerDevice(): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            return devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        }
        return null
    }
}

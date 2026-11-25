package com.valora.icebeats.playback.automix

import android.content.Context
import android.net.Uri
import android.util.Log
import com.valora.icebeats.constants.AutomixPerformanceMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * High-performance on-device track analyzer for Automix.
 * Runs on a configurable background thread pool (Efficient, Balanced, Performance).
 */
class TrackAnalyzer(private val context: Context) {

    private val store = AnalysisStore(context)
    private val results = ConcurrentHashMap<String, TrackAnalysis>()
    private val running = ConcurrentHashMap.newKeySet<String>()

    private var currentMode = AutomixPerformanceMode.BALANCED
    private var executor = Executors.newFixedThreadPool(currentMode.threads)
    private var scope = CoroutineScope(SupervisorJob() + executor.asCoroutineDispatcher())

    fun setPerformanceMode(mode: AutomixPerformanceMode) {
        if (mode == currentMode) return
        currentMode = mode
        val oldExecutor = executor
        executor = Executors.newFixedThreadPool(mode.threads)
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + executor.asCoroutineDispatcher())
        oldExecutor.shutdown()
    }

    fun analysisFor(trackId: String?): TrackAnalysis {
        if (trackId.isNullOrBlank()) return TrackAnalysis()
        results[trackId]?.let { return it }
        val loaded = store.load(trackId)
        if (loaded != null) {
            results[trackId] = loaded
            return loaded
        }
        return TrackAnalysis(trackId = trackId)
    }

    fun isAnalysing(trackId: String?): Boolean = !trackId.isNullOrBlank() && trackId in running

    fun request(trackId: String?, uri: Uri?, durationSeconds: Double, file: File? = null) {
        if (trackId.isNullOrBlank()) return
        if (trackId in running) return

        val cached = results[trackId] ?: store.load(trackId)
        if (cached != null && cached.isUsable) {
            results[trackId] = cached
            return
        }

        if (uri == null && file == null) return
        running.add(trackId)

        scope.launch {
            try {
                val analysis = performAnalysis(trackId, uri, durationSeconds, file)
                if (analysis.isUsable) {
                    results[trackId] = analysis
                    store.save(trackId, analysis)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Automix analysis failed for $trackId", e)
            } finally {
                running.remove(trackId)
            }
        }
    }

    private fun performAnalysis(
        trackId: String,
        uri: Uri?,
        durationSeconds: Double,
        file: File?,
    ): TrackAnalysis {
        val duration = if (durationSeconds > 0) durationSeconds else 180.0
        val headDuration = min(30.0, duration * 0.4)
        val tailStart = max(0.0, duration - 35.0)

        // 1. Decode head region
        val headPcm = if (file != null && file.exists()) {
            AudioDecoder.decodeRegion(file, 0.0, headDuration)
        } else if (uri != null) {
            AudioDecoder.decodeRegion(context, uri, 0.0, headDuration)
        } else null

        // 2. Decode tail region
        val tailPcm = if (file != null && file.exists()) {
            AudioDecoder.decodeRegion(file, tailStart, duration)
        } else if (uri != null) {
            AudioDecoder.decodeRegion(context, uri, tailStart, duration)
        } else null

        var audibleStart: Double? = null
        var mixInTime: Double = 0.0
        var bpm: Double = 120.0
        var beatInterval: Double = 0.5
        var beatConfidence: Double = 0.65
        val downbeats = mutableListOf<Double>()
        val phraseBoundaries = mutableListOf<Double>()
        val energySamples = mutableListOf<EnergySample>()

        if (headPcm != null) {
            val samples = headPcm.first.samples
            val rate = headPcm.first.sampleRate
            val offset = headPcm.second
            val hopSize = (rate * 0.05).roundToInt().coerceAtLeast(1) // 50ms hop
            val headEnergies = calculateRmsEnvelope(samples, hopSize)

            // Detect audible start (silence threshold 0.006)
            for (i in headEnergies.indices) {
                if (headEnergies[i] > SILENCE_THRESHOLD) {
                    audibleStart = offset + (i * hopSize) / rate
                    break
                }
            }

            // Estimate tempo and beat grid via onset autocorrelation
            val estimated = estimateTempo(headEnergies, 0.05)
            bpm = estimated.first
            beatInterval = 60.0 / bpm
            beatConfidence = estimated.second

            val startAnchor = audibleStart ?: 0.0
            var t = startAnchor
            var beatIndex = 0
            while (t < offset + headDuration) {
                if (beatIndex % 4 == 0) {
                    downbeats.add(t)
                }
                if (beatIndex % 16 == 0) {
                    phraseBoundaries.add(t)
                }
                t += beatInterval
                beatIndex++
            }

            // Mix-in drop: look for largest positive energy jump in first 20s
            var maxJump = 0.0f
            var bestMixIn = startAnchor
            for (i in 1 until min(headEnergies.size, 400)) {
                val jump = headEnergies[i] - headEnergies[i - 1]
                if (jump > maxJump && jump > 0.03f) {
                    maxJump = jump
                    bestMixIn = offset + (i * hopSize).toDouble() / rate
                }
            }
            mixInTime = bestMixIn

            // Store head energy contour
            for (i in headEnergies.indices step 2) {
                val time = offset + (i * hopSize).toDouble() / rate
                energySamples.add(EnergySample(time, headEnergies[i].toDouble()))
            }
        }

        var contentEnd = duration
        var mixOutTime = max(0.0, duration - 8.0)

        if (tailPcm != null) {
            val samples = tailPcm.first.samples
            val rate = tailPcm.first.sampleRate
            val offset = tailPcm.second
            val hopSize = (rate * 0.05).roundToInt().coerceAtLeast(1)
            val tailEnergies = calculateRmsEnvelope(samples, hopSize)

            // Detect content end by scanning backwards
            for (i in tailEnergies.indices.reversed()) {
                if (tailEnergies[i] > SILENCE_THRESHOLD) {
                    contentEnd = offset + (i * hopSize).toDouble() / rate
                    break
                }
            }

            // Mix-out outro drop: detect start of sustained energy descent
            var bestMixOut = max(0.0, contentEnd - 10.0)
            if (tailEnergies.size > 20) {
                for (i in 0 until tailEnergies.size - 10) {
                    val curr = tailEnergies[i]
                    val future = tailEnergies[i + 8]
                    if (curr > 0.08f && future < curr * 0.5f) {
                        bestMixOut = offset + (i * hopSize).toDouble() / rate
                        break
                    }
                }
            }
            mixOutTime = bestMixOut

            for (i in tailEnergies.indices step 2) {
                val time = offset + (i * hopSize).toDouble() / rate
                energySamples.add(EnergySample(time, tailEnergies[i].toDouble()))
            }
        }

        energySamples.sortBy { it.time }

        val mixInCandidates = listOf(
            MixCandidate(mixInTime, 0.9, "intro_drop"),
            MixCandidate(audibleStart ?: 0.0, 0.5, "pickup")
        )
        val mixOutCandidates = listOf(
            MixCandidate(mixOutTime, 0.95, "outro_start"),
            MixCandidate(contentEnd, 0.75, "content_end")
        )

        return TrackAnalysis(
            status = TrackAnalysis.STATUS_READY,
            trackId = trackId,
            duration = duration,
            bpm = bpm,
            beatInterval = beatInterval,
            beatConfidence = beatConfidence,
            firstBeat = audibleStart ?: 0.0,
            downbeats = downbeats,
            phraseBoundaries = phraseBoundaries,
            audibleStartTime = audibleStart,
            contentEndTime = contentEnd,
            introEndTime = mixInTime,
            outroStartTime = mixOutTime,
            mixInTime = mixInTime,
            mixOutTime = mixOutTime,
            mixInCandidates = mixInCandidates,
            mixOutCandidates = mixOutCandidates,
            energyCurve = energySamples,
            lowEnergyCurve = energySamples,
        )
    }

    private fun calculateRmsEnvelope(samples: FloatArray, hopSize: Int): FloatArray {
        if (samples.isEmpty() || hopSize <= 0) return FloatArray(0)
        val numFrames = samples.size / hopSize
        val envelope = FloatArray(numFrames)
        for (i in 0 until numFrames) {
            var sum = 0.0
            val start = i * hopSize
            val end = min(samples.size, start + hopSize)
            for (j in start until end) {
                val s = samples[j]
                sum += s * s
            }
            envelope[i] = sqrt(sum / (end - start)).toFloat()
        }
        return envelope
    }

    /**
     * Autocorrelation-based tempo estimation from energy envelope.
     * Searches standard tempo range (60..180 BPM).
     */
    private fun estimateTempo(energies: FloatArray, hopDurationSeconds: Double): Pair<Double, Double> {
        if (energies.size < 30) return 120.0 to 0.5
        val minLag = (60.0 / 180.0 / hopDurationSeconds).roundToInt().coerceAtLeast(1)
        val maxLag = (60.0 / 60.0 / hopDurationSeconds).roundToInt().coerceAtMost(energies.size / 2)

        var bestLag = minLag
        var maxCorr = -1.0

        for (lag in minLag..maxLag) {
            var corr = 0.0
            for (i in 0 until energies.size - lag) {
                corr += energies[i] * energies[i + lag]
            }
            if (corr > maxCorr) {
                maxCorr = corr
                bestLag = lag
            }
        }

        val estimatedBpm = (60.0 / (bestLag * hopDurationSeconds)).coerceIn(60.0, 180.0)
        val confidence = if (maxCorr > 0) 0.85 else 0.55
        return estimatedBpm to confidence
    }

    fun release() {
        executor.shutdown()
    }

    companion object {
        private const val TAG = "IceBeatsTrackAnalyzer"
        private const val SILENCE_THRESHOLD = 0.007f
    }
}

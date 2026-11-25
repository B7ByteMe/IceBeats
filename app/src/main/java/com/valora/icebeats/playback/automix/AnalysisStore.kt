package com.valora.icebeats.playback.automix

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Persists track analyses to disk so each track is computed once and cached permanently.
 */
class AnalysisStore(private val context: Context) {

    private val directory by lazy { File(context.filesDir, DIRECTORY) }
    private val known = ConcurrentHashMap<String, Boolean>()

    fun load(trackId: String): TrackAnalysis? {
        if (trackId.isBlank()) return null
        if (known[trackId] == false) return null
        val file = File(directory, fileNameFor(trackId))
        if (!file.exists()) {
            known[trackId] = false
            return null
        }
        return runCatching {
            val json = JSONObject(file.readText())
            require(json.optInt("version", 0) == SCHEMA_VERSION)
            val duration = json.optDouble("duration", 0.0)
            val bpm = json.optDouble("bpm", 0.0)
            val beatInterval = json.optDouble("beatInterval", 0.0)
            val beatConfidence = json.optDouble("beatConfidence", 0.0)
            val firstBeat = json.optDouble("firstBeat", 0.0)
            val key = json.optString("key", "")
            val keyConfidence = json.optDouble("keyConfidence", 0.0)
            val audibleStartTime = json.optDouble("audibleStartTime", Double.NaN).takeIf { it.isFinite() }
            val pickupTime = json.optDouble("pickupTime", Double.NaN).takeIf { it.isFinite() }
            val introEndTime = json.optDouble("introEndTime", 0.0)
            val outroStartTime = json.optDouble("outroStartTime", 0.0)
            val contentEndTime = json.optDouble("contentEndTime", 0.0)
            val mixInTime = json.optDouble("mixInTime", 0.0)
            val mixOutTime = json.optDouble("mixOutTime", 0.0)

            val downbeats = json.optJSONArray("downbeats").toDoubleList()
            val phraseBoundaries = json.optJSONArray("phraseBoundaries").toDoubleList()
            val vocalActivityMask = json.optJSONArray("vocalActivityMask").toDoubleList()
            val vocalProbability = json.optDouble("vocalProbability", 0.0)

            val energyCurve = json.optJSONArray("energyCurve").toEnergySampleList()
            val lowEnergyCurve = json.optJSONArray("lowEnergyCurve").toEnergySampleList()
            val mixInCandidates = json.optJSONArray("mixInCandidates").toMixCandidateList()
            val mixOutCandidates = json.optJSONArray("mixOutCandidates").toMixCandidateList()

            TrackAnalysis(
                status = TrackAnalysis.STATUS_READY,
                trackId = trackId,
                duration = duration,
                bpm = bpm,
                beatInterval = beatInterval,
                beatConfidence = beatConfidence,
                firstBeat = firstBeat,
                downbeats = downbeats,
                phraseBoundaries = phraseBoundaries,
                key = key,
                keyConfidence = keyConfidence,
                audibleStartTime = audibleStartTime,
                pickupTime = pickupTime,
                introEndTime = introEndTime,
                outroStartTime = outroStartTime,
                contentEndTime = contentEndTime,
                mixInTime = mixInTime,
                mixOutTime = mixOutTime,
                mixInCandidates = mixInCandidates,
                mixOutCandidates = mixOutCandidates,
                energyCurve = energyCurve,
                lowEnergyCurve = lowEnergyCurve,
                vocalActivityMask = vocalActivityMask,
                vocalProbability = vocalProbability,
            )
        }.onFailure {
            Log.w(TAG, "Discarding corrupted analysis for $trackId", it)
            file.delete()
            known[trackId] = false
        }.getOrNull()
    }

    fun save(trackId: String, analysis: TrackAnalysis) {
        if (trackId.isBlank() || !analysis.isUsable) return
        runCatching {
            directory.mkdirs()
            val file = File(directory, fileNameFor(trackId))
            val json = JSONObject().apply {
                put("version", SCHEMA_VERSION)
                put("duration", analysis.duration)
                put("bpm", analysis.bpm)
                put("beatInterval", analysis.beatInterval)
                put("beatConfidence", analysis.beatConfidence)
                put("firstBeat", analysis.firstBeat)
                put("key", analysis.key)
                put("keyConfidence", analysis.keyConfidence)
                analysis.audibleStartTime?.let { put("audibleStartTime", it) }
                analysis.pickupTime?.let { put("pickupTime", it) }
                put("introEndTime", analysis.introEndTime)
                put("outroStartTime", analysis.outroStartTime)
                put("contentEndTime", analysis.contentEndTime)
                put("mixInTime", analysis.mixInTime)
                put("mixOutTime", analysis.mixOutTime)

                put("downbeats", JSONArray(analysis.downbeats))
                put("phraseBoundaries", JSONArray(analysis.phraseBoundaries))
                put("vocalActivityMask", JSONArray(analysis.vocalActivityMask))
                put("vocalProbability", analysis.vocalProbability)

                put("energyCurve", JSONArray().apply {
                    analysis.energyCurve.forEach { sample ->
                        put(JSONObject().apply {
                            put("t", sample.time)
                            put("e", sample.energy)
                        })
                    }
                })
                put("lowEnergyCurve", JSONArray().apply {
                    analysis.lowEnergyCurve.forEach { sample ->
                        put(JSONObject().apply {
                            put("t", sample.time)
                            put("e", sample.energy)
                        })
                    }
                })
                put("mixInCandidates", JSONArray().apply {
                    analysis.mixInCandidates.forEach { candidate ->
                        put(JSONObject().apply {
                            put("t", candidate.time)
                            put("s", candidate.score)
                            put("y", candidate.type)
                        })
                    }
                })
                put("mixOutCandidates", JSONArray().apply {
                    analysis.mixOutCandidates.forEach { candidate ->
                        put(JSONObject().apply {
                            put("t", candidate.time)
                            put("s", candidate.score)
                            put("y", candidate.type)
                        })
                    }
                })
            }
            val temporary = File(directory, file.name + ".tmp")
            temporary.writeText(json.toString())
            if (!temporary.renameTo(file)) temporary.delete()
            known[trackId] = true
        }.onFailure { Log.w(TAG, "Could not store analysis for $trackId", it) }
        prune()
    }

    private fun prune() {
        val files = directory.listFiles() ?: return
        if (files.size <= MAX_ENTRIES) return
        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_ENTRIES)
            .forEach { it.delete() }
    }

    private fun fileNameFor(trackId: String): String = "${trackId.hashCode().toUInt()}_${trackId.length}.json"

    private fun JSONArray?.toDoubleList(): List<Double> {
        if (this == null) return emptyList()
        return buildList(length()) {
            for (i in 0 until length()) {
                val value = optDouble(i, Double.NaN)
                if (value.isFinite()) add(value)
            }
        }
    }

    private fun JSONArray?.toEnergySampleList(): List<EnergySample> {
        if (this == null) return emptyList()
        return buildList(length()) {
            for (i in 0 until length()) {
                val obj = optJSONObject(i) ?: continue
                val time = obj.optDouble("t", Double.NaN)
                val energy = obj.optDouble("e", Double.NaN)
                if (time.isFinite() && energy.isFinite()) {
                    add(EnergySample(time, energy))
                }
            }
        }
    }

    private fun JSONArray?.toMixCandidateList(): List<MixCandidate> {
        if (this == null) return emptyList()
        return buildList(length()) {
            for (i in 0 until length()) {
                val obj = optJSONObject(i) ?: continue
                val time = obj.optDouble("t", Double.NaN)
                val score = obj.optDouble("s", 0.0)
                val type = obj.optString("y", "")
                if (time.isFinite()) {
                    add(MixCandidate(time, score, type))
                }
            }
        }
    }

    companion object {
        private const val TAG = "IceBeatsAnalysisStore"
        private const val DIRECTORY = "automix_analysis"
        private const val SCHEMA_VERSION = 1
        private const val MAX_ENTRIES = 500
    }
}

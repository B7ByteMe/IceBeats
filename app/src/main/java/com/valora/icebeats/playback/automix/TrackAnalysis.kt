package com.valora.icebeats.playback.automix

/**
 * Stored offline analysis for one track, in that track's own timeline seconds.
 * Contract between analyzer and transition policy.
 */
data class TrackAnalysis(
    /**
     * Blank means "no status was reported", which counts as ready. Any other
     * value must be [STATUS_READY] for the planner to trust the rest of the fields.
     */
    val status: String = "",
    /** Guards against a stale analysis being paired with the wrong track. */
    val trackId: String = "",
    val duration: Double = 0.0,

    val bpm: Double = 0.0,
    /**
     * Seconds per beat. Redundant with [bpm], but measured directly and survives
     * tempo drift better.
     */
    val beatInterval: Double = 0.0,
    /** How far the beat grid can be trusted, 0..1. */
    val beatConfidence: Double = 0.0,
    val downbeats: List<Double> = emptyList(),
    val phraseBoundaries: List<Double> = emptyList(),
    val firstBeat: Double = 0.0,

    val key: String = "",
    val keyConfidence: Double = 0.0,

    /** Where the file starts making sound, and where the first musical event lands. */
    val audibleStartTime: Double? = null,
    val pickupTime: Double? = null,
    val introEndTime: Double = 0.0,
    /** Where the content actually ends, excluding trailing silence. */
    val contentEndTime: Double = 0.0,
    val outroStartTime: Double = 0.0,

    val mixInTime: Double = 0.0,
    val mixOutTime: Double = 0.0,
    val mixInCandidates: List<MixCandidate> = emptyList(),
    val mixOutCandidates: List<MixCandidate> = emptyList(),

    val energyCurve: List<EnergySample> = emptyList(),
    /** Low-band energy, present only when band split is available. Drives the bass swap. */
    val lowEnergyCurve: List<EnergySample> = emptyList(),
    /** Per-sample vocal activity, indexed against energy curve times. */
    val vocalActivityMask: List<Double> = emptyList(),
    /** Whole-track vocal likelihood. */
    val vocalProbability: Double = 0.0,
) {
    val isUsable: Boolean get() = status == STATUS_READY && bpm > 0

    companion object {
        const val STATUS_READY = "ready"
    }
}

/** One point on an energy curve. */
data class EnergySample(val time: Double, val energy: Double)

/**
 * A candidate point for a transition to enter or leave on.
 */
data class MixCandidate(val time: Double, val score: Double, val type: String)

/** A ranked [MixCandidate], carrying the score the policy actually ordered it by. */
data class RankedMixCandidate(
    val time: Double,
    val score: Double,
    val type: String,
    val rankScore: Double,
    /** Seconds of audible music this candidate would skip by ending early. */
    val discardedMusicSeconds: Double = 0.0,
    val measured: Boolean = true,
)

/** Where a transition should end on the outgoing track, and what it costs to end there. */
data class MixOutAnchor(
    val time: Double,
    val type: String,
    val discardedMusicSeconds: Double,
)

/** The verdict on how ambitious a transition the stored evidence supports. */
data class TransitionPolicyVerdict(
    val tier: TransitionTier,
    val reasons: List<String>,
    val beatConfidence: Double,
)

/**
 * The degradation ladder. Ambition falls in explicit steps as certainty does.
 */
enum class TransitionTier {
    BEATMATCHED,
    DJ_ASSISTED,
    PLAIN_CROSSFADE,
}

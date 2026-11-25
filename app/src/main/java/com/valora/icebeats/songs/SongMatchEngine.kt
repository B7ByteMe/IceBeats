package com.valora.icebeats.songs

import com.valora.icebeats.providers.ProviderSong
import kotlin.math.abs

object SongMatchEngine {

    fun rankCandidates(identified: IdentifiedSong, candidates: List<ProviderSong>): List<ProviderSong> {
        if (candidates.isEmpty()) return emptyList()

        val normalizedTargetTitle = normalizeForMatching(identified.title)
        val normalizedTargetArtist = normalizeForMatching(identified.artist.orEmpty())

        return candidates.map { candidate ->
            val candidateTitle = normalizeForMatching(candidate.title)
            val candidateArtist = normalizeForMatching(candidate.artists.joinToString(" "))

            var score = 0.0

            // 1. Title matching (up to 50 points)
            if (candidateTitle == normalizedTargetTitle) {
                score += 50.0
            } else if (candidateTitle.contains(normalizedTargetTitle) || normalizedTargetTitle.contains(candidateTitle)) {
                score += 35.0
            } else {
                val titleOverlap = tokenOverlapScore(candidateTitle, normalizedTargetTitle)
                score += titleOverlap * 40.0
            }

            // 2. Artist matching (up to 35 points)
            if (normalizedTargetArtist.isNotBlank()) {
                if (candidateArtist.contains(normalizedTargetArtist) || normalizedTargetArtist.contains(candidateArtist)) {
                    score += 35.0
                } else {
                    val artistOverlap = tokenOverlapScore(candidateArtist, normalizedTargetArtist)
                    score += artistOverlap * 30.0
                }
            } else {
                score += 20.0 // No artist available in source
            }

            // 3. Duration matching (up to 15 points)
            if (identified.durationMs != null && identified.durationMs > 0 && candidate.duration > 0) {
                val targetSec = identified.durationMs / 1000.0
                val diff = abs(targetSec - candidate.duration)
                if (diff <= 5.0) {
                    score += 15.0
                } else if (diff <= 15.0) {
                    score += 10.0
                } else if (diff <= 30.0) {
                    score += 5.0
                }
            } else {
                score += 10.0
            }

            Pair(candidate, score)
        }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    private fun normalizeForMatching(text: String): String {
        return text.lowercase()
            .replace("[^a-z0-9\\s]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    private fun tokenOverlapScore(s1: String, s2: String): Double {
        val tokens1 = s1.split(" ").filter { it.isNotBlank() }.toSet()
        val tokens2 = s2.split(" ").filter { it.isNotBlank() }.toSet()
        if (tokens1.isEmpty() || tokens2.isEmpty()) return 0.0
        val intersection = tokens1.intersect(tokens2).size
        val union = tokens1.union(tokens2).size
        return if (union > 0) intersection.toDouble() / union.toDouble() else 0.0
    }
}

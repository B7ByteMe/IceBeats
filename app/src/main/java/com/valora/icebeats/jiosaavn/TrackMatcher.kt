package com.valora.icebeats.jiosaavn

import com.valora.icebeats.innertube.models.SongItem
import java.util.Locale
import kotlin.math.abs

/**
 * Decides whether a track from one catalogue (e.g. JioSaavn) is genuinely the same
 * recording as another (e.g. YouTube), stripping packaging and comparing core identity.
 */
object TrackMatcher {

    data class Target(
        val title: String,
        val artist: String = "",
        val durationSec: Int? = null,
        val album: String? = null,
        val isExplicit: Boolean? = null,
        val isVideo: Boolean = false,
    )

    fun queries(title: String, artist: String = ""): List<String> {
        val cleanTitle = searchableTitle(title, artist)
        if (cleanTitle.isBlank()) return emptyList()
        val primary = primaryArtist(artist)
        if (primary.isBlank()) return listOf(cleanTitle)
        return listOf("$cleanTitle $primary", cleanTitle)
    }

    fun searchableTitle(title: String, artist: String = ""): String =
        parseTitle(title, artist).let { (it.words + it.versions).joinToString(" ") }

    internal fun primaryArtist(artist: String): String =
        artist.lowercase(Locale.ROOT).split(ARTIST_SEPARATORS).firstOrNull()?.trim().orEmpty()

    fun matches(candidate: SongItem, targetTitle: String, targetArtist: String = "", targetDuration: Int? = null): Boolean =
        score(candidate, Target(targetTitle, targetArtist, targetDuration)) != null

    /**
     * Checks if a JioSaavn search result accurately matches a user's search query.
     * Prevents loose/irrelevant results from polluting search.
     */
    fun matchesQuery(candidate: SongItem, query: String): Boolean {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isBlank()) return false
        val candidateTitle = candidate.title.lowercase(Locale.ROOT)
        val candidateArtists = candidate.artists.joinToString(" ") { it.name.lowercase(Locale.ROOT) }

        // If candidate title contains the query, or query contains candidate's core title
        val parsedTarget = parseTitle(q, "")
        val parsedCandidate = parseTitle(candidateTitle, candidateArtists)

        if (parsedTarget.core.isNotEmpty() && parsedCandidate.core.isNotEmpty()) {
            if (parsedCandidate.core == parsedTarget.core ||
                parsedCandidate.core.contains(parsedTarget.core) ||
                parsedTarget.core.contains(parsedCandidate.core)
            ) {
                return true
            }
        }

        // Check if query words appear in candidate title and artists
        val queryWords = q.split(WORD_SPLIT).filter { it.length > 1 && it !in NOISE_WORDS }
        if (queryWords.isEmpty()) return true
        val matchedWords = queryWords.count { word ->
            candidateTitle.contains(word) || candidateArtists.contains(word)
        }
        return (matchedWords.toFloat() / queryWords.size) >= 0.6f
    }

    fun best(candidates: List<SongItem>, targetTitle: String, targetArtist: String = "", targetDuration: Int? = null): SongItem? =
        ranked(candidates, Target(targetTitle, targetArtist, targetDuration)).firstOrNull()

    fun ranked(candidates: List<SongItem>, target: Target): List<SongItem> =
        candidates
            .mapNotNull { candidate -> score(candidate, target)?.let { candidate to it } }
            .sortedByDescending { it.second }
            .map { it.first }

    fun score(candidate: SongItem, target: Target): Int? {
        val candidateArtists = candidate.artists.joinToString(", ") { it.name }
        val wanted = parseTitle(target.title, target.artist)
        val got = parseTitle(candidate.title, candidateArtists)
        if (wanted.core.isEmpty() || got.core.isEmpty()) return null
        if (wanted.core != got.core) return null
        if (wanted.versions != got.versions) return null

        val creditedArtist = artistScore(target.artist, candidateArtists)
        val duration = durationScore(
            target.durationSec,
            candidate.duration,
            allowVideoDrift = creditedArtist != null,
        ) ?: return null

        val artist = creditedArtist
            ?: CREDITS_DISAGREE.takeIf {
                !target.isVideo && withinSeconds(candidate.duration, target.durationSec, CREDIT_OVERRIDE_SEC)
            }
            ?: return null

        return BASE + artist + duration + contextScore(wanted, got)
    }

    fun withinSeconds(candidateDur: Int?, targetDur: Int?, seconds: Int): Boolean {
        val wanted = targetDur ?: return false
        val got = candidateDur ?: return false
        return abs(wanted - got) <= seconds
    }

    internal data class TitleParts(
        val words: List<String>,
        val core: String,
        val versions: Set<String>,
        val context: Set<String>,
    )

    internal fun parseTitle(raw: String, artist: String = ""): TitleParts {
        val versions = sortedSetOf<String>()
        val context = mutableSetOf<String>()
        var text = raw.lowercase(Locale.ROOT).replace("&", " and ")

        repeat(BRACKET_PASSES) {
            if (!BRACKETED.containsMatchIn(text)) return@repeat
            text = BRACKETED.replace(text) { match ->
                classify(match.groupValues[1], versions, context)
                " "
            }
        }
        text.indexOfFirst { it == '(' || it == '[' }.takeIf { it >= 0 }?.let { open ->
            classify(text.substring(open), versions, context)
            text = text.substring(0, open)
        }

        repeat(DASH_PASSES) {
            val dash = DASH.find(text) ?: return@repeat
            val head = text.substring(0, dash.range.first)
            val tail = text.substring(dash.range.last + 1)
            text = if (isArtistName(head, artist)) {
                classify(head, versions, context)
                tail
            } else {
                classify(tail, versions, context)
                head
            }
        }

        text = text.replace(FEATURING, " ")

        var words = text.split(WORD_SPLIT)
            .map { it.replace(NON_ALNUM, "") }
            .filter { it.isNotEmpty() && it !in JOINING_WORDS }
        while (words.size > 1 && words.last() in TRAILING_NOISE) {
            words = words.dropLast(1)
        }

        return TitleParts(
            words = words,
            core = words.joinToString(""),
            versions = versions,
            context = context,
        )
    }

    private fun classify(
        segment: String,
        versions: MutableSet<String>,
        context: MutableSet<String>,
    ) {
        val words = segment.split(WORD_SPLIT)
            .map { it.replace(NON_ALNUM, "") }
            .filter { it.isNotEmpty() }
        if (words.isEmpty()) return
        if (words.joinToString("") in NEUTRAL_SEGMENTS) return
        val marks = words.filter { it in VERSION_WORDS }
        if (marks.isNotEmpty()) {
            versions += marks
            return
        }
        context += words.filter { it.length > 2 && it !in NOISE_WORDS }
    }

    private fun isArtistName(text: String, artist: String): Boolean {
        if (artist.isBlank()) return false
        val words = text.split(WORD_SPLIT).map { it.replace(NON_ALNUM, "") }.filter { it.isNotEmpty() }
        if (words.isEmpty()) return false
        val credited = artist.lowercase(Locale.ROOT).split(WORD_SPLIT)
            .map { it.replace(NON_ALNUM, "") }
            .filter { it.isNotEmpty() }
            .toSet()
        return words.all { it in credited }
    }

    private fun artistScore(wanted: String, got: String): Int? {
        val want = artistNames(wanted)
        val have = artistNames(got)
        if (want.isEmpty() || have.isEmpty()) return 0
        if (want == have) return ARTIST_EXACT
        if (want.any { w -> have.any { h -> sameArtist(w, h) } }) return ARTIST_SHARED
        return null
    }

    private fun artistNames(raw: String): List<String> =
        raw.lowercase(Locale.ROOT).split(ARTIST_SEPARATORS).map { it.trim() }.filter { it.isNotEmpty() }

    private fun sameArtist(a: String, b: String): Boolean {
        if (a == b) return true
        val ca = a.replace(NON_ALNUM, "")
        val cb = b.replace(NON_ALNUM, "")
        return ca.isNotEmpty() && ca == cb
    }

    private fun durationScore(wantedSec: Int?, gotSec: Int?, allowVideoDrift: Boolean): Int? {
        if (wantedSec == null || gotSec == null) return 0
        val diff = abs(wantedSec - gotSec)
        val limit = if (allowVideoDrift) VIDEO_DURATION_LIMIT_SEC else DURATION_LIMIT_SEC
        if (diff > limit) return null
        return when {
            diff <= DURATION_TIGHT_SEC -> DURATION_TIGHT
            diff <= 10 -> DURATION_LOOSE
            else -> 0
        }
    }

    private fun contextScore(wanted: TitleParts, got: TitleParts): Int =
        if (wanted.context.any { it in got.context }) CONTEXT_SHARED else 0

    private const val BASE = 100
    private const val ARTIST_EXACT = 25
    private const val ARTIST_SHARED = 10
    private const val CREDITS_DISAGREE = -30
    private const val CREDIT_OVERRIDE_SEC = 2
    private const val DURATION_TIGHT = 40
    private const val DURATION_LOOSE = 15
    private const val CONTEXT_SHARED = 20
    private const val DURATION_TIGHT_SEC = 3
    private const val DURATION_LIMIT_SEC = 30
    private const val VIDEO_DURATION_LIMIT_SEC = 90
    private const val BRACKET_PASSES = 3
    private const val DASH_PASSES = 3

    private val BRACKETED = Regex("""[(\[]([^()\[\]]*)[)\]]""")
    private val DASH = Regex("""\s+[-–—|]+\s+""")
    private val FEATURING = Regex("""\b(feat|ft|featuring|with)\b.*""")
    private val WORD_SPLIT = Regex("""[\s.·]+""")
    private val NON_ALNUM = Regex("""[^a-z0-9]""")
    private val ARTIST_SEPARATORS =
        Regex("""\s*(?:[,&/;·|]|\band\b|\bx\b|\bvs\.?\b|\bfeat\.?\b|\bft\.?\b|\bfeaturing\b|\bwith\b)\s*""")

    private val VERSION_WORDS = setOf(
        "remix", "remixes", "rmx", "refix", "flip", "bootleg", "mashup", "medley",
        "live", "concert", "unplugged", "acoustic", "instrumental", "karaoke",
        "vocals", "vocal", "acapella", "acappella", "backing", "stems", "stem",
        "cover", "demo", "reprise", "remake", "rework", "extended", "edit",
        "version", "mix", "dub", "vip", "session", "sessions",
        "sped", "slowed", "reverb", "nightcore", "lofi", "orchestral", "symphonic",
        "part", "pt", "chapter",
    )

    private val NEUTRAL_SEGMENTS = setOf(
        "albumversion", "originalversion", "originalmix", "singleversion",
        "radioversion", "radioedit", "stereoversion", "monoversion",
        "studioversion", "fullversion", "standardversion", "explicitversion",
        "deluxeversion", "originaltrack",
    )

    private val NOISE_WORDS = setOf(
        "official", "video", "audio", "lyrics", "lyric", "lyrical", "visualizer",
        "song", "songs", "full", "music", "the", "and", "from", "feat", "ft",
        "featuring", "with", "new", "latest", "free", "download", "remaster",
        "remastered", "explicit", "clean", "bonus", "track", "deluxe", "original",
        "album", "single", "hd", "hq", "4k", "mp3",
    )

    private val TRAILING_NOISE = setOf(
        "song", "songs", "video", "audio", "lyrics", "lyric", "lyrical",
        "official", "full", "hd", "hq", "4k", "mp3", "ost", "soundtrack",
    )

    private val JOINING_WORDS = setOf("and")
}

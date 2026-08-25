package com.amurayada.playlistTransfer

object SpotifyMapper {
    private val FEAT_PATTERN = Regex("\\(feat\\..*?\\)", RegexOption.IGNORE_CASE)
    private val FT_PATTERN = Regex("\\(ft\\..*?\\)", RegexOption.IGNORE_CASE)
    private val BRACKET_PATTERN = Regex("\\[.*?]")
    private val REMASTER_PATTERN = Regex("\\(.*?remaster.*?\\)", RegexOption.IGNORE_CASE)
    private val REMIX_PATTERN = Regex("\\(.*?remix.*?\\)", RegexOption.IGNORE_CASE)
    private val NON_ALNUM_PATTERN = Regex("[^a-z0-9\\s]", RegexOption.IGNORE_CASE)
    private val MULTI_SPACE_PATTERN = Regex("\\s+")

    private const val NORM_CACHE_MAX_SIZE = 256
    private const val EARLY_EXIT_THRESHOLD = 0.95

    private val normalizeCache = LinkedHashMap<String, String>()
    private val bigramCache = LinkedHashMap<String, Set<String>>()

    data class PrecomputedTrack(
        val normalizedTitle: String,
        val titleBigrams: Set<String>,
        val normalizedArtist: String,
        val artistBigrams: Set<String>,
        val durationMs: Int,
    )

    fun precompute(
        title: String,
        artist: String,
        durationMs: Int,
    ): PrecomputedTrack {
        val normTitle = cachedNormalize(title)
        val normArtist = cachedNormalize(artist)
        return PrecomputedTrack(
            normalizedTitle = normTitle,
            titleBigrams = cachedBigrams(normTitle),
            normalizedArtist = normArtist,
            artistBigrams = cachedBigrams(normArtist),
            durationMs = durationMs,
        )
    }

    fun matchScorePrecomputed(
        precomputed: PrecomputedTrack,
        candidateTitle: String,
        candidateArtist: String,
        candidateDurationSec: Int?,
    ): Double {
        val normCandidateTitle = cachedNormalize(candidateTitle)
        val normCandidateArtist = cachedNormalize(candidateArtist)

        val titleScore = bigramSimilarity(
            precomputed.normalizedTitle, precomputed.titleBigrams,
            normCandidateTitle, cachedBigrams(normCandidateTitle),
        )
        val artistScore = bigramSimilarity(
            precomputed.normalizedArtist, precomputed.artistBigrams,
            normCandidateArtist, cachedBigrams(normCandidateArtist),
        )

        val durationScore = durationScore(precomputed.durationMs, candidateDurationSec)
        return titleScore * 0.45 + artistScore * 0.35 + durationScore * 0.20
    }

    fun earlyExitThreshold(): Double = EARLY_EXIT_THRESHOLD

    private fun durationScore(spotifyDurationMs: Int, candidateDurationSec: Int?): Double {
        if (candidateDurationSec == null || spotifyDurationMs <= 0) return 0.5
        val diff = kotlin.math.abs(spotifyDurationMs / 1000 - candidateDurationSec)
        return when {
            diff <= 2 -> 1.0
            diff <= 5 -> 0.8
            diff <= 10 -> 0.5
            diff <= 30 -> 0.2
            else -> 0.0
        }
    }

    private fun cachedNormalize(title: String): String {
        if (normalizeCache.size > NORM_CACHE_MAX_SIZE) {
            val iterator = normalizeCache.keys.iterator()
            if (iterator.hasNext()) {
                val oldestKey = iterator.next()
                normalizeCache.remove(oldestKey)
            }
        }
        
        normalizeCache[title]?.let { return it }
        val normalized = normalizeTitle(title)
        normalizeCache[title] = normalized
        return normalized
    }

    private fun cachedBigrams(normalized: String): Set<String> {
        if (bigramCache.size > NORM_CACHE_MAX_SIZE) {
            val iterator = bigramCache.keys.iterator()
            if (iterator.hasNext()) {
                val oldestKey = iterator.next()
                bigramCache.remove(oldestKey)
            }
        }

        bigramCache[normalized]?.let { return it }
        val bigrams = if (normalized.length < 2) emptySet() else normalized.windowed(2).toSet()
        bigramCache[normalized] = bigrams
        return bigrams
    }

    private fun normalizeTitle(title: String): String {
        return title.lowercase()
            .replace(FEAT_PATTERN, "")
            .replace(FT_PATTERN, "")
            .replace(BRACKET_PATTERN, "")
            .replace(REMASTER_PATTERN, "")
            .replace(REMIX_PATTERN, "")
            .replace(NON_ALNUM_PATTERN, "")
            .replace(MULTI_SPACE_PATTERN, " ")
            .trim()
    }

    private fun bigramSimilarity(
        a: String, bigramsA: Set<String>,
        b: String, bigramsB: Set<String>,
    ): Double {
        if (a == b) return 1.0
        if (bigramsA.isEmpty() || bigramsB.isEmpty()) return 0.0
        val intersection = bigramsA.count { it in bigramsB }
        return (2.0 * intersection) / (bigramsA.size + bigramsB.size)
    }
}

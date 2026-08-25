package org.music.lyrics.parser

import com.amurayada.domain.extension.decodeHtmlEntities
import org.music.lyrics.domain.Lyrics

fun parseSyncedLyrics(data: String): Lyrics {
    val regex = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2})\\](.+)")
    val lines = data.lines()
    val linesLyrics = ArrayList<Lyrics.LyricsX.Line>()
    lines.map { line ->
        val matchResult = regex.matchEntire(line)
        if (matchResult != null) {
            val minutes = matchResult.groupValues[1].toLong()
            val seconds = matchResult.groupValues[2].toLong()
            val milliseconds = matchResult.groupValues[3].toLong()
            val timeInMillis = minutes * 60_000L + seconds * 1000L + milliseconds
            val content = (if (matchResult.groupValues[4] == " ") " ♫" else matchResult.groupValues[4]).removeRange(0, 1)
            linesLyrics.add(
                Lyrics.LyricsX.Line(
                    endTimeMs = "0",
                    startTimeMs = timeInMillis.toString(),
                    syllables = listOf(),
                    words = decodeHtmlEntities(content),
                ),
            )
        }
    }
    return Lyrics(
        lyrics =
            Lyrics.LyricsX(
                lines = linesLyrics,
                syncType = "LINE_SYNCED",
            ),
    )
}

fun parseRichSyncLyrics(data: String): Lyrics {
    // Unescape JSON string if needed (remove quotes and replace \n with actual newlines)
    val unescapedData =
        data
            .trim()
            .removePrefix("\"")
            .removeSuffix("\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")

    // Handle different line separators (Unix \n, Windows \r\n, Mac \r)
    val lines = unescapedData.lines()
    // Skip offset line if present (starts with [offset:)
    val lyricsLines =
        lines.filter { line ->
            line.isNotBlank() && !line.trim().startsWith("[offset:")
        }

    println("[parseRichSyncLyrics] Total lines: ${lines.size}, Filtered lines: ${lyricsLines.size}")
    if (lyricsLines.isNotEmpty()) {
        println("[parseRichSyncLyrics] First line sample: ${lyricsLines.first()}")
    }

    // Regex to match [MM:SS.mm] format (flexible with 1-2 digits)
    val regex = Regex("\\[(\\d{1,2}):(\\d{2})\\.(\\d{2,3})\\](.+)")
    val linesLyrics = ArrayList<Lyrics.LyricsX.Line>()

    lyricsLines.forEachIndexed { index, line ->
        val matchResult = regex.matchEntire(line.trim())
        if (matchResult != null) {
            val minutes = matchResult.groupValues[1].toLongOrNull() ?: 0L
            val seconds = matchResult.groupValues[2].toLongOrNull() ?: 0L
            val centiseconds = matchResult.groupValues[3].toLongOrNull() ?: 0L

            // Convert to milliseconds
            // If centiseconds has 3 digits (milliseconds), use directly
            // If 2 digits (centiseconds), multiply by 10
            val millisPart = if (matchResult.groupValues[3].length == 3) centiseconds else centiseconds * 10
            val timeInMillis = minutes * 60_000L + seconds * 1000L + millisPart

            // Keep the rich sync content as-is (with <MM:SS.mm> word format)
            val content = matchResult.groupValues[4].trimStart()

            if (content.isNotBlank()) {
                linesLyrics.add(
                    Lyrics.LyricsX.Line(
                        endTimeMs = "0",
                        startTimeMs = timeInMillis.toString(),
                        syllables = listOf(),
                        words = content,
                    ),
                )
            }
        } else {
            if (index < 3) { // Only log first 3 failed matches to avoid spam
                println("[parseRichSyncLyrics] Line $index failed to match: '${line.take(100)}'")
            }
        }
    }

    println("[parseRichSyncLyrics] Parsed ${linesLyrics.size} lines successfully")

    return Lyrics(
        lyrics =
            Lyrics.LyricsX(
                lines = linesLyrics,
                syncType = "RICH_SYNCED",
            ),
    )
}

/**
 * Parse TTML (Timed Text Markup Language) lyrics from BetterLyrics.
 * Supports both line-synced and word-by-word synced lyrics.
 *
 * TTML format: `<p begin="M:SS.mmm" end="M:SS.mmm">` contains `<span begin="..." end="...">word</span>`
 * If spans with timing exist → word-by-word (RICH_SYNCED)
 * If no spans → line-synced (LINE_SYNCED)
 */
fun parseTtmlLyrics(data: String): Lyrics {
    println("LrcTextParser: Starting parseTtmlLyrics, input length = ${data.length}")
    if (data.length < 500) {
        println("LrcTextParser: Input data preview: $data")
    }

    val linesLyrics = ArrayList<Lyrics.LyricsX.Line>()
    var hasWordTiming = false

    // Extract global offset from <audio lyricOffset="...">
    val offsetRegex = Regex("""<audio[^>]*lyricOffset="([^"]+)"""")
    val offsetMatch = offsetRegex.find(data)
    val globalOffsetS = offsetMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
    val globalOffsetMs = (globalOffsetS * 1000).toLong()

    // More flexible regex for <p> and <span>
    val pRegex = Regex("""<p([^>]*)>([\s\S]*?)</p>""")
    val attrRegex = Regex("""([a-zA-Z0-9_:-]+)="([^"]+)"""")
    val spanRegex = Regex("""<span([^>]*)>([\s\S]*?)</span>""")

    for (pMatch in pRegex.findAll(data)) {
        val pAttrsStr = pMatch.groupValues[1]
        val innerContent = pMatch.groupValues[2]

        val pAttrs = attrRegex.findAll(pAttrsStr).associate { it.groupValues[1] to it.groupValues[2] }
        val beginStr = pAttrs["begin"] ?: pAttrs["ttp:begin"] ?: pAttrs["ttm:begin"]
        var lineBeginMs = beginStr?.let { parseTtmlTime(it) }

        val pEndStr = pAttrs["end"] ?: pAttrs["ttp:end"] ?: pAttrs["ttm:end"]
        val lineEndMs = pEndStr?.let { parseTtmlTime(it) }

        val spans = spanRegex.findAll(innerContent).toList()

        if (lineBeginMs == null) {
            if (spans.isNotEmpty()) {
                val firstSpanAttrsStr = spans.first().groupValues[1]
                val firstSpanAttrs = attrRegex.findAll(firstSpanAttrsStr).associate { it.groupValues[1] to it.groupValues[2] }
                val firstBeginStr = firstSpanAttrs["begin"] ?: firstSpanAttrs["ttp:begin"] ?: firstSpanAttrs["ttm:begin"]
                lineBeginMs = firstBeginStr?.let { parseTtmlTime(it) } ?: 0L
            } else {
                continue // Skip line if no begin time can be found at all
            }
        }

        lineBeginMs += globalOffsetMs
        val finalLineEndMs = (lineEndMs?.plus(globalOffsetMs)) ?: (lineBeginMs + 5000L)

        if (spans.isNotEmpty()) {
            hasWordTiming = true
            var lastIndex = 0
            val mergedWords = mutableListOf<Pair<Long, String>>()
            var currentWord = StringBuilder()
            var currentBeginMs: Long? = null
            var lastSpanHadSpaceAfter = true

            for (match in spans) {
                val spaceBefore = innerContent.substring(lastIndex, match.range.first).any { it.isWhitespace() }
                
                val spanAttrsStr = match.groupValues[1]
                val spanAttrs = attrRegex.findAll(spanAttrsStr).associate { it.groupValues[1] to it.groupValues[2] }
                val spanBeginStr = spanAttrs["begin"] ?: spanAttrs["ttp:begin"] ?: spanAttrs["ttm:begin"]
                val spanBegin = spanBeginStr?.let { parseTtmlTime(it) + globalOffsetMs } ?: lineBeginMs
                
                val spanInner = match.groupValues[2].replace(Regex("<[^>]*>"), "")
                val wordText = spanInner.trim()
                
                val hasSpaceInsideAtEnd = spanInner.lastOrNull()?.isWhitespace() == true
                
                val shouldSplit = (lastSpanHadSpaceAfter || spaceBefore) && currentWord.isNotEmpty() && !currentWord.endsWith("-")
                
                if (shouldSplit) {
                    mergedWords.add(Pair(currentBeginMs ?: 0L, currentWord.toString()))
                    currentWord.clear()
                    currentBeginMs = spanBegin
                } else if (currentBeginMs == null) {
                    currentBeginMs = spanBegin
                }
                
                currentWord.append(wordText)
                lastSpanHadSpaceAfter = hasSpaceInsideAtEnd
                lastIndex = match.range.last + 1
            }

            if (currentWord.isNotEmpty()) {
                mergedWords.add(Pair(currentBeginMs ?: 0L, currentWord.toString()))
            }

            val wordParts = StringBuilder()
            for ((begin, text) in mergedWords) {
                if (text.isNotEmpty()) {
                    val beginFormatted = formatMsToLrc(begin)
                    wordParts.append("<$beginFormatted>$text ")
                }
            }
            
            val words = wordParts.toString().trimEnd()
            if (words.isNotBlank()) {
                linesLyrics.add(
                    Lyrics.LyricsX.Line(
                        startTimeMs = lineBeginMs.toString(),
                        endTimeMs = finalLineEndMs.toString(),
                        syllables = listOf(),
                        words = words,
                    ),
                )
            }
        } else {
            // No spans — extract plain text (strip any remaining tags)
            val plainText = innerContent.replace(Regex("<[^>]*>"), "").trim()
            if (plainText.isNotBlank()) {
                linesLyrics.add(
                    Lyrics.LyricsX.Line(
                        startTimeMs = lineBeginMs.toString(),
                        endTimeMs = finalLineEndMs.toString(),
                        syllables = listOf(),
                        words = plainText,
                    ),
                )
            }
        }
    }

    return Lyrics(
        lyrics =
            Lyrics.LyricsX(
                lines = linesLyrics,
                syncType = if (hasWordTiming) "RICH_SYNCED" else "LINE_SYNCED",
            ),
    )
}

/**
 * Parse TTML time format to milliseconds.
 * Supports: "M:SS.mmm", "MM:SS.mmm", "H:MM:SS.mmm", "SS.mmm", "s", "ms", "m", "h"
 */
private fun parseTtmlTime(time: String): Long {
    val t = time.trim()
    val c1 = t.indexOf(':')
    if (c1 != -1) {
        val c2 = t.lastIndexOf(':')
        val seconds = if (c1 == c2) {
            (t.substring(0, c1).toIntOrNull() ?: 0) * 60.0 + (t.substring(c1 + 1).toDoubleOrNull() ?: 0.0)
        } else {
            (t.substring(0, c1).toIntOrNull() ?: 0) * 3600.0 + (t.substring(c1 + 1, c2).toIntOrNull() ?: 0) * 60.0 + (t.substring(c2 + 1).toDoubleOrNull() ?: 0.0)
        }
        return (seconds * 1000).toLong()
    }
    if (t.endsWith("ms")) return (t.substring(0, t.length - 2).toDoubleOrNull() ?: 0.0).toLong()
    val s = if (t.endsWith("s") || t.endsWith("m") || t.endsWith("h")) t.substring(0, t.length - 1) else t
    val v = s.toDoubleOrNull() ?: 0.0
    return when {
        t.endsWith("m") -> (v * 60000.0).toLong()
        t.endsWith("h") -> (v * 3600000.0).toLong()
        else -> (v * 1000.0).toLong()
    }
}

private fun formatMsToLrc(ms: Long): String {
    val minutes = ms / 60_000L
    val seconds = (ms % 60_000L) / 1000L
    val centis = (ms % 1000L) / 10L
    val m = if (minutes < 10) "0$minutes" else "$minutes"
    val s = if (seconds < 10) "0$seconds" else "$seconds"
    val c = if (centis < 10) "0$centis" else "$centis"
    return "$m:$s.$c"
}

fun parseUnsyncedLyrics(data: String): Lyrics {
    val lines = data.lines()
    val linesLyrics = ArrayList<Lyrics.LyricsX.Line>()
    lines.map { line ->
        linesLyrics.add(
            Lyrics.LyricsX.Line(
                endTimeMs = "0",
                startTimeMs = "0",
                syllables = listOf(),
                words = line,
            ),
        )
    }
    return Lyrics(
        lyrics =
            Lyrics.LyricsX(
                lines = linesLyrics,
                syncType = "UNSYNCED",
            ),
    )
}
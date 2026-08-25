package com.amurayada.data.zarz

import com.amurayada.logger.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.amurayada.data.io.fileDir
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

private const val TAG = "ZarzClient"

// ─── Config (mirrors qobuz-web manifest.json) ───────────────────────────────
private const val BASE_URL         = "https://api.zarz.moe/v2"
private const val APP_VERSION      = "qobuz-web@1.1.0"
private const val PLATFORM         = "extension"
private const val SCHEME_LABEL     = "ZARZ-HMAC-V1"
private const val HEADER_PREFIX    = "X-Zarz-"
private const val TIME_WINDOW_SECS = 300L

private const val ENDPOINT_BOOTSTRAP = "$BASE_URL/bootstrap"
private const val ENDPOINT_EXCHANGE  = "$BASE_URL/session/exchange"
private const val ENDPOINT_TICKETS   = "$BASE_URL/tickets"
private const val ENDPOINT_DL_QBZ   = "$BASE_URL/dl/qbz"
private const val API_BASE_URL_QBZ  = "https://api.zarz.moe/v2/qbz"

// ─── Data classes ────────────────────────────────────────────────────────────
@Serializable
data class ZarzBootstrapResponse(
    @SerialName("session_id")     val sessionId: String? = null,
    @SerialName("session_secret") val sessionSecret: String? = null,
    @SerialName("expires_at")     val expiresAt: String? = null,
    @SerialName("auth_url")       val authUrl: String? = null,
    @SerialName("challenge_url")  val challengeUrl: String? = null,
    @SerialName("challenge_id")   val challengeId: String? = null,
)

@Serializable
data class ZarzExchangeResponse(
    @SerialName("session_id")     val sessionId: String? = null,
    @SerialName("session_secret") val sessionSecret: String? = null,
    @SerialName("expires_at")     val expiresAt: String? = null,
)

@Serializable
data class ZarzTicketResponse(
    @SerialName("ticket_id") val ticketId: String? = null,
    @SerialName("ticket")    val ticket: String? = null,
)

@Serializable
data class ZarzDownloadResponse(
    @SerialName("download_url")  val downloadUrl: String? = null,
    @SerialName("url")           val url: String? = null,
    @SerialName("link")          val link: String? = null,
    @SerialName("bit_depth")     val bitDepth: Int? = null,
    @SerialName("sampling_rate") val samplingRate: Double? = null,
    val data: ZarzDownloadData? = null,
    val error: String? = null,
    val detail: String? = null,
    val success: Boolean? = null,
    val message: String? = null,
)

@Serializable
data class ZarzDownloadData(
    @SerialName("download_url")  val downloadUrl: String? = null,
    @SerialName("url")           val url: String? = null,
    @SerialName("link")          val link: String? = null,
    @SerialName("bit_depth")     val bitDepth: Int? = null,
    @SerialName("sampling_rate") val samplingRate: Double? = null,
)

@Serializable
data class QobuzSearchResponse(
    val tracks: QobuzTrackList? = null,
)

@Serializable
data class QobuzTrackList(
    val items: List<QobuzTrack>? = null,
)

@Serializable
data class QobuzTrack(
    val id: Long? = null,
    val title: String? = null,
    @SerialName("maximum_bit_depth")     val maximumBitDepth: Int? = null,
    @SerialName("maximum_sampling_rate") val maximumSamplingRate: Double? = null,
    val isrc: String? = null,
    val duration: Int? = null,
    val performer: QobuzArtist? = null,
    val album: QobuzAlbum? = null,
)

@Serializable
data class QobuzArtist(val name: String? = null)

@Serializable
data class QobuzAlbum(val title: String? = null)

// ─── Session State ───────────────────────────────────────────────────────────
@Serializable
private data class SessionRecord(
    val installId: String = UUID.randomUUID().toString().replace("-", ""),
    var sessionId: String = "",
    var sessionSecret: String = "",
    var expiresAt: String = "",
)

// ─── Client ─────────────────────────────────────────────────────────────────
class ZarzClient(private val dataDir: String = fileDir()) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient = HttpClient {
        install(ContentNegotiation) { json(json) }
    }

    private val mutex = Mutex()
    private var session: SessionRecord = loadSessionFromDisk()

    fun hasValidSession(): Boolean {
        return session.sessionId.isNotEmpty() && session.sessionSecret.isNotEmpty()
    }

    fun clearSession() {
        session = session.copy(sessionId = "", sessionSecret = "", expiresAt = "")
        saveSessionToDisk(session)
        Logger.i(TAG, "Zarz session cleared")
    }

    suspend fun getVerificationUrl(): String? {
        return try {
            val installId = mutex.withLock { session.installId }
            val url = "$ENDPOINT_BOOTSTRAP?app_version=$APP_VERSION&install_id=$installId"
            val response = httpClient.get(url) {
                header("Accept", "application/json")
                header("User-Agent", "SpotiFLAC-Mobile/$APP_VERSION")
            }
            if (!response.status.isSuccess()) return null
            val boot = json.decodeFromString<ZarzBootstrapResponse>(response.bodyAsText())
            if (boot.sessionId != null && boot.sessionSecret != null && boot.expiresAt != null) {
                mutex.withLock {
                    session = session.copy(
                        sessionId = boot.sessionId,
                        sessionSecret = boot.sessionSecret,
                        expiresAt = boot.expiresAt,
                    )
                    saveSessionToDisk(session)
                }
                null // Already valid, no verification needed!
            } else {
                when {
                    boot.authUrl != null -> boot.authUrl
                    boot.challengeUrl != null -> boot.challengeUrl
                    boot.challengeId != null -> "$BASE_URL/challenge?id=${boot.challengeId}&cb=spotiflac%3A%2F%2Fsession-grant%3Fcb_version%3Dv2grant"
                    else -> null
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "getVerificationUrl error: ${e.message}")
            null
        }
    }

    // ── Persistence ─────────────────────────────────────────────────────────

    private fun sessionFile(): File {
        // Use app's persistent files directory (survives reinstall, unlike cacheDir/tmpdir)
        val dir = File(dataDir, "zarz_session")
        dir.mkdirs()
        val f = File(dir, "zarz_session_qobuz.json")
        Logger.d(TAG, "Session file path: ${f.absolutePath} (exists=${f.exists()})")
        return f
    }

    private fun loadSessionFromDisk(): SessionRecord {
        return try {
            val file = sessionFile()
            if (file.exists()) {
                json.decodeFromString<SessionRecord>(file.readText())
            } else {
                SessionRecord()
            }
        } catch (e: Exception) {
            SessionRecord()
        }
    }

    private fun saveSessionToDisk(record: SessionRecord) {
        try {
            sessionFile().writeText(json.encodeToString(SessionRecord.serializer(), record))
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to save session: ${e.message}")
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    suspend fun getLosslessUrl(title: String, artist: String): String? {
        return try {
            val qobuzTrackId = searchQobuzTrackId(title, artist) ?: return null
            Logger.i(TAG, "Fetching lossless stream from hires-taco for track: $qobuzTrackId")
            getLosslessUrlFromTaco(qobuzTrackId)
        } catch (e: Exception) {
            Logger.e(TAG, "getLosslessUrl failed: ${e.message}")
            null
        }
    }

    private suspend fun getLosslessUrlFromTaco(trackId: Long): String? {
        return try {
            val url = "https://hires-taco.onrender.com/api/download-music?track_id=$trackId&quality=27"
            val response = httpClient.get(url) {
                header("Accept", "application/json")
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            }
            if (!response.status.isSuccess()) {
                Logger.w(TAG, "Taco request failed: HTTP ${response.status.value}")
                return null
            }
            val text = response.bodyAsText()
            Logger.d(TAG, "Taco response: $text")
            val body = json.decodeFromString<TacoResponse>(text)
            if (body.success) {
                body.data?.url
            } else {
                Logger.w(TAG, "Taco error: ${body.error}")
                null
            }
        } catch (e: Exception) {
            Logger.e(TAG, "getLosslessUrlFromTaco failed: ${e.message}")
            null
        }
    }

    /**
     * Call this when a Turnstile grant callback (spotiflac://session-grant?grant=...)
     * is received to exchange it for a session.
     */
    suspend fun exchangeGrant(grant: String): Boolean {
        return try {
            val installId = mutex.withLock { session.installId }
            val body = buildJsonBody(
                "grant" to grant,
                "install_id" to installId,
                "app_version" to APP_VERSION,
                "platform" to PLATFORM,
            )
            val response = httpClient.post(ENDPOINT_EXCHANGE) {
                contentType(ContentType.Application.Json)
                header("Accept", "application/json")
                header("User-Agent", "SpotiFLAC-Mobile/$APP_VERSION")
                setBody(String(body))
            }
            if (!response.status.isSuccess()) {
                Logger.e(TAG, "Exchange failed: HTTP ${response.status.value}")
                return false
            }
            val res = json.decodeFromString<ZarzExchangeResponse>(response.bodyAsText())
            if (!res.sessionId.isNullOrBlank() && !res.sessionSecret.isNullOrBlank()) {
                mutex.withLock {
                    session = session.copy(
                        sessionId = res.sessionId,
                        sessionSecret = res.sessionSecret,
                        expiresAt = res.expiresAt ?: "",
                    )
                    saveSessionToDisk(session)
                }
                Logger.i(TAG, "Session exchange successful! Session ID: ${res.sessionId}")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Exchange error: ${e.message}")
            false
        }
    }

    // ── Qobuz search ─────────────────────────────────────────────────────────

    private suspend fun searchQobuzTrackId(title: String, artist: String): Long? {
        val query = buildSearchQuery(title, artist)
        val url = "https://www.qobuz.com/api.json/0.2/track/search?query=${encode(query)}&limit=8&app_id=712109809"
        val response = httpClient.get(url) {
            header("Accept", "application/json")
            header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
        }
        if (!response.status.isSuccess()) {
            Logger.w(TAG, "Qobuz search HTTP ${response.status}")
            return null
        }
        val body = json.decodeFromString<QobuzSearchResponse>(response.bodyAsText())
        val tracks = body.tracks?.items ?: return null
        val best = selectBestTrack(tracks, title, artist) ?: return null
        Logger.i(TAG, "Found Qobuz track: id=${best.id} title='${best.title}'")
        return best.id
    }

    private fun buildSearchQuery(title: String, artist: String): String {
        val t = normalizeSearchText(title)
        val a = normalizeSearchText(artist)
        return if (a.isNotEmpty()) "$t $a" else t
    }

    private fun selectBestTrack(
        tracks: List<QobuzTrack>,
        expectedTitle: String,
        expectedArtist: String,
    ): QobuzTrack? {
        val candidates = tracks.filter { track ->
            val titleOk  = titlesMatch(expectedTitle, track.title ?: "")
            val artistOk = expectedArtist.isBlank() ||
                artistNamesMatch(expectedArtist, track.performer?.name ?: "")
            titleOk && artistOk
        }
        return candidates.maxByOrNull { track ->
            (track.maximumBitDepth ?: 0) * 100 + (track.maximumSamplingRate ?: 0.0).toInt()
        } ?: if (candidates.isEmpty()) null else candidates.first()
    }

    // ── Download URL ──────────────────────────────────────────────────────────

    private suspend fun getDownloadUrl(trackId: Long): String? {
        ensureSession()
        val trackUrl = "https://open.qobuz.com/track/$trackId"
        val ticketId = getTicket("qbz", "track", trackUrl) ?: return null
        val body = buildJsonBody(
            "quality" to "hi-res-max",
            "upload_to_r2" to false,
            "id" to trackId.toString(),
            "type" to "track",
            "url" to trackUrl,
        )
        val response = doSignedRequest("POST", "/dl/qbz", body, mapOf("X-Zarz-Ticket" to ticketId))
        if (!response.status.isSuccess()) {
            Logger.w(TAG, "Download request failed: HTTP ${response.status.value}")
            return null
        }
        val dl = json.decodeFromString<ZarzDownloadResponse>(response.bodyAsText())
        if (dl.error != null) throw RuntimeException(dl.error)
        val url = dl.downloadUrl ?: dl.url ?: dl.link
            ?: dl.data?.downloadUrl ?: dl.data?.url ?: dl.data?.link
        if (url == null) {
            Logger.w(TAG, "No download URL in response")
        }
        return url
    }

    // ── Signed ticket ─────────────────────────────────────────────────────────

    private suspend fun getTicket(provider: String, type: String, id: String): String? {
        val resourceHash = sha256Hex("$provider:$type:${id.lowercase()}")
        val body = buildJsonBody(
            "capability" to "download_ticket",
            "provider" to provider,
            "resource_hash" to resourceHash,
        )
        val response = doSignedRequest("POST", "/tickets", body)
        if (!response.status.isSuccess()) {
            val errBody = runCatching { response.bodyAsText() }.getOrDefault("")
            Logger.w(TAG, "Ticket request failed: HTTP ${response.status.value} - body: $errBody")
            return null
        }
        val ticket = json.decodeFromString<ZarzTicketResponse>(response.bodyAsText())
        val ticketId = ticket.ticketId ?: ticket.ticket
        if (ticketId.isNullOrBlank()) {
            Logger.w(TAG, "Empty ticket response")
            return null
        }
        return ticketId
    }

    // ── ZARZ-HMAC-V1 signing ──────────────────────────────────────────────────

    private suspend fun doSignedRequest(
        method: String,
        path: String,
        bodyBytes: ByteArray,
        extraHeaders: Map<String, String> = emptyMap(),
    ): HttpResponse {
        val record = mutex.withLock { session }
        // Capture a single Instant snapshot — used for both the formatted ts header and window calc.
        // This mirrors Go: ts = Now().Format(...); parsedTs, _ = time.Parse(ts); window = parsedTs.Unix()/300
        // We skip re-parsing (which caused OffsetDateTime parse errors) because epochSecond is unaffected
        // by millisecond-level formatting — the same instant gives the same window value.
        val now    = Instant.now()
        val fmt    = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        val ts     = now.atOffset(ZoneOffset.UTC).format(fmt)
        val window = now.epochSecond / TIME_WINDOW_SECS
        // nonce = randomHex(12) in Go = 12 bytes = 24 hex chars
        val nonce    = generateNonce()
        val bodyHash = sha256Hex(bodyBytes)

        val rollingInput = "$window:${record.sessionId}"
        val rk = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(hmacSha256(record.sessionSecret.toByteArray(), rollingInput.toByteArray()))

        val fullUrl  = BASE_URL.trimEnd('/') + path
        val fullPath = if (path.startsWith("/v2")) path else "/v2" + if (path.startsWith("/")) path else "/$path"

        Logger.d(TAG, "HMAC sign | path=$fullPath ts=$ts nonce=$nonce window=$window bodyHash=${bodyHash.take(16)}...")

        val signingInput = listOf(
            SCHEME_LABEL, method, fullPath, "",
            bodyHash, ts, nonce,
            record.sessionId, APP_VERSION, PLATFORM,
        ).joinToString("\n")
        val sig = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(hmacSha256(rk.toByteArray(), signingInput.toByteArray()))

        return httpClient.post(fullUrl) {
            contentType(ContentType.Application.Json)
            header("Accept", "application/json")
            header("User-Agent", "SpotiFLAC-Mobile/$APP_VERSION")
            header("${HEADER_PREFIX}Session",    record.sessionId)
            header("${HEADER_PREFIX}Timestamp",  ts)
            header("${HEADER_PREFIX}Nonce",      nonce)
            header("${HEADER_PREFIX}Body-SHA256", bodyHash)
            header("${HEADER_PREFIX}Signature",  sig)
            header("${HEADER_PREFIX}App-Version", APP_VERSION)
            header("${HEADER_PREFIX}Platform",   PLATFORM)
            extraHeaders.forEach { (k, v) -> header(k, v) }
            setBody(String(bodyBytes))
        }
    }

    // ── Session management ────────────────────────────────────────────────────

    private suspend fun ensureSession() {
        val hasSession = mutex.withLock {
            session.sessionId.isNotEmpty() && session.sessionSecret.isNotEmpty()
        }
        if (hasSession) return
        bootstrapSession()
    }

    private suspend fun bootstrapSession() {
        Logger.i(TAG, "Bootstrapping Zarz session...")
        val installId = mutex.withLock { session.installId }
        val url = "$ENDPOINT_BOOTSTRAP?app_version=$APP_VERSION&install_id=$installId"
        val response = httpClient.get(url) {
            header("Accept", "application/json")
            header("User-Agent", "SpotiFLAC-Mobile/$APP_VERSION")
        }
        if (!response.status.isSuccess()) {
            throw RuntimeException("Bootstrap failed: HTTP ${response.status.value}")
        }
        val boot = json.decodeFromString<ZarzBootstrapResponse>(response.bodyAsText())
        if (boot.sessionId != null && boot.sessionSecret != null && boot.expiresAt != null) {
            mutex.withLock {
                session = session.copy(
                    sessionId = boot.sessionId,
                    sessionSecret = boot.sessionSecret,
                    expiresAt = boot.expiresAt,
                )
                saveSessionToDisk(session)
            }
            Logger.i(TAG, "Zarz session established directly: ${boot.sessionId}")
        } else {
            val challengeUrl = when {
                boot.authUrl != null -> boot.authUrl
                boot.challengeUrl != null -> boot.challengeUrl
                boot.challengeId != null -> "$BASE_URL/challenge?id=${boot.challengeId}&cb=spotiflac%3A%2F%2Fsession-grant%3Fcb_version%3Dv2grant"
                else -> "unknown"
            }
            Logger.w(TAG, "Zarz bootstrap requires verification challenge: $challengeUrl")
            throw RuntimeException("Zarz session verification required. Open URL in browser: $challengeUrl")
        }
    }

    // ── Crypto helpers ────────────────────────────────────────────────────────

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun sha256Hex(input: String): String = sha256Hex(input.toByteArray(Charsets.UTF_8))

    private fun sha256Hex(input: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input).joinToString("") { "%02x".format(it) }
    }

    // Go: randomHex(12) = 12 random bytes encoded as 24 lowercase hex chars
    private fun generateNonce(): String {
        val bytes = ByteArray(12)
        java.security.SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun encode(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")

    private fun buildJsonBody(vararg pairs: Pair<String, Any?>): ByteArray {
        val sb = StringBuilder("{")
        pairs.forEachIndexed { i, (k, v) ->
            if (i > 0) sb.append(",")
            sb.append("\"$k\":")
            when (v) {
                is String  -> sb.append("\"${v.replace("\"", "\\\"")}\"")
                is Boolean -> sb.append(v.toString())
                is Number  -> sb.append(v.toString())
                null       -> sb.append("null")
                else       -> sb.append("\"$v\"")
            }
        }
        sb.append("}")
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    // ── Text matching helpers ─────────────────────────────────────────────────

    private fun normalizeSearchText(value: String): String =
        value.lowercase()
            .replace("&", " and ")
            .replace(Regex("[^\\w\\s]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun titlesMatch(expected: String, found: String): Boolean {
        val a = normalizeSearchText(expected)
        val b = normalizeSearchText(found)
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true
        if (a.contains(b) || b.contains(a)) return true
        return false
    }

    private fun artistNamesMatch(expected: String, found: String): Boolean {
        val a = normalizeSearchText(expected)
        val b = normalizeSearchText(found)
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b || a.contains(b) || b.contains(a)) return true
        return false
    }
}

@Serializable
data class TacoResponse(
    val success: Boolean,
    val data: TacoData? = null,
    val error: String? = null,
)

@Serializable
data class TacoData(
    val url: String,
)

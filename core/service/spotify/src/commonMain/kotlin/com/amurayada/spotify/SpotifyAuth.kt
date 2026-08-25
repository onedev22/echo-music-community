/*
 * EchoMusic (2026)
 * © Chartreux Westia — github.com/koiverse
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.amurayada.spotify

import com.amurayada.spotify.models.SpotifyInternalToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import com.amurayada.ktorext.getEngine
import kotlin.math.floor

/**
 * Handles Spotify authentication using the web player's internal token endpoint.
 * Uses sp_dc cookies (extracted from WebView login) to obtain access tokens
 * without requiring a Spotify Developer Client ID.
 *
 * Token acquisition requires a TOTP (Time-based One-Time Password) generated
 * from a shared secret that Spotify rotates periodically. The secret and its
 * version are fetched from a community-maintained GitHub Gist.
 *
 * Reference: https://github.com/sonic-liberation/spotube-plugin-spotify
 */
object SpotifyAuth {
    private const val TOKEN_URL = "https://open.spotify.com/api/token"
    private const val SERVER_TIME_URL = "https://open.spotify.com/api/server-time"
    private const val NUANCE_GIST_URL =
        "https://gist.githubusercontent.com/sonic-liberation/22ed9c6ba463899e933427f7de1f0eef/raw/nuances.json"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    const val LOGIN_URL = "https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F"

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }
    
    private val httpClient = HttpClient(getEngine())

    @Serializable
    private data class Nuance(val s: String, val v: Int)

    @Serializable
    private data class ServerTimeResponse(val serverTime: Long)

    /**
     * Fetches an internal web-player access token using session cookies and TOTP.
     *
     * 1. Fetches the TOTP secret from the community Gist
     * 2. Gets the server time from Spotify
     * 3. Generates a 6-digit TOTP (SHA1, 30s interval)
     * 4. Calls /api/token with the TOTP and sp_dc cookie
     */
    suspend fun fetchAccessToken(
        spDc: String,
        spKey: String = "",
    ): Result<SpotifyInternalToken> = runCatching {
        val nuance = fetchNuance()
        val serverTimeSec = fetchServerTime()
        val totp = SpotifyTotp.generateTotp(nuance.s, serverTimeSec)

        val tokenUrl = buildString {
            append(TOKEN_URL)
            append("?reason=transport")
            append("&productType=web-player")
            append("&totp=$totp")
            append("&totpServer=$totp")
            append("&totpVer=${nuance.v}")
        }

        val cookieHeader = buildString {
            append("sp_dc=$spDc")
            if (spKey.isNotEmpty()) {
                append("; sp_key=$spKey")
            }
        }

        val body = withContext(Dispatchers.IO) {
            httpGet(tokenUrl, mapOf("Cookie" to cookieHeader))
        }

        val token = json.decodeFromString<SpotifyInternalToken>(body)

        if (token.isAnonymous || token.accessToken.isBlank()) {
            throw Spotify.SpotifyException(
                401,
                "Received anonymous token — sp_dc cookie is invalid or expired",
            )
        }

        token
    }

    private suspend fun fetchNuance(): Nuance = withContext(Dispatchers.IO) {
        val body = try {
            httpGet(NUANCE_GIST_URL, emptyMap())
        } catch (e: Exception) {
            throw Spotify.SpotifyException(
                503,
                "Failed to fetch TOTP secret from gist: ${e.message}",
            )
        }
        val nuances = json.decodeFromString<List<Nuance>>(body)
        nuances.maxByOrNull { it.v }
            ?: throw Spotify.SpotifyException(500, "No nuance data found in gist")
    }

    private suspend fun fetchServerTime(): Long = withContext(Dispatchers.IO) {
        val body = try {
            httpGet(SERVER_TIME_URL, emptyMap())
        } catch (e: Exception) {
            throw Spotify.SpotifyException(
                503,
                "Failed to fetch Spotify server time: ${e.message}",
            )
        }
        val response = json.decodeFromString<ServerTimeResponse>(body)
        response.serverTime
    }

    private suspend fun httpGet(urlString: String, extraHeaders: Map<String, String>): String {
        val response = httpClient.get(urlString) {
            header("User-Agent", USER_AGENT)
            header("Accept", "application/json, text/plain, */*")
            header("Accept-Language", "en")
            extraHeaders.forEach { (key, value) ->
                header(key, value)
            }
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            throw Spotify.SpotifyException(
                response.status.value,
                "HTTP ${response.status.value}: $errorBody",
            )
        }

        return response.bodyAsText()
    }
}

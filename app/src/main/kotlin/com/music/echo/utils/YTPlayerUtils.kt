

package iad1tya.echo.music.utils

import android.net.ConnectivityManager
import android.util.Log
import androidx.media3.common.PlaybackException
import com.music.innertube.NewPipeExtractor
import com.music.innertube.YouTube
import com.music.innertube.models.YouTubeClient
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_CREATOR
import iad1tya.echo.music.utils.BotDetectionMitigator
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_43_32
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_61_48
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_NO_AUTH
import com.music.innertube.models.YouTubeClient.Companion.IOS
import com.music.innertube.models.YouTubeClient.Companion.IPADOS
import com.music.innertube.models.YouTubeClient.Companion.MOBILE
import com.music.innertube.models.YouTubeClient.Companion.TVHTML5
import com.music.innertube.models.YouTubeClient.Companion.TVHTML5_SIMPLY_EMBEDDED_PLAYER
import com.music.innertube.models.YouTubeClient.Companion.WEB
import com.music.innertube.models.YouTubeClient.Companion.WEB_CREATOR
import com.music.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.music.innertube.models.response.PlayerResponse
import iad1tya.echo.music.constants.AudioQuality
import iad1tya.echo.music.utils.cipher.CipherDeobfuscator
import iad1tya.echo.music.utils.YTPlayerUtils.MAIN_CLIENT
import iad1tya.echo.music.utils.YTPlayerUtils.STREAM_FALLBACK_CLIENTS
import iad1tya.echo.music.utils.YTPlayerUtils.validateStatus
import iad1tya.echo.music.utils.potoken.PoTokenGenerator
import iad1tya.echo.music.utils.potoken.PoTokenResult
import iad1tya.echo.music.utils.sabr.EjsNTransformSolver
import iad1tya.echo.music.utils.PlaybackLogLevel
import iad1tya.echo.music.utils.PlaybackLogManager
import com.music.innertube.models.IpVersion
import okhttp3.Dns
import okhttp3.OkHttpClient
import timber.log.Timber
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.io.IOException
import kotlinx.coroutines.flow.first

object YTPlayerUtils {
    private const val logTag = "YTPlayerUtils"
    private const val TAG = "YTPlayerUtils"

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                val addresses = Dns.SYSTEM.lookup(hostname)
                return when (YouTube.ipVersion) {
                    IpVersion.IPV4 -> addresses.filter { it is Inet4Address }.ifEmpty { addresses }
                    IpVersion.IPV6 -> addresses.filter { it is Inet6Address }.ifEmpty { addresses }
                    IpVersion.AUTO -> addresses
                }
            }
        })
        .proxySelector(object : ProxySelector() {
            override fun select(uri: URI?): List<Proxy> = listOfNotNull(YouTube.proxy ?: Proxy.NO_PROXY)
            override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
                Timber.tag(TAG).e(ioe, "Proxy connection failed for URI: $uri")
            }
        })
        .proxyAuthenticator { _, response ->
            YouTube.proxyAuth?.let { auth ->
                response.request.newBuilder()
                    .header("Proxy-Authorization", auth)
                    .build()
            } ?: response.request
        }
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val poTokenGenerator = PoTokenGenerator()

    
    private val MAIN_CLIENT: YouTubeClient = ANDROID_VR_1_43_32

    
    private val METADATA_CLIENT: YouTubeClient = WEB_REMIX

    private val STREAM_FALLBACK_CLIENTS: Array<YouTubeClient> = arrayOf(
        ANDROID_VR_1_61_48,
        WEB_REMIX,
        TVHTML5_SIMPLY_EMBEDDED_PLAYER,  
        TVHTML5,
        ANDROID_CREATOR,
        IPADOS,
        ANDROID_VR_NO_AUTH,
        MOBILE,
        IOS,
        WEB,
        WEB_CREATOR
    )
    data class PlaybackData(
        val audioConfig: PlayerResponse.PlayerConfig.AudioConfig?,
        val videoDetails: PlayerResponse.VideoDetails?,
        val playbackTracking: PlayerResponse.PlaybackTracking?,
        val format: PlayerResponse.StreamingData.Format,
        val streamUrl: String,
        val streamExpiresInSeconds: Int,
    )
    
    suspend fun playerResponseForPlayback(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        context: android.content.Context? = null,
        knownArtist: String? = null,
        knownTitle: String? = null,
        knownDurationMs: Long? = null
    ): Result<PlaybackData> {

        suspend fun tryOpus(): Result<PlaybackData> {
            val firstAttempt = resolvePlaybackData(videoId, playlistId, audioQuality, connectivityManager, context, knownArtist, knownTitle)
            if (firstAttempt.isFailure && YouTube.cookie == null) {
                Timber.tag(TAG).w("Playback failed for guest. Rotating session and retrying...")
                PlaybackLogManager.log(PlaybackLogLevel.BOT, "Playback failed for guest", "Triggering bot detection mitigation (rotating guest session)")
                BotDetectionMitigator.rotateGuestSession()
                val retryResult = resolvePlaybackData(videoId, playlistId, audioQuality, connectivityManager, context, knownArtist, knownTitle)
                retryResult.onSuccess { BotDetectionMitigator.notifyPlaybackSuccess() }
                return retryResult
            }
            firstAttempt.onSuccess { BotDetectionMitigator.notifyPlaybackSuccess() }
            return firstAttempt
        }


        return tryOpus()
    }

    private suspend fun resolvePlaybackData(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        context: android.content.Context? = null,
        knownArtist: String? = null,
        knownTitle: String? = null
    ): Result<PlaybackData> = runCatching {
        Timber.tag(logTag).d("Fetching player response via core module for videoId: $videoId, playlistId: $playlistId")
        PlaybackLogManager.log(PlaybackLogLevel.INFO, "Resolving playback data via core", "Video: $videoId")

        val coreYouTube = com.amurayada.kotlinytmusicscraper.YouTube()
        val coreTriple = coreYouTube.player(
            videoId = videoId,
            playlistId = playlistId,
            noLogIn = false
        ).getOrThrow()

        val playerRes: com.amurayada.kotlinytmusicscraper.models.response.PlayerResponse = coreTriple.second

        val adaptiveFormats = playerRes.streamingData?.adaptiveFormats.orEmpty()
        val format: com.amurayada.kotlinytmusicscraper.models.response.PlayerResponse.StreamingData.Format = adaptiveFormats
            .filter { it.isAudio }
            .maxByOrNull { it.bitrate }
            ?: playerRes.streamingData?.formats?.firstOrNull()
            ?: throw Exception("No suitable stream format returned by core player")

        val streamUrl = format.url
        if (streamUrl.isNullOrEmpty()) {
            throw Exception("Core player returned empty stream URL for videoId: $videoId")
        }

        val expiresIn = playerRes.streamingData?.expiresInSeconds ?: 3600

        // Map core response models to innertube response models
        val audioConfig = playerRes.playerConfig?.audioConfig?.let {
            com.music.innertube.models.response.PlayerResponse.PlayerConfig.AudioConfig(
                loudnessDb = it.loudnessDb,
                perceptualLoudnessDb = it.perceptualLoudnessDb
            )
        }

        val videoDetails = playerRes.videoDetails?.let {
            com.music.innertube.models.response.PlayerResponse.VideoDetails(
                videoId = it.videoId,
                title = it.title,
                author = it.author,
                channelId = it.channelId,
                lengthSeconds = it.lengthSeconds ?: "0",
                musicVideoType = it.musicVideoType,
                viewCount = it.viewCount,
                thumbnail = com.music.innertube.models.Thumbnails(
                    thumbnails = it.thumbnail?.thumbnails?.map { t ->
                        com.music.innertube.models.Thumbnail(
                            url = t.url,
                            width = t.width,
                            height = t.height
                        )
                    }.orEmpty()
                )
            )
        }

        val innertubeFormat = com.music.innertube.models.response.PlayerResponse.StreamingData.Format(
            itag = format.itag,
            url = streamUrl,
            mimeType = format.mimeType,
            bitrate = format.bitrate,
            width = format.width,
            height = format.height,
            contentLength = null,
            quality = "",
            fps = null,
            qualityLabel = null,
            averageBitrate = null,
            audioQuality = null,
            approxDurationMs = null,
            audioSampleRate = null,
            audioChannels = null,
            loudnessDb = null,
            lastModified = null,
            signatureCipher = null,
            cipher = null,
            audioTrack = null
        )

        Timber.tag(logTag).d("Successfully obtained playback data via core module for videoId: $videoId")
        PlaybackData(
            audioConfig = audioConfig,
            videoDetails = videoDetails,
            playbackTracking = null,
            format = innertubeFormat,
            streamUrl = streamUrl,
            streamExpiresInSeconds = expiresIn
        )
    }.onFailure { e ->
        Timber.tag(logTag).e(e, "Playback resolution failed")
        PlaybackLogManager.log(PlaybackLogLevel.ERROR, "Playback failed", "${e::class.simpleName}: ${e.message}")
    }
    
    suspend fun playerResponseForMetadata(
        videoId: String,
        playlistId: String? = null,
    ): Result<PlayerResponse> {
        Timber.tag(logTag).d("Fetching metadata-only player response for videoId: $videoId using MAIN_CLIENT: ${MAIN_CLIENT.clientName}")
        return YouTube.player(videoId, playlistId, client = WEB_REMIX) 
            .onSuccess { Timber.tag(logTag).d("Successfully fetched metadata") }
            .onFailure { Timber.tag(logTag).e(it, "Failed to fetch metadata") }
    }

    private fun findFormat(
        playerResponse: PlayerResponse,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
    ): PlayerResponse.StreamingData.Format? {
        Timber.tag(logTag).d("Finding format with audioQuality: $audioQuality, network metered: ${connectivityManager.isActiveNetworkMetered}")

        val format = playerResponse.streamingData?.adaptiveFormats
            ?.filter { it.isAudio && it.isOriginal }
            ?.maxByOrNull {
                it.bitrate * when (audioQuality) {
                    AudioQuality.OPUS, AudioQuality.LOSSLESS -> 1
                } + (if (it.mimeType.startsWith("audio/webm")) 10240 else 0) 
            }

        if (format != null) {
            Timber.tag(logTag).d("Selected format: ${format.mimeType}, bitrate: ${format.bitrate}")
        } else {
            Timber.tag(logTag).d("No suitable audio format found")
        }

        return format
    }
    
    private fun validateStatus(url: String): Boolean {
        Timber.tag(logTag).d("Validating stream URL status")
        try {
            val requestBuilder = okhttp3.Request.Builder()
                .head()
                .url(url)
                .header("User-Agent", YouTubeClient.USER_AGENT_WEB)

            
            YouTube.cookie?.let { cookie ->
                requestBuilder.addHeader("Cookie", cookie)
            }

            val response = httpClient.newCall(requestBuilder.build()).execute()
            val isSuccessful = response.isSuccessful
            Timber.tag(logTag).d("Stream URL validation result: ${if (isSuccessful) "Success" else "Failed"} (${response.code})")
            return isSuccessful
        } catch (e: Exception) {
            Timber.tag(logTag).e(e, "Stream URL validation failed with exception")
            reportException(e)
        }
        return false
    }
    data class SignatureTimestampResult(
        val timestamp: Int?,
        val isAgeRestricted: Boolean
    )

    private fun getSignatureTimestampOrNull(videoId: String): SignatureTimestampResult {
        Timber.tag(logTag).d("Getting signature timestamp for videoId: $videoId")
        val result = NewPipeExtractor.getSignatureTimestamp(videoId)
        return result.fold(
            onSuccess = { timestamp ->
                Timber.tag(logTag).d("Signature timestamp obtained: $timestamp")
                SignatureTimestampResult(timestamp, isAgeRestricted = false)
            },
            onFailure = { error ->
                val isAgeRestricted = error.message?.contains("age-restricted", ignoreCase = true) == true ||
                    error.cause?.message?.contains("age-restricted", ignoreCase = true) == true
                if (isAgeRestricted) {
                    Timber.tag(logTag).d("Age-restricted content detected from NewPipe")
                    Log.i(TAG, "Age-restricted detected early via NewPipe: videoId=$videoId")
                } else {
                    Timber.tag(logTag).e(error, "Failed to get signature timestamp")
                    reportException(error)
                }
                SignatureTimestampResult(null, isAgeRestricted)
            }
        )
    }

    suspend fun findUrlOrNull(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        playerResponse: PlayerResponse,
        skipNewPipe: Boolean = false
    ): String? {
        Timber.tag(logTag).d("Finding stream URL for format: ${format.mimeType}, videoId: $videoId, skipNewPipe: $skipNewPipe")

        // 1. Direct URL from format (no deobfuscation needed)
        if (!format.url.isNullOrEmpty()) {
            Timber.tag(logTag).d("Using URL from format directly")
            return format.url
        }

        // 2. Try NewPipe methods FIRST (they handle cipher internally and are more reliable)
        if (!skipNewPipe) {
            // 2a. NewPipe StreamInfo.getInfo() — full stream extraction with built-in cipher handling
            Timber.tag(logTag).d("Trying NewPipe StreamInfo for URL (primary method)")
            try {
                val streamUrls = YouTube.getNewPipeStreamUrls(videoId)
                if (streamUrls.isNotEmpty()) {
                    val streamUrl = streamUrls.find { it.first == format.itag }?.second
                    if (streamUrl != null) {
                        Timber.tag(logTag).d("Stream URL obtained from NewPipe StreamInfo (matched itag)")
                        return streamUrl
                    }

                    // Try any audio stream if exact itag not found
                    val audioStream = streamUrls.find { urlPair ->
                        playerResponse.streamingData?.adaptiveFormats?.any {
                            it.itag == urlPair.first && it.isAudio
                        } == true
                    }?.second

                    if (audioStream != null) {
                        Timber.tag(logTag).d("Audio stream URL obtained from NewPipe StreamInfo (different itag)")
                        return audioStream
                    }
                }
            } catch (e: Exception) {
                Timber.tag(logTag).e(e, "NewPipe StreamInfo failed: ${e.message}")
            }

            // 2b. NewPipe per-format deobfuscation
            try {
                val deobfuscatedUrl = NewPipeExtractor.getStreamUrl(format, videoId)
                if (deobfuscatedUrl != null) {
                    Timber.tag(logTag).d("Stream URL obtained via NewPipe deobfuscation")
                    return deobfuscatedUrl
                }
            } catch (e: Exception) {
                Timber.tag(logTag).e(e, "NewPipe getStreamUrl failed: ${e.message}")
            }
        } else {
            Timber.tag(logTag).d("Skipping NewPipe methods for age-restricted content")
        }

        // 3. CipherDeobfuscator WebView as last resort fallback
        val signatureCipher = format.signatureCipher ?: format.cipher
        if (!signatureCipher.isNullOrEmpty()) {
            Timber.tag(logTag).d("Format has signatureCipher, trying CipherWebView as fallback")
            try {
                val customDeobfuscatedUrl = CipherDeobfuscator.deobfuscateStreamUrl(signatureCipher, videoId)
                if (customDeobfuscatedUrl != null) {
                    Timber.tag(logTag).d("Stream URL obtained via CipherWebView deobfuscation")
                    return customDeobfuscatedUrl
                }
            } catch (e: Exception) {
                Timber.tag(logTag).e(e, "CipherWebView deobfuscation failed: ${e.message}")
            }
            Timber.tag(logTag).d("CipherWebView deobfuscation failed")
        }

        Timber.tag(logTag).e("Failed to get stream URL")
        return null
    }

    fun forceRefreshForVideo(videoId: String) {
        Timber.tag(logTag).d("Force refreshing for videoId: $videoId")
    }
}



package com.amurayada.data

import com.amurayada.data.zarz.ZarzClient
import com.amurayada.logger.Logger

import com.amurayada.domain.manager.DataStoreManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * LosslessManager — handles FLAC / Hi-Res streaming resolution.
 * Direct lookup is bypassable via "flac_lossless_activation" user setting.
 */
class LosslessManager(
    private val zarzClient: ZarzClient = ZarzClient(),
    private val dataStoreManager: DataStoreManager
) {
    /**
     * Returns a direct FLAC/Hi-Res download URL for the given track, or null if none found.
     *
     * The call is a suspend function and must be invoked from a coroutine.
     */
    suspend fun getLosslessUrl(title: String, artist: String): String? {
        if (title.isBlank()) return null
        if (!isFlacEnabled()) return null
        
        Logger.i("LosslessManager", "Looking up lossless URL for: '$title' by '$artist'")
        val url = runCatching {
            zarzClient.getLosslessUrl(title, artist)
        }.onFailure { e ->
            Logger.e("LosslessManager", "Lossless lookup failed: ${e.message}")
        }.getOrNull()
        if (url != null) {
            Logger.i("LosslessManager", "Found lossless URL for '$title'")
        } else {
            Logger.w("LosslessManager", "No lossless URL found for '$title'")
        }
        return url
    }

    suspend fun isFlacEnabled(): Boolean {
        return dataStoreManager.getString("flac_lossless_activation").first() == "TRUE"
    }

    suspend fun getVerificationUrl(): String? = null

    suspend fun exchangeGrant(grant: String): Boolean = false

    fun hasValidSession(): Boolean = runBlocking {
        dataStoreManager.getString("flac_lossless_activation").first() == "TRUE"
    }

    fun clearSession() {
        runBlocking {
            dataStoreManager.putString("flac_lossless_activation", "FALSE")
        }
    }

    /** Kept for compatibility with non-suspend callers that only check availability. */
    fun isLossless(title: String, artist: String): Boolean = false
}

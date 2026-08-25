package com.amurayada.spotify.auth

import com.amurayada.spotify.SpotifyTotp

actual fun generateLegacyTotp(secret: String, timestamp: Long): String {
    return SpotifyTotp.generateTotp(secret, timestamp / 1000L)
}

package com.amurayada.spotify

expect object SpotifyTotp {
    fun generateTotp(secret: String, serverTimeSec: Long): String
}

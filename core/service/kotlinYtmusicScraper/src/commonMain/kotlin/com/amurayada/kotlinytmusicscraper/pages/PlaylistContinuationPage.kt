package com.amurayada.kotlinytmusicscraper.pages

import com.amurayada.kotlinytmusicscraper.models.SongItem

data class PlaylistContinuationPage(
    val songs: List<SongItem>,
    val continuation: String?,
)
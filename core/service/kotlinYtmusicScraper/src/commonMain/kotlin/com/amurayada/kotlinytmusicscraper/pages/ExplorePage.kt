package com.amurayada.kotlinytmusicscraper.pages

import com.amurayada.kotlinytmusicscraper.models.PlaylistItem
import com.amurayada.kotlinytmusicscraper.models.VideoItem

data class ExplorePage(
    val released: List<PlaylistItem>,
    val musicVideo: List<VideoItem>,
)
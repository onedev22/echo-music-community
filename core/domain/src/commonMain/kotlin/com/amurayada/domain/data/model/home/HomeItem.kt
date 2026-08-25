package com.amurayada.domain.data.model.home

import com.amurayada.domain.data.model.searchResult.songs.Thumbnail

data class HomeItem(
    val contents: List<Content?>,
    val title: String,
    val subtitle: String? = null,
    val thumbnail: List<Thumbnail>? = null,
    val channelId: String? = null,
)

data class HomeShortcut(
    val label: String,
    val color: Long? = null,
    val playlistId: String? = null,
    val browseId: String? = null,
)
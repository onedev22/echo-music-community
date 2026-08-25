package com.amurayada.domain.data.model.home.chart

import com.amurayada.domain.data.model.browse.artist.ResultPlaylist

data class ChartItemPlaylist(
    val title: String,
    val playlists: List<ResultPlaylist>,
)
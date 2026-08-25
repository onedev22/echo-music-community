package com.amurayada.domain.data.model.mood.genre

import com.amurayada.domain.data.model.searchResult.songs.Thumbnail
import com.amurayada.domain.data.type.HomeContentType

data class Content(
    val playlistBrowseId: String,
    val thumbnail: List<Thumbnail>?,
    val title: Title,
) : HomeContentType
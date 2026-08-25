package com.amurayada.data.parser

import com.amurayada.domain.data.model.searchResult.playlists.PlaylistsResult
import com.amurayada.kotlinytmusicscraper.models.GridRenderer
import com.amurayada.kotlinytmusicscraper.models.MusicTwoRowItemRenderer

internal fun parseLibraryPlaylist(input: List<GridRenderer.Item>): List<PlaylistsResult> {
    val list: MutableList<PlaylistsResult> = mutableListOf()
    if (input.isNotEmpty()) {
        for (i in input.indices) {
            input[i].musicTwoRowItemRenderer?.let {
                if (it.navigationEndpoint?.browseEndpoint?.browseId != null) {
                    list.add(
                        PlaylistsResult(
                            author =
                                it.subtitle
                                    ?.runs
                                    ?.firstOrNull()
                                    ?.text ?: "",
                            browseId = it.navigationEndpoint?.browseEndpoint?.browseId ?: "",
                            category = "",
                            itemCount = "",
                            resultType = "",
                            thumbnails =
                                it.thumbnailRenderer
                                    ?.musicThumbnailRenderer
                                    ?.thumbnail
                                    ?.thumbnails
                                    ?.toListThumbnail() ?: listOf(),
                            title =
                                it.title
                                    ?.runs
                                    ?.firstOrNull()
                                    ?.text ?: "",
                        ),
                    )
                }
            }
        }
    }
    return list
}

internal fun parseNextLibraryPlaylist(input: List<MusicTwoRowItemRenderer>): List<PlaylistsResult> =
    input.map {
        PlaylistsResult(
            author =
                it.subtitle
                    ?.runs
                    ?.firstOrNull()
                    ?.text ?: "",
            browseId = it.navigationEndpoint?.browseEndpoint?.browseId ?: "",
            category = "",
            itemCount = "",
            resultType = "",
            thumbnails =
                it.thumbnailRenderer
                    ?.musicThumbnailRenderer
                    ?.thumbnail
                    ?.thumbnails
                    ?.toListThumbnail() ?: listOf(),
            title =
                it.title
                    ?.runs
                    ?.firstOrNull()
                    ?.text ?: "",
        )
    }
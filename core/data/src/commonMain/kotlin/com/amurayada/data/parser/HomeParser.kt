package com.amurayada.data.parser

import com.amurayada.domain.data.model.home.Content
import com.amurayada.domain.data.model.home.HomeItem
import com.amurayada.domain.data.model.searchResult.songs.Album
import com.amurayada.domain.data.model.searchResult.songs.Artist
import com.amurayada.kotlinytmusicscraper.models.ArtistItem
import com.amurayada.kotlinytmusicscraper.models.MusicResponsiveListItemRenderer
import com.amurayada.kotlinytmusicscraper.models.MusicTwoRowItemRenderer
import com.amurayada.kotlinytmusicscraper.models.PlaylistItem
import com.amurayada.kotlinytmusicscraper.models.Run
import com.amurayada.kotlinytmusicscraper.models.SectionListRenderer
import com.amurayada.kotlinytmusicscraper.models.SongItem
import com.amurayada.kotlinytmusicscraper.models.Thumbnail
import com.amurayada.kotlinytmusicscraper.models.VideoItem
import com.amurayada.kotlinytmusicscraper.pages.ArtistPage
import com.amurayada.kotlinytmusicscraper.pages.ExplorePage
import com.amurayada.kotlinytmusicscraper.pages.RelatedPage
import com.amurayada.logger.Logger

internal fun parseMixedContent(
    data: List<SectionListRenderer.Content>?,
    viewString: String,
    songString: String,
): List<HomeItem> {
    val list = mutableListOf<HomeItem>()
    if (data != null) {
        for (row in data) {
            val results = row.musicDescriptionShelfRenderer
            if (results != null) {
                val title =
                    results.header
                        ?.runs
                        ?.firstOrNull()
                        ?.text ?: ""
                val content =
                    results.description.runs
                        ?.firstOrNull()
                        ?.text ?: ""
                if (title.isNotEmpty()) {
                    list.add(
                        HomeItem(
                            contents =
                                listOf(
                                    Content(
                                        album = null,
                                        artists = listOf(),
                                        description = content,
                                        isExplicit = null,
                                        playlistId = null,
                                        browseId = null,
                                        thumbnails = listOf(),
                                        title = content,
                                        videoId = null,
                                        views = null,
                                    ),
                                ),
                            title = title,
                        ),
                    )
                }
            } else {
                val results1 = row.musicCarouselShelfRenderer
                Logger.w("parse_mixed_content", results1.toString())
                val contentList = results1?.contents
                Logger.w("parse_mixed_content", results1?.contents?.size.toString())
                val title =
                    results1
                        ?.header
                        ?.musicCarouselShelfBasicHeaderRenderer
                        ?.title
                        ?.runs
                        ?.firstOrNull()
                        ?.text
                        ?: ""
                Logger.w("parse_mixed_content", title)
                if (title == "Your daily discover") {
                    Logger.w("parse_mixed_content", list.toString())
                }
                val subtitle =
                    results1
                        ?.header
                        ?.musicCarouselShelfBasicHeaderRenderer
                        ?.strapline
                        ?.runs
                        ?.firstOrNull()
                        ?.text
                val thumbnail =
                    results1
                        ?.header
                        ?.musicCarouselShelfBasicHeaderRenderer
                        ?.thumbnail
                        ?.musicThumbnailRenderer
                        ?.thumbnail
                        ?.thumbnails
                        ?.toListThumbnail()
                val artistChannelId =
                    results1
                        ?.header
                        ?.musicCarouselShelfBasicHeaderRenderer
                        ?.title
                        ?.runs
                        ?.firstOrNull()
                        ?.navigationEndpoint
                        ?.browseEndpoint
                        ?.browseId
                val listContent = mutableListOf<Content?>()
                if (!contentList.isNullOrEmpty()) {
                    for (result1 in contentList) {
                        val musicTwoRowItemRenderer = result1.musicTwoRowItemRenderer
                        if (musicTwoRowItemRenderer != null) {
                            //                        if (pageType == null) {
//                            if (result1.musicTwoRowItemRenderer!!.navigationEndpoint.watchEndpoint?.playlistId != null && result1.musicTwoRowItemRenderer!!.navigationEndpoint.watchEndpoint?.videoId == null){
//                                val content = parseWatchPlaylist(result1.musicTwoRowItemRenderer!!)
//                                listContent.add(content)
//                            }
//                            else if (result1.musicTwoRowItemRenderer!!.navigationEndpoint.watchEndpoint?.playlistId == null && result1.musicTwoRowItemRenderer!!.navigationEndpoint.watchEndpoint?.videoId != null){
//                                val content = parseSong(result1.musicTwoRowItemRenderer!!)
//                                listContent.add(content)
//                            }
//                        }
//                        else if (pageType == "MUSIC_PAGE_TYPE_ALBUM"){
//                            val content = parseAlbum(result1.musicTwoRowItemRenderer!!)
//                            listContent.add(content)
//                        }
//                        else if (pageType == "MUSIC_PAGE_TYPE_ARTIST"){
//                            val content = parseRelatedArtists(result1.musicTwoRowItemRenderer!!)
//                            listContent.add(content)
//                        }
//                        else if (pageType == "MUSIC_PAGE_TYPE_PLAYLIST") {
//                            if (result1.musicTwoRowItemRenderer!!.navigationEndpoint.browseEndpoint?.browseId?.startsWith("MPRE") == true) {
//                                val content = parseAlbum(result1.musicTwoRowItemRenderer!!)
//                                listContent.add(content)
//                            }
//                            else {
//                                val content = parsePlaylist(result1.musicTwoRowItemRenderer!!)
//                                listContent.add(content)
//                            }
//                        }
//                        when (result1.musicTwoRowItemRenderer!!.title.runs?.firstOrNull()?.navigationEndpoint?.browseEndpoint?.browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType) {
//                            "MUSIC_PAGE_TYPE_ALBUM" -> {
//                                val content = parseAlbum(result1.musicTwoRowItemRenderer!!)
//                                listContent.add(content)
//                            }
//                            "MUSIC_PAGE_TYPE_ARTIST" -> {
//                                val content = parseRelatedArtists(result1.musicTwoRowItemRenderer!!)
//                                listContent.add(content)
//                            }
//                            "MUSIC_PAGE_TYPE_PLAYLIST" -> {
//                                if (result1.musicTwoRowItemRenderer!!.navigationEndpoint.browseEndpoint?.browseId?.startsWith("MPRE") == true) {
//                                    val content = parseAlbum(result1.musicTwoRowItemRenderer!!)
//                                    listContent.add(content)
//                                }
//                                else {
//                                    val content = parsePlaylist(result1.musicTwoRowItemRenderer!!)
//                                    listContent.add(content)
//                                }
//                            }
//                            null -> {
//                                if (result1.musicTwoRowItemRenderer!!.navigationEndpoint.watchEndpoint?.playlistId != null && result1.musicTwoRowItemRenderer!!.navigationEndpoint.watchEndpoint?.videoId == null){
//                                    val content = parseWatchPlaylist(result1.musicTwoRowItemRenderer!!)
//                                    listContent.add(content)
//                                }
//                                else if (result1.musicTwoRowItemRenderer!!.navigationEndpoint.watchEndpoint?.playlistId == null && result1.musicTwoRowItemRenderer!!.navigationEndpoint.watchEndpoint?.videoId != null){
//                                    val content = parseSong(result1.musicTwoRowItemRenderer!!, context)
//                                    listContent.add(content)
//                                }
//                            }
//                        }
                            if (musicTwoRowItemRenderer.isSong) {
                                val ytItem =
                                    RelatedPage.fromMusicTwoRowItemRenderer(musicTwoRowItemRenderer, songString) as SongItem?
                                val artists =
                                    ytItem
                                        ?.artists
                                        ?.map {
                                            Artist(
                                                name = it.name,
                                                id = it.id,
                                            )
                                        }?.toMutableList()
                                if (artists?.lastOrNull()?.id == null &&
                                    artists?.lastOrNull()?.name?.contains(
                                        Regex("\\d"),
                                    ) == true
                                ) {
                                    runCatching { artists.removeAt(artists.lastIndex) }
                                        .onSuccess {
                                            Logger.i("parse_mixed_content", "Removed last artist")
                                        }.onFailure {
                                            Logger.e("parse_mixed_content", "Failed to remove last artist")
                                            it.printStackTrace()
                                        }
                                }
                                Logger.w("Song", ytItem.toString())
                                if (ytItem != null) {
                                    listContent.add(
                                        Content(
                                            album =
                                                ytItem.album?.let {
                                                    Album(
                                                        name = it.name,
                                                        id = it.id,
                                                    )
                                                },
                                            artists = artists,
                                            description = null,
                                            isExplicit = ytItem.explicit,
                                            playlistId = null,
                                            browseId = null,
                                            thumbnails =
                                                musicTwoRowItemRenderer.thumbnailRenderer
                                                    ?.musicThumbnailRenderer
                                                    ?.thumbnail
                                                    ?.thumbnails
                                                    ?.toListThumbnail()
                                                    ?: listOf(),
                                            title = ytItem.title,
                                            videoId = ytItem.id,
                                            views = null,
                                            durationSeconds = ytItem.duration,
                                            radio = null,
                                        ),
                                    )
                                }
                            } else if (musicTwoRowItemRenderer.isVideo) {
                                val ytItem =
                                    ArtistPage.fromMusicTwoRowItemRenderer(musicTwoRowItemRenderer) as VideoItem?
                                Logger.w("Video", ytItem.toString())
                                val artists =
                                    ytItem
                                        ?.artists
                                        ?.map {
                                            Artist(
                                                name = it.name,
                                                id = it.id,
                                            )
                                        }?.toMutableList()
                                if (artists?.lastOrNull()?.id == null &&
                                    artists?.lastOrNull()?.name?.contains(
                                        Regex("\\d"),
                                    ) == true
                                ) {
                                    runCatching { artists.removeAt(artists.lastIndex) }
                                        .onSuccess {
                                            Logger.i("parse_mixed_content", "Removed last artist")
                                        }.onFailure {
                                            Logger.e("parse_mixed_content", "Failed to remove last artist")
                                            it.printStackTrace()
                                        }
                                }
                                if (ytItem != null) {
                                    listContent.add(
                                        Content(
                                            album =
                                                ytItem.album?.let {
                                                    Album(
                                                        name = it.name,
                                                        id = it.id,
                                                    )
                                                },
                                            artists = artists,
                                            description = null,
                                            isExplicit = ytItem.explicit,
                                            playlistId = null,
                                            browseId = null,
                                            thumbnails =
                                                musicTwoRowItemRenderer.thumbnailRenderer
                                                    ?.musicThumbnailRenderer
                                                    ?.thumbnail
                                                    ?.thumbnails
                                                    ?.toListThumbnail()
                                                    ?: listOf(),
                                            title = ytItem.title,
                                            videoId = ytItem.id,
                                            views = ytItem.view,
                                            durationSeconds = ytItem.duration,
                                            radio = null,
                                        ),
                                    )
                                }
                            } else if (musicTwoRowItemRenderer.isArtist) {
                                val ytItem =
                                    RelatedPage.fromMusicTwoRowItemRenderer(musicTwoRowItemRenderer) as ArtistItem?
                                Logger.w("Artists", ytItem.toString())
                                if (ytItem != null) {
                                    listContent.add(
                                        Content(
                                            album = null,
                                            artists = listOf(),
                                            description = null,
                                            isExplicit = null,
                                            playlistId = null,
                                            browseId = ytItem.id,
                                            thumbnails =
                                                musicTwoRowItemRenderer.thumbnailRenderer
                                                    ?.musicThumbnailRenderer
                                                    ?.thumbnail
                                                    ?.thumbnails
                                                    ?.toListThumbnail()
                                                    ?: listOf(),
                                            title = ytItem.title,
                                            videoId = null,
                                            views = null,
                                            radio = null,
                                        ),
                                    )
                                }
                            } else if (musicTwoRowItemRenderer.isAlbum) {
                                listContent.add(
                                    Content(
                                        album =
                                            Album(
                                                id =
                                                    musicTwoRowItemRenderer.navigationEndpoint?.browseEndpoint?.browseId
                                                        ?: "",
                                                name = title,
                                            ),
                                        artists = listOf(),
                                        description = null,
                                        isExplicit = false,
                                        playlistId = null,
                                        browseId = musicTwoRowItemRenderer.navigationEndpoint?.browseEndpoint?.browseId,
                                        thumbnails =
                                            musicTwoRowItemRenderer.thumbnailRenderer
                                                ?.musicThumbnailRenderer
                                                ?.thumbnail
                                                ?.thumbnails
                                                ?.toListThumbnail()
                                                ?: listOf(),
                                        title =
                                            musicTwoRowItemRenderer.title
                                                ?.runs
                                                ?.firstOrNull()
                                                ?.text
                                                ?: "",
                                        videoId = "",
                                        views = "",
                                    ),
                                )
                            } else if (musicTwoRowItemRenderer.isPlaylist) {
                                val subtitle1 = musicTwoRowItemRenderer.subtitle
                                var description = ""
                                if (subtitle1 != null) {
                                    if (subtitle1.runs != null) {
                                        for (run in subtitle1.runs!!) {
                                            description += run.text
                                        }
                                    }
                                }
                                if (musicTwoRowItemRenderer.navigationEndpoint?.browseEndpoint?.browseId?.startsWith(
                                        "MPRE",
                                    ) == true
                                ) {
                                    listContent.add(
                                        Content(
                                            album =
                                                Album(
                                                    id =
                                                        musicTwoRowItemRenderer.navigationEndpoint?.browseEndpoint?.browseId
                                                            ?: "",
                                                    name = title,
                                                ),
                                            artists = listOf(),
                                            description = null,
                                            isExplicit = false,
                                            playlistId = null,
                                            browseId = musicTwoRowItemRenderer.navigationEndpoint?.browseEndpoint?.browseId,
                                            thumbnails =
                                                musicTwoRowItemRenderer.thumbnailRenderer
                                                    ?.musicThumbnailRenderer
                                                    ?.thumbnail
                                                    ?.thumbnails
                                                    ?.toListThumbnail()
                                                    ?: listOf(),
                                            title =
                                                musicTwoRowItemRenderer.title
                                                    ?.runs
                                                    ?.firstOrNull()?.text ?: "",
                                            videoId = "",
                                            views = "",
                                        ),
                                    )
                                } else {
                                    val ytItem1 =
                                        RelatedPage.fromMusicTwoRowItemRenderer(
                                            musicTwoRowItemRenderer,
                                        ) as PlaylistItem?
                                    ytItem1?.let { ytItem ->
                                        listContent.add(
                                            Content(
                                                album = null,
                                                artists =
                                                    listOf(
                                                        Artist(
                                                            id = ytItem.author?.id ?: "",
                                                            name = ytItem.author?.name ?: "",
                                                        ),
                                                    ),
                                                description = description,
                                                isExplicit = ytItem.explicit,
                                                playlistId = ytItem.id,
                                                browseId = ytItem.id,
                                                thumbnails =
                                                    musicTwoRowItemRenderer.thumbnailRenderer
                                                        ?.musicThumbnailRenderer
                                                        ?.thumbnail
                                                        ?.thumbnails
                                                        ?.toListThumbnail()
                                                        ?: listOf(),
                                                title = ytItem.title,
                                                videoId = null,
                                                views = null,
                                                radio = null,
                                            ),
                                        )
                                    }
                                }
                            } else {
                                continue
                            }
                        } else if (result1.musicResponsiveListItemRenderer != null) {
                            Logger.w(
                                "parse Song flat",
                                result1.musicResponsiveListItemRenderer.toString(),
                            )
                            val ytItem =
                                RelatedPage.fromMusicResponsiveListItemRenderer(result1.musicResponsiveListItemRenderer!!)
                            if (ytItem != null) {
                                val content =
                                    Content(
                                        album = ytItem.album?.let { Album(name = it.name, id = it.id) },
                                        artists =
                                            parseSongArtists(
                                                result1.musicResponsiveListItemRenderer!!,
                                                1,
                                                viewString,
                                            ) ?: listOf(),
                                        description = null,
                                        isExplicit = ytItem.explicit,
                                        playlistId = null,
                                        browseId = null,
                                        thumbnails =
                                            result1.musicResponsiveListItemRenderer!!
                                                .thumbnail
                                                ?.musicThumbnailRenderer
                                                ?.thumbnail
                                                ?.thumbnails
                                                ?.toListThumbnail()
                                                ?: listOf(),
                                        title = ytItem.title,
                                        videoId = ytItem.id,
                                        views = "",
                                        radio = null,
                                    )
                                listContent.add(content)
                            }
                        } else if (result1.musicMultiRowListItemRenderer != null) {
                            val multiRow = result1.musicMultiRowListItemRenderer ?: break
                            val content =
                                Content(
                                    description =
                                        multiRow.description
                                            ?.runs
                                            ?.firstOrNull()
                                            ?.text,
                                    thumbnails =
                                        multiRow.thumbnail
                                            ?.musicThumbnailRenderer
                                            ?.thumbnail
                                            ?.thumbnails
                                            ?.toListThumbnail()
                                            ?: listOf(),
                                    title =
                                        multiRow.title
                                            ?.runs
                                            ?.firstOrNull()
                                            ?.text ?: "",
                                    videoId = multiRow.onTap?.watchEndpoint?.videoId ?: "",
                                    album = null,
                                    artists = emptyList(),
                                    isExplicit = false,
                                    playlistId = null,
                                    browseId = null,
                                    views = null,
                                )
                            listContent.add(content)
                        } else {
                            break
                        }
                    }
                }
                if (title.isNotEmpty()) {
                    list.add(
                        HomeItem(
                            contents = listContent,
                            title = title,
                            subtitle = subtitle,
                            thumbnail = thumbnail,
                            channelId = if (artistChannelId?.contains("UC") == true) artistChannelId else null,
                        ),
                    )
                }
                Logger.w("parse_mixed_content", list.toString())
            }
        }
    }
    return list
}

internal fun parseSongFlat(
    data: MusicResponsiveListItemRenderer?,
    viewString: String,
): Content? {
    if (data?.flexColumns != null) {
        val column =
            mutableListOf<MusicResponsiveListItemRenderer.FlexColumn.MusicResponsiveListItemFlexColumnRenderer?>()
        for (i in 0..data.flexColumns.size) {
            column.add(getFlexColumnItem(data, i))
        }
        return Content(
            album =
                if (column.size > 2 &&
                    column[2] != null &&
                    column[2]
                        ?.text
                        ?.runs
                        ?.firstOrNull()
                        ?.text != null
                ) {
                    Album(
                        id =
                            column[2]
                                ?.text
                                ?.runs
                                ?.firstOrNull()
                                ?.navigationEndpoint
                                ?.browseEndpoint
                                ?.browseId!!,
                        name =
                            column[2]
                                ?.text
                                ?.runs
                                ?.firstOrNull()
                                ?.text!!,
                    )
                } else {
                    null
                },
            artists = parseSongArtists(data, 1, viewString) ?: listOf(),
            description = null,
            isExplicit = null,
            playlistId = null,
            browseId = null,
            thumbnails =
                data.thumbnail
                    ?.musicThumbnailRenderer
                    ?.thumbnail
                    ?.thumbnails
                    ?.toListThumbnail()
                    ?: listOf(),
            title =
                column[0]
                    ?.text
                    ?.runs
                    ?.firstOrNull()
                    ?.text ?: "",
            videoId =
                column[0]
                    ?.text
                    ?.runs
                    ?.firstOrNull()
                    ?.navigationEndpoint
                    ?.watchEndpoint
                    ?.videoId
                    ?: "",
            views =
                if (column.size <= 2 ||
                    column[2] == null ||
                    column[2]
                        ?.text
                        ?.runs
                        ?.firstOrNull()
                        ?.text == null
                ) {
                    column[1]
                        ?.text
                        ?.runs
                        ?.last()
                        ?.text
                        ?.split(" ")
                        ?.firstOrNull() ?: ""
                } else {
                    null
                },
        )
    } else {
        return null
    }
}

internal fun parseSongArtists(
    data: MusicResponsiveListItemRenderer,
    index: Int,
    viewString: String,
): List<Artist>? {
    val flexItem = getFlexColumnItem(data, index)
    return if (flexItem == null) {
        null
    } else {
        val runs = flexItem.text?.runs
        runs?.let { parseSongArtistsRuns(it, viewString) }
    }
}

fun getFlexColumnItem(
    data: MusicResponsiveListItemRenderer,
    index: Int,
): MusicResponsiveListItemRenderer.FlexColumn.MusicResponsiveListItemFlexColumnRenderer? =
    if (data.flexColumns.size <= index ||
        data.flexColumns[index].musicResponsiveListItemFlexColumnRenderer.text == null ||
        data.flexColumns[index]
            .musicResponsiveListItemFlexColumnRenderer.text
            ?.runs == null
    ) {
        null
    } else {
        data.flexColumns[index].musicResponsiveListItemFlexColumnRenderer
    }

internal fun parsePlaylist(
    data: MusicTwoRowItemRenderer,
    viewString: String,
): Content {
    val subtitle = data.subtitle
    var description = ""
    var count = ""
    val author: MutableList<Artist> = mutableListOf()
    val thumbnails =
        data.thumbnailRenderer
            ?.musicThumbnailRenderer
            ?.thumbnail
            ?.thumbnails
    if (subtitle != null) {
        if (subtitle.runs != null) {
            for (run in subtitle.runs!!) {
                description += run.text
            }
            if (subtitle.runs!!.size == 3) {
                if (data.subtitle!!
                        .runs
                        ?.get(2)
                        ?.text
                        ?.split(" ")
                        ?.firstOrNull() != null
                ) {
                    count +=
                        data.subtitle!!
                            .runs
                            ?.get(2)
                            ?.text
                            ?.split(" ")
                            ?.firstOrNull()
                }
                author.addAll(parseSongArtistsRuns(subtitle.runs!!.take(1), viewString))
            }
        }
    }
    Logger.w("parse_playlist", description)
    return Content(
        album = null,
        artists = author,
        description = description,
        isExplicit = false,
        playlistId =
            data.title
                ?.runs
                ?.firstOrNull()
                ?.navigationEndpoint
                ?.browseEndpoint
                ?.browseId,
        browseId = null,
        thumbnails = thumbnails?.toListThumbnail() ?: listOf(),
        title =
            data.title
                ?.runs
                ?.firstOrNull()
                ?.text ?: "",
        videoId = null,
        views = null,
    )
}

internal fun parseSongArtistsRuns(
    runs: List<Run>,
    viewString: String,
): List<Artist> {
    val artists = mutableListOf<Artist>()
    for (i in 0 until runs.size step 2) {
        if (runs[i].navigationEndpoint?.browseEndpoint?.browseId != null) {
            artists.add(
                Artist(
                    name = runs[i].text,
                    id = runs[i].navigationEndpoint?.browseEndpoint?.browseId,
                ),
            )
        } else {
            val shouldFilter = viewString.length > 4 && runs[i].text.contains(viewString.removeRange(0..4))
            if (!shouldFilter) {
                artists.add(Artist(name = runs[i].text, id = null))
            }
        }
    }
    Logger.d("artists_log", artists.toString())
    return artists
}

fun Thumbnail.toThumbnail(): com.amurayada.domain.data.model.searchResult.songs.Thumbnail =
    com.amurayada.domain.data.model.searchResult.songs.Thumbnail(
        height = this.height ?: 0,
        url = this.url,
        width = this.width ?: 0,
    )

fun List<Thumbnail>.toListThumbnail(): List<com.amurayada.domain.data.model.searchResult.songs.Thumbnail> {
    val list = mutableListOf<com.amurayada.domain.data.model.searchResult.songs.Thumbnail>()
    this.forEach {
        list.add(it.toThumbnail())
    }
    return list
}

internal fun parseNewRelease(
    explore: ExplorePage,
    newReleaseString: String,
    musicVideoString: String,
): ArrayList<HomeItem> {
    val result = arrayListOf<HomeItem>()
    result.add(
        HomeItem(
            title = newReleaseString,
            contents =
                explore.released.map {
                    Content(
                        album = null,
                        artists =
                            listOf(
                                Artist(
                                    id = it.author?.id ?: "",
                                    name = it.author?.name ?: "",
                                ),
                            ),
                        description = it.author?.name ?: "YouTube Music",
                        isExplicit = it.explicit,
                        playlistId = it.id,
                        browseId = it.id,
                        thumbnails =
                            listOf(
                                com.amurayada.domain.data.model.searchResult.songs.Thumbnail(
                                    522,
                                    it.thumbnail,
                                    522,
                                ),
                            ),
                        title = it.title,
                        videoId = null,
                        views = null,
                        radio = null,
                    )
                },
        ),
    )
    result.add(
        HomeItem(
            title = musicVideoString,
            contents =
                explore.musicVideo.map { videoItem ->
                    val artists =
                        videoItem.artists
                            .map {
                                Artist(
                                    name = it.name,
                                    id = it.id,
                                )
                            }.toMutableList()
                    if (artists.lastOrNull()?.id == null &&
                        artists.lastOrNull()?.name?.contains(
                            Regex("\\d"),
                        ) == true
                    ) {
                        runCatching { artists.removeAt(artists.lastIndex) }
                            .onSuccess {
                                Logger.i("parse_mixed_content", "Removed last artist")
                            }.onFailure {
                                Logger.e("parse_mixed_content", "Failed to remove last artist")
                                it.printStackTrace()
                            }
                    }
                    Content(
                        album =
                            videoItem.album?.let {
                                Album(
                                    name = it.name,
                                    id = it.id,
                                )
                            },
                        artists = artists,
                        description = null,
                        isExplicit = videoItem.explicit,
                        playlistId = null,
                        browseId = null,
                        thumbnails =
                            listOf(
                                com.amurayada.domain.data.model.searchResult.songs.Thumbnail(
                                    522,
                                    videoItem.thumbnail,
                                    1080,
                                ),
                            ),
                        title = videoItem.title,
                        videoId = videoItem.id,
                        views = videoItem.view,
                        durationSeconds = videoItem.duration,
                        radio = null,
                    )
                },
        ),
    )
    return result
}
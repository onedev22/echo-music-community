package com.amurayada.kotlinytmusicscraper.extractor

import com.amurayada.kotlinytmusicscraper.models.SongItem
import com.amurayada.kotlinytmusicscraper.models.response.DownloadProgress

expect class Extractor() {
    fun init()

    fun mergeAudioVideoDownload(filePath: String): DownloadProgress

    fun saveAudioWithThumbnail(
        filePath: String,
        track: SongItem,
    ): DownloadProgress

    fun newPipePlayer(videoId: String): List<Pair<Int, String>>
}
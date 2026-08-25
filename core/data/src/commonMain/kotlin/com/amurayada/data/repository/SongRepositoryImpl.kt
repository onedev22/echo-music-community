package com.amurayada.data.repository

import com.amurayada.common.MERGING_DATA_TYPE
import com.amurayada.data.db.datasource.LocalDataSource
import com.amurayada.data.extension.getFullDataFromDB
import com.amurayada.data.mapping.toListTrack
import com.amurayada.data.mapping.toSongItemForDownload
import com.amurayada.data.mapping.toWatchEndpoint
import com.amurayada.domain.data.entities.QueueEntity
import com.amurayada.domain.data.entities.SongEntity
import com.amurayada.domain.data.entities.SongInfoEntity
import com.amurayada.domain.data.model.browse.album.Track
import com.amurayada.domain.data.model.download.DownloadProgress
import com.amurayada.domain.data.model.streams.YouTubeWatchEndpoint
import com.amurayada.domain.manager.DataStoreManager
import com.amurayada.data.mapping.toTrack
import com.amurayada.domain.utils.toSongEntity
import com.amurayada.domain.extension.now
import com.amurayada.domain.extension.plusSeconds
import com.amurayada.domain.utils.LocalResource
import com.amurayada.domain.manager.DataStoreManager.Values.TRUE
import com.amurayada.domain.repository.SongRepository
import com.amurayada.domain.utils.Resource
import com.amurayada.kotlinytmusicscraper.YouTube
import com.amurayada.kotlinytmusicscraper.models.SongItem
import com.amurayada.kotlinytmusicscraper.models.WatchEndpoint
import com.amurayada.kotlinytmusicscraper.models.response.LikeStatus
import com.amurayada.kotlinytmusicscraper.pages.NextPage
import com.amurayada.kotlinytmusicscraper.parser.getPlaylistContinuation
import com.amurayada.logger.Logger
import com.amurayada.data.parser.search.parseSearchSong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime

private const val TAG = "SongRepositoryImpl"

internal class SongRepositoryImpl(
    private val dataStoreManager: DataStoreManager,
    private val localDataSource: LocalDataSource,
    private val youTube: YouTube,
) : SongRepository {
    override fun getAllSongs(limit: Int): Flow<List<SongEntity>> =
        flow {
            emit(localDataSource.getAllSongs(limit))
        }.flowOn(Dispatchers.IO)

    override suspend fun setInLibrary(
        videoId: String,
        inLibrary: LocalDateTime,
    ) = withContext(Dispatchers.IO) { localDataSource.setInLibrary(videoId, inLibrary) }

    override fun getSongsByListVideoId(listVideoId: List<String>): Flow<List<SongEntity>> =
        flow {
            emit(
                localDataSource.getSongByListVideoIdFull(listVideoId),
            )
        }.flowOn(Dispatchers.IO)

    override fun getDownloadedSongs(): Flow<List<SongEntity>> =
        localDataSource.getDownloadedSongsAsFlow()
            .flowOn(Dispatchers.IO)

    override fun getDownloadingSongs(): Flow<List<SongEntity>?> =
        flow {
            emit(
                getFullDataFromDB { limit, offset ->
                    localDataSource.getDownloadingSongs(limit, offset)
                },
            )
        }.flowOn(Dispatchers.IO)

    override fun getPreparingSongs(): Flow<List<SongEntity>> =
        flow {
            emit(
                getFullDataFromDB { limit, offset ->
                    localDataSource.getPreparingSongs(limit, offset)
                },
            )
        }.flowOn(Dispatchers.IO)

    override fun getDownloadedVideoIdListFromListVideoIdAsFlow(listVideoId: List<String>) =
        localDataSource.getDownloadedVideoIdListFromListVideoIdAsFlow(listVideoId)

    override fun getLikedSongs(): Flow<List<SongEntity>> =
        flow {
            emit(
                getFullDataFromDB { limit, offset ->
                    localDataSource.getLikedSongs(limit, offset)
                },
            )
        }.flowOn(Dispatchers.IO)

    override fun getCanvasSong(max: Int): Flow<List<SongEntity>> =
        flow {
            emit(localDataSource.getCanvasSong(max))
        }.flowOn(Dispatchers.IO)

    override fun getSongById(id: String): Flow<SongEntity?> =
        flow {
            emit(localDataSource.getSong(id))
        }.flowOn(Dispatchers.IO)

    override fun getSongAsFlow(id: String) = localDataSource.getSongAsFlow(id)

    override fun insertSong(songEntity: SongEntity): Flow<Long> = flow<Long> { emit(localDataSource.insertSong(songEntity)) }.flowOn(Dispatchers.IO)

    override fun updateThumbnailsSongEntity(
        thumbnail: String,
        videoId: String,
    ): Flow<Int> = flow { emit(localDataSource.updateThumbnailsSongEntity(thumbnail, videoId)) }.flowOn(Dispatchers.IO)

    override suspend fun updateListenCount(videoId: String) =
        withContext(Dispatchers.IO) {
            localDataSource.updateListenCount(videoId)
        }

    override suspend fun resetTotalPlayTime(videoId: String) =
        withContext(Dispatchers.IO) {
            localDataSource.resetTotalPlayTime(videoId)
        }

    override suspend fun updateLikeStatus(
        videoId: String,
        likeStatus: Int,
    ) = withContext(Dispatchers.Main) {
        localDataSource.updateLiked(likeStatus, videoId)
//        if (dataStoreManager.combineLocalAndYouTubeLiked.first() == TRUE) {
//            if (likeStatus == 1) {
//                addToYouTubeLiked(videoId).collect { result ->
//                    Logger.d(TAG, "updateLikeStatus -> addToYouTubeLiked: $result")
//                }
//            } else {
//                removeFromYouTubeLiked(videoId).collect { result ->
//                    Logger.d(TAG, "updateLikeStatus -> removeFromYouTubeLiked: $result")
//                }
//            }
//        }
    }

    override fun updateSongInLibrary(
        inLibrary: LocalDateTime,
        videoId: String,
    ): Flow<Int> = flow { emit(localDataSource.updateSongInLibrary(inLibrary, videoId)) }

    override suspend fun updateDurationSeconds(
        durationSeconds: Int,
        videoId: String,
    ) = withContext(Dispatchers.Main) {
        localDataSource.updateDurationSeconds(
            durationSeconds,
            videoId,
        )
    }

    override fun getMostPlayedSongs(): Flow<List<SongEntity>> = localDataSource.getMostPlayedSongs()

    override suspend fun updateDownloadState(
        videoId: String,
        downloadState: Int,
    ) = withContext(Dispatchers.Main) {
        localDataSource.updateDownloadState(
            downloadState,
            videoId,
        )
    }

    override suspend fun getRecentSong(
        limit: Int,
        offset: Int,
    ) = localDataSource.getRecentSongs(limit, offset)

    override suspend fun insertSongInfo(songInfo: SongInfoEntity) =
        withContext(Dispatchers.IO) {
            localDataSource.insertSongInfo(songInfo)
        }

    override suspend fun getSongInfoEntity(videoId: String): Flow<SongInfoEntity?> =
        flow { emit(localDataSource.getSongInfo(videoId)) }.flowOn(Dispatchers.Main)

    override suspend fun recoverQueue(temp: List<Track>) {
        val queueEntity = QueueEntity(listTrack = temp)
        withContext(Dispatchers.IO) { localDataSource.recoverQueue(queueEntity) }
    }

    override suspend fun removeQueue() {
        withContext(Dispatchers.IO) { localDataSource.deleteQueue() }
    }

    override suspend fun getSavedQueue(): Flow<List<QueueEntity>?> =
        flow {
            emit(localDataSource.getQueue())
        }.flowOn(Dispatchers.IO)

    override fun getContinueTrack(
        playlistId: String,
        continuation: String,
        fromPlaylist: Boolean,
    ): Flow<Pair<ArrayList<Track>?, String?>> =
        flow {
            runCatching {
                var newContinuation: String? = null
                Logger.d(TAG, "getContinueTrack -> playlistId: $playlistId")
                Logger.d(TAG, "getContinueTrack -> continuation: $continuation")
                if (!fromPlaylist) {
                    youTube
                        .next(
                            if (playlistId.startsWith("RRDAMVM")) {
                                WatchEndpoint(videoId = playlistId.removePrefix("RRDAMVM"))
                            } else {
                                WatchEndpoint(playlistId = playlistId)
                            },
                            continuation = continuation,
                        ).onSuccess { next ->
                            val data: ArrayList<SongItem> = arrayListOf()
                            data.addAll(next.items)
                            newContinuation = next.continuation
                            emit(Pair(data.toListTrack(), newContinuation))
                        }.onFailure { exception ->
                            exception.printStackTrace()
                            emit(Pair(null, null))
                        }
                } else {
                    youTube
                        .customQuery(
                            browseId = null,
                            continuation = continuation,
                            setLogin = true,
                        ).onSuccess { values ->
                            Logger.d(TAG, "getPlaylistData -> continue: $continuation")
                            Logger.d(TAG, "getPlaylistData -> values: ${values.onResponseReceivedActions}")
                            val dataMore: List<SongItem> =
                                values.onResponseReceivedActions
                                    ?.firstOrNull()
                                    ?.appendContinuationItemsAction
                                    ?.continuationItems
                                    ?.apply {
                                        Logger.w(TAG, "getContinueTrack -> dataMore: ${this.size}")
                                    }?.mapNotNull {
                                        NextPage.fromMusicResponsiveListItemRenderer(
                                            it.musicResponsiveListItemRenderer ?: return@mapNotNull null,
                                        )
                                    } ?: emptyList()
                            newContinuation =
                                values.getPlaylistContinuation()
                            emit(
                                Pair<ArrayList<Track>?, String?>(
                                    dataMore.toListTrack(),
                                    newContinuation,
                                ),
                            )
                        }.onFailure {
                            Logger.e(TAG, "getContinueTrack -> Error: ${it.message}")
                            emit(Pair(null, null))
                        }
                }
            }
        }.flowOn(Dispatchers.IO)

    override fun getSongInfo(videoId: String): Flow<SongInfoEntity?> =
        flow {
            if (videoId.startsWith("local:")) {
                emit(localDataSource.getSongInfo(videoId))
                return@flow
            }
            runCatching {
                val id =
                    if (videoId.contains(MERGING_DATA_TYPE.VIDEO)) {
                        videoId.removePrefix(MERGING_DATA_TYPE.VIDEO)
                    } else {
                        videoId
                    }
                youTube
                    .getSongInfo(id)
                    .onSuccess { songInfo ->
                        val song =
                            SongInfoEntity(
                                videoId = songInfo.videoId,
                                author = songInfo.author,
                                authorId = songInfo.authorId,
                                authorThumbnail = songInfo.authorThumbnail,
                                description = songInfo.description,
                                uploadDate = songInfo.uploadDate,
                                subscribers = songInfo.subscribers,
                                viewCount = songInfo.viewCount,
                                like = songInfo.like,
                                dislike = songInfo.dislike,
                            )
                        emit(song)
                        insertSongInfo(
                            song,
                        )
                    }.onFailure {
                        if (it is CancellationException) throw it
                        it.printStackTrace()
                        emit(localDataSource.getSongInfo(videoId))
                    }
            }
        }.flowOn(Dispatchers.IO)

    override suspend fun getLikeStatus(videoId: String): Flow<Boolean> =
        flow {
            runCatching {
                youTube
                    .getLikedInfo(videoId)
                    .onSuccess {
                        if (it == LikeStatus.LIKE) emit(true) else emit(false)
                    }.onFailure {
                        if (it is CancellationException) throw it
                        it.printStackTrace()
                        emit(false)
                    }
            }
        }

    override suspend fun addToYouTubeLiked(mediaId: String?): Flow<Int> =
        flow {
            if (mediaId != null) {
                runCatching {
                    youTube
                        .addToLiked(mediaId)
                        .onSuccess {
                            Logger.d(TAG, "Liked -> Success: $it")
                            emit(it)
                        }.onFailure {
                            it.printStackTrace()
                            emit(0)
                        }
                }
            }
        }.flowOn(Dispatchers.IO)

    override suspend fun removeFromYouTubeLiked(mediaId: String?): Flow<Int> =
        flow {
            if (mediaId != null) {
                runCatching {
                    youTube
                        .removeFromLiked(mediaId)
                        .onSuccess {
                            Logger.d(TAG, "Liked -> Success: $it")
                            emit(it)
                        }.onFailure {
                            it.printStackTrace()
                            emit(0)
                        }
                }
            }
        }.flowOn(Dispatchers.IO)

    override fun downloadToFile(
        track: Track,
        path: String,
        videoId: String,
        isVideo: Boolean,
    ): Flow<DownloadProgress> =
        youTube
            .download(
                track.toSongItemForDownload(),
                path,
                videoId,
                runBlocking {
                    (
                        dataStoreManager.prefer320kbpsStream.first() == TRUE &&
                            dataStoreManager.your320kbpsUrl.first().isNotEmpty()
                    ) to
                        dataStoreManager.your320kbpsUrl.first()
                },
                isVideo,
            ).map {
                DownloadProgress(
                    audioDownloadProgress = it.audioDownloadProgress,
                    videoDownloadProgress = it.videoDownloadProgress,
                    downloadSpeed = it.downloadSpeed,
                    errorMessage = it.errorMessage,
                    isMerging = it.isMerging,
                    isError = it.isError,
                    isDone = it.isDone,
                )
            }

    override fun getRelatedData(videoId: String): Flow<Resource<Pair<List<Track>, String?>>> =
        flow {
            var targetVideoId = videoId
            if (videoId.startsWith("qobuz:")) {
                runCatching {
                    val localSong = localDataSource.getSong(videoId)
                    if (localSong != null) {
                        val query = "${localSong.title} ${localSong.artistName?.joinToString(", ") ?: ""}"
                        youTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()?.let { searchResult ->
                            val parsed = parseSearchSong(searchResult)
                            val firstTrack = parsed.firstOrNull()
                            if (firstTrack != null) {
                                targetVideoId = firstTrack.videoId
                                Logger.d("Stream", "Mapped Qobuz track $videoId to YouTube videoId $targetVideoId ('${localSong.title}')")
                            }
                        }
                    }
                }
            }

            // First attempt: plain videoId (may return automix if YouTube Music has it cached)
            val firstResult = runCatching {
                youTube.next(WatchEndpoint(videoId = targetVideoId))
            }.getOrElse { Result.failure(it) }

            val firstItems = firstResult.getOrNull()?.items
            val hasRelatedItems = firstItems != null && firstItems.any { it.id != targetVideoId }

            val next = if (firstResult.isSuccess && hasRelatedItems) {
                firstResult
            } else {
                // Retry with RDAMVM radio playlist prefix - YouTube Music requires this to
                // generate a radio queue for standalone song playback (mirrors Echo-Music YouTubeQueue)
                Logger.d("Stream", "getRelatedData: first /next call failed or empty for $targetVideoId, retrying with RDAMVM prefix")
                runCatching {
                    youTube.next(WatchEndpoint(videoId = targetVideoId, playlistId = "RDAMVM$targetVideoId"))
                }.getOrElse { Result.failure(it) }
            }

            next
                .onSuccess { result ->
                    val data: ArrayList<SongItem> = arrayListOf()
                    data.addAll(result.items.filter { it.id != targetVideoId }.toSet())
                    val nextContinuation = result.continuation
                    Logger.d("Stream", "getRelatedData: got ${data.size} related tracks for $targetVideoId (continuation=${nextContinuation != null})")
                    emit(Resource.Success<Pair<List<Track>, String?>>(Pair(data.toListTrack().toList(), nextContinuation)))
                }.onFailure { exception ->
                    Logger.e("Stream", "getRelatedData FAILED for $targetVideoId: ${exception.message}", exception)
                    emit(Resource.Error<Pair<List<Track>, String?>>(exception.message.toString()))
                }
        }.flowOn(Dispatchers.IO)

    override fun getRadioFromEndpoint(endpoint: YouTubeWatchEndpoint): Flow<Resource<Pair<List<Track>, String?>>> =
        flow {
            runCatching {
                youTube
                    .next(endpoint.toWatchEndpoint())
                    .onSuccess { next ->
                        emit(Resource.Success(Pair(next.items.toListTrack(), next.continuation)))
                    }.onFailure {
                        it.printStackTrace()
                        emit(Resource.Error(it.message ?: "Error"))
                    }
            }
        }

    override fun syncWatchHistory(): Flow<LocalResource<Unit>> = flow {
        emit(LocalResource.Loading())
        try {
            val historyResult = youTube.getWatchHistory()
            Logger.d("YouTubeHistory", "syncWatchHistory result isSuccess: ${historyResult.isSuccess}")
            if (historyResult.isSuccess) {
                val songs = historyResult.getOrThrow()
                Logger.d("YouTubeHistory", "syncWatchHistory returned ${songs.size} songs to import")
                val baseTime = now()
                songs.reversed().forEachIndexed { index, songItem ->
                    val track = songItem.toTrack()
                    val inLibraryTime = baseTime.plusSeconds(index.toLong())
                    val songEntity = track.toSongEntity().copy(
                        inLibrary = inLibraryTime
                    )
                    val existing = localDataSource.getSong(songEntity.videoId)
                    if (existing == null) {
                        localDataSource.insertSong(songEntity)
                    } else {
                        localDataSource.setInLibrary(songEntity.videoId, inLibraryTime)
                    }
                }
                emit(LocalResource.Success(Unit))
            } else {
                val exception = historyResult.exceptionOrNull()
                Logger.e("YouTubeHistory", "syncWatchHistory error: ${exception?.message}", exception)
                emit(LocalResource.Error(exception?.message ?: "Unknown error"))
            }
        } catch (e: Exception) {
            emit(LocalResource.Error(e.message ?: "Unknown error"))
        }
    }.flowOn(Dispatchers.IO)
}
package com.amurayada.data.mediaservice

import com.amurayada.domain.repository.AnalyticsRepository

actual fun createMediaServiceHandler(
    dataStoreManager: com.amurayada.domain.manager.DataStoreManager,
    songRepository: com.amurayada.domain.repository.SongRepository,
    streamRepository: com.amurayada.domain.repository.StreamRepository,
    localPlaylistRepository: com.amurayada.domain.repository.LocalPlaylistRepository,
    analyticsRepository: AnalyticsRepository,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
): com.amurayada.domain.mediaservice.handler.MediaPlayerHandler =
    JvmMediaPlayerHandlerImpl(
        dataStoreManager = dataStoreManager,
        songRepository = songRepository,
        streamRepository = streamRepository,
        localPlaylistRepository = localPlaylistRepository,
        analyticsRepository = analyticsRepository,
        coroutineScope = coroutineScope,
    )
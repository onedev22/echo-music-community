package com.amurayada.data.mediaservice

actual fun createMediaServiceHandler(
    dataStoreManager: com.amurayada.domain.manager.DataStoreManager,
    songRepository: com.amurayada.domain.repository.SongRepository,
    streamRepository: com.amurayada.domain.repository.StreamRepository,
    localPlaylistRepository: com.amurayada.domain.repository.LocalPlaylistRepository,
    analyticsRepository: com.amurayada.domain.repository.AnalyticsRepository,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
): com.amurayada.domain.mediaservice.handler.MediaPlayerHandler {
    TODO("Not yet implemented")
}
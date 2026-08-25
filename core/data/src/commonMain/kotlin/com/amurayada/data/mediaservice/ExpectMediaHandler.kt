package com.amurayada.data.mediaservice

import com.amurayada.domain.manager.DataStoreManager
import com.amurayada.domain.mediaservice.handler.MediaPlayerHandler
import com.amurayada.domain.repository.AnalyticsRepository
import com.amurayada.domain.repository.LocalPlaylistRepository
import com.amurayada.domain.repository.SongRepository
import com.amurayada.domain.repository.StreamRepository
import kotlinx.coroutines.CoroutineScope

expect fun createMediaServiceHandler(
    dataStoreManager: DataStoreManager,
    songRepository: SongRepository,
    streamRepository: StreamRepository,
    localPlaylistRepository: LocalPlaylistRepository,
    analyticsRepository: AnalyticsRepository,
    coroutineScope: CoroutineScope,
): MediaPlayerHandler
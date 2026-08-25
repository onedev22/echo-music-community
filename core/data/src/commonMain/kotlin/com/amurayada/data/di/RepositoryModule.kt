package com.amurayada.data.di

import com.amurayada.common.Config.SERVICE_SCOPE
import com.amurayada.data.io.fileDir
import com.amurayada.data.repository.AccountRepositoryImpl
import com.amurayada.data.repository.AlbumRepositoryImpl
import com.amurayada.data.repository.AnalyticsRepositoryImpl
import com.amurayada.data.repository.ArtistRepositoryImpl
import com.amurayada.data.repository.CommonRepositoryImpl
import com.amurayada.data.repository.HomeRepositoryImpl
import com.amurayada.data.repository.LocalPlaylistRepositoryImpl
import com.amurayada.data.repository.LyricsCanvasRepositoryImpl
import com.amurayada.data.repository.PlaylistRepositoryImpl
import com.amurayada.data.repository.PodcastRepositoryImpl
import com.amurayada.data.repository.SearchRepositoryImpl
import com.amurayada.data.repository.SongRepositoryImpl
import com.amurayada.data.repository.StreamRepositoryImpl
import com.amurayada.data.repository.UpdateRepositoryImpl
import com.amurayada.domain.repository.AccountRepository
import com.amurayada.domain.repository.AlbumRepository
import com.amurayada.domain.repository.AnalyticsRepository
import com.amurayada.domain.repository.ArtistRepository
import com.amurayada.domain.repository.CommonRepository
import com.amurayada.domain.repository.HomeRepository
import com.amurayada.domain.repository.LocalPlaylistRepository
import com.amurayada.domain.repository.LyricsCanvasRepository
import com.amurayada.domain.repository.PlaylistRepository
import com.amurayada.domain.repository.PodcastRepository
import com.amurayada.domain.repository.SearchRepository
import com.amurayada.domain.repository.SongRepository
import com.amurayada.domain.repository.StreamRepository
import com.amurayada.domain.repository.UpdateRepository
import org.koin.core.qualifier.named
import org.koin.dsl.module

val repositoryModule =
    module {
        single<AccountRepository>(createdAtStart = true) {
            AccountRepositoryImpl(get(), get())
        }

        single<AlbumRepository>(createdAtStart = true) {
            AlbumRepositoryImpl(get(), get())
        }

        single<ArtistRepository>(createdAtStart = true) {
            ArtistRepositoryImpl(get(), get())
        }

        single<CommonRepository>(createdAtStart = true) {
            CommonRepositoryImpl(get(named(SERVICE_SCOPE)), get(), get(), get(), get()).apply {
                this.init("${fileDir()}/ytdlp-cookie.txt", get())
            }
        }

        single<HomeRepository>(createdAtStart = true) {
            HomeRepositoryImpl(get(), get())
        }

        single<LocalPlaylistRepository>(createdAtStart = true) {
            LocalPlaylistRepositoryImpl(get(), get())
        }

        single<LyricsCanvasRepository>(createdAtStart = true) {
            LyricsCanvasRepositoryImpl(get(), get(), get(), get())
        }

        single<PlaylistRepository>(createdAtStart = true) {
            PlaylistRepositoryImpl(get(), get(), get())
        }

        single<PodcastRepository>(createdAtStart = true) {
            PodcastRepositoryImpl(get(), get())
        }

        single<SearchRepository>(createdAtStart = true) {
            SearchRepositoryImpl(get(), get())
        }

        single<SongRepository>(createdAtStart = true) {
            SongRepositoryImpl(get(), get(), get())
        }

        single<StreamRepository>(createdAtStart = true) {
            StreamRepositoryImpl(get(), get(), get(), get())
        }

        single<UpdateRepository>(createdAtStart = true) {
            UpdateRepositoryImpl(get())
        }

        single<AnalyticsRepository>(createdAtStart = true) {
            AnalyticsRepositoryImpl(get())
        }

        single(createdAtStart = true) {
            com.amurayada.data.LosslessManager(dataStoreManager = get())
        }
    }
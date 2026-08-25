package com.amurayada.data.di

import DatabaseDao
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.amurayada.data.dataStore.DataStoreManagerImpl
import com.amurayada.data.dataStore.createDataStoreInstance
import com.amurayada.data.db.Converters
import com.amurayada.data.db.MusicDatabase
import com.amurayada.data.db.datasource.AnalyticsDatasource
import com.amurayada.data.db.datasource.LocalDataSource
import com.amurayada.data.db.getDatabaseBuilder
import com.amurayada.domain.manager.DataStoreManager
import com.amurayada.kotlinytmusicscraper.YouTube
import com.amurayada.spotify.SpotifyLegacy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import org.koin.dsl.module
import org.music.lyrics.SimpMusicLyricsClient

val databaseModule =
    module {
        single(createdAtStart = true) {
            Converters()
        }
        // Database
        single(createdAtStart = true) {
            getDatabaseBuilder(
                get<Converters>()
            )
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
        }
        // DatabaseDao
        single(createdAtStart = true) {
            get<MusicDatabase>().getDatabaseDao()
        }
        // LocalDataSource
        single(createdAtStart = true) {
            LocalDataSource(get<DatabaseDao>())
        }
        // AnalyticsDatasource
        single(createdAtStart = true) {
            AnalyticsDatasource(get<DatabaseDao>())
        }
        // Datastore
        single(createdAtStart = true) {
            createDataStoreInstance()
        }
        // DatastoreManager
        single<DataStoreManager>(createdAtStart = true) {
            DataStoreManagerImpl(get<DataStore<Preferences>>())
        }

        // Move YouTube from Singleton to Koin DI
        single(createdAtStart = true) {
            YouTube()
        }

        single(createdAtStart = true) {
            SpotifyLegacy()
        }

        single(createdAtStart = true) {
            SimpMusicLyricsClient()
        }
    }
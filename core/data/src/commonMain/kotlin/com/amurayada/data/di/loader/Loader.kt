package com.amurayada.data.di.loader

import com.amurayada.data.di.databaseModule
import com.amurayada.data.di.mediaHandlerModule
import com.amurayada.data.di.repositoryModule
import org.koin.core.context.loadKoinModules

fun loadAllModules() {
    loadKoinModules(
        listOf(
            databaseModule,
            repositoryModule,
        ),
    )
    loadKoinModules(mediaHandlerModule)
    loadMediaService()
}

expect fun loadMediaService()
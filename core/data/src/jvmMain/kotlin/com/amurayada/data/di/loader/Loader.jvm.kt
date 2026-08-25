package com.amurayada.data.di.loader

import com.music.media_jvm.di.loadVlcModule

actual fun loadMediaService() {
    loadVlcModule()
}

package com.amurayada.data.dataStore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.amurayada.common.SETTINGS_FILENAME
import com.amurayada.data.io.getHomeFolderPath
import createDataStore
import java.io.File

actual fun createDataStoreInstance(): DataStore<Preferences> = createDataStore(
    producePath = {
        val file = File(getHomeFolderPath(listOf(".music")), "$SETTINGS_FILENAME.preferences_pb")
        file.absolutePath
    }
)
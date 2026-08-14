package org.mistykmedia.insertabot.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "insertabot_settings")

class AppPreferences(private val context: Context) {
    private object Keys {
        val workerUrl = stringPreferencesKey("worker_url")
        val bearerToken = stringPreferencesKey("bearer_token")
        val modelLane = stringPreferencesKey("model_lane")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            workerUrl = prefs[Keys.workerUrl].orEmpty(),
            bearerToken = prefs[Keys.bearerToken].orEmpty(),
            modelLane = ModelLane.entries.firstOrNull { it.wireValue == prefs[Keys.modelLane] } ?: ModelLane.AUTO
        )
    }

    suspend fun save(settings: AppSettings) {
        context.dataStore.edit { prefs: MutableMap<Preferences.Key<*>, Any?> ->
            prefs[Keys.workerUrl] = settings.workerUrl.trim().trimEnd('/')
            prefs[Keys.bearerToken] = settings.bearerToken.trim()
            prefs[Keys.modelLane] = settings.modelLane.wireValue
        }
    }
}

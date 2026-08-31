package org.mistykmedia.insertabot.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "insertabot_settings")

class AppPreferences(private val context: Context) {
    private object Keys {
        val workerUrl = stringPreferencesKey("worker_url")
        val bearerToken = stringPreferencesKey("bearer_token")
        val cfAccessClientId = stringPreferencesKey("cf_access_client_id")
        val cfAccessClientSecret = stringPreferencesKey("cf_access_client_secret")
        val modelLane = stringPreferencesKey("model_lane")
        val instanceId = stringPreferencesKey("agent_instance_id")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            workerUrl = prefs[Keys.workerUrl].orEmpty(),
            bearerToken = prefs[Keys.bearerToken].orEmpty(),
            cfAccessClientId = prefs[Keys.cfAccessClientId].orEmpty(),
            cfAccessClientSecret = prefs[Keys.cfAccessClientSecret].orEmpty(),
            modelLane = ModelLane.entries.firstOrNull { it.wireValue == prefs[Keys.modelLane] } ?: ModelLane.AUTO
        )
    }

    /**
     * The Durable Object name for the agent connection — the conversation
     * identity. Generated once and kept so a thread survives app restarts.
     */
    val instanceId: Flow<String> = context.dataStore.data.map { prefs -> prefs[Keys.instanceId].orEmpty() }

    /** Returns the stored instance id, generating one atomically on first use. */
    suspend fun ensureInstanceId(): String =
        context.dataStore.edit { prefs ->
            if (prefs[Keys.instanceId].isNullOrBlank()) prefs[Keys.instanceId] = UUID.randomUUID().toString()
        }[Keys.instanceId].orEmpty()

    /** Rotates the instance id, which starts a fresh conversation on the Worker. */
    suspend fun resetInstanceId(): String {
        val generated = UUID.randomUUID().toString()
        context.dataStore.edit { prefs -> prefs[Keys.instanceId] = generated }
        return generated
    }

    suspend fun save(settings: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.workerUrl] = settings.workerUrl.trim().trimEnd('/')
            prefs[Keys.bearerToken] = settings.bearerToken.trim()
            prefs[Keys.cfAccessClientId] = settings.cfAccessClientId.trim()
            prefs[Keys.cfAccessClientSecret] = settings.cfAccessClientSecret.trim()
            prefs[Keys.modelLane] = settings.modelLane.wireValue
        }
    }
}

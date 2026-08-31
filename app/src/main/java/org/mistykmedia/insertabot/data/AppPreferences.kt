package org.mistykmedia.insertabot.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "insertabot_settings")

class AppPreferences(private val context: Context) {
    private companion object {
        /** Canonical UUID — the shape `AgentWebSocket` puts in the request path. */
        val INSTANCE_ID = Regex("^[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$")
    }

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
     * Returns the Durable Object name for the agent connection — the
     * conversation identity — generating one atomically on first use so a
     * thread survives app restarts.
     */
    suspend fun ensureInstanceId(): String =
        context.dataStore.edit { prefs ->
            // Repair a missing or corrupt id here, where the replacement is
            // persisted — regenerating it per connect would strand the thread
            // on a new Durable Object every time.
            val stored = prefs[Keys.instanceId]
            if (stored == null || !INSTANCE_ID.matches(stored)) {
                prefs[Keys.instanceId] = UUID.randomUUID().toString()
            }
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

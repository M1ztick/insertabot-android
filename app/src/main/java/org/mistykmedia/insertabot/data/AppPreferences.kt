package org.mistykmedia.insertabot.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.mistykmedia.insertabot.BuildConfig
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
        val accessToken = stringPreferencesKey("cf_access_session_token")
        val accessBinding = stringPreferencesKey("cf_access_session_binding")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            // A blank URL means "use the default", so a first-run user never
            // has to know or type one.
            workerUrl = prefs[Keys.workerUrl]?.takeIf { it.isNotBlank() } ?: BuildConfig.DEFAULT_WORKER_URL,
            bearerToken = prefs[Keys.bearerToken].orEmpty(),
            cfAccessClientId = prefs[Keys.cfAccessClientId].orEmpty(),
            cfAccessClientSecret = prefs[Keys.cfAccessClientSecret].orEmpty(),
            modelLane = ModelLane.entries.firstOrNull { it.wireValue == prefs[Keys.modelLane] } ?: ModelLane.AUTO,
            accessToken = prefs[Keys.accessToken].orEmpty(),
            accessBinding = prefs[Keys.accessBinding].orEmpty()
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

    /**
     * Saves the advanced connection fields only. The model lane and the
     * sign-in session have their own writers, so a Settings form opened before
     * a sign-in finished cannot overwrite the new session with a stale copy.
     */
    suspend fun saveConnection(settings: AppSettings) {
        context.dataStore.edit { prefs ->
            val url = settings.workerUrl.trim().trimEnd('/')
            // Storing the default verbatim would pin it; blank keeps following it.
            if (url.isBlank() || url == BuildConfig.DEFAULT_WORKER_URL) prefs.remove(Keys.workerUrl)
            else prefs[Keys.workerUrl] = url
            prefs[Keys.bearerToken] = settings.bearerToken.trim()
            prefs[Keys.cfAccessClientId] = settings.cfAccessClientId.trim()
            prefs[Keys.cfAccessClientSecret] = settings.cfAccessClientSecret.trim()
        }
    }

    suspend fun saveModelLane(lane: ModelLane) {
        context.dataStore.edit { prefs -> prefs[Keys.modelLane] = lane.wireValue }
    }

    suspend fun saveAccessSession(token: String, binding: String?) {
        context.dataStore.edit { prefs ->
            prefs[Keys.accessToken] = token.trim()
            if (binding.isNullOrBlank()) prefs.remove(Keys.accessBinding)
            else prefs[Keys.accessBinding] = binding.trim()
        }
    }

    suspend fun clearAccessSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.accessToken)
            prefs.remove(Keys.accessBinding)
        }
    }
}

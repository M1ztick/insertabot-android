package org.mistykmedia.insertabot.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.mistykmedia.insertabot.data.WorkerFeatures
import org.mistykmedia.insertabot.data.WorkerHealth
import org.mistykmedia.insertabot.data.parseWorkerFeatures

class InsertaBotApi(private val client: OkHttpClient = OkHttpClient()) {
    suspend fun checkWorker(baseUrl: String, credentials: Credentials): WorkerHealth = withContext(Dispatchers.IO) {
        val normalized = baseUrl.trim().trimEnd('/')
        if (!normalized.startsWith("https://")) {
            return@withContext WorkerHealth(false, "Use an HTTPS Worker URL.")
        }
        val request = credentials.applyTo(Request.Builder().url("$normalized/health")).build()
        // Redirects are not followed: Access answers a missing or expired
        // sign-in with a redirect to its login page, and following it would
        // report that page's 200 as a healthy Worker.
        val probe = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
        runCatching {
            probe.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (Credentials.isAuthRejection(response.code)) {
                    return@use WorkerHealth(false, "Reached the Worker, but sign-in was refused. Sign in again.")
                }
                if (!response.isSuccessful) return@use WorkerHealth(false, "Worker returned HTTP ${response.code}.")
                val json = JSONObject(body)
                WorkerHealth(
                    ok = json.optString("status") == "ok",
                    summary = "Connected to ${json.optString("worker", "Worker")}",
                    version = json.optString("version").ifBlank { null }
                )
            }
        }.getOrElse { WorkerHealth(false, it.message ?: "Connection failed.") }
    }

    /** The Worker's feature flags, or null if it couldn't be asked. */
    suspend fun features(baseUrl: String, credentials: Credentials): WorkerFeatures? = withContext(Dispatchers.IO) {
        val url = baseUrl.trim().trimEnd('/')
        if (!url.startsWith("https://")) return@withContext null
        val request = credentials.applyTo(Request.Builder().url("$url/health")).build()
        val probe = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
        runCatching {
            probe.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else parseWorkerFeatures(response.body?.string().orEmpty())
            }
        }.getOrNull()
    }
}

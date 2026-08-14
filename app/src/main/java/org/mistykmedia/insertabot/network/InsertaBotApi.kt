package org.mistykmedia.insertabot.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.mistykmedia.insertabot.data.WorkerHealth

class InsertaBotApi(private val client: OkHttpClient = OkHttpClient()) {
    suspend fun checkWorker(baseUrl: String, bearerToken: String): WorkerHealth = withContext(Dispatchers.IO) {
        val normalized = baseUrl.trim().trimEnd('/')
        if (!normalized.startsWith("https://")) {
            return@withContext WorkerHealth(false, "Use an HTTPS Worker URL.")
        }
        val request = Request.Builder()
            .url("$normalized/health")
            .apply { if (bearerToken.isNotBlank()) header("Authorization", "Bearer $bearerToken") }
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                val body = response.body.string()
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
}

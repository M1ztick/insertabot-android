package org.mistykmedia.insertabot.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.mistykmedia.insertabot.data.ConversationPage
import org.mistykmedia.insertabot.data.ConversationSummary
import org.mistykmedia.insertabot.data.parseConversation
import org.mistykmedia.insertabot.data.parseConversationPage

/** Why a conversations request failed, in terms the UI can act on. */
sealed class ConversationsError(message: String) : Exception(message) {
    /** Access refused the session: signing in again is the fix. */
    class SignInRequired : ConversationsError("Your sign-in has expired. Sign in again to see your conversations.")

    /** The Worker has no conversation index (503); history is simply not offered. */
    class Unavailable : ConversationsError("Conversation history isn't set up on this Worker.")

    class NotFound : ConversationsError("That conversation no longer exists.")

    class Failed(message: String) : ConversationsError(message)
}

/**
 * Client for the Worker's conversation index — the same endpoints the web app's
 * history panel uses, so a rename or delete here shows up there and vice versa.
 *
 *   GET    /api/conversations?limit=&cursor=
 *   PATCH  /api/conversations/:id   {"title": "..."}
 *   DELETE /api/conversations/:id   (also deletes the thread's messages)
 */
class ConversationsApi(client: OkHttpClient = OkHttpClient()) {

    // Access answers a missing or expired session with a redirect to its login
    // page; following it would hand an HTML page to the JSON parser.
    private val http = client.newBuilder().followRedirects(false).followSslRedirects(false).build()

    suspend fun list(
        workerUrl: String,
        credentials: Credentials,
        cursor: String? = null,
        limit: Int = 30
    ): Result<ConversationPage> = call {
        val url = collectionUrl(workerUrl).newBuilder()
            .addQueryParameter("limit", limit.toString())
            .apply { if (!cursor.isNullOrBlank()) addQueryParameter("cursor", cursor) }
            .build()
        execute(credentials.applyTo(Request.Builder().url(url).get()).build(), ::parseConversationPage)
    }

    suspend fun rename(
        workerUrl: String,
        credentials: Credentials,
        id: String,
        title: String
    ): Result<ConversationSummary> = call {
        val body = JSONObject().put("title", title).toString().toRequestBody(JSON)
        val request = credentials.applyTo(Request.Builder().url(itemUrl(workerUrl, id)).patch(body)).build()
        execute(request, ::parseConversation)
    }

    /** Deletes the thread and its history on the Worker. Already gone counts as done. */
    suspend fun delete(workerUrl: String, credentials: Credentials, id: String): Result<Unit> = call {
        val request = credentials.applyTo(Request.Builder().url(itemUrl(workerUrl, id)).delete()).build()
        execute(request) { }
    }

    private suspend fun <T> call(block: () -> T): Result<T> = withContext(Dispatchers.IO) {
        runCatching(block).recoverCatching { failure ->
            throw failure as? ConversationsError
                ?: ConversationsError.Failed(failure.message ?: "Couldn't reach the Worker.")
        }
    }

    private fun <T> execute(request: Request, parse: (String) -> T): T =
        http.newCall(request).execute().use { response ->
            val code = response.code
            when {
                Credentials.isAuthRejection(code) -> throw ConversationsError.SignInRequired()
                code == 503 -> throw ConversationsError.Unavailable()
                code == 404 -> throw ConversationsError.NotFound()
                !response.isSuccessful -> throw ConversationsError.Failed("The Worker returned HTTP $code.")
            }
            parse(response.body.string())
        }

    private fun collectionUrl(workerUrl: String) =
        (workerUrl.trim().trimEnd('/') + "/api/conversations").toHttpUrlOrNull()
            ?: throw ConversationsError.Failed("The Worker URL isn't valid.")

    private fun itemUrl(workerUrl: String, id: String) =
        collectionUrl(workerUrl).newBuilder().addPathSegment(id).build()

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

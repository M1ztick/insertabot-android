package org.mistykmedia.insertabot.network

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Transport boundary for the Cloudflare Agents ChatAgent connection.
 *
 * `insertabot-cfworker` delegates `/agents/` requests to `routeAgentRequest`.
 * The worker-side agent protocol is not plain MCP, so do not invent production
 * frame names here. Verify the connection URL and frames against the deployed
 * Pages client or the Agents SDK, then implement them in this one class.
 */
class AgentWebSocket(private val client: OkHttpClient = OkHttpClient()) {
    sealed interface Event {
        data object Open : Event
        data class Text(val value: String) : Event
        data class Failure(val message: String) : Event
        data object Closed : Event
    }

    fun connect(endpoint: String, bearerToken: String = ""): Flow<Event> = callbackFlow {
        val request = Request.Builder()
            .url(endpoint)
            .apply { if (bearerToken.isNotBlank()) header("Authorization", "Bearer $bearerToken") }
            .build()
        val socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { trySend(Event.Open) }
            override fun onMessage(webSocket: WebSocket, text: String) { trySend(Event.Text(text)) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                trySend(Event.Failure(t.message ?: "WebSocket connection failed"))
                close(t)
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                trySend(Event.Closed)
                close()
            }
        })
        awaitClose { socket.close(1000, "Client closed") }
    }
}

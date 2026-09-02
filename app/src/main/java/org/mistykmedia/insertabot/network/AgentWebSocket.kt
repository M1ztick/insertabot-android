package org.mistykmedia.insertabot.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import android.util.Log
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import org.mistykmedia.insertabot.data.ChatMessage
import org.mistykmedia.insertabot.data.ChatRole
import org.mistykmedia.insertabot.data.ContentPart
import org.mistykmedia.insertabot.data.McpServer
import org.mistykmedia.insertabot.data.ModelLane
import java.net.URLEncoder
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Wire frame names spoken by the Cloudflare Agents SDK over the agent WebSocket.
 *
 * These are not a published spec — they are the frames the deployed
 * `insertabot-cfworker` build (agents ^0.20.1, @cloudflare/ai-chat ^0.10.2)
 * exchanges with its own PWA client (`public/index.js`). Upstream has since
 * renamed them in `@cloudflare/ai-chat` (`chat-request`, `messages`, `cancel`),
 * so a Worker dependency bump can change them: keep every literal here.
 */
object AgentFrames {
    const val PATH_PREFIX = "agents"

    /** `ChatAgent` is routed as its kebab-cased class name. */
    const val AGENT_NAME = "chat-agent"

    const val CHAT_REQUEST = "cf_agent_use_chat_request"
    const val CHAT_RESPONSE = "cf_agent_use_chat_response"
    const val CHAT_MESSAGES = "cf_agent_chat_messages"
    const val STATE = "cf_agent_state"
    const val MCP_SERVERS = "cf_agent_mcp_servers"
    const val RPC = "rpc"

    /** Server names the agent instance this socket is bound to on connect. */
    const val IDENTITY = "cf_agent_identity"

    /**
     * Server offers to replay a stream that was interrupted — but sends nothing
     * until the client acknowledges. [STREAM_RESUME_ACK] is what requests the
     * chunks; without it the rest of the reply is simply never delivered.
     */
    const val STREAM_RESUMING = "cf_agent_stream_resuming"
    const val STREAM_RESUME_ACK = "cf_agent_stream_resume_ack"
}

/**
 * Transport boundary for the Cloudflare Agents `ChatAgent` connection.
 *
 * One instance owns one WebSocket. [connect] returns the inbound event stream;
 * [sendChat] and [callRpc] write to the socket held open by that stream and are
 * no-ops (or failures) while it is closed.
 */
class AgentWebSocket(private val client: OkHttpClient = OkHttpClient()) {

    private companion object {
        const val TAG = "AgentWebSocket"
    }

    sealed interface Event {
        data object Open : Event
        data object Closed : Event
        data class Failure(val message: String) : Event

        /** Server began a new assistant message for [requestId]. */
        data class StreamStart(val requestId: String, val messageId: String) : Event
        data class TextDelta(val requestId: String, val delta: String) : Event
        data class ToolCall(val requestId: String, val toolName: String) : Event
        data class ToolFailed(val requestId: String, val toolName: String, val error: String) : Event
        data class StreamError(val requestId: String, val error: String) : Event
        data class StreamFinish(val requestId: String) : Event

        /** An interrupted stream is being replayed for [requestId]. */
        data class StreamResuming(val requestId: String) : Event

        /** Full history replace — the agent is authoritative, not the client. */
        data class History(val messages: List<ChatMessage>) : Event
        data class State(val modelLane: ModelLane) : Event
        data class McpServers(val servers: List<McpServer>) : Event
        data class RpcResult(val id: String, val result: Result<Any?>) : Event

    }

    @Volatile
    private var socket: WebSocket? = null
    private val pendingRpc = ConcurrentHashMap<String, CompletableDeferred<Result<Any?>>>()

    val isOpen: Boolean get() = socket != null

    /**
     * Opens `wss://<worker>/agents/chat-agent/<instanceId>`.
     *
     * [instanceId] names the Durable Object, so it *is* the conversation
     * identity: persist it to resume a thread, rotate it to start a new one.
     */
    fun connect(
        workerUrl: String,
        instanceId: String,
        bearerToken: String = "",
        cfAccessClientId: String = "",
        cfAccessClientSecret: String = ""
    ): Flow<Event> = callbackFlow {
        val request = Request.Builder()
            .url(agentUrl(workerUrl, instanceId, bearerToken))
            .apply {
                // The Worker does not yet authenticate the upgrade; the PWA passes
                // its key as ?ib_key= (see agentUrl) and these headers cost nothing.
                // Cloudflare Access, when enabled, does enforce the service headers.
                if (bearerToken.isNotBlank()) header("Authorization", "Bearer $bearerToken")
                if (cfAccessClientId.isNotBlank()) header("CF-Access-Client-Id", cfAccessClientId)
                if (cfAccessClientSecret.isNotBlank()) header("CF-Access-Client-Secret", cfAccessClientSecret)
            }
            .build()

        val ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                socket = webSocket
                trySend(Event.Open)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                decode(text).forEach { trySend(it) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                socket = null
                failPendingRpc(t.message ?: "WebSocket connection failed")
                trySend(Event.Failure(t.message ?: "WebSocket connection failed"))
                close(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                socket = null
                failPendingRpc("Connection closed")
                trySend(Event.Closed)
                close()
            }
        })
        awaitClose {
            socket = null
            failPendingRpc("Connection closed")
            ws.close(1000, "Client closed")
        }
    }

    fun disconnect() {
        socket?.close(1000, "Client closed")
        socket = null
    }

    /**
     * Sends one chat turn. The agent replays [history] as an HTTP POST body, so
     * the **entire** conversation goes on the wire each turn — there is no
     * delta form. Returns the request id the response frames will carry, or
     * null if the socket is not open.
     */
    fun sendChat(history: List<ChatMessage>, requestId: String = UUID.randomUUID().toString()): String? {
        val ws = socket ?: return null
        val messages = JSONArray()
        history.filterNot { it.error || it.role == ChatRole.SYSTEM }.forEach { messages.put(uiMessage(it)) }
        val body = JSONObject()
            .put("messages", messages)
            .put("trigger", "submit-message")
            .toString()
        val frame = JSONObject()
            .put("type", AgentFrames.CHAT_REQUEST)
            .put("id", requestId)
            // `init` mirrors a fetch() RequestInit; `body` is a *string*, not an object.
            .put("init", JSONObject().put("method", "POST").put("body", body))
        return if (ws.send(frame.toString())) requestId else null
    }

    /** Calls one of the agent's `@callable()` methods and awaits its reply. */
    suspend fun callRpc(method: String, args: List<Any?> = emptyList(), timeoutMs: Long = 30_000): Result<Any?> {
        val ws = socket ?: return Result.failure(IllegalStateException("Not connected"))
        val id = UUID.randomUUID().toString()
        val pending = CompletableDeferred<Result<Any?>>()
        pendingRpc[id] = pending

        val frame = JSONObject()
            .put("type", AgentFrames.RPC)
            .put("id", id)
            .put("method", method)
            .put("args", JSONArray().apply { args.forEach { put(it ?: JSONObject.NULL) } })

        if (!ws.send(frame.toString())) {
            pendingRpc.remove(id)
            return Result.failure(IllegalStateException("Send failed"))
        }
        return withTimeoutOrNull(timeoutMs) { pending.await() }
            ?: Result.failure<Any?>(IllegalStateException("RPC '$method' timed out"))
                .also { pendingRpc.remove(id) }
    }

    suspend fun setModelLane(lane: ModelLane): Result<Any?> =
        callRpc("setModelLane", listOf(lane.wireValue))

    // ── Decoding ─────────────────────────────────────────────────────────────

    /** One inbound frame may produce several events (a chunk plus a terminal `done`). */
    private fun decode(text: String): List<Event> {
        val frame = runCatching { JSONObject(text) }.getOrNull() ?: return emptyList()
        return when (val type = frame.optString("type")) {
            AgentFrames.CHAT_RESPONSE -> decodeChatResponse(frame)
            AgentFrames.CHAT_MESSAGES -> listOf(Event.History(decodeHistory(frame.optJSONArray("messages"))))
            AgentFrames.STATE -> decodeState(frame)
            AgentFrames.MCP_SERVERS -> listOf(Event.McpServers(decodeMcpServers(frame.optJSONObject("mcp"))))
            AgentFrames.RPC -> listOf(decodeRpc(frame))
            AgentFrames.IDENTITY -> {
                // Worth a line: `name` is the server-scoped Durable Object this
                // socket resolved to, which is the only place the client can see
                // that its conversation id was namespaced to the caller.
                Log.i(TAG, "Agent identity: ${frame.optString("agent")}/${frame.optString("name")}")
                emptyList()
            }
            AgentFrames.STREAM_RESUMING -> resumeStream(frame)
            // Worth a log: an upstream rename lands here rather than failing loudly.
            else -> emptyList<Event>().also { Log.w(TAG, "Unhandled agent frame: $type") }
        }
    }

    /**
     * `body` is a JSON string *inside* the JSON frame — one AI SDK stream part
     * per frame — so it is parsed a second time here.
     */
    private fun decodeChatResponse(frame: JSONObject): List<Event> {
        val requestId = frame.optString("id")
        val events = mutableListOf<Event>()

        frame.optString("body").takeIf { it.isNotBlank() }?.let { body ->
            val chunk = runCatching { JSONObject(body) }.getOrNull()
            if (chunk != null) {
                when (chunk.optString("type")) {
                    "start" ->
                        events += Event.StreamStart(requestId, chunk.optString("messageId").ifBlank { requestId })
                    "text-delta" -> {
                        // `delta` is v5; `textDelta` is the older field name. Select
                        // on key presence, not blankness: a delta that is exactly
                        // "\n" is blank, and falling back on it drops the newline
                        // entirely, silently running paragraphs together.
                        val delta =
                            if (chunk.has("delta")) chunk.optString("delta")
                            else chunk.optString("textDelta")
                        if (delta.isNotEmpty()) events += Event.TextDelta(requestId, delta)
                    }
                    "tool-input-start", "tool-input-available", "tool-call" ->
                        events += Event.ToolCall(requestId, chunk.toolName())
                    "tool-output-error", "tool-error" ->
                        events += Event.ToolFailed(
                            requestId,
                            chunk.toolName(),
                            chunk.optString("errorText").ifBlank { chunk.optString("error") }
                                .ifBlank { "tool execution failed" }
                        )
                    "error" ->
                        events += Event.StreamError(
                            requestId,
                            chunk.optString("errorText").ifBlank { chunk.optString("error") }.ifBlank { "unknown" }
                        )
                    "finish" -> events += Event.StreamFinish(requestId)
                    // "tool-output-available" / "tool-result" carry no user-visible text.
                }
            }
        }

        if (frame.optBoolean("done")) events += Event.StreamFinish(requestId)
        return events
    }

    /**
     * Acknowledge a resumable stream so the server replays it.
     *
     * The acknowledgement is sent here rather than from the view model because
     * it is a transport obligation, not a UI decision: the replay does not start
     * until it lands, and routing it through a suspend handler only delays it.
     */
    private fun resumeStream(frame: JSONObject): List<Event> {
        val requestId = frame.optString("id")
        if (requestId.isBlank()) return emptyList()
        val ws = socket ?: return emptyList()
        val ack = JSONObject()
            .put("type", AgentFrames.STREAM_RESUME_ACK)
            .put("id", requestId)
        if (!ws.send(ack.toString())) {
            Log.w(TAG, "Failed to acknowledge resumable stream $requestId")
            return emptyList()
        }
        return listOf(Event.StreamResuming(requestId))
    }

    private fun decodeState(frame: JSONObject): List<Event> {
        val state = frame.optJSONObject("state") ?: return emptyList()
        val events = mutableListOf<Event>()
        val lane = ModelLane.entries.firstOrNull { it.wireValue == state.optString("modelLane") }
        if (lane != null) events += Event.State(lane)
        state.optJSONArray("messages")?.let { events += Event.History(decodeHistory(it)) }
        return events
    }

    private fun decodeRpc(frame: JSONObject): Event {
        val id = frame.optString("id")
        val result = if (frame.optBoolean("success")) {
            Result.success(if (frame.isNull("result")) null else frame.opt("result"))
        } else {
            Result.failure(RuntimeException(frame.optString("error").ifBlank { "RPC error" }))
        }
        pendingRpc.remove(id)?.complete(result)
        return Event.RpcResult(id, result)
    }

    private fun decodeMcpServers(mcp: JSONObject?): List<McpServer> {
        val servers = mcp?.optJSONObject("servers") ?: return emptyList()
        return servers.keys().asSequence().mapNotNull { id ->
            val server = servers.optJSONObject(id) ?: return@mapNotNull null
            val state = server.optString("state")
            McpServer(
                id = id,
                name = server.optString("name").ifBlank { id },
                url = server.optString("server_url"),
                connected = state == "ready",
                state = state,
                authUrl = server.optString("auth_url").ifBlank { null },
                error = server.optString("error").ifBlank { null }
            )
        }.toList()
    }

    private fun decodeHistory(messages: JSONArray?): List<ChatMessage> {
        if (messages == null) return emptyList()
        return (0 until messages.length()).mapNotNull { index ->
            val message = messages.optJSONObject(index) ?: return@mapNotNull null
            val role = when (message.optString("role")) {
                "user" -> ChatRole.USER
                "assistant" -> ChatRole.ASSISTANT
                else -> ChatRole.SYSTEM
            }
            val text = messageText(message)
            ChatMessage(
                id = message.optString("id").ifBlank { UUID.randomUUID().toString() },
                role = role,
                text = text,
                // Parts we do not model (reasoning, tool calls) decode to nothing.
                // Falling back to the flattened text keeps the turn from being
                // replayed to the model as an empty message.
                contentParts = decodeMessageParts(message.optJSONArray("parts"))
                    .ifEmpty { if (text.isBlank()) emptyList() else listOf(ContentPart.Text(text)) }
            )
        }
    }

    /** v5 `UIMessage`s carry text in `parts`; `content` is the v4 fallback. */
    private fun messageText(message: JSONObject): String {
        val parts = message.optJSONArray("parts")
        if (parts != null) {
            val text = (0 until parts.length())
                .mapNotNull { parts.optJSONObject(it) }
                .filter { it.optString("type") == "text" }
                .joinToString("") { it.optString("text") }
            if (text.isNotBlank()) return text
        }
        return message.optString("content")
    }

    private fun decodeMessageParts(parts: JSONArray?): List<ContentPart> {
        if (parts == null) return emptyList()
        return (0 until parts.length()).mapNotNull { index ->
            val part = parts.optJSONObject(index) ?: return@mapNotNull null
            when (part.optString("type")) {
                "text" -> ContentPart.Text(part.optString("text"))
                "file" -> {
                    val url = part.optString("url")
                    if (url.isBlank()) null else ContentPart.File(
                        url = url,
                        mediaType = part.optString("mediaType").ifBlank { "application/octet-stream" },
                        filename = part.optString("filename").ifBlank { null }
                    )
                }
                else -> null
            }
        }
    }

    private fun uiMessage(message: ChatMessage): JSONObject {
        val role = when (message.role) {
            ChatRole.USER -> "user"
            ChatRole.ASSISTANT -> "assistant"
            ChatRole.SYSTEM -> "system"
        }
        val parts = JSONArray()
        message.contentParts.forEach { part ->
            when (part) {
                is ContentPart.Text -> {
                    parts.put(JSONObject().put("type", "text").put("text", part.text))
                }
                is ContentPart.File -> {
                    parts.put(
                        JSONObject()
                            .put("type", "file")
                            .put("mediaType", part.mediaType)
                            .put("url", part.url)
                            .apply { part.filename?.let { put("filename", it) } }
                    )
                }
            }
        }
        // `content` is the v4 field and must not carry UI decoration: ChatMessage.text
        // holds the "[Image]" prefix shown in the list, which would otherwise reach the
        // model as literal text. The PWA sends a bare space when a turn is image-only.
        val content = message.contentParts
            .filterIsInstance<ContentPart.Text>()
            .joinToString("") { it.text }
            .ifBlank { " " }
        return JSONObject()
            .put("id", message.id)
            .put("role", role)
            .put("content", content)
            .put("parts", parts)
            .put("createdAt", Instant.now().toString())
    }

    private fun failPendingRpc(reason: String) {
        pendingRpc.values.forEach { it.complete(Result.failure(IllegalStateException(reason))) }
        pendingRpc.clear()
    }

    private fun JSONObject.toolName(): String =
        optString("toolName").ifBlank { optString("name") }.ifBlank { "tool" }

    internal fun agentUrl(workerUrl: String, instanceId: String, key: String): String {
        val normalized = workerUrl.trim().trimEnd('/')
        val scheme = if (normalized.startsWith("http://")) "ws://" else "wss://"
        val host = normalized.removePrefix("https://").removePrefix("http://")
        val query = if (key.isBlank()) "" else "?ib_key=" + URLEncoder.encode(key, "UTF-8")
        // Percent-encoding is what keeps a stray separator from escaping the
        // path segment; the id's shape is enforced where it is stored.
        val instance = URLEncoder.encode(instanceId, "UTF-8")
        return "$scheme$host/${AgentFrames.PATH_PREFIX}/${AgentFrames.AGENT_NAME}/$instance$query"
    }
}

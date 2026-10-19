package org.mistykmedia.insertabot.ui.chat

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.mistykmedia.insertabot.data.AppPreferences
import org.mistykmedia.insertabot.data.ChatMessage
import org.mistykmedia.insertabot.data.ChatRole
import org.mistykmedia.insertabot.data.ContentPart
import org.mistykmedia.insertabot.data.McpServer
import org.mistykmedia.insertabot.network.AgentWebSocket
import org.mistykmedia.insertabot.ui.JPEG_MEDIA_TYPE
import java.util.UUID

/** Connection parameters that require reopening the socket when they change. */
private data class Endpoint(
    val workerUrl: String,
    val bearerToken: String,
    val cfAccessClientId: String,
    val cfAccessClientSecret: String
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    sealed interface Connection {
        /** No Worker URL saved yet. */
        data object Unconfigured : Connection
        data object Connecting : Connection
        data object Connected : Connection

        /** Retries exhausted — [reconnect] is the way back. */
        data class Disconnected(val reason: String?) : Connection
    }

    private companion object {
        const val MAX_RETRIES = 5
        const val INITIAL_RETRY_MS = 1_000L
        const val MAX_RETRY_MS = 30_000L

        /** Longest a streamed token waits before it is drawn; about three frames. */
        const val STREAM_FLUSH_MS = 50L
    }

    private val prefs = AppPreferences(application)
    private val transport = AgentWebSocket()
    private val connectivity = application.getSystemService(ConnectivityManager::class.java)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _connection = MutableStateFlow<Connection>(Connection.Unconfigured)
    val connection = _connection.asStateFlow()

    /**
     * MCP servers as last reported by the agent. The agent is authoritative:
     * this is replaced wholesale on every `cf_agent_mcp_servers` frame rather
     * than edited locally, so a failed connection upstream is visible here.
     */
    private val _mcpServers = MutableStateFlow<List<McpServer>>(emptyList())
    val mcpServers = _mcpServers.asStateFlow()

    /** In flight while an addServer/removeServer RPC is outstanding. */
    private val _serverBusy = MutableStateFlow(false)
    val serverBusy = _serverBusy.asStateFlow()

    /** Failure text from the last server RPC, cleared when the next one starts. */
    private val _serverError = MutableStateFlow<String?>(null)
    val serverError = _serverError.asStateFlow()

    /** True from sending a turn until the agent finishes streaming its reply. */
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    /**
     * A base64 data URI currently staged to be sent with the next message.
     * Cleared after a successful send so one image is tied to one turn.
     */
    private val _pendingImage = MutableStateFlow<String?>(null)
    val pendingImage = _pendingImage.asStateFlow()

    private var session: Job? = null
    private var endpoint: Endpoint? = null

    /** Id of the assistant message currently being streamed into, if any. */
    private var streamingId: String? = null

    /** Id of the turn in flight — what a cancel has to be addressed to. */
    private var activeRequestId: String? = null

    /**
     * The turn the reader stopped. Its frames are dropped from here on: chunks
     * already in flight when the cancel lands would otherwise keep appending to
     * a message that has been closed off, and a resume offer for it would put
     * the composer back into busy after the socket blips.
     */
    private var cancelledRequestId: String? = null

    /** Streamed text not yet published to [messages]; see [appendToStream]. */
    private val pendingDelta = StringBuilder()
    private var flushJob: Job? = null

    /** The default network the socket was last opened over. Main thread only. */
    private var lastNetwork: Network? = null

    /**
     * Resume once the network comes back, or moves.
     *
     * Doze and network handoffs fail DNS while the radio is still returning, so
     * the retry budget is spent against a dead link and the socket stays down
     * for good — the reader comes back to an app that quietly stopped working
     * and has to find the Reconnect link. Retrying on the signal that actually
     * matters makes that recovery invisible instead.
     *
     * A *different* default network (Wi-Fi to mobile, a VPN reconnecting)
     * means the open socket is bound to a route that no longer exists. It
     * fails silently until a keepalive notices, so reconnect right away rather
     * than wait out the ping timeout.
     */
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            viewModelScope.launch {
                val switched = lastNetwork != null && lastNetwork != network
                lastNetwork = network
                val current = _connection.value
                if (current is Connection.Disconnected || (switched && current !is Connection.Unconfigured)) {
                    endpoint?.let { open(it) }
                }
            }
        }
    }

    init {
        viewModelScope.launch {
            prefs.settings
                .map { Endpoint(it.workerUrl, it.bearerToken, it.cfAccessClientId, it.cfAccessClientSecret) }
                .distinctUntilChanged()
                .collect { open(it) }
        }
        viewModelScope.launch {
            // The lane is agent state, not a connection parameter: push it over
            // RPC instead of reopening the socket.
            prefs.settings.map { it.modelLane }.distinctUntilChanged().collect { lane ->
                if (transport.isOpen) transport.setModelLane(lane)
            }
        }
        runCatching { connectivity?.registerDefaultNetworkCallback(networkCallback) }
    }

    /** Stage a base64 image data URI to be sent with the next message. */
    fun attachImage(base64DataUri: String) {
        _pendingImage.value = base64DataUri
    }

    /** Discard the staged image without sending it. */
    fun clearPendingImage() {
        _pendingImage.value = null
    }

    fun submit(text: String) {
        val trimmed = text.trim()
        if ((trimmed.isEmpty() && _pendingImage.value == null) || _busy.value) return

        // Order mirrors public/index.js in the Worker repo: text first, then files.
        val parts = buildList {
            if (trimmed.isNotEmpty()) {
                add(ContentPart.Text(trimmed))
            }
            _pendingImage.value?.let { uri ->
                add(ContentPart.File(url = uri, mediaType = JPEG_MEDIA_TYPE, filename = "image.jpg"))
            }
        }

        val user = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = ChatRole.USER,
            // The attachment is rendered from contentParts, so the text carries
            // only what was typed — an "[Image]" prefix here would also have to
            // be stripped back out before the turn went on the wire.
            text = trimmed,
            contentParts = parts
        )
        val history = _messages.value + user
        _messages.value = history
        _pendingImage.value = null

        send(history)
    }

    /**
     * Reissue the last turn.
     *
     * The reply being replaced is dropped along with any error card above it,
     * so a failed or unhelpful answer is retried in place rather than appended
     * to — the agent replays the whole history each turn, and keeping the dead
     * reply would feed it back as context for its own replacement.
     */
    fun retry() {
        if (_busy.value) return
        val history = truncateForRetry(_messages.value) ?: return
        _messages.value = history
        send(history)
    }

    /** True when there is a user turn to reissue and nothing in flight. */
    val canRetry = messages.map { !it.isEmpty() && truncateForRetry(it) != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private fun send(history: List<ChatMessage>) {
        val requestId = transport.sendChat(history)
        if (requestId == null) {
            _messages.value = _messages.value + ChatMessage(
                id = UUID.randomUUID().toString(),
                role = ChatRole.SYSTEM,
                text = "Not connected to the Worker. Check the URL in Settings, then reconnect.",
                error = true
            )
            return
        }
        activeRequestId = requestId
        cancelledRequestId = null
        _busy.value = true
    }

    /**
     * Abandon the turn in flight — the model has gone after the wrong thing and
     * the reader wants the composer back.
     *
     * Nothing is awaited: a cancelled turn ends in silence rather than a
     * `finish` frame, so this closes the stream out locally and hands the input
     * back immediately.
     */
    fun stop() {
        if (!_busy.value) return
        // Queued tokens are part of what the reader saw arriving; publish them
        // before deciding whether anything was streamed at all.
        flushPending()
        activeRequestId?.let { requestId ->
            transport.cancelChat(requestId)
            cancelledRequestId = requestId
        }
        val streamed = streamingId?.let { id -> _messages.value.firstOrNull { it.id == id } }
        when {
            // Stopped before the first token: an empty card is worse than no
            // card, and the turn is fully described by the user message above it.
            streamed != null && streamed.text.isBlank() ->
                _messages.value = _messages.value.filterNot { it.id == streamed.id }

            streamed != null -> appendToStream("\n\n_[Stopped]_")
        }
        finishStream()
    }

    /** Manual retry after the backoff gave up. */
    fun reconnect() {
        endpoint?.let { open(it) }
    }

    /**
     * The app is back in front of the reader.
     *
     * The backoff gives up after a handful of attempts, and a phone that was
     * dozing spends them against a radio that is still asleep. Without this the
     * reader comes back to a dead connection on an unchanged network, where
     * nothing else would ever prompt a retry.
     */
    fun onForeground() {
        if (_connection.value is Connection.Disconnected) reconnect()
    }

    /** Rotates the instance id, which is a new Durable Object and a new thread. */
    fun newConversation() {
        viewModelScope.launch {
            prefs.resetInstanceId()
            endpoint?.let { open(it, freshThread = true) } ?: run {
                finishStream()
                _messages.value = emptyList()
                _pendingImage.value = null
            }
        }
    }

    /**
     * (Re)open the agent socket for [target].
     *
     * A reconnect to the same endpoint and thread keeps the transcript on
     * screen: the agent re-sends the full history once connected anyway, and
     * clearing it first flashed the empty state over the whole conversation on
     * every network blip. Only a different endpoint or [freshThread] wipes it.
     */
    private fun open(target: Endpoint, freshThread: Boolean = false) {
        val sameThread = !freshThread && target == endpoint
        endpoint = target
        session?.cancel()
        // Whatever was streaming is over: its socket is gone.
        finishStream()
        cancelledRequestId = null

        if (!sameThread) {
            _messages.value = emptyList()
            // Servers belong to the agent we were talking to; a new endpoint
            // reports its own set once connected.
            _mcpServers.value = emptyList()
            _pendingImage.value = null
        }

        if (target.workerUrl.isBlank()) {
            _connection.value = Connection.Unconfigured
            return
        }

        session = viewModelScope.launch {
            var attempt = 0
            var backoff = INITIAL_RETRY_MS
            var lastError: String? = null

            while (isActive && attempt < MAX_RETRIES) {
                _connection.value = Connection.Connecting
                try {
                    val instanceId = prefs.ensureInstanceId()
                    transport.connect(
                        workerUrl = target.workerUrl,
                        instanceId = instanceId,
                        bearerToken = target.bearerToken,
                        cfAccessClientId = target.cfAccessClientId,
                        cfAccessClientSecret = target.cfAccessClientSecret
                    ).collect { event ->
                        if (event is AgentWebSocket.Event.Open) {
                            attempt = 0
                            backoff = INITIAL_RETRY_MS
                            lastError = null
                        }
                        if (event is AgentWebSocket.Event.Failure) lastError = event.message
                        handle(event)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    lastError = failure.message ?: "Connection failed"
                }

                if (!isActive) return@launch
                // The socket dropped mid-stream; nothing more is coming for it.
                finishStream()
                attempt++
                if (attempt >= MAX_RETRIES) break
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(MAX_RETRY_MS)
            }
            _connection.value = Connection.Disconnected(lastError)
        }
    }

    private fun handle(event: AgentWebSocket.Event) {
        cancelledRequestId?.let { if (event.requestIdOrNull() == it) return }
        when (event) {
            is AgentWebSocket.Event.Open -> {
                _connection.value = Connection.Connected
                // Re-assert the saved lane: agent state lives in the Durable
                // Object and may have hibernated or been reset. Launched, not
                // awaited — this handler is the only reader of the socket's
                // frames, and suspending it on the RPC reply held up history
                // and stream frames for as long as the agent took to answer.
                viewModelScope.launch {
                    transport.setModelLane(prefs.settings.first().modelLane)
                }
            }

            // The retry loop is about to reconnect, so this is a blip rather
            // than an outage. Disconnected is reserved for when it gives up;
            // reporting it on every drop flashed an error over the chat.
            is AgentWebSocket.Event.Closed,
            is AgentWebSocket.Event.Failure -> _connection.value = Connection.Connecting

            is AgentWebSocket.Event.StreamStart -> startStream(event.messageId)
            is AgentWebSocket.Event.TextDelta -> {
                if (streamingId == null) startStream(event.requestId)
                appendToStream(event.delta)
            }

            is AgentWebSocket.Event.ToolFailed -> {
                if (streamingId == null) startStream(event.requestId)
                appendToStream("\n\n_[Tool ${event.toolName} failed: ${event.error}]_")
                finishStream()
            }

            is AgentWebSocket.Event.StreamError -> {
                if (streamingId == null) startStream(event.requestId)
                appendToStream("\n\n_[Error: ${event.error}]_")
                finishStream()
            }

            is AgentWebSocket.Event.StreamFinish -> finishStream()

            // The agent owns the history; only trust it between streams.
            is AgentWebSocket.Event.History ->
                if (streamingId == null && event.messages.isNotEmpty()) _messages.value = event.messages

            is AgentWebSocket.Event.McpServers -> _mcpServers.value = event.servers

            // A reply that was in flight when the socket dropped is being
            // replayed. Re-enter the streaming state so the chunks land in a
            // message instead of arriving with nothing to append to.
            is AgentWebSocket.Event.StreamResuming -> {
                _busy.value = true
                startStream(event.requestId)
            }

            is AgentWebSocket.Event.ToolCall,
            is AgentWebSocket.Event.State,
            is AgentWebSocket.Event.RpcResult -> Unit
        }
    }

    /**
     * Attach an MCP server. The agent pushes a fresh `cf_agent_mcp_servers`
     * frame once it has connected (or failed), so nothing is added optimistically
     * here — the list updates when the agent says so.
     */
    fun addServer(name: String, url: String, token: String) {
        viewModelScope.launch {
            _serverBusy.value = true
            _serverError.value = null
            val result = transport.callRpc("addServer", listOf(name, url, token.ifBlank { null }))
            _serverError.value = result.exceptionOrNull()?.message
            _serverBusy.value = false
        }
    }

    /** Detach an MCP server by friendly name or id. */
    fun removeServer(nameOrId: String) {
        viewModelScope.launch {
            _serverBusy.value = true
            _serverError.value = null
            val result = transport.callRpc("removeServer", listOf(nameOrId))
            _serverError.value = result.exceptionOrNull()?.message
            _serverBusy.value = false
        }
    }

    private fun startStream(messageId: String) {
        if (streamingId != null) return
        streamingId = messageId
        _messages.value = _messages.value + ChatMessage(messageId, ChatRole.ASSISTANT, "", streaming = true)
    }

    /**
     * Queue [delta] for the streaming message.
     *
     * Tokens are coalesced and published at most once per [STREAM_FLUSH_MS]
     * rather than one state change each. Every publish rebuilds the transcript
     * list and re-parses the reply's Markdown, so per-token publishing did work
     * that grew with the reply on every token — enough to fall behind on a long
     * answer and make the screen stutter. Text arrives just as fast; it is only
     * drawn in batches no slower than the display refreshes them anyway.
     */
    private fun appendToStream(delta: String) {
        if (streamingId == null) return
        pendingDelta.append(delta)
        if (flushJob == null) {
            flushJob = viewModelScope.launch {
                delay(STREAM_FLUSH_MS)
                flushJob = null
                flushPending()
            }
        }
    }

    /** Publish whatever [appendToStream] has queued. Safe to call when empty. */
    private fun flushPending() {
        flushJob?.cancel()
        flushJob = null
        val id = streamingId
        if (id == null || pendingDelta.isEmpty()) {
            pendingDelta.clear()
            return
        }
        val delta = pendingDelta.toString()
        pendingDelta.clear()
        _messages.value = _messages.value.map {
            if (it.id == id) {
                // contentParts is what goes on the wire, and the whole history
                // is replayed every turn. Growing `text` alone would send each
                // finished assistant turn back as an empty text part.
                val grown = it.text + delta
                it.copy(text = grown, contentParts = listOf(ContentPart.Text(grown)))
            } else {
                it
            }
        }
    }

    private fun finishStream() {
        flushPending()
        val id = streamingId
        if (id != null) {
            _messages.value = _messages.value.map { if (it.id == id) it.copy(streaming = false) else it }
            streamingId = null
        }
        activeRequestId = null
        _busy.value = false
    }

    /** The turn a frame belongs to; null for connection, state and RPC frames. */
    private fun AgentWebSocket.Event.requestIdOrNull(): String? = when (this) {
        is AgentWebSocket.Event.StreamStart -> requestId
        is AgentWebSocket.Event.TextDelta -> requestId
        is AgentWebSocket.Event.ToolCall -> requestId
        is AgentWebSocket.Event.ToolFailed -> requestId
        is AgentWebSocket.Event.StreamError -> requestId
        is AgentWebSocket.Event.StreamFinish -> requestId
        is AgentWebSocket.Event.StreamResuming -> requestId
        else -> null
    }

    override fun onCleared() {
        runCatching { connectivity?.unregisterNetworkCallback(networkCallback) }
        session?.cancel()
        transport.disconnect()
        super.onCleared()
    }
}

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

        /**
         * Deltas are batched into [messages] on this cadence rather than per
         * token. A fast stream delivers hundreds of chunks a second and each
         * commit copies the message list and re-parses Markdown, so token-rate
         * commits spend the budget on recomposition; a 50ms window is still
         * within a few frames of the wire and cuts that work ~20x.
         */
        const val STREAM_COMMIT_MS = 50L
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

    /**
     * The streaming message's full text so far, accumulated here and committed
     * to [messages] in [STREAM_COMMIT_MS] batches. Every byte still lands —
     * [flushStream] runs before any code reads the message — but the list copy
     * and Markdown re-parse happen at a bounded rate instead of per token.
     */
    private val streamBuffer = StringBuilder()

    /** Scheduled but not yet run batched commit, if any. */
    private var commitJob: Job? = null

    /** Id of the turn in flight — what a cancel has to be addressed to. */
    private var activeRequestId: String? = null

    /**
     * The turn the reader stopped. Its frames are dropped from here on: chunks
     * already in flight when the cancel lands would otherwise keep appending to
     * a message that has been closed off, and a resume offer for it would put
     * the composer back into busy after the socket blips.
     */
    private var cancelledRequestId: String? = null

    /**
     * Resume once the network comes back.
     *
     * Doze and network handoffs fail DNS while the radio is still returning, so
     * the retry budget is spent against a dead link and the socket stays down
     * for good — the reader comes back to an app that quietly stopped working
     * and has to find the Reconnect link. Retrying on the signal that actually
     * matters makes that recovery invisible instead.
     */
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            viewModelScope.launch {
                if (_connection.value is Connection.Disconnected) endpoint?.let { open(it) }
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

    /**
     * Send one turn. Returns false when the send was refused — busy, empty, or
     * no open socket — so the caller can keep what the reader typed rather
     * than discarding a draft the Worker never received.
     */
    fun submit(text: String): Boolean {
        val trimmed = text.trim()
        if ((trimmed.isEmpty() && _pendingImage.value == null) || _busy.value) return false

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

        return send(history)
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

    private fun send(history: List<ChatMessage>): Boolean {
        val requestId = transport.sendChat(history)
        if (requestId == null) {
            _messages.value = _messages.value + ChatMessage(
                id = UUID.randomUUID().toString(),
                role = ChatRole.SYSTEM,
                text = "Not connected to the Worker. Check the URL in Settings, then reconnect.",
                error = true
            )
            return false
        }
        activeRequestId = requestId
        cancelledRequestId = null
        _busy.value = true
        return true
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
        activeRequestId?.let { requestId ->
            transport.cancelChat(requestId)
            cancelledRequestId = requestId
        }
        // Batched commits mean the last tokens may still be buffered; push
        // them through before deciding whether anything arrived at all.
        flushStream()
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

    /** Rotates the instance id, which is a new Durable Object and a new thread. */
    fun newConversation() {
        viewModelScope.launch {
            prefs.resetInstanceId()
            _messages.value = emptyList()
            streamingId = null
            activeRequestId = null
            cancelledRequestId = null
            _busy.value = false
            _pendingImage.value = null
            endpoint?.let { open(it) }
        }
    }

    private fun open(target: Endpoint) {
        endpoint = target
        session?.cancel()
        _messages.value = emptyList()
        // Servers belong to the agent we were talking to; a new endpoint
        // reports its own set once connected.
        _mcpServers.value = emptyList()
        streamingId = null
        activeRequestId = null
        cancelledRequestId = null
        _busy.value = false
        _pendingImage.value = null

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

    private suspend fun handle(event: AgentWebSocket.Event) {
        cancelledRequestId?.let { if (event.requestIdOrNull() == it) return }
        when (event) {
            is AgentWebSocket.Event.Open -> {
                _connection.value = Connection.Connected
                // Re-assert the saved lane: agent state lives in the Durable
                // Object and may have hibernated or been reset.
                transport.setModelLane(prefs.settings.first().modelLane)
            }

            is AgentWebSocket.Event.Closed -> _connection.value = Connection.Disconnected(null)
            is AgentWebSocket.Event.Failure -> _connection.value = Connection.Disconnected(event.message)

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
    fun addServer(name: String, url: String, token: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _serverBusy.value = true
            _serverError.value = null
            val result = transport.callRpc("addServer", listOf(name, url, token.ifBlank { null }))
            _serverError.value = result.exceptionOrNull()?.message
            _serverBusy.value = false
            onResult(result.isSuccess)
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
        streamBuffer.clear()
        commitJob = null
        _messages.value = _messages.value + ChatMessage(messageId, ChatRole.ASSISTANT, "", streaming = true)
    }

    private fun appendToStream(delta: String) {
        if (streamingId == null) return
        streamBuffer.append(delta)
        // One scheduled commit per window; deltas arriving while one is pending
        // ride along instead of scheduling their own.
        if (commitJob == null) {
            commitJob = viewModelScope.launch {
                delay(STREAM_COMMIT_MS)
                commitJob = null
                commitStream()
            }
        }
    }

    /** Write everything buffered for the streaming message into [messages]. */
    private fun commitStream() {
        val id = streamingId ?: return
        val text = streamBuffer.toString()
        val list = _messages.value
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return
        // contentParts is what goes on the wire, and the whole history is
        // replayed every turn. Growing `text` alone would send each finished
        // assistant turn back as an empty text part.
        val updated = list[index].copy(text = text, contentParts = listOf(ContentPart.Text(text)))
        _messages.value = list.toMutableList().also { it[index] = updated }
    }

    /** Cancel any pending batch and commit now — read paths call this first. */
    private fun flushStream() {
        commitJob?.cancel()
        commitJob = null
        commitStream()
    }

    private fun finishStream() {
        flushStream()
        val id = streamingId
        if (id != null) {
            val list = _messages.value
            val index = list.indexOfFirst { it.id == id }
            if (index >= 0) {
                _messages.value = list.toMutableList().also { it[index] = list[index].copy(streaming = false) }
            }
            streamingId = null
        }
        streamBuffer.clear()
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

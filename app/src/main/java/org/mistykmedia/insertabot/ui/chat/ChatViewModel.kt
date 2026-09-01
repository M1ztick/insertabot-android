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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.mistykmedia.insertabot.data.AppPreferences
import org.mistykmedia.insertabot.data.ChatMessage
import org.mistykmedia.insertabot.data.ChatRole
import org.mistykmedia.insertabot.data.McpServer
import org.mistykmedia.insertabot.network.AgentWebSocket
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

    private var session: Job? = null
    private var endpoint: Endpoint? = null

    /** Id of the assistant message currently being streamed into, if any. */
    private var streamingId: String? = null

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


    fun submit(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _busy.value) return

        val user = ChatMessage(UUID.randomUUID().toString(), ChatRole.USER, trimmed)
        val history = _messages.value + user
        _messages.value = history

        if (transport.sendChat(history) == null) {
            _messages.value = _messages.value + ChatMessage(
                id = UUID.randomUUID().toString(),
                role = ChatRole.SYSTEM,
                text = "Not connected to the Worker. Check the URL in Settings, then reconnect.",
                error = true
            )
            return
        }
        _busy.value = true
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
            _busy.value = false
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
        _busy.value = false

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
                appendToStream("\n\n_[Tool `${event.toolName}` failed: ${event.error}]_")
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

    private fun appendToStream(delta: String) {
        val id = streamingId ?: return
        _messages.value = _messages.value.map {
            if (it.id == id) it.copy(text = it.text + delta) else it
        }
    }

    private fun finishStream() {
        val id = streamingId
        if (id != null) {
            _messages.value = _messages.value.map { if (it.id == id) it.copy(streaming = false) else it }
            streamingId = null
        }
        _busy.value = false
    }

    override fun onCleared() {
        runCatching { connectivity?.unregisterNetworkCallback(networkCallback) }
        session?.cancel()
        transport.disconnect()
        super.onCleared()
    }
}

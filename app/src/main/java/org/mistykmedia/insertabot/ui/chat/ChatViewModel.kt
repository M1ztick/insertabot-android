package org.mistykmedia.insertabot.ui.chat

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mistykmedia.insertabot.data.ChatMessage
import org.mistykmedia.insertabot.data.ChatRole
import java.util.UUID

class ChatViewModel : ViewModel() {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages = _messages.asStateFlow()

    fun submit(text: String) {
        if (text.isBlank()) return
        _messages.value += ChatMessage(UUID.randomUUID().toString(), ChatRole.USER, text.trim())
        _messages.value += ChatMessage(
            UUID.randomUUID().toString(),
            ChatRole.SYSTEM,
            "Chat transport is not connected yet. Configure the Worker in Settings, then finalize the Cloudflare Agents WebSocket protocol in AgentWebSocket.",
            error = true
        )
    }
}

package org.mistykmedia.insertabot.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class ModelLane(val wireValue: String, val label: String) {
    AUTO("auto", "Auto"),
    RESEARCH("research", "Research"),
    CODING("coding", "Coding")
}

data class AppSettings(
    val workerUrl: String = "",
    val bearerToken: String = "",
    val cfAccessClientId: String = "",
    val cfAccessClientSecret: String = "",
    val modelLane: ModelLane = ModelLane.AUTO
)

data class McpServer(
    val id: String = "",
    val name: String,
    val url: String,
    val token: String = "",
    val connected: Boolean = false,
    /** Agents SDK connection state: connecting, authenticating, ready, failed. */
    val state: String = "",
    /** Present while [state] is "authenticating" — open it to finish MCP OAuth. */
    val authUrl: String? = null,
    val error: String? = null
)

enum class ChatRole { USER, ASSISTANT, SYSTEM }

/**
 * A content part inside a multimodal chat message.
 *
 * The wire format matches the Vercel AI SDK / Cloudflare Agents UIMessage
 * `parts` array so that the Worker can pass messages straight to
 * `convertToModelMessages()`.
 */
@Serializable
sealed interface ContentPart {

    @Serializable
    @SerialName("text")
    data class Text(val text: String) : ContentPart

    /**
     * An AI SDK v5 `file` part — the only image shape
     * `convertToModelMessages()` understands.
     *
     * An OpenAI-style `image_url` part is *not* a UIMessage part type: the SDK
     * drops it without complaint and the model then answers as though no image
     * were sent. Keep this identical to what `public/index.js` in
     * `insertabot-cfworker` pushes.
     */
    @Serializable
    @SerialName("file")
    data class File(
        val url: String,
        val mediaType: String,
        val filename: String? = null
    ) : ContentPart
}

data class ChatMessage(
    val id: String,
    val role: ChatRole,
    /** Human-readable summary used in the message list. */
    val text: String,
    /** Wire parts sent to and received from the Worker. */
    val contentParts: List<ContentPart> = listOf(ContentPart.Text(text)),
    val streaming: Boolean = false,
    val error: Boolean = false
)

data class WorkerHealth(
    val ok: Boolean,
    val summary: String,
    val version: String? = null
)

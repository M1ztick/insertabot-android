package org.mistykmedia.insertabot.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One row of the Worker's cross-device conversation index
 * (`GET /api/conversations`, see `src/lib/conversations.ts` in
 * insertabot-cfworker). The same list the web app's history panel shows: both
 * clients sign in with the same email, so they share one set of conversations.
 *
 * Message bodies are not here. Opening a conversation means connecting the
 * agent socket to [id]; the agent sends the transcript on connect.
 */
@Serializable
data class ConversationSummary(
    val id: String,
    val title: String = "",
    val preview: String = "",
    val messageCount: Int = 0,
    /** Epoch milliseconds. */
    val createdAt: Long = 0,
    /** Epoch milliseconds. */
    val updatedAt: Long = 0
) {
    /** What to call it in a list. The Worker titles threads from the first user message. */
    val displayTitle: String get() = title.ifBlank { "New conversation" }
}

@Serializable
data class ConversationPage(
    val conversations: List<ConversationSummary> = emptyList(),
    /** Opaque cursor for the next page; null on the last one. */
    val nextCursor: String? = null
)

/** Answer to `DELETE /api/conversations?confirm=all`; repeat while [remaining] > 0. */
@Serializable
data class DeleteAllResult(val deleted: Int = 0, val remaining: Int = 0)

object ConversationIds {
    /**
     * The Worker's own rule (`CONVERSATION_ID_RE`). Both clients mint UUIDs,
     * but anything the Worker accepts must open here too, or a thread started
     * by some other client could never be opened on the phone.
     */
    private val VALID = Regex("^[A-Za-z0-9_-]{1,128}$")

    fun isValid(id: String?): Boolean = id != null && VALID.matches(id)
}

internal val ConversationJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

internal fun parseConversationPage(body: String): ConversationPage =
    ConversationJson.decodeFromString(ConversationPage.serializer(), body)

internal fun parseConversation(body: String): ConversationSummary =
    ConversationJson.decodeFromString(ConversationSummary.serializer(), body)

internal fun parseDeleteAll(body: String): DeleteAllResult =
    ConversationJson.decodeFromString(DeleteAllResult.serializer(), body)

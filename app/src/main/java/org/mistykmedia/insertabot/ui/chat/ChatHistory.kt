package org.mistykmedia.insertabot.ui.chat

import org.mistykmedia.insertabot.data.ChatMessage
import org.mistykmedia.insertabot.data.ChatRole

/**
 * History to resend when the reader retries the last turn.
 *
 * Everything after the final user message is dropped — the reply being replaced,
 * plus any error card the failed turn left behind. The user message itself is
 * kept, because it *is* the request being reissued and the agent replays the
 * whole history every turn.
 *
 * Returns null when there is nothing to retry, which is the signal to leave the
 * transcript untouched rather than send an empty turn.
 */
internal fun truncateForRetry(messages: List<ChatMessage>): List<ChatMessage>? {
    val lastUser = messages.indexOfLast { it.role == ChatRole.USER && !it.error }
    if (lastUser < 0) return null
    return messages.subList(0, lastUser + 1).toList()
}

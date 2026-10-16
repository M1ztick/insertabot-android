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

/**
 * Whether the transcript should follow new tokens.
 *
 * Pinning to the tail unconditionally fights a reader who has scrolled up to
 * re-read something — the view yanks itself back down on every delta. So the
 * tail is only followed when the viewport is already within [threshold] items
 * of it, which is also true on the first render of an empty list.
 */
internal fun shouldFollowTail(
    lastVisibleIndex: Int?,
    itemCount: Int,
    threshold: Int = 2
): Boolean {
    if (itemCount == 0) return false
    val last = lastVisibleIndex ?: return true
    return last >= itemCount - 1 - threshold
}

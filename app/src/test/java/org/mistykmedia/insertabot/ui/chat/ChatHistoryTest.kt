package org.mistykmedia.insertabot.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mistykmedia.insertabot.data.ChatMessage
import org.mistykmedia.insertabot.data.ChatRole

/**
 * Retry rewrites the transcript before resending it, and the agent replays
 * whatever it is handed. Keeping a dead reply in that history would feed the
 * model its own rejected answer as context for the replacement.
 */
class ChatHistoryTest {

    private fun user(text: String) =
        ChatMessage(id = text, role = ChatRole.USER, text = text)

    private fun assistant(text: String) =
        ChatMessage(id = text, role = ChatRole.ASSISTANT, text = text)

    private fun error(text: String) =
        ChatMessage(id = text, role = ChatRole.SYSTEM, text = text, error = true)

    @Test
    fun `retry drops the reply but keeps the question`() {
        val history = listOf(user("q1"), assistant("a1"), user("q2"), assistant("a2"))
        assertEquals(listOf("q1", "a1", "q2"), truncateForRetry(history)?.map { it.text })
    }

    @Test
    fun `retry drops an error card left by a failed turn`() {
        val history = listOf(user("q1"), error("Not connected to the Worker."))
        assertEquals(listOf("q1"), truncateForRetry(history)?.map { it.text })
    }

    @Test
    fun `retry drops a partial streamed reply`() {
        val history = listOf(user("q1"), assistant("half an ans"))
        assertEquals(listOf("q1"), truncateForRetry(history)?.map { it.text })
    }

    @Test
    fun `a transcript with no user turn has nothing to retry`() {
        assertNull(truncateForRetry(emptyList()))
        assertNull(truncateForRetry(listOf(assistant("greeting"))))
        assertNull(truncateForRetry(listOf(error("Not connected to the Worker."))))
    }

    @Test
    fun `retrying twice is idempotent`() {
        val history = listOf(user("q1"), assistant("a1"))
        val once = truncateForRetry(history)!!
        assertEquals(once.map { it.text }, truncateForRetry(once)?.map { it.text })
    }

    // ── Autoscroll ───────────────────────────────────────────────────────────

    @Test
    fun `the tail is followed when the viewport is already at it`() {
        assertTrue(shouldFollowTail(lastVisibleIndex = 9, itemCount = 10))
    }

    @Test
    fun `the tail is followed from just above it`() {
        // One or two items off the bottom still counts as "reading the tail",
        // so a reply growing by a line does not need a scroll to stay visible.
        assertTrue(shouldFollowTail(lastVisibleIndex = 8, itemCount = 10))
        assertTrue(shouldFollowTail(lastVisibleIndex = 7, itemCount = 10))
    }

    @Test
    fun `scrolling up to re-read is not fought by inbound tokens`() {
        // The regression this guards: every delta yanking the viewport back to
        // the bottom while the reader is looking at something further up.
        assertFalse(shouldFollowTail(lastVisibleIndex = 3, itemCount = 10))
        assertFalse(shouldFollowTail(lastVisibleIndex = 0, itemCount = 10))
    }

    @Test
    fun `an unmeasured list follows the tail`() {
        // First composition reports no visible items; the first message must
        // still land in view.
        assertTrue(shouldFollowTail(lastVisibleIndex = null, itemCount = 1))
    }

    @Test
    fun `an empty transcript has no tail to follow`() {
        assertFalse(shouldFollowTail(lastVisibleIndex = null, itemCount = 0))
    }
}

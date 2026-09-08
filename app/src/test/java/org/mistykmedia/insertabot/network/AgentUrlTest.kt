package org.mistykmedia.insertabot.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The instance id is the conversation identity and goes in the path, so a
 * separator that escaped the segment would silently address a different
 * Durable Object — a thread that reads as "my history vanished".
 */
class AgentUrlTest {

    private val socket = AgentWebSocket()
    private val instance = "0f9c2a11-4c3d-4a1e-9c77-2b8d6e5a1f30"

    @Test
    fun `https worker url becomes a wss agent url`() {
        assertEquals(
            "wss://insertabot.example.workers.dev/agents/chat-agent/$instance",
            socket.agentUrl("https://insertabot.example.workers.dev", instance, "")
        )
    }

    @Test
    fun `trailing slash and surrounding whitespace are trimmed`() {
        assertEquals(
            "wss://insertabot.example.workers.dev/agents/chat-agent/$instance",
            socket.agentUrl("  https://insertabot.example.workers.dev/  ", instance, "")
        )
    }

    @Test
    fun `a blank key adds no query string`() {
        assertTrue(socket.agentUrl("https://w.example.dev", instance, "").endsWith(instance))
        assertTrue(socket.agentUrl("https://w.example.dev", instance, "   ").endsWith(instance))
    }

    @Test
    fun `a key is percent-encoded into the query`() {
        val url = socket.agentUrl("https://w.example.dev", instance, "a b/c&d=e")
        assertEquals("wss://w.example.dev/agents/chat-agent/$instance?ib_key=a+b%2Fc%26d%3De", url)
    }

    @Test
    fun `a separator in the instance id cannot escape its path segment`() {
        val url = socket.agentUrl("https://w.example.dev", "../../other-agent", "")
        assertTrue("path segment escaped: $url", url.endsWith("/agents/chat-agent/..%2F..%2Fother-agent"))
    }

    @Test
    fun `a query separator in the instance id cannot start a query string`() {
        val url = socket.agentUrl("https://w.example.dev", "id?ib_key=stolen", "")
        assertEquals("wss://w.example.dev/agents/chat-agent/id%3Fib_key%3Dstolen", url)
    }
}

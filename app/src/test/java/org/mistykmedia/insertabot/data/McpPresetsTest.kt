package org.mistykmedia.insertabot.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class McpPresetsTest {

    private fun preset(id: String) = McpPresets.all.first { it.id == id }

    @Test
    fun `bearer presets keep the key out of the url`() {
        val (url, token) = preset("github").connection("  ghp_abc123  ")
        assertEquals("https://api.githubcopilot.com/mcp/", url)
        assertEquals("ghp_abc123", token)
    }

    @Test
    fun `query presets put the encoded key in the url and send no token`() {
        // The agent rejects a URL-shaped token, and these servers ignore one.
        val (url, token) = preset("tavily").connection("tvly-a b&c")
        assertEquals("https://mcp.tavily.com/mcp/?tavilyApiKey=tvly-a+b%26c", url)
        assertEquals("", token)
    }

    @Test
    fun `a blank optional key attaches the keyless server`() {
        assertEquals("https://mcp.exa.ai/mcp" to "", preset("exa").connection(""))
        assertEquals("https://mcp.context7.com/mcp" to "", preset("context7").connection("   "))
    }

    @Test
    fun `every preset is https with a key link and a unique id`() {
        val presets = McpPresets.all
        assertEquals(presets.size, presets.map { it.id }.toSet().size)
        presets.forEach {
            assertTrue(it.id, it.url.startsWith("https://"))
            assertTrue(it.id, it.getKeyUrl.startsWith("https://"))
            assertTrue(it.id, it.host.isNotBlank())
        }
    }

    @Test
    fun `attached servers are matched to presets by host`() {
        val attached = McpServer(name = "my search", url = "https://mcp.tavily.com/mcp/?tavilyApiKey=x")
        assertEquals("tavily", McpPresets.matching(attached)?.id)
        assertNull(McpPresets.matching(McpServer(name = "other", url = "https://example.com/mcp")))
        assertNull(McpPresets.matching(McpServer(name = "blank", url = "")))
    }

    @Test
    fun `credential query values are masked and everything else is kept`() {
        assertEquals(
            "https://mcp.exa.ai/mcp?exaApiKey=••••&enabledTools=a",
            McpPresets.redactUrl("https://mcp.exa.ai/mcp?exaApiKey=secret123&enabledTools=a")
        )
        assertEquals(
            "https://x.dev/mcp?access_token=••••#frag",
            McpPresets.redactUrl("https://x.dev/mcp?access_token=abc#frag")
        )
        val plain = "https://api.githubcopilot.com/mcp/"
        assertEquals(plain, McpPresets.redactUrl(plain))
        // An empty value has nothing to hide.
        assertEquals("https://x.dev/?apiKey=", McpPresets.redactUrl("https://x.dev/?apiKey="))
    }

    @Test
    fun `host parsing ignores credentials, ports and paths`() {
        assertEquals("example.com", hostOf("https://user:pw@example.com:8443/a?b#c"))
        assertNull(hostOf("not a url"))
        assertFalse(hostOf("https://mcp.exa.ai/mcp").isNullOrBlank())
    }
}

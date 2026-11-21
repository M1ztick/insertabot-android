package org.mistykmedia.insertabot.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mistykmedia.insertabot.data.McpServer

class TurnActivityTest {

    private val servers = listOf(
        McpServer(id = "3f9a-bc12", name = "Tavily", url = "https://mcp.tavily.com/mcp/"),
        McpServer(id = "gh_77", name = "GitHub", url = "https://api.githubcopilot.com/mcp/")
    )

    @Test
    fun `mcp tool keys map back to the server and action`() {
        assertEquals(ToolLabel("Tavily", "search"), describeTool("tool_3f9abc12_tavily_search", servers))
        assertEquals(ToolLabel("GitHub", "list issues"), describeTool("tool_gh_77_list_issues", servers))
        assertEquals("Using GitHub · list issues", describeTool("tool_gh_77_list_issues", servers).text)
    }

    @Test
    fun `unknown servers and plain tool names still read as words`() {
        assertEquals(ToolLabel(null, "fetch page"), describeTool("tool_zzz_fetch_page", servers))
        assertEquals(ToolLabel(null, "get weather"), describeTool("getWeather", servers))
        assertEquals(ToolLabel(null, "a tool"), describeTool("", servers))
        assertEquals("Using get weather", describeTool("getWeather", emptyList()).text)
    }

    @Test
    fun `an action that is only the server name keeps the name`() {
        assertEquals(ToolLabel("Tavily", "tavily"), describeTool("tool_3f9abc12_tavily", servers))
    }

    @Test
    fun `a turn goes thinking, tool, thinking, writing, done`() {
        val tracker = TurnActivityTracker()
        tracker.turnStarted()
        assertEquals(TurnActivity.Thinking, tracker.current)

        val search = ToolLabel("Tavily", "search")
        tracker.toolStarted("c1", search)
        assertEquals(TurnActivity.UsingTool(search), tracker.current)

        // Reading the result is the gap that used to look frozen.
        tracker.toolEnded("c1")
        assertEquals(TurnActivity.Thinking, tracker.current)

        tracker.text()
        assertNull(tracker.current)

        tracker.turnEnded()
        assertNull(tracker.current)
    }

    @Test
    fun `text or reasoning during a tool call does not hide the tool`() {
        val tracker = TurnActivityTracker()
        tracker.turnStarted()
        tracker.toolStarted("c1", ToolLabel("Tavily", "search"))
        tracker.text()
        tracker.reasoning()
        assertEquals(TurnActivity.UsingTool(ToolLabel("Tavily", "search")), tracker.current)
    }

    @Test
    fun `parallel tool calls show the latest with a count`() {
        val tracker = TurnActivityTracker()
        tracker.turnStarted()
        val a = ToolLabel("Tavily", "search")
        val b = ToolLabel("GitHub", "get file")
        tracker.toolStarted("a", a)
        tracker.toolStarted("b", b)
        assertEquals(TurnActivity.UsingTool(b, others = 1), tracker.current)
        tracker.toolEnded("b")
        assertEquals(TurnActivity.UsingTool(a, others = 0), tracker.current)
    }

    @Test
    fun `a result with no call id retires the oldest call`() {
        val tracker = TurnActivityTracker()
        tracker.turnStarted()
        tracker.toolStarted("a", ToolLabel(null, "one"))
        tracker.toolStarted("b", ToolLabel(null, "two"))
        tracker.toolEnded("")
        assertEquals(TurnActivity.UsingTool(ToolLabel(null, "two")), tracker.current)
    }

    @Test
    fun `reasoning after text brings thinking back`() {
        val tracker = TurnActivityTracker()
        tracker.turnStarted()
        tracker.text()
        tracker.reasoning()
        assertEquals(TurnActivity.Thinking, tracker.current)
    }
}

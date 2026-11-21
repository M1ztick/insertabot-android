package org.mistykmedia.insertabot.ui.chat

import org.mistykmedia.insertabot.data.McpServer

/**
 * What the agent is doing during a turn when it is not visibly writing.
 *
 * Without this the screen goes still in exactly the moments that feel broken:
 * before the first word, while a tool runs, and while the model reads a tool's
 * result. The reader cannot tell "working" from "stuck".
 */
sealed interface TurnActivity {
    /** Working on the answer: before the first token, or between tool calls. */
    data object Thinking : TurnActivity

    /** A tool call is in flight. [others] counts any running alongside it. */
    data class UsingTool(val label: ToolLabel, val others: Int = 0) : TurnActivity
}

/** A tool call described for a person: which service, and what it is doing there. */
data class ToolLabel(val server: String?, val action: String) {
    val text: String
        get() = if (server != null) "Using $server · $action" else "Using $action"
}

/**
 * Turn an agent tool key into words.
 *
 * The Agents SDK names MCP tools `tool_<serverId without dashes>_<toolName>`,
 * and the agent reports each attached server's id and name, so the key maps
 * back to "Tavily · search" rather than `tool_3f9a…_tavily_search`.
 */
fun describeTool(toolKey: String, servers: List<McpServer>): ToolLabel {
    if (!toolKey.startsWith("tool_")) return ToolLabel(null, humanize(toolKey))
    val rest = toolKey.removePrefix("tool_")
    val server = servers
        .filter { it.id.isNotBlank() }
        .firstOrNull { rest.startsWith(it.id.replace("-", "") + "_") }
    if (server != null) {
        val toolName = rest.removePrefix(server.id.replace("-", "") + "_")
        // "tavily_search" on the Tavily server reads better as just "search".
        val action = humanize(toolName)
            .removePrefix(server.name.lowercase() + " ")
            .ifBlank { humanize(toolName) }
        return ToolLabel(server.name, action)
    }
    // A server the list does not know (yet): drop its id rather than show it.
    return ToolLabel(null, humanize(rest.substringAfter('_', rest)))
}

internal fun humanize(name: String): String =
    name.replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
        .replace('_', ' ')
        .replace('-', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
        .lowercase()
        .ifBlank { "a tool" }

/**
 * The state machine behind [TurnActivity], kept free of Android and coroutines
 * so it is unit-tested directly. The view model feeds it stream events and
 * publishes [current].
 *
 * Several tool calls can run at once (parallel tool calls), so they are tracked
 * by call id; the most recent one is shown, with a count of the rest.
 */
class TurnActivityTracker {
    private val tools = LinkedHashMap<String, ToolLabel>()

    var current: TurnActivity? = null
        private set

    /** A turn was sent; nothing has come back yet. */
    fun turnStarted() {
        tools.clear()
        current = TurnActivity.Thinking
    }

    /** The model is reasoning (it streams reasoning before, or instead of, text). */
    fun reasoning() {
        if (tools.isEmpty()) current = TurnActivity.Thinking
    }

    /** Text is arriving: the words themselves are the progress. */
    fun text() {
        if (tools.isEmpty()) current = null
    }

    fun toolStarted(callId: String, label: ToolLabel) {
        tools[callId] = label
        current = usingTool()
    }

    /**
     * A tool call finished, successfully or not. The model now reads its
     * result before saying anything, which used to look like a freeze, so
     * this goes back to Thinking rather than to nothing.
     */
    fun toolEnded(callId: String) {
        if (tools.remove(callId) == null && callId.isBlank()) {
            // A result with no id: retire the oldest call rather than none.
            tools.keys.firstOrNull()?.let { tools.remove(it) }
        }
        current = if (tools.isEmpty()) TurnActivity.Thinking else usingTool()
    }

    /** The turn finished, was stopped, or its socket went away. */
    fun turnEnded() {
        tools.clear()
        current = null
    }

    private fun usingTool() = TurnActivity.UsingTool(tools.values.last(), tools.size - 1)
}

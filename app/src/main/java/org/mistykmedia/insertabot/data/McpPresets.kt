package org.mistykmedia.insertabot.data

import java.net.URLEncoder

/** How a preset's API key reaches the MCP server. */
sealed interface KeyPlacement {
    /** Sent as `Authorization: Bearer <key>` (the agent's `token` argument). */
    data object BearerHeader : KeyPlacement

    /** Appended to the server URL as `?<param>=<key>`. */
    data class QueryParam(val param: String) : KeyPlacement
}

/**
 * A known MCP server that can be attached with one tap and, at most, one key.
 *
 * Addresses and key placement follow each provider's own docs (checked
 * Nov 2026). A provider changing either breaks only its preset; the custom
 * server form still works for anything.
 */
data class McpPreset(
    val id: String,
    /** Also the server name sent to the agent, which keys "already attached". */
    val name: String,
    val description: String,
    val url: String,
    val keyPlacement: KeyPlacement,
    /** False when the server works without a key, just at lower limits. */
    val keyRequired: Boolean,
    val keyLabel: String,
    /** Where to sign up for the key. Opened in the browser. */
    val getKeyUrl: String,
    /** One line on what the key unlocks or how to make it. */
    val keyHelp: String
) {
    /** The server address, so a preset can be matched to an attached server. */
    val host: String get() = hostOf(url).orEmpty()

    /**
     * The URL and Bearer token to hand to the agent's `addServer` for [key].
     * A blank optional key attaches the keyless form of the server.
     */
    fun connection(key: String): Pair<String, String> {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return url to ""
        return when (keyPlacement) {
            KeyPlacement.BearerHeader -> url to trimmed
            is KeyPlacement.QueryParam -> {
                val separator = if ('?' in url) '&' else '?'
                val encoded = URLEncoder.encode(trimmed, "UTF-8")
                "$url$separator${keyPlacement.param}=$encoded" to ""
            }
        }
    }
}

object McpPresets {

    val all: List<McpPreset> = listOf(
        McpPreset(
            id = "tavily",
            name = "Tavily",
            description = "Search the web and read pages for up-to-date answers.",
            url = "https://mcp.tavily.com/mcp/",
            keyPlacement = KeyPlacement.QueryParam("tavilyApiKey"),
            keyRequired = true,
            keyLabel = "Tavily API key",
            getKeyUrl = "https://app.tavily.com/home",
            keyHelp = "Free to sign up. Your key is on the Tavily dashboard."
        ),
        McpPreset(
            id = "exa",
            name = "Exa",
            description = "AI web search, plus search across code and docs.",
            url = "https://mcp.exa.ai/mcp",
            keyPlacement = KeyPlacement.QueryParam("exaApiKey"),
            keyRequired = false,
            keyLabel = "Exa API key",
            getKeyUrl = "https://dashboard.exa.ai/api-keys",
            keyHelp = "Works without a key at lower limits. A key raises them."
        ),
        McpPreset(
            id = "github",
            name = "GitHub",
            description = "Read and work with your repositories, issues and pull requests.",
            url = "https://api.githubcopilot.com/mcp/",
            keyPlacement = KeyPlacement.BearerHeader,
            keyRequired = true,
            keyLabel = "GitHub personal access token",
            getKeyUrl = "https://github.com/settings/personal-access-tokens/new",
            keyHelp = "Create a fine-grained token with access to the repos you want the bot to see."
        ),
        McpPreset(
            id = "firecrawl",
            name = "Firecrawl",
            description = "Scrape and search websites, turning pages into clean text.",
            url = "https://mcp.firecrawl.dev/v2/mcp",
            keyPlacement = KeyPlacement.BearerHeader,
            keyRequired = false,
            keyLabel = "Firecrawl API key",
            getKeyUrl = "https://www.firecrawl.dev/app/api-keys",
            keyHelp = "Works without a key for basic scraping. A key unlocks crawling and higher limits."
        ),
        McpPreset(
            id = "context7",
            name = "Context7",
            description = "Current documentation for programming libraries and frameworks.",
            url = "https://mcp.context7.com/mcp",
            keyPlacement = KeyPlacement.BearerHeader,
            keyRequired = false,
            keyLabel = "Context7 API key",
            getKeyUrl = "https://context7.com/dashboard",
            keyHelp = "Works without a key. A free key raises rate limits."
        )
    )

    /**
     * The preset an attached server came from, matched by host so a server
     * added through the custom form with the same address still counts.
     */
    fun matching(server: McpServer): McpPreset? {
        val host = hostOf(server.url) ?: return null
        return all.firstOrNull { it.host.equals(host, ignoreCase = true) }
    }

    private val SECRET_PARAM = Regex("key|token|secret|password|auth", RegexOption.IGNORE_CASE)

    /**
     * [url] with the values of credential-looking query parameters masked.
     *
     * Some servers take their key in the URL, and the agent reports attached
     * servers by URL. Shown as-is, the server list would put the key on screen
     * (and in every screenshot of it).
     */
    fun redactUrl(url: String): String {
        val queryStart = url.indexOf('?')
        if (queryStart < 0) return url
        val fragmentStart = url.indexOf('#', queryStart).let { if (it < 0) url.length else it }
        val query = url.substring(queryStart + 1, fragmentStart)
        val masked = query.split('&').joinToString("&") { pair ->
            val eq = pair.indexOf('=')
            if (eq > 0 && SECRET_PARAM.containsMatchIn(pair.substring(0, eq)) && eq < pair.length - 1) {
                pair.substring(0, eq + 1) + "••••"
            } else {
                pair
            }
        }
        return url.substring(0, queryStart + 1) + masked + url.substring(fragmentStart)
    }
}

/** Host of an http(s) URL, without android.net.Uri so it runs in JVM tests. */
internal fun hostOf(url: String): String? {
    val afterScheme = url.substringAfter("://", missingDelimiterValue = "")
    if (afterScheme.isEmpty()) return null
    val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
    val host = authority.substringAfter('@').substringBefore(':')
    return host.ifBlank { null }
}

package org.mistykmedia.insertabot.data

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
    val connected: Boolean = false
)

enum class ChatRole { USER, ASSISTANT, SYSTEM }

data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val text: String,
    val streaming: Boolean = false,
    val error: Boolean = false
)

data class WorkerHealth(
    val ok: Boolean,
    val summary: String,
    val version: String? = null
)

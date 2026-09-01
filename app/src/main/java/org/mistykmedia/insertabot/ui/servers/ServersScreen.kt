package org.mistykmedia.insertabot.ui.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.mistykmedia.insertabot.data.McpServer
import org.mistykmedia.insertabot.ui.chat.ChatViewModel

@Composable
fun ServersScreen(padding: PaddingValues, viewModel: ChatViewModel = viewModel()) {
    val servers by viewModel.mcpServers.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val busy by viewModel.serverBusy.collectAsStateWithLifecycle()
    val error by viewModel.serverError.collectAsStateWithLifecycle()

    val connected = connection is ChatViewModel.Connection.Connected
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().padding(padding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("MCP servers", style = MaterialTheme.typography.headlineMedium)

        if (!connected) {
            Text(
                "Connect to the agent before attaching servers.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (servers.isEmpty() && connected) {
            Text("No servers attached.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        LazyColumn(
            Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(servers, key = { it.id.ifBlank { it.name } }) { server ->
                ServerCard(server, enabled = !busy, onRemove = { viewModel.removeServer(server.id.ifBlank { server.name }) })
            }
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        OutlinedTextField(
            name, { name = it }, Modifier.fillMaxWidth(),
            label = { Text("Name") }, singleLine = true, enabled = connected && !busy
        )
        OutlinedTextField(
            url, { url = it }, Modifier.fillMaxWidth(),
            label = { Text("MCP server URL") }, singleLine = true, enabled = connected && !busy
        )
        OutlinedTextField(
            token, { token = it }, Modifier.fillMaxWidth(),
            // The agent rejects a URL here — some servers take their key as a
            // query parameter on the URL instead, and that mix-up is common.
            label = { Text("Access token (optional)") }, singleLine = true, enabled = connected && !busy
        )

        Button(
            onClick = {
                viewModel.addServer(name.trim(), url.trim(), token.trim())
                name = ""; url = ""; token = ""
            },
            enabled = connected && !busy && name.isNotBlank() && url.startsWith("https://")
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Connect")
        }
    }
}

@Composable
private fun ServerCard(server: McpServer, enabled: Boolean, onRemove: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(server.name, style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = onRemove, enabled = enabled) { Text("Remove") }
            }

            if (server.url.isNotBlank()) {
                Text(
                    server.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                server.state.ifBlank { if (server.connected) "ready" else "unknown" },
                style = MaterialTheme.typography.labelMedium,
                color = if (server.connected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )

            // OAuth servers park in "authenticating" until this is opened.
            server.authUrl?.let { auth ->
                TextButton(onClick = { uriHandler.openUri(auth) }) { Text("Finish sign-in") }
            }

            server.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

package org.mistykmedia.insertabot.ui.servers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
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

    val urlValid = url.isBlank() || url.startsWith("https://")

    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column {
            Text("MCP servers", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Tools the agent can call during a conversation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box(Modifier.weight(1f)) {
            when {
                !connected -> Hint("Connect to the agent before attaching servers.")

                servers.isEmpty() -> EmptyServers()

                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(servers, key = { it.id.ifBlank { it.name } }) { server ->
                        ServerCard(
                            server,
                            enabled = !busy,
                            onRemove = { viewModel.removeServer(server.id.ifBlank { server.name }) }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(visible = error != null) {
            error?.let {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(it, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        OutlinedTextField(
            name, { name = it }, Modifier.fillMaxWidth(),
            label = { Text("Name") },
            singleLine = true,
            enabled = connected && !busy,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )
        OutlinedTextField(
            url, { url = it }, Modifier.fillMaxWidth(),
            label = { Text("MCP server URL") },
            isError = !urlValid,
            supportingText = if (!urlValid) {
                { Text("Must start with https://") }
            } else null,
            singleLine = true,
            enabled = connected && !busy,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next
            )
        )
        OutlinedTextField(
            token, { token = it }, Modifier.fillMaxWidth(),
            // The agent rejects a URL here — some servers take their key as a
            // query parameter on the URL instead, and that mix-up is common.
            label = { Text("Access token (optional)") },
            singleLine = true,
            enabled = connected && !busy,
            // A server key is a credential like any other; it is not shown.
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            )
        )

        Button(
            onClick = {
                viewModel.addServer(name.trim(), url.trim(), token.trim())
                name = ""; url = ""; token = ""
            },
            enabled = connected && !busy && name.isNotBlank() && url.startsWith("https://"),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Text("Connect server")
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun EmptyServers() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.Dns,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(12.dp))
        Text("No servers attached", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.size(4.dp))
        Text(
            "Attach an MCP server below to give the agent extra tools.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ServerCard(server: McpServer, enabled: Boolean, onRemove: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // A coloured dot reads as status at a glance, where the
                    // state word alone needs parsing on every card.
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(statusColor(server), CircleShape)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(server.name, style = MaterialTheme.typography.titleSmall)
                }
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
                color = statusColor(server)
            )

            // OAuth servers park in "authenticating" until this is opened.
            server.authUrl?.let { auth ->
                TextButton(onClick = { uriHandler.openUri(auth) }) { Text("Finish sign-in") }
            }

            server.error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun statusColor(server: McpServer): Color = when {
    server.error != null || server.state == "failed" -> MaterialTheme.colorScheme.error
    server.connected -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

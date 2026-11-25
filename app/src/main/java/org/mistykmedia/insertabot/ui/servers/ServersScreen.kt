package org.mistykmedia.insertabot.ui.servers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.mistykmedia.insertabot.data.McpPreset
import org.mistykmedia.insertabot.data.McpPresets
import org.mistykmedia.insertabot.data.McpServer
import org.mistykmedia.insertabot.ui.chat.ChatViewModel

@Composable
fun ServersScreen(padding: PaddingValues, viewModel: ChatViewModel = viewModel()) {
    val servers by viewModel.mcpServers.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val busy by viewModel.serverBusy.collectAsStateWithLifecycle()
    val error by viewModel.serverError.collectAsStateWithLifecycle()
    val accountTools by viewModel.accountTools.collectAsStateWithLifecycle()

    val connected = connection is ChatViewModel.Connection.Connected
    var adding by remember { mutableStateOf<McpPreset?>(null) }
    var showCustom by rememberSaveable { mutableStateOf(false) }
    // Hosts already attached, so a preset can say "Added" instead of offering
    // a second copy the agent would refuse anyway.
    val attachedPresetIds = servers.mapNotNull { McpPresets.matching(it)?.id }.toSet()

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .imePadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column {
                Text(
                    "Tools",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    "Services the bot can use while it answers, like web search or GitHub.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!connected) {
            item {
                Notice(
                    if (connection is ChatViewModel.Connection.SignInRequired) {
                        "Sign in on the Chat tab, then come back here to add tools."
                    } else {
                        "Waiting for the connection to the bot. Tools can be added once it's connected."
                    }
                )
            }
        }

        // Shown here only while no dialog is open; the dialog shows its own.
        if (adding == null && error != null) {
            item { ErrorBanner(error.orEmpty()) }
        }

        item {
            Column {
                SectionTitle("Your tools")
                Text(
                    if (accountTools) {
                        "Saved to your account: every conversation can use them, here and on the web."
                    } else {
                        "These belong to this conversation. A new chat starts without them."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (servers.isEmpty()) {
            item {
                Text(
                    if (connected) "None yet. Add one below." else "None yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(servers, key = { "attached:" + it.id.ifBlank { it.name } }) { server ->
                ServerCard(
                    server,
                    enabled = !busy,
                    onRemove = { viewModel.removeServer(server.id.ifBlank { server.name }) }
                )
            }
        }

        item { SectionTitle("Add a tool", Modifier.padding(top = 8.dp)) }
        items(McpPresets.all, key = { "preset:" + it.id }) { preset ->
            PresetCard(
                preset = preset,
                added = preset.id in attachedPresetIds,
                enabled = connected && !busy,
                onAdd = { adding = preset }
            )
        }

        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { showCustom = !showCustom },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Custom server",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() }
                )
                Icon(
                    if (showCustom) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (showCustom) "Hide custom server" else "Show custom server",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        item {
            AnimatedVisibility(visible = showCustom) {
                CustomServerForm(
                    enabled = connected && !busy,
                    busy = busy,
                    onConnect = { name, url, token, onAccepted ->
                        viewModel.addServer(name, url, token) { if (it) onAccepted() }
                    }
                )
            }
        }
    }

    adding?.let { preset ->
        AddPresetDialog(
            preset = preset,
            busy = busy,
            error = error,
            onDismiss = { if (!busy) adding = null },
            onConnect = { key ->
                val (url, token) = preset.connection(key)
                viewModel.addServer(preset.name, url, token) { accepted ->
                    if (accepted) adding = null
                }
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.semantics { heading() }
    )
}

@Composable
private fun Notice(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ErrorBanner(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
    }
}

/** One catalog entry: what it does, and a single button to add it. */
@Composable
private fun PresetCard(preset: McpPreset, added: Boolean, enabled: Boolean, onAdd: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(preset.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    preset.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (preset.keyRequired) "Needs an API key" else "API key optional",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (added) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(4.dp))
                    Text("Added", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
                }
            } else {
                FilledTonalButton(onClick = onAdd, enabled = enabled) { Text("Add") }
            }
        }
    }
}

/**
 * The only form a preset needs: one key, with a link to where it comes from.
 * The address and how the key is sent are the preset's business, not the
 * user's.
 */
@Composable
private fun AddPresetDialog(
    preset: McpPreset,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConnect: (String) -> Unit
) {
    val uriHandler = LocalUriHandler.current
    var key by remember(preset.id) { mutableStateOf("") }
    var revealed by remember(preset.id) { mutableStateOf(false) }
    // The view model keeps the last error until the next call; only show one
    // this dialog's own attempt produced.
    var attempted by remember(preset.id) { mutableStateOf(false) }
    val canConnect = !busy && (key.isNotBlank() || !preset.keyRequired)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add ${preset.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(preset.description, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (preset.keyRequired) preset.keyLabel else "${preset.keyLabel} (optional)") },
                    supportingText = { Text(preset.keyHelp) },
                    singleLine = true,
                    enabled = !busy,
                    visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    trailingIcon = {
                        IconButton(onClick = { revealed = !revealed }) {
                            Icon(
                                if (revealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (revealed) "Hide key" else "Show key"
                            )
                        }
                    }
                )
                TextButton(onClick = { uriHandler.openUri(preset.getKeyUrl) }) {
                    Icon(
                        Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(if (preset.keyRequired) "Get a key" else "Get a key (optional)")
                }
                if (attempted && !busy && error != null) ErrorBanner(error)
            }
        },
        confirmButton = {
            Button(
                onClick = { attempted = true; onConnect(key) },
                enabled = canConnect
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Connect")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
        }
    )
}

/** Anything not in the catalog: name, address and an optional Bearer token. */
@Composable
private fun CustomServerForm(
    enabled: Boolean,
    busy: Boolean,
    onConnect: (name: String, url: String, token: String, onAccepted: () -> Unit) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    val urlValid = url.isBlank() || url.startsWith("https://")

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "For any other MCP server. If it takes its key in the URL " +
                "(like ?apiKey=…), put it there and leave the token empty.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            name, { name = it }, Modifier.fillMaxWidth(),
            label = { Text("Name") },
            singleLine = true,
            enabled = enabled,
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
            enabled = enabled,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next
            )
        )
        OutlinedTextField(
            token, { token = it }, Modifier.fillMaxWidth(),
            label = { Text("Access token (optional)") },
            singleLine = true,
            enabled = enabled,
            // A server key is a credential like any other; it is not shown.
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            )
        )
        Button(
            onClick = {
                // Keep the form when the agent refuses the server: retyping a
                // secret is the worst kind of input to lose.
                onConnect(name.trim(), url.trim(), token.trim()) { name = ""; url = ""; token = "" }
            },
            enabled = enabled && name.isNotBlank() && url.startsWith("https://"),
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
private fun ServerCard(server: McpServer, enabled: Boolean, onRemove: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val preset = McpPresets.matching(server)
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
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

            // A catalog server is described in words; anything else by its
            // address, with any key in it masked.
            val detail = preset?.description ?: McpPresets.redactUrl(server.url)
            if (detail.isNotBlank()) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                statusLabel(server),
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

/** The agent's connection state in words a non-developer reads at a glance. */
private fun statusLabel(server: McpServer): String = when {
    server.error != null || server.state == "failed" -> "Couldn't connect"
    server.state == "authenticating" -> "Needs sign-in"
    server.state == "connecting" || server.state == "discovering" -> "Connecting…"
    server.connected || server.state == "ready" -> "Ready"
    else -> server.state.ifBlank { "Unknown" }
}

@Composable
private fun statusColor(server: McpServer): Color = when {
    server.error != null || server.state == "failed" -> MaterialTheme.colorScheme.error
    server.connected -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

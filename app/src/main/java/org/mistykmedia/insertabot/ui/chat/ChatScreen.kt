package org.mistykmedia.insertabot.ui.chat

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.mistykmedia.insertabot.data.ChatRole

@Composable
fun ChatScreen(padding: PaddingValues, viewModel: ChatViewModel = viewModel()) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Follow the tail as tokens stream in.
    LaunchedEffect(messages.size, messages.lastOrNull()?.text) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("InsertaBot", style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = { viewModel.newConversation() }, enabled = !busy) { Text("New chat") }
        }

        ConnectionBanner(connection, onReconnect = viewModel::reconnect)

        if (messages.isEmpty() && connection is ChatViewModel.Connection.Connected) {
            Text("Connected. Send a message to start the conversation.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        LazyColumn(Modifier.weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages, key = { it.id }) { message ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(message.role.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium)
                        if (message.text.isBlank() && message.streaming) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else if (message.role == ChatRole.ASSISTANT && !message.error) {
                            // Only the model answers in Markdown. What the user
                            // typed is shown as typed, and errors stay plain.
                            MarkdownText(message.text, MaterialTheme.colorScheme.onSurface)
                        } else {
                            Text(message.text, color = if (message.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                label = { Text("Message") },
                enabled = !busy,
                minLines = 1,
                maxLines = 4
            )
            Button(
                onClick = { viewModel.submit(draft); draft = "" },
                enabled = draft.isNotBlank() && !busy && connection is ChatViewModel.Connection.Connected
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Send")
            }
        }
    }
}

@Composable
private fun ConnectionBanner(connection: ChatViewModel.Connection, onReconnect: () -> Unit) {
    when (connection) {
        is ChatViewModel.Connection.Unconfigured ->
            Text("Set your Worker URL under Settings to connect.", color = MaterialTheme.colorScheme.onSurfaceVariant)

        is ChatViewModel.Connection.Connecting ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Text("Connecting to the agent…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

        is ChatViewModel.Connection.Connected -> Unit

        is ChatViewModel.Connection.Disconnected ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    connection.reason?.let { "Disconnected: $it" } ?: "Disconnected.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onReconnect) { Text("Reconnect") }
            }
    }
}

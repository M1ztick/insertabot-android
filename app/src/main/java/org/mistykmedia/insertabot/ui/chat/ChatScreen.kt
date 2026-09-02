package org.mistykmedia.insertabot.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.mistykmedia.insertabot.data.ChatRole
import org.mistykmedia.insertabot.ui.uriToJpegBase64

@Composable
fun ChatScreen(padding: PaddingValues, viewModel: ChatViewModel = viewModel()) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val pendingImage by viewModel.pendingImage.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Follow the tail as tokens stream in.
    LaunchedEffect(messages.size, messages.lastOrNull()?.text) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                uriToJpegBase64(context, uri)?.let { dataUri ->
                    viewModel.attachImage(dataUri)
                }
            }
        }
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

                        // Render any image parts from the user message.
                        if (message.role == ChatRole.USER) {
                            RenderUserImages(message.contentParts)
                        }

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

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            pendingImage?.let { imageUri ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = "Selected image",
                        modifier = Modifier.size(64.dp)
                    )
                    IconButton(onClick = { viewModel.clearPendingImage() }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove image"
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = !busy
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = "Attach image"
                    )
                }

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
                    enabled = (draft.isNotBlank() || pendingImage != null) && !busy && connection is ChatViewModel.Connection.Connected
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Icon(imageVector = Icons.Default.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
private fun RenderUserImages(parts: List<org.mistykmedia.insertabot.data.ContentPart>) {
    val imageUrls = parts.filterIsInstance<org.mistykmedia.insertabot.data.ContentPart.ImageUrl>()
        .map { it.image_url.url }
    if (imageUrls.isEmpty()) return

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        imageUrls.forEach { url ->
            AsyncImage(
                model = url,
                contentDescription = "Attached image",
                modifier = Modifier.size(96.dp)
            )
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

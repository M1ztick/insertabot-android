package org.mistykmedia.insertabot.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.DragInteraction
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mistykmedia.insertabot.R
import org.mistykmedia.insertabot.data.ChatMessage
import org.mistykmedia.insertabot.data.ChatRole
import org.mistykmedia.insertabot.data.ContentPart
import org.mistykmedia.insertabot.ui.uriToJpegBase64

/** Openers offered on the empty transcript, so the first turn is one tap away. */
private val SUGGESTIONS = listOf(
    "What can you help me with?",
    "Summarise today's AI news",
    "Review this code for bugs"
)

@Composable
fun ChatScreen(padding: PaddingValues, viewModel: ChatViewModel = viewModel()) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val pendingImage by viewModel.pendingImage.collectAsStateWithLifecycle()
    val canRetry by viewModel.canRetry.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val connected = connection is ChatViewModel.Connection.Connected

    // Follow the reply unless the reader has taken over the scroll.
    //
    // This is decided by intent, not position: a streaming reply is a single
    // list item, so "is the last item visible" stays true the whole time the
    // reader is scrolled up inside it, and every token yanked them back down.
    // A drag detaches; coming to rest at the very bottom, or tapping the
    // jump button, re-attaches.
    var following by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect {
            if (it is DragInteraction.Start) following = false
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress || listState.canScrollForward }
            .collect { awayFromBottom -> if (!awayFromBottom) following = true }
    }
    // A snap, not an animation: tokens arrive many times a second, and each
    // one cancelling and restarting an animated scroll is what made the whole
    // screen shudder while a reply streamed in.
    LaunchedEffect(messages.size, messages.lastOrNull()?.text) {
        if (messages.isNotEmpty() && following) listState.snapToEnd(messages.lastIndex)
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

    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .imePadding()
    ) {
        ChatHeader(
            messageCount = messages.size,
            busy = busy,
            onNewChat = viewModel::newConversation
        )

        // A reconnect usually finishes inside a second. Showing the banner for
        // it pushed the whole transcript down and back up again — so a socket
        // blip only surfaces if it outlasts that.
        var shownConnection by remember { mutableStateOf(connection) }
        LaunchedEffect(connection) {
            if (connection is ChatViewModel.Connection.Connecting &&
                shownConnection is ChatViewModel.Connection.Connected
            ) {
                delay(1_500)
            }
            shownConnection = connection
        }
        ConnectionBanner(shownConnection, onReconnect = viewModel::reconnect)

        Box(Modifier.weight(1f)) {
            if (messages.isEmpty()) {
                EmptyState(
                    connected = connected,
                    onSuggestion = { viewModel.submit(it) }
                )
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        onCopy = { clipboard.setText(AnnotatedString(message.text)) },
                        // Placement animation belongs to the list, not the item:
                        // a new turn settles into position instead of appearing
                        // fully formed under the one above it.
                        modifier = Modifier.animateItem()
                    )
                }
            }

            // Jumping back to the tail is only offered once the reader has
            // actually left it, so it never covers the last reply by default.
            // Boxed so the transition resolves against a single receiver — the
            // enclosing Column and this Box both offer an AnimatedVisibility.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = !following && messages.isNotEmpty(),
                    enter = fadeIn() + scaleIn(initialScale = 0.8f),
                    exit = fadeOut() + scaleOut(targetScale = 0.8f)
                ) {
                    FilledIconButton(
                        onClick = {
                            following = true
                            scope.launch { listState.snapToEnd(messages.lastIndex) }
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = "Scroll to latest")
                    }
                }
            }
        }

        Composer(
            draft = draft,
            onDraftChange = { draft = it },
            pendingImage = pendingImage,
            busy = busy,
            connected = connected,
            canRetry = canRetry && !busy,
            onPickImage = {
                photoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onClearImage = viewModel::clearPendingImage,
            onSend = { viewModel.submit(draft); draft = "" },
            onStop = viewModel::stop,
            onRetry = viewModel::retry
        )
    }
}

@Composable
private fun ChatHeader(messageCount: Int, busy: Boolean, onNewChat: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("InsertaBot", style = MaterialTheme.typography.headlineMedium)
        // A reset is meaningless on an empty thread, and offering it there
        // invites a tap that appears to do nothing.
        if (messageCount > 0) {
            TextButton(onClick = onNewChat, enabled = !busy) { Text("New chat") }
        }
    }
}

@Composable
private fun EmptyState(connected: Boolean, onSuggestion: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.insertabot_logo),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.heightIn(max = 200.dp)
        )
        Spacer(Modifier.size(20.dp))
        Text(
            "Ask InsertaBot anything",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.size(6.dp))
        Text(
            if (connected) "Your conversation stays on this device and your own Worker."
            else "Connect to your Worker to start chatting.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        // Suggestions are only actionable once there is a socket to send on.
        if (connected) {
            Spacer(Modifier.size(24.dp))
            SUGGESTIONS.forEach { suggestion ->
                AssistChip(
                    onClick = { onSuggestion(suggestion) },
                    label = { Text(suggestion) },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fromUser = message.role == ChatRole.USER
    var showActions by remember { mutableStateOf(false) }

    val container = when {
        message.error -> MaterialTheme.colorScheme.errorContainer
        fromUser -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when {
        message.error -> MaterialTheme.colorScheme.onErrorContainer
        fromUser -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = if (fromUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = container,
            contentColor = content,
            // The square corner marks the speaker's own side, so a glance at
            // the shape says who is talking even in a dense transcript.
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomEnd = if (fromUser) 4.dp else 18.dp,
                bottomStart = if (fromUser) 18.dp else 4.dp
            ),
            modifier = Modifier
                .widthIn(max = 340.dp)
                .clickable { showActions = !showActions }
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (fromUser) RenderUserImages(message.contentParts)

                when {
                    message.text.isBlank() && message.streaming -> TypingIndicator()

                    message.text.isBlank() -> Unit

                    message.role == ChatRole.ASSISTANT && !message.error ->
                        // Only the model answers in Markdown. What the user
                        // typed is shown as typed, and errors stay plain.
                        MarkdownText(message.text, content)

                    else -> Text(message.text, color = content)
                }
            }
        }

        // Copy is revealed on tap rather than always shown: a row of controls
        // under every bubble turns a transcript into a wall of buttons.
        AnimatedVisibility(visible = showActions && message.text.isNotBlank()) {
            TextButton(onClick = onCopy, modifier = Modifier.padding(top = 2.dp)) {
                Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.size(6.dp))
                Text("Copy", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** Three pulsing dots — the agent is working but has not emitted a token yet. */
@Composable
private fun TypingIndicator() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = InfiniteRepeatableSpec(
                    animation = tween(600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 160)
                ),
                label = "dot$index"
            )
            Box(
                Modifier
                    .size(7.dp)
                    .alpha(alpha)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
            )
        }
    }
}

@Composable
private fun Composer(
    draft: String,
    onDraftChange: (String) -> Unit,
    pendingImage: String?,
    busy: Boolean,
    connected: Boolean,
    canRetry: Boolean,
    onPickImage: () -> Unit,
    onClearImage: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onRetry: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            pendingImage?.let { imageUri ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        AsyncImage(
                            model = imageUri,
                            contentDescription = "Selected image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    }
                    IconButton(onClick = onClearImage) {
                        Icon(Icons.Outlined.Close, contentDescription = "Remove image")
                    }
                }
            }

            // Retry sits above the composer, not inside the send slot: it acts
            // on the reply already on screen rather than on what is typed.
            AnimatedVisibility(visible = canRetry) {
                TextButton(onClick = onRetry) {
                    Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.size(6.dp))
                    Text("Regenerate reply", style = MaterialTheme.typography.labelMedium)
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                IconButton(onClick = onPickImage, enabled = !busy) {
                    Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = "Attach image")
                }

                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message InsertaBot…") },
                    enabled = !busy,
                    shape = RoundedCornerShape(22.dp),
                    minLines = 1,
                    maxLines = 5
                )

                // The same slot sends and stops. A turn that went after the
                // wrong thing is abandoned where the reader is already looking,
                // rather than from a control that only exists while streaming.
                if (busy) {
                    FilledIconButton(
                        onClick = onStop,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop generating")
                    }
                } else {
                    FilledIconButton(
                        onClick = onSend,
                        enabled = (draft.isNotBlank() || pendingImage != null) && connected
                    ) {
                        // Auto-mirrored: the arrow has to point the way the
                        // text runs, and the manifest declares supportsRtl.
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderUserImages(parts: List<ContentPart>) {
    val imageUrls = parts.filterIsInstance<ContentPart.File>()
        .filter { it.mediaType.startsWith("image/") }
        .map { it.url }
    if (imageUrls.isEmpty()) return

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 6.dp)
    ) {
        imageUrls.forEach { url ->
            AsyncImage(
                model = url,
                contentDescription = "Attached image",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(140.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        }
    }
}

@Composable
private fun ConnectionBanner(connection: ChatViewModel.Connection, onReconnect: () -> Unit) {
    val (message, tone, action) = when (connection) {
        is ChatViewModel.Connection.Unconfigured ->
            Triple("Set your Worker URL under Settings to connect.", BannerTone.NEUTRAL, false)

        is ChatViewModel.Connection.Connecting ->
            Triple("Connecting to the agent…", BannerTone.NEUTRAL, false)

        // Nothing is wrong, so nothing is said.
        is ChatViewModel.Connection.Connected -> return

        is ChatViewModel.Connection.Disconnected ->
            Triple(
                connection.reason?.let { "Disconnected: $it" } ?: "Disconnected.",
                BannerTone.ERROR,
                true
            )
    }

    Surface(
        color = when (tone) {
            BannerTone.ERROR -> MaterialTheme.colorScheme.errorContainer
            BannerTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = when (tone) {
            BannerTone.ERROR -> MaterialTheme.colorScheme.onErrorContainer
            BannerTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (connection is ChatViewModel.Connection.Connecting) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            }
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            if (action) {
                TextButton(onClick = onReconnect) { Text("Reconnect") }
            }
        }
    }
}

private enum class BannerTone { NEUTRAL, ERROR }

/**
 * Bring the *bottom* of the transcript into view.
 *
 * `scrollToItem(last)` alone aligns the last item's top with the top of the
 * viewport, which for a reply taller than the screen shows its first lines and
 * hides the ones arriving. Scrolling on by more than the item's height lands
 * on its end instead: the list clamps at the end of its content, so the
 * overshoot is safe.
 */
private suspend fun LazyListState.snapToEnd(lastIndex: Int) {
    if (lastIndex < 0) return
    if (layoutInfo.visibleItemsInfo.none { it.index == lastIndex }) scrollToItem(lastIndex)
    val last = layoutInfo.visibleItemsInfo.lastOrNull { it.index == lastIndex } ?: return
    scrollBy((last.size + layoutInfo.viewportSize.height).toFloat())
}

package org.mistykmedia.insertabot.ui.chat

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mistykmedia.insertabot.data.ConversationSummary

/**
 * The conversation list, opened from the Chat screen.
 *
 * It is the Worker's index, the same one the web app's history panel reads,
 * so a thread started on either shows up on both, and a rename or delete on
 * one is a rename or delete everywhere.
 */
@Composable
fun ConversationDrawer(
    history: ChatViewModel.HistoryState,
    currentId: String?,
    onNewChat: () -> Unit,
    onOpen: (String) -> Unit,
    onRename: (id: String, title: String, onResult: (Boolean) -> Unit) -> Unit,
    onDelete: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit
) {
    var renaming by remember { mutableStateOf<ConversationSummary?>(null) }
    var deleting by remember { mutableStateOf<ConversationSummary?>(null) }

    ModalDrawerSheet {
        Column(Modifier.fillMaxHeight()) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)) {
                Text(
                    "Conversations",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    "The same list as the web app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.size(12.dp))
                FilledTonalButton(onClick = onNewChat, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("New chat")
                }
            }

            val items = history.items
            when {
                history.unavailable -> DrawerMessage(history.error ?: "History isn't available.")

                items.isEmpty() && history.loading -> Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                items.isEmpty() && history.error != null -> Column(Modifier.padding(horizontal = 20.dp)) {
                    DrawerMessage(history.error, padded = false)
                    TextButton(onClick = onRetry) { Text("Try again") }
                }

                items.isEmpty() && history.loaded ->
                    DrawerMessage("No conversations yet. Send a message to start one.")

                else -> LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (history.error != null) {
                        item(key = "error") {
                            Text(
                                history.error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    items(items, key = { it.id }) { conversation ->
                        ConversationRow(
                            conversation = conversation,
                            selected = conversation.id == currentId,
                            onOpen = { onOpen(conversation.id) },
                            onRename = { renaming = conversation },
                            onDelete = { deleting = conversation }
                        )
                    }
                    if (history.nextCursor != null) {
                        item(key = "more") {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (history.loading) {
                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    TextButton(onClick = onLoadMore) { Text("Show older") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    renaming?.let { conversation ->
        RenameDialog(
            conversation = conversation,
            onDismiss = { renaming = null },
            onSave = { title, onResult -> onRename(conversation.id, title, onResult) },
            onSaved = { renaming = null }
        )
    }

    deleting?.let { conversation ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete conversation?") },
            text = {
                Text(
                    "“${conversation.displayTitle}” will be deleted from this phone and the web app. " +
                        "This can't be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = { onDelete(conversation.id); deleting = null },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun DrawerMessage(text: String, padded: Boolean = true) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = if (padded) Modifier.padding(horizontal = 20.dp, vertical = 12.dp) else Modifier
    )
}

@Composable
private fun ConversationRow(
    conversation: ConversationSummary,
    selected: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Surface(
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClickLabel = "Open conversation", onClick = onOpen)
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 2.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    conversation.displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val detail = listOfNotNull(
                    conversation.preview.takeIf { it.isNotBlank() },
                    relativeTime(conversation.updatedAt)
                ).joinToString(" · ")
                if (detail.isNotBlank()) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Options for ${conversation.displayTitle}")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        onClick = { menu = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        },
                        onClick = { menu = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
private fun RenameDialog(
    conversation: ConversationSummary,
    onDismiss: () -> Unit,
    onSave: (String, (Boolean) -> Unit) -> Unit,
    onSaved: () -> Unit
) {
    var title by remember(conversation.id) { mutableStateOf(conversation.title) }
    var saving by remember(conversation.id) { mutableStateOf(false) }
    var failed by remember(conversation.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Rename conversation") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it; failed = false },
                label = { Text("Title") },
                singleLine = true,
                isError = failed,
                supportingText = if (failed) {
                    { Text("Couldn't rename it. Check your connection and try again.") }
                } else null,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    saving = true
                    onSave(title) { ok ->
                        saving = false
                        if (ok) onSaved() else failed = true
                    }
                },
                enabled = !saving && title.isNotBlank()
            ) {
                if (saving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") } }
    )
}

/** "5 min. ago", "Yesterday", "Nov 3" — or nothing for a missing timestamp. */
private fun relativeTime(epochMillis: Long): String? {
    if (epochMillis <= 0) return null
    return DateUtils.getRelativeTimeSpanString(
        epochMillis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()
}

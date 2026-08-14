package org.mistykmedia.insertabot.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Settings
import org.mistykmedia.insertabot.ui.chat.ChatScreen
import org.mistykmedia.insertabot.ui.servers.ServersScreen
import org.mistykmedia.insertabot.ui.settings.SettingsScreen

private enum class Destination(val label: String, val icon: ImageVector) {
    CHAT("Chat", Icons.Outlined.ChatBubbleOutline),
    SERVERS("Servers", Icons.Outlined.Dns),
    SETTINGS("Settings", Icons.Outlined.Settings)
}

@Composable
fun InsertaBotApp() {
    var destination by rememberSaveable { mutableStateOf(Destination.CHAT) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = { destination = item },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { padding ->
        when (destination) {
            Destination.CHAT -> ChatScreen(padding)
            Destination.SERVERS -> ServersScreen(padding)
            Destination.SETTINGS -> SettingsScreen(padding)
        }
    }
}

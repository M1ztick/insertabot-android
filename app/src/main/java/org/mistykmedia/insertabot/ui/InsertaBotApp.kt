package org.mistykmedia.insertabot.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Settings
import kotlinx.coroutines.launch
import org.mistykmedia.insertabot.data.AppPreferences
import org.mistykmedia.insertabot.ui.auth.SignInScreen
import org.mistykmedia.insertabot.ui.chat.ChatScreen
import org.mistykmedia.insertabot.ui.chat.ChatViewModel
import org.mistykmedia.insertabot.ui.servers.ServersScreen
import org.mistykmedia.insertabot.ui.settings.SettingsScreen

private enum class Destination(val label: String, val icon: ImageVector) {
    CHAT("Chat", Icons.Outlined.ChatBubbleOutline),
    SERVERS("Tools", Icons.Outlined.Extension),
    SETTINGS("Settings", Icons.Outlined.Settings)
}

@Composable
fun InsertaBotApp() {
    var destination by rememberSaveable { mutableStateOf(Destination.CHAT) }
    // Sign-in covers the whole app rather than being a tab: it is a step to
    // get through, reachable from wherever the app says it is needed.
    var signingIn by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val settings by prefs.settings.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()

    // Chat and Servers talk to the same agent over the same socket, so the
    // view model is hoisted here rather than resolved separately per screen.
    val viewModel: ChatViewModel = viewModel()
    // Coming back to the app is the moment a reader expects it to work again,
    // whichever tab they land on.
    LifecycleStartEffect(viewModel) {
        viewModel.onForeground()
        onStopOrDispose { }
    }

    val workerUrl = settings?.workerUrl
    if (signingIn && workerUrl != null) {
        SignInScreen(
            workerUrl = workerUrl,
            onSignedIn = { token, binding ->
                scope.launch {
                    prefs.saveAccessSession(token, binding)
                    signingIn = false
                    // Straight into the conversation is where a new sign-in
                    // was headed.
                    destination = Destination.CHAT
                }
            },
            onCancel = { signingIn = false }
        )
        return
    }

    val startSignIn = { signingIn = true }
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
            Destination.CHAT -> ChatScreen(padding, viewModel, onSignIn = startSignIn)
            Destination.SERVERS -> ServersScreen(padding, viewModel)
            Destination.SETTINGS -> SettingsScreen(padding, onSignIn = startSignIn)
        }
    }
}

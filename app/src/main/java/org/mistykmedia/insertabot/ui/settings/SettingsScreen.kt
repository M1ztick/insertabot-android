package org.mistykmedia.insertabot.ui.settings

import android.webkit.CookieManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mistykmedia.insertabot.BuildConfig
import org.mistykmedia.insertabot.data.AccessToken
import org.mistykmedia.insertabot.data.AppPreferences
import org.mistykmedia.insertabot.data.AppSettings
import org.mistykmedia.insertabot.data.ModelLane
import org.mistykmedia.insertabot.network.Credentials
import org.mistykmedia.insertabot.network.InsertaBotApi
import java.text.DateFormat
import java.util.Date

/** Outcome of the last connection probe, so it can be coloured as well as read. */
private sealed interface ProbeResult {
    data class Ok(val message: String) : ProbeResult
    data class Failed(val message: String) : ProbeResult
    data object Saved : ProbeResult
}

/** The fields the Advanced form edits. The lane and the session save on their own. */
private fun AppSettings.connectionFields() =
    listOf(workerUrl, bearerToken, cfAccessClientId, cfAccessClientSecret)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    padding: PaddingValues,
    onSignIn: () -> Unit = {},
    onDeleteAll: (onResult: (Result<Int>) -> Unit) -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val scope = rememberCoroutineScope()
    // The Advanced form's working copy.
    var settings by remember { mutableStateOf(AppSettings()) }
    // What is on disk. The account card and lane picker read this directly.
    var saved by remember { mutableStateOf<AppSettings?>(null) }
    var result by remember { mutableStateOf<ProbeResult?>(null) }
    var checking by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var revealSecrets by remember { mutableStateOf(false) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        prefs.settings.collect {
            // Only adopt disk state while the form is untouched; otherwise a
            // DataStore emission mid-edit would overwrite what is being typed.
            val untouched = saved?.connectionFields() == settings.connectionFields()
            if (saved == null || untouched) settings = it
            saved = it
        }
    }

    val urlError = settings.workerUrl.isNotBlank() && !settings.workerUrl.trim().startsWith("https://")
    val dirty = saved != null && saved?.connectionFields() != settings.connectionFields()

    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() }
        )

        SectionTitle("Account")
        saved?.let { current ->
            AccountCard(
                settings = current,
                onSignIn = onSignIn,
                onSignOut = {
                    scope.launch {
                        prefs.clearAccessSession()
                        // Also forget the login page's own session, so the next
                        // sign-in asks for an email instead of reusing this one.
                        CookieManager.getInstance().removeAllCookies(null)
                        CookieManager.getInstance().flush()
                    }
                }
            )
        }

        SectionTitle("Model")
        val lane = saved?.modelLane ?: settings.modelLane
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                lane.label,
                {},
                Modifier
                    .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                readOnly = true,
                label = { Text("Model lane") },
                supportingText = { Text(lane.description) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                ModelLane.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        // The lanes are not self-explanatory from their names
                        // alone, and this is the one place to say what they do.
                        trailingIcon = {
                            Text(
                                option.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = {
                            expanded = false
                            // A choice from a menu is the decision; no separate Save.
                            scope.launch { prefs.saveModelLane(option) }
                        }
                    )
                }
            }
        }

        SectionTitle("Your data")
        saved?.let { current ->
            YourDataCard(
                signedIn = current.accessToken.isNotBlank() ||
                    (current.cfAccessClientId.isNotBlank() && current.cfAccessClientSecret.isNotBlank()),
                onDeleteAll = onDeleteAll
            )
        }

        // Developer settings. A tester who signs in above never needs these,
        // so they stay out of the way until asked for.
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button) { showAdvanced = !showAdvanced },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Advanced",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() }
            )
            Icon(
                if (showAdvanced) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (showAdvanced) "Hide advanced settings" else "Show advanced settings",
                tint = MaterialTheme.colorScheme.primary
            )
        }

        AnimatedVisibility(visible = showAdvanced) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "For development. Signing in with email above is all most people need.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    settings.workerUrl,
                    { settings = settings.copy(workerUrl = it); result = null },
                    Modifier.fillMaxWidth(),
                    label = { Text("Worker URL") },
                    supportingText = {
                        Text(
                            if (urlError) "Must start with https://"
                            else "Default: ${BuildConfig.DEFAULT_WORKER_URL}"
                        )
                    },
                    isError = urlError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next
                    )
                )

                Text(
                    "Service token and API key are stored only on this device and sent only to " +
                        "the Worker URL above. A service token is ignored while you're signed in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SecretField(
                    value = settings.bearerToken,
                    onValueChange = { settings = settings.copy(bearerToken = it); result = null },
                    label = "Bearer token (optional)",
                    revealed = revealSecrets,
                    onToggleReveal = { revealSecrets = !revealSecrets }
                )
                SecretField(
                    value = settings.cfAccessClientId,
                    onValueChange = { settings = settings.copy(cfAccessClientId = it); result = null },
                    label = "CF Access Client ID",
                    revealed = revealSecrets,
                    onToggleReveal = { revealSecrets = !revealSecrets }
                )
                SecretField(
                    value = settings.cfAccessClientSecret,
                    onValueChange = { settings = settings.copy(cfAccessClientSecret = it); result = null },
                    label = "CF Access Client Secret",
                    revealed = revealSecrets,
                    onToggleReveal = { revealSecrets = !revealSecrets }
                )

                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { prefs.saveConnection(settings) }
                            result = ProbeResult.Saved
                        }
                    },
                    // A disabled Save on an unchanged form is how the screen says
                    // the settings are already persisted.
                    enabled = dirty && !urlError,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (dirty) "Save" else "Saved")
                }

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            checking = true
                            result = null
                            val probe = withContext(Dispatchers.IO) {
                                InsertaBotApi().checkWorker(
                                    settings.workerUrl,
                                    Credentials(
                                        bearerToken = settings.bearerToken,
                                        cfAccessClientId = settings.cfAccessClientId,
                                        cfAccessClientSecret = settings.cfAccessClientSecret,
                                        accessToken = saved?.accessToken.orEmpty(),
                                        accessBinding = saved?.accessBinding.orEmpty()
                                    )
                                )
                            }
                            result = if (probe.ok) {
                                ProbeResult.Ok(probe.summary + probe.version?.let { " · v$it" }.orEmpty())
                            } else {
                                ProbeResult.Failed(probe.summary)
                            }
                            checking = false
                        }
                    },
                    enabled = settings.workerUrl.isNotBlank() && !urlError && !checking,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (checking) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                        Text("Checking…")
                    } else {
                        Text("Check connection")
                    }
                }

                AnimatedVisibility(visible = result != null) {
                    result?.let { ResultBanner(it) }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { heading() }
    )
}

/**
 * Who the app is signed in as, and the one action that makes sense from here.
 * This is the first thing in Settings because it is the only thing most
 * people will ever need to change.
 */
@Composable
private fun AccountCard(
    settings: AppSettings,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit
) {
    val identity = AccessToken.parse(settings.accessToken)
    val now = System.currentTimeMillis() / 1000
    val expiry = identity?.expiresAtEpochSeconds?.let {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it * 1000))
    }
    val hasServiceToken = settings.cfAccessClientId.isNotBlank() && settings.cfAccessClientSecret.isNotBlank()

    val title: String
    val detail: String
    when {
        identity != null && !identity.isExpired(now) -> {
            title = identity.email ?: "Signed in"
            detail = expiry?.let { "Signed in until $it" } ?: "Signed in"
        }
        identity != null -> {
            title = identity.email ?: "Signed out"
            detail = expiry?.let { "Your sign-in expired on $it." } ?: "Your sign-in expired."
        }
        hasServiceToken -> {
            title = "Using a service token"
            detail = "Set under Advanced. Sign in with email to use your own account instead."
        }
        else -> {
            title = "Not signed in"
            detail = "Sign in with your email to start chatting. You'll get a one-time code; no password or token needed."
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Outlined.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            when {
                identity != null && !identity.isExpired(now) ->
                    OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                        Text("Sign out")
                    }
                identity != null -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSignIn, modifier = Modifier.weight(1f)) { Text("Sign in again") }
                    TextButton(onClick = onSignOut) { Text("Sign out") }
                }
                else -> Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
                    Text("Sign in with email")
                }
            }
        }
    }
}

/**
 * What is kept and the one control that removes it all. Conversations live on
 * the Worker (that is what makes them sync with the web app), so deleting them
 * here deletes them everywhere.
 */
@Composable
private fun YourDataCard(
    signedIn: Boolean,
    onDeleteAll: (onResult: (Result<Int>) -> Unit) -> Unit
) {
    val uriHandler = LocalUriHandler.current
    var confirming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<ProbeResult?>(null) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Your conversations are stored on the InsertaBot server so they sync between this " +
                    "phone and the web. They aren't used to train AI, and this app has no ads or trackers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = { uriHandler.openUri(PRIVACY_URL) },
                contentPadding = PaddingValues(horizontal = 0.dp)
            ) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("What's stored, and for how long")
            }
            OutlinedButton(
                onClick = { outcome = null; confirming = true },
                enabled = signedIn && !deleting,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (deleting) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                    Text("Deleting…")
                } else {
                    Text("Delete all my conversations")
                }
            }
            if (!signedIn) {
                Text(
                    "Sign in to manage your conversations.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(visible = outcome != null) { outcome?.let { ResultBanner(it) } }
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Delete all conversations?") },
            text = {
                Text(
                    "Every conversation, with its messages, saved notes and tools, will be deleted " +
                        "from this phone and the web app. This can't be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirming = false
                        deleting = true
                        onDeleteAll { result ->
                            deleting = false
                            outcome = result.fold(
                                onSuccess = { n ->
                                    ProbeResult.Ok(
                                        if (n == 1) "Deleted 1 conversation." else "Deleted $n conversations."
                                    )
                                },
                                onFailure = { ProbeResult.Failed(it.message ?: "Couldn't delete. Try again.") }
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text("Delete everything") }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } }
        )
    }
}

/** The Worker's plain-language privacy accounting. */
private const val PRIVACY_URL = "https://github.com/M1ztick/insertabot-cfworker/blob/master/PRIVACY.md"

/**
 * A credential field, masked by default.
 *
 * These hold live Cloudflare Access tokens. Rendering them in clear text means
 * anyone glancing at the screen — or any screenshot of it — walks away with the
 * secret, so the characters are hidden until deliberately revealed.
 */
@Composable
private fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    revealed: Boolean,
    onToggleReveal: () -> Unit
) {
    OutlinedTextField(
        value,
        onValueChange,
        Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        visualTransformation =
            if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Next
        ),
        trailingIcon = {
            IconButton(onClick = onToggleReveal) {
                Icon(
                    if (revealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (revealed) "Hide secrets" else "Show secrets"
                )
            }
        }
    )
}

@Composable
private fun ResultBanner(result: ProbeResult) {
    val (text, container, onContainer, icon) = when (result) {
        is ProbeResult.Ok -> Quad(
            result.message,
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            Icons.Outlined.CheckCircle
        )

        is ProbeResult.Failed -> Quad(
            result.message,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            Icons.Outlined.ErrorOutline
        )

        ProbeResult.Saved -> Quad(
            "Saved on this device.",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            Icons.Outlined.CheckCircle
        )
    }

    Surface(
        color = container,
        contentColor = onContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

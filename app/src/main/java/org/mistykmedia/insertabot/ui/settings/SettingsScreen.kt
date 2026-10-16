package org.mistykmedia.insertabot.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mistykmedia.insertabot.data.AppPreferences
import org.mistykmedia.insertabot.data.AppSettings
import org.mistykmedia.insertabot.data.ModelLane
import org.mistykmedia.insertabot.network.InsertaBotApi

/** Outcome of the last connection probe, so it can be coloured as well as read. */
private sealed interface ProbeResult {
    data class Ok(val message: String) : ProbeResult
    data class Failed(val message: String) : ProbeResult
    data object Saved : ProbeResult
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(AppSettings()) }
    // What is actually on disk, so the Save button can tell whether anything
    // has been edited rather than inviting a no-op write on every visit.
    var saved by remember { mutableStateOf<AppSettings?>(null) }
    var result by remember { mutableStateOf<ProbeResult?>(null) }
    var checking by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var revealSecrets by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        prefs.settings.collect {
            // Only adopt disk state while the form is untouched; otherwise a
            // DataStore emission mid-edit would overwrite what is being typed.
            if (saved == null || saved == settings) settings = it
            saved = it
        }
    }

    val urlError = settings.workerUrl.isNotBlank() && !settings.workerUrl.trim().startsWith("https://")
    val dirty = saved != null && saved != settings

    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        Text(
            "Connection",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )

        OutlinedTextField(
            settings.workerUrl,
            { settings = settings.copy(workerUrl = it); result = null },
            Modifier.fillMaxWidth(),
            label = { Text("Worker URL") },
            supportingText = {
                Text(
                    if (urlError) "Must start with https://"
                    else "e.g. https://insertabot.example.workers.dev"
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
            "Credentials",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Stored only on this device, and sent only to the Worker URL above.",
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

        Text(
            "Model",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                settings.modelLane.label,
                {},
                Modifier
                    .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                readOnly = true,
                label = { Text("Model lane") },
                supportingText = { Text(settings.modelLane.description) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                ModelLane.entries.forEach { lane ->
                    DropdownMenuItem(
                        text = { Text(lane.label) },
                        // The lanes are not self-explanatory from their names
                        // alone, and this is the one place to say what they do.
                        trailingIcon = {
                            Text(
                                lane.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = {
                            settings = settings.copy(modelLane = lane)
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.size(4.dp))

        Button(
            onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) { prefs.save(settings) }
                    result = ProbeResult.Saved
                }
            },
            // A disabled Save on an unchanged form is how the screen says the
            // settings are already persisted, rather than acknowledging a write
            // that changed nothing.
            enabled = dirty && !urlError,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (dirty) "Save settings" else "Saved")
        }

        OutlinedButton(
            onClick = {
                scope.launch {
                    checking = true
                    result = null
                    val probe = withContext(Dispatchers.IO) {
                        InsertaBotApi().checkWorker(
                            settings.workerUrl,
                            settings.bearerToken,
                            settings.cfAccessClientId,
                            settings.cfAccessClientSecret
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

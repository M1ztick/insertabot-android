package org.mistykmedia.insertabot.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mistykmedia.insertabot.data.AppPreferences
import org.mistykmedia.insertabot.data.AppSettings
import org.mistykmedia.insertabot.data.ModelLane
import org.mistykmedia.insertabot.network.InsertaBotApi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(AppSettings()) }
    var status by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            prefs.settings.collect { settings = it }
        }
    }

    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(settings.workerUrl, { settings = settings.copy(workerUrl = it) }, Modifier.fillMaxWidth(), label = { Text("Worker URL") }, supportingText = { Text("HTTPS only — e.g. https://insertabot.example.workers.dev") }, singleLine = true)
        OutlinedTextField(settings.bearerToken, { settings = settings.copy(bearerToken = it) }, Modifier.fillMaxWidth(), label = { Text("Bearer token (optional)") }, singleLine = true)
        OutlinedTextField(settings.cfAccessClientId, { settings = settings.copy(cfAccessClientId = it) }, Modifier.fillMaxWidth(), label = { Text("CF Access Client ID") }, singleLine = true)
        OutlinedTextField(settings.cfAccessClientSecret, { settings = settings.copy(cfAccessClientSecret = it) }, Modifier.fillMaxWidth(), label = { Text("CF Access Client Secret") }, singleLine = true)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(settings.modelLane.label, {}, Modifier.menuAnchor().fillMaxWidth(), readOnly = true, label = { Text("Model lane") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) })
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                ModelLane.entries.forEach { lane -> DropdownMenuItem(text = { Text(lane.label) }, onClick = { settings = settings.copy(modelLane = lane); expanded = false }) }
            }
        }
        Button(onClick = { scope.launch(Dispatchers.IO) { prefs.save(settings); withContext(Dispatchers.Main) { status = "Saved locally." } } }, modifier = Modifier.fillMaxWidth()) { Text("Save settings") }
        Button(
            onClick = {
                scope.launch {
                    checking = true
                    val result = withContext(Dispatchers.IO) { InsertaBotApi().checkWorker(settings.workerUrl, settings.bearerToken, settings.cfAccessClientId, settings.cfAccessClientSecret) }
                    status = if (result.ok) "${result.summary}${result.version?.let { " · v$it" }.orEmpty()}" else result.summary
                    checking = false
                }
            },
            enabled = settings.workerUrl.isNotBlank() && !checking,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (checking) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp) else Text("Check connection")
        }
        status?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

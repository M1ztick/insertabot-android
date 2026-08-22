# InsertaBot Android — Guidelines

## Code Quality Standards

### Naming Conventions
- Packages: lowercase, reverse-domain (`org.mistykmedia.insertabot.*`)
- Classes/objects: PascalCase (`AppPreferences`, `AgentWebSocket`, `ChatViewModel`)
- Functions/properties: camelCase (`workerUrl`, `bearerToken`, `checkWorker`)
- Constants/keys: camelCase inside a private `object Keys { ... }` nested class
- Enum entries: SCREAMING_SNAKE_CASE (`ModelLane.AUTO`, `ChatRole.USER`)
- Wire/serialization values kept separate from display labels via dedicated properties (`wireValue`, `label`)

### File & Package Structure
- One top-level class per file, filename matches class name
- Screens live in `ui/<feature>/`, ViewModels co-located with their screen
- Data models in `data/Models.kt` (plain `data class` / `enum`, no Android deps)
- Persistence in `data/AppPreferences.kt`, network in `network/`

---

## Architectural Patterns

### DataStore Preferences
```kotlin
// Extension property on Context — one DataStore per app
private val Context.dataStore by preferencesDataStore(name = "insertabot_settings")

// Keys isolated in a private nested object
private object Keys {
    val workerUrl = stringPreferencesKey("worker_url")
}

// Expose as Flow<DomainModel>, never raw Preferences
val settings: Flow<AppSettings> = context.dataStore.data.map { prefs -> AppSettings(...) }

// Save with suspend + edit lambda; sanitize inputs at write time
suspend fun save(settings: AppSettings) {
    context.dataStore.edit { prefs ->
        prefs[Keys.workerUrl] = settings.workerUrl.trim().trimEnd('/')
    }
}
```

### WebSocket / Async Transport
```kotlin
// Wrap callback-based APIs in callbackFlow
fun connect(endpoint: String): Flow<Event> = callbackFlow {
    val socket = client.newWebSocket(request, object : WebSocketListener() {
        override fun onOpen(...) { trySend(Event.Open) }
        override fun onFailure(...) { trySend(Event.Failure(...)); close(t) }
        override fun onClosed(...) { trySend(Event.Closed); close() }
    })
    awaitClose { socket.close(1000, "Client closed") }
}

// Events modelled as sealed interface with data object / data class variants
sealed interface Event {
    data object Open : Event
    data class Text(val value: String) : Event
    data class Failure(val message: String) : Event
    data object Closed : Event
}
```

### Compose UI Patterns
```kotlin
// Screen receives PaddingValues from Scaffold, applies it first
@Composable
fun SettingsScreen(padding: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), ...) { ... }
}

// Local state: remember + mutableStateOf with by delegation
var settings by remember { mutableStateOf(AppSettings()) }

// Coroutine scope for one-shot actions (save, check)
val scope = rememberCoroutineScope()
scope.launch(Dispatchers.IO) { prefs.save(settings) }

// Side effects on entry: LaunchedEffect(Unit)
LaunchedEffect(Unit) { prefs.settings.collect { settings = it } }

// Guard rendering behind auth state — early return pattern
if (!authenticated) return
```

### Bottom Navigation
```kotlin
// Destinations as private enum with label + icon
private enum class Destination(val label: String, val icon: ImageVector) { ... }

// State via rememberSaveable (survives config change)
var destination by rememberSaveable { mutableStateOf(Destination.CHAT) }

// Render with when expression, not NavHost (current approach)
when (destination) {
    Destination.CHAT -> ChatScreen(padding)
    ...
}
```

### Data Models
```kotlin
// Plain data classes with default values for optional fields
data class AppSettings(
    val workerUrl: String = "",
    val modelLane: ModelLane = ModelLane.AUTO
)

// Enums carry both wire value and display label
enum class ModelLane(val wireValue: String, val label: String) {
    AUTO("auto", "Auto"), RESEARCH("research", "Research")
}

// Deserialize enum from wire value safely
ModelLane.entries.firstOrNull { it.wireValue == prefs[Keys.modelLane] } ?: ModelLane.AUTO
```

---

## Security Practices
- Biometric auth (`BIOMETRIC_WEAK or DEVICE_CREDENTIAL`) gates both app launch and the Settings screen
- If no screen lock is enrolled, access is allowed but credentials are not silently exposed
- Bearer token and CF Access credentials stored in DataStore (not SharedPreferences)
- Credentials trimmed at save time; URL trailing slash stripped
- Never commit keystores, APKs, tokens, or Cloudflare credentials

---

## Dispatcher Conventions
- `Dispatchers.IO` for DataStore reads/writes and HTTP calls
- `withContext(Dispatchers.Main)` to post UI state updates back from IO context
- `Dispatchers.IO` passed directly to `scope.launch(Dispatchers.IO)` for fire-and-forget saves

---

## Annotations
- `@OptIn(ExperimentalMaterial3Api::class)` required for `ExposedDropdownMenuBox`
- `@Composable` on all UI functions
- `@Serializable` on DTOs that cross the network boundary (kotlinx.serialization)

---

## What NOT to Do
- Do not add navigation graph / `NavHost` unless the current enum-state approach becomes insufficient
- Do not invent Cloudflare Agents frame names — verify against deployed Pages client or Agents SDK first
- Do not use `SharedPreferences`; use DataStore
- Do not expose raw `Preferences` objects outside `AppPreferences`
- Do not commit release keys, generated APKs, or credentials

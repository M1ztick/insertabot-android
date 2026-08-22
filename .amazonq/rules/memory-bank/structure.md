# InsertaBot Android — Structure

## Directory Layout
```
insertabot-android/
├── app/
│   ├── src/main/
│   │   ├── java/org/mistykmedia/insertabot/
│   │   │   ├── data/
│   │   │   │   ├── AppPreferences.kt   # DataStore wrapper (endpoint URL, bearer token)
│   │   │   │   └── Models.kt           # @Serializable data models (requests/responses)
│   │   │   ├── network/
│   │   │   │   ├── AgentWebSocket.kt   # WebSocket adapter for Cloudflare Agents protocol
│   │   │   │   └── InsertaBotApi.kt    # OkHttp REST client (/health, /info, chat)
│   │   │   ├── ui/
│   │   │   │   ├── chat/
│   │   │   │   │   ├── ChatScreen.kt       # Chat Composable
│   │   │   │   │   └── ChatViewModel.kt    # Chat state + coroutine logic
│   │   │   │   ├── servers/
│   │   │   │   │   └── ServersScreen.kt    # MCP server list Composable
│   │   │   │   ├── settings/
│   │   │   │   │   └── SettingsScreen.kt   # Endpoint/token config Composable
│   │   │   │   ├── theme/
│   │   │   │   │   └── Theme.kt            # Material3 theme
│   │   │   │   └── InsertaBotApp.kt        # Root Scaffold + bottom nav
│   │   │   └── MainActivity.kt             # Single activity entry point
│   │   ├── res/values/
│   │   │   ├── strings.xml
│   │   │   └── themes.xml
│   │   └── AndroidManifest.xml
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── gradle/
│   ├── libs.versions.toml              # Version catalog (single source of truth)
│   └── wrapper/
├── fdroid/
│   └── README.md                       # Self-hosted F-Droid repo layout docs
├── .github/workflows/android.yml       # CI build workflow
├── build.gradle.kts                    # Root build (plugins only, apply false)
└── settings.gradle.kts                 # Single-module project (:app)
```

## Core Components

| Component | Role |
|---|---|
| `InsertaBotApp` | Root `@Composable`; owns bottom nav state via `rememberSaveable` |
| `AppPreferences` | DataStore `Preferences` singleton; exposes `Flow<String>` for endpoint/token |
| `InsertaBotApi` | OkHttp-based REST client; suspending functions for health/info/chat |
| `AgentWebSocket` | Isolated adapter behind an interface; connects to Cloudflare Agents WS path |
| `ChatViewModel` | `ViewModel` bridging `AgentWebSocket`/`InsertaBotApi` with Compose UI state |
| `Models.kt` | `@Serializable` DTOs shared across network and UI layers |

## Architectural Patterns
- Single-activity, single-module app
- Jetpack Compose UI with Material3
- Bottom navigation managed by `rememberSaveable` enum state in `InsertaBotApp`
- `ViewModel` + `StateFlow`/`Flow` for reactive UI state
- DataStore Preferences for persistent on-device config (no Room/SQLite)
- WebSocket transport isolated behind an interface so UI is decoupled from protocol changes
- Version catalog (`libs.versions.toml`) for all dependency versions

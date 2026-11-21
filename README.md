# InsertaBot for Android

A native Android companion app for [InsertaBot](https://github.com/M1ztick/insertabot-cfworker), built with Kotlin and Jetpack Compose.

> Status: early (0.4.0). Chat runs end to end against a deployed Worker — the Cloudflare Agents WebSocket transport, streaming replies, turn activity ("Thinking…" / "Using Tavily…"), email sign-in through Cloudflare Access, one-tap MCP server presets, and persistent conversation identity are all in place. No signed release has been published yet.

## Goals

- Native Android chat experience for the InsertaBot Cloudflare Worker
- Manage remote MCP servers from the phone
- Switch between research, coding, and automatic model lanes
- Keep endpoint configuration and optional bearer token on-device
- Remain compatible with F-Droid-style, reproducible Android builds

## Requirements

- JDK 17
- Android SDK with **API 37** installed (`compileSdk` and `targetSdk` are both 37)
- An Android Studio release new enough to support AGP 9.5
- An InsertaBot Worker deployment

## Run

A default Worker URL is compiled in via `BuildConfig`, so a fresh install needs no setup — open the app and sign in with email.

To point the app at a different deployment, set the Worker endpoint in **Settings > Advanced**, for example `https://your-worker.workers.dev`.

To build and install a debug APK:

```bash
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Use **Check connection** to request `/health` and `/info` from the Worker.

## Transport status

The Worker exposes a Cloudflare Agents `ChatAgent` through `routeAgentRequest`. That protocol is not a published spec, so it was captured from the Worker's own PWA client (`public/index.js` in `insertabot-cfworker`) and implemented in `AgentWebSocket`. Every frame name lives in the `AgentFrames` object in that one file.

**Connection**

```
wss://<worker>/agents/chat-agent/<instance-id>[?ib_key=<key>]
```

`routeAgentRequest` parses the path as `/{prefix}/{kebab-case-class}/{instance-name}`, so `ChatAgent` routes as `chat-agent`. `<instance-id>` is client-chosen and becomes the Durable Object name — it *is* the conversation identity, so persist it to resume a thread and rotate it to start a new one.

Every frame in both directions is a JSON text frame with a `type` field.

**Client to server**

| type | payload |
| --- | --- |
| `cf_agent_use_chat_request` | `{id, init: {method: "POST", body}}` — `body` is a *string* holding `{"messages": UIMessage[], "trigger": "submit-message"}`. The full history is resent every turn; there is no delta form. |
| `rpc` | `{id, method, args}` for the agent's `@callable()` methods: `setModelLane`, `addServer`, `removeServer`, `memoryAdd`, `memoryList`, `memorySearch`, `memoryDelete`. |

**Server to client**

| type | payload |
| --- | --- |
| `cf_agent_use_chat_response` | `{id, body, done}` — `body` is a JSON-encoded string holding one AI SDK stream part, so it is parsed a second time (`start`, `text-delta`, `reasoning-*`, `tool-input-*`, `tool-output-*`, `tool-result`, `finish`, `error`). |
| `cf_agent_chat_messages` | `{messages}` — full history replace; the agent is authoritative. |
| `cf_agent_state` | `{state}` — the agent's `ChatAgentState`, currently `{modelLane}`. |
| `cf_agent_mcp_servers` | `{mcp: {servers, tools, prompts, resources}}` |
| `rpc` | `{id, success, result}` or `{id, success: false, error}` |

`GET /agents/chat-agent/<instance-id>/get-messages` returns the same history over plain HTTP.

**Caveats**

- The deployed Worker pins `agents ^0.20.1` and `@cloudflare/ai-chat ^0.10.2`. Upstream `@cloudflare/ai-chat` has already renamed these frames (`chat-request`, `messages`, `cancel`, `tool-approval`), so a Worker dependency bump will require updating `AgentFrames`.
- Authentication is Cloudflare Access in front of the Worker, which then verifies the Access JWT itself. The app signs in with email (one-time code) in a WebView, keeps the resulting `CF_Authorization` token, and sends it as `cf-access-token` on the upgrade. A service token (Settings > Advanced) is the fallback for development and is not sent while an email session exists. A bearer key, if set, still rides as `?ib_key=` and `Authorization: Bearer`, but the Worker does not read it.

## Client behaviour

`ChatViewModel` opens the socket whenever a Worker URL is saved, reconnects with capped exponential backoff (5 attempts, 1s doubling to 30s), and surfaces `Connecting` / `Connected` / `Disconnected` / `SignInRequired` to the chat screen. An Access refusal (401, 403 or a redirect to the login page) ends the retry loop straight away as `SignInRequired`, since retrying with the same token cannot succeed.

Replies stream token by token into a placeholder assistant message, batched at most every 50ms (`STREAM_COMMIT_MS`). The placeholder also carries the turn's activity line — "Thinking…", or "Using Tavily · search…" while a tool runs — so the chat never sits still without saying why. That state machine lives in `TurnActivityTracker`.

The saved model lane is re-asserted over RPC on every connect, since agent state lives in the Durable Object and may have hibernated. The instance id is generated once and stored in DataStore, so a conversation survives app restarts; **New chat** rotates it, which is a new Durable Object and a fresh thread.

## Tools (MCP servers)

MCP tool execution happens **in the Worker, not on the device**. This app does not link an MCP client SDK: it attaches remote MCP servers to the agent over RPC (`addServer` / `removeServer`) and renders whatever the agent reports back in `cf_agent_mcp_servers`.

The Tools tab lists five presets — Tavily, Exa, GitHub, Firecrawl, Context7. Each asks for a single API key and places it where that provider expects it; a collapsed **Custom server** section covers anything else. Query values that look like keys are masked in the server list, and connection states read as Ready / Connecting / Needs sign-in / Couldn't connect.

## Tests

```bash
./gradlew :app:testDebugUnitTest
```

Unit tests cover pure logic rather than Compose: markdown parsing, credential and auth-header selection, URL building, image scaling, MCP presets, and turn activity tracking. There are no instrumentation or Compose UI tests yet.

## Not done yet

- The tool-call UX has not been verified on a physical device. Emulator-only coverage is not enough for streaming and tool-call timing.
- No signed APK is attached to any release. v0.1.0–v0.4.0 exist as drafts.
- Release builds are not minified or resource-shrunk (`isMinifyEnabled = false`), so the ProGuard rules are configured but inert.
- No `androidTest` source set.

## F-Droid

The `fdroid/` directory documents the eventual self-hosted repository layout. Do **not** commit release keys, keystores, generated APKs, API tokens, or Cloudflare credentials.

## License

MIT — see [LICENSE](LICENSE). F-Droid requires a libre license, so this needs to stay in place for the planned self-hosted repository.

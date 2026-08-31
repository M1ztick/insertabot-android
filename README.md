# InsertaBot for Android

A native Android companion app for [InsertaBot](https://github.com/M1ztick/insertabot-cfworker), built with Kotlin and Jetpack Compose.

> Status: early. Chat runs end to end against a deployed Worker — the Cloudflare Agents WebSocket transport, streaming replies, and persistent conversation identity are in place. MCP server management is still stubbed.

## Goals

- Native Android chat experience for the InsertaBot Cloudflare Worker
- Manage remote MCP servers from the phone
- Switch between research, coding, and automatic model lanes
- Keep endpoint configuration and optional bearer token on-device
- Remain compatible with F-Droid-style, reproducible Android builds

## Requirements

- JDK 17
- Android SDK with API 35 installed
- Android Studio Ladybug or newer recommended
- An InsertaBot Worker deployment

## Run

1. Copy the Worker endpoint into **Settings** in the app, for example `https://your-worker.workers.dev`.
2. Build and install a debug APK:

```bash
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

3. Use **Check connection** to request `/health` and `/info` from the Worker.

## Transport status

The Worker exposes a Cloudflare Agents `ChatAgent` through `routeAgentRequest`. That protocol is not a published spec, so it was captured from the Worker's own PWA client (`public/index.js` in `insertabot-cfworker`) and implemented in `AgentWebSocket`. Every frame name lives in the `AgentFrames` object in that one file.

**Connection**

```
wss://<worker>/agents/chat-agent/<instanceId>[?ib_key=<token>]
```

`routeAgentRequest` parses the path as `/{prefix}/{kebab-case-class}/{instance-name}`, so `ChatAgent` routes as `chat-agent`. `<instanceId>` is client-chosen and becomes the Durable Object name — it *is* the conversation identity, so persist it to resume a thread and rotate it to start a new one. Every frame in both directions is a JSON text frame with a `type` field.

**Client to server**

| type | payload |
| --- | --- |
| `cf_agent_use_chat_request` | `{id, init: {method: "POST", body}}` — `body` is a *string* holding `{"messages": UIMessage[], "trigger": "submit-message"}`. The full history is resent every turn; there is no delta form. |
| `rpc` | `{id, method, args}` for the agent's `@callable()` methods: `setModelLane`, `addServer`, `removeServer`, `memoryAdd`, `memoryList`, `memorySearch`, `memoryDelete`. |

**Server to client**

| type | payload |
| --- | --- |
| `cf_agent_use_chat_response` | `{id, body, done}` — `body` is a JSON-encoded string holding one AI SDK stream part, so it is parsed a second time (`start`, `text-delta`, `tool-input-*`, `tool-output-*`, `finish`, `error`). |
| `cf_agent_chat_messages` | `{messages}` — full history replace; the agent is authoritative. |
| `cf_agent_state` | `{state}` — the agent's `ChatAgentState`, currently `{modelLane}`. |
| `cf_agent_mcp_servers` | `{mcp: {servers, tools, prompts, resources}}` |
| `rpc` | `{id, success, result}` or `{id, success: false, error}` |

`GET /agents/chat-agent/<instanceId>/get-messages` returns the same history over plain HTTP.

**Caveats**

- The deployed Worker pins `agents ^0.20.1` and `@cloudflare/ai-chat ^0.10.2`. Upstream `@cloudflare/ai-chat` has already renamed these frames (`chat-request`, `messages`, `cancel`, `tool-approval`), so a Worker dependency bump will require updating `AgentFrames`.
- The Worker does not currently authenticate the WebSocket upgrade. The app sends the configured token both as `?ib_key=` (matching the PWA) and as an `Authorization: Bearer` header, and forwards the Cloudflare Access service headers, but nothing server-side reads the first two yet.

**Client behaviour**

`ChatViewModel` opens the socket whenever a Worker URL is saved, reconnects with capped exponential backoff (5 attempts, 1s doubling to 30s), and surfaces `Connecting` / `Connected` / `Disconnected` to the chat screen. Replies stream token by token into a placeholder assistant message. The saved model lane is re-asserted over RPC on every connect, since agent state lives in the Durable Object and may have hibernated.

The instance id is generated once and stored in DataStore, so a conversation survives app restarts; **New chat** rotates it, which is a new Durable Object and a fresh thread.

**Not done yet**

- `ServersScreen` is still a stub. `AgentWebSocket.addServer` / `removeServer` exist and `cf_agent_mcp_servers` already decodes into `McpServer`, but the screen is not wired to a session — that needs the view model hoisted so chat and servers share one connection.

## F-Droid

The `fdroid/` directory documents the eventual self-hosted repository layout. Do **not** commit release keys, keystores, generated APKs, API tokens, or Cloudflare credentials.

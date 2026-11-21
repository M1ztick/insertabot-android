# Worker protocol

How the app talks to the InsertaBot Worker. This is developer reference; for building and running the app, see the [README](../README.md).

The Worker exposes a Cloudflare Agents `ChatAgent` through `routeAgentRequest`. That protocol is not a published spec, so it was captured from the Worker's own web client (`public/index.js` in `insertabot-cfworker`) and implemented in `AgentWebSocket`. Every frame name lives in the `AgentFrames` object in that one file.

## Connection

```
wss://<worker>/agents/chat-agent/<instance-id>[?ib_key=<key>]
```

`routeAgentRequest` parses the path as `/{prefix}/{kebab-case-class}/{instance-name}`, so `ChatAgent` routes as `chat-agent`. `<instance-id>` is chosen by the client and names the Durable Object, which makes it the conversation identity: persist it to resume a thread, rotate it to start a new one. The Worker scopes it to the signed-in user, so one user cannot open another's conversation.

Every frame in both directions is a JSON text frame with a `type` field.

## Client to server

| type | payload |
| --- | --- |
| `cf_agent_use_chat_request` | `{id, init: {method: "POST", body}}`. `body` is a *string* holding `{"messages": UIMessage[], "trigger": "submit-message"}`. The full history is sent every turn; there is no delta form. |
| `cf_agent_chat_request_cancel` | `{id}`: stop the turn with that request id. |
| `cf_agent_stream_resume_ack` | `{id}`: accept the replay of an interrupted reply. |
| `rpc` | `{id, method, args}` for the agent's `@callable()` methods: `setModelLane`, `addServer`, `removeServer`, `memoryAdd`, `memoryList`, `memorySearch`, `memoryDelete`. |

## Server to client

| type | payload |
| --- | --- |
| `cf_agent_use_chat_response` | `{id, body, done}`. `body` is a JSON-encoded string holding one AI SDK stream part, so it is parsed a second time. The app reads `start`, `text-delta`, `reasoning-*`, `tool-input-*`, `tool-output-available` / `tool-result`, `tool-output-error`, `finish` and `error`. |
| `cf_agent_chat_messages` | `{messages}`: full history replace. The agent is authoritative. Also sent on connect, so a reopened thread shows its history. |
| `cf_agent_state` | `{state}`: the agent's `ChatAgentState`, currently `{modelLane}`. |
| `cf_agent_mcp_servers` | `{mcp: {servers, tools, prompts, resources}}` |
| `cf_agent_stream_resuming` | `{id}`: a reply was in flight when the socket dropped and can be replayed. |
| `rpc` | `{id, success, result}` or `{id, success: false, error}` |

`GET /agents/chat-agent/<instance-id>/get-messages` returns the same history over plain HTTP.

## Tool names

The Agents SDK names each MCP tool `tool_<serverId without dashes>_<toolName>`. The app maps that back to the attached server's name for the activity line (`describeTool` in `TurnActivity.kt`).

## Authentication

Cloudflare Access sits in front of the Worker, and the Worker verifies the Access JWT itself.

- **Email session (normal):** the app runs the Access one-time-code login in a WebView, keeps the `CF_Authorization` token it sets, and sends it as `cf-access-token` (plus the cookie, with `CF_Binding` if issued).
- **Service token (development):** `CF-Access-Client-Id` / `CF-Access-Client-Secret` from Settings > Advanced. Not sent while an email session exists, because the Worker maps the two to different owners and so to different conversation lists.
- A bearer key, if set, still rides as `?ib_key=` and `Authorization: Bearer`, but the Worker does not read it.

An Access refusal (401, 403, or a redirect to the login page) ends the reconnect loop at once as `SignInRequired`, since retrying with the same token cannot succeed. Otherwise the app reconnects with capped exponential backoff: 5 attempts, from 1s doubling to 30s.

**Check connection** in Settings requests `/health`.

## Versions

Captured against `agents ^0.20` and `@cloudflare/ai-chat ^0.10`; the Worker currently runs `agents ^0.32.0` and `@cloudflare/ai-chat ^0.12.2`, and these frame names still apply. Upstream has renamed some frames in other releases, so check `AgentFrames` whenever the Worker's dependencies are bumped.

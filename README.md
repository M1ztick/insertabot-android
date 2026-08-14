# InsertaBot for Android

A native Android companion app for [InsertaBot](https://github.com/M1ztick/insertabot-cfworker), built with Kotlin and Jetpack Compose.

> Status: initial scaffold. The UI, local preferences, Worker capability probe, and an isolated WebSocket adapter are in place. The Cloudflare Agents wire protocol must be verified against the deployed Worker before chat transport is enabled.

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

The Worker exposes a Cloudflare Agents `ChatAgent` through `routeAgentRequest`. The exact WebSocket path and frame protocol should be captured from the existing Pages PWA or verified with the Agents SDK before enabling `AgentWebSocket.connect()`. The adapter is intentionally kept behind a single interface so the app UI does not need to change when that protocol is finalized.

## F-Droid

The `fdroid/` directory documents the eventual self-hosted repository layout. Do **not** commit release keys, keystores, generated APKs, API tokens, or Cloudflare credentials.

# InsertaBot for Android

A native Android client for [InsertaBot](https://github.com/M1ztick/insertabot-cfworker), built with Kotlin and Jetpack Compose. It talks to an InsertaBot Cloudflare Worker: you chat, the Worker runs the model and any tools you attach.

> **Pre-release.** The app is under active development and not yet published. See **[ROADMAP.md](ROADMAP.md)** for where things stand, what's next, and what testers should expect.

## What it does

- Chat with an InsertaBot Worker, with replies streaming in as they're written
- Sign in with your email (a one-time code through Cloudflare Access); no tokens to paste
- Add tools such as web search or GitHub with one tap and, at most, one API key
- Show what the bot is doing while it works ("Thinking…", "Using Tavily · search…")

## Build and run

Requirements:

- JDK 17
- The Android SDK platform named by `compileSdk` in [`app/build.gradle.kts`](app/build.gradle.kts), and an Android Studio release that supports the project's Android Gradle Plugin version

```bash
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

A fresh install connects to the default Worker compiled in through `BuildConfig`, so there is nothing to configure: open the app and sign in. To use a different Worker, change the URL under **Settings > Advanced**.

Run the unit tests with:

```bash
./gradlew :app:testDebugUnitTest
```

## How it fits together

- **Transport:** `AgentWebSocket` speaks the Cloudflare Agents WebSocket protocol to the Worker's `ChatAgent`. The frames are documented in [docs/PROTOCOL.md](docs/PROTOCOL.md).
- **Chat state:** `ChatViewModel` owns the connection, streaming and reconnects; `TurnActivityTracker` decides what the "Thinking… / Using …" line says.
- **Sign-in:** `SignInScreen` runs the Cloudflare Access email login in a WebView; `Credentials` decides which auth headers each request carries.
- **Tools:** MCP servers run **in the Worker, not on the phone**. The app attaches and removes them over RPC; `McpPresets` holds the one-tap catalog.

## F-Droid

The app is meant to ship through F-Droid: first a self-hosted repository for testers, later the main f-droid.org catalog. The `fdroid/` directory documents the planned repository layout. Never commit release keys, keystores, generated APKs, API tokens or Cloudflare credentials.

## License

Not chosen yet. Until a license file is added, all rights are reserved. A free-software license will be picked before the app is submitted to F-Droid; it's tracked in the [roadmap](ROADMAP.md).

The InsertaBot name and the Mistyk Media logo belong to Mistyk Media and are not covered by any code license.

# InsertaBot Android — Developer User Guide

## Prerequisites

Make sure you have the following installed and available:

- JDK 17
- Android SDK at `~/android-sdk/`
- `cloudflared` installed (required for wrangler dev remote AI binding)
- Node.js + npm
- An email address allowed by the Cloudflare Access policy on `https://cfworker.insertabot.io`

---

## 1. Start the ADB Server

```bash
~/android-sdk/platform-tools/adb start-server
```

Verify it's running:

```bash
~/android-sdk/platform-tools/adb devices
```

---

## 2. Launch the Android Emulator

List your available AVDs:

```bash
~/android-sdk/emulator/emulator -list-avds
```

Launch one:

```bash
~/android-sdk/emulator/emulator -avd <avd_name>
```

Wait for the emulator to fully boot. Keep this terminal open — closing it kills the emulator.

Confirm the emulator is online in a second terminal:

```bash
~/android-sdk/platform-tools/adb devices
```

You want to see `emulator-5554   device` (not `offline`). If it shows `offline`, wait 30–60 seconds and check again.

---

## 3. Build and Install the Debug APK

From the `insertabot-android` directory:

```bash
cd ~/Storage/T7data/Projects/insertabot-android
./gradlew :app:assembleDebug
~/android-sdk/platform-tools/adb install app/build/outputs/apk/debug/app-debug.apk
```

To reinstall over an existing build:

```bash
~/android-sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 4. Sign In

1. Open the app. A fresh install points at `https://cfworker.insertabot.io` already.
2. Tap **Sign in with email** (on the Chat screen, or under **Settings > Account**).
3. Enter your email on the Cloudflare Access page. Access emails you a one-time code;
   switch to your email app, copy it, come back, and enter it.
4. The sign-in screen closes on its own and the chat connects. **Settings > Account**
   shows who you are signed in as and when the session ends.

The session lasts as long as the Access application's **session duration** (Zero
Trust > Access > Applications > your app). When it ends, the app says so and offers
**Sign in again**; if the team-domain session is still valid, that completes without
a new code.

**Developer options** (Settings > Advanced): a different Worker URL, a bearer token,
or a CF Access service token. A service token is only sent when no email session
exists, because the Worker maps the two to different accounts (an email vs. a
service-token id) and so to different conversation lists. Link a token to an email
with the Worker's `SERVICE_TOKEN_OWNERS` secret if you want them to share one.

**Check connection** (under Advanced) should report `Connected to insertabot-cfworker`.

---

## 5. Inspect the WebSocket Protocol (for AgentWebSocket implementation)

To capture the live wire frames from the deployed Worker, use wscat in a separate terminal.

With bearer token:

```bash
npx wscat -H "Authorization: Bearer <your-token>" -c "wss://cfworker.insertabot.io/agents/chat-agent/test-session"
```

With an email session (copy `CF_Authorization` from a browser signed in to the Worker):

```bash
npx wscat -H "cf-access-token: <token>" -c "wss://cfworker.insertabot.io/agents/chat-agent/test-session"
```

With CF Access service token:

```bash
npx wscat -H "CF-Access-Client-Id: <client-id>" -H "CF-Access-Client-Secret: <client-secret>" -c "wss://cfworker.insertabot.io/agents/chat-agent/test-session"
```

Once connected, send a test message and observe the response frames. These frames define what `AgentWebSocket.kt` needs to serialize and deserialize.

---

## 6. Run the Worker Locally (optional)

If you need to test Worker changes before deploying:

```bash
cd ~/Storage/T7data/Projects/insertabot-cfworker
npx wrangler dev
```

Note: `env.AI` runs in remote mode, so `cloudflared` must be installed and you must be authenticated with Cloudflare. The Worker will be available at `ws://localhost:8787`.

Then connect wscat to local instead:

```bash
npx wscat -c "ws://localhost:8787/agents/chat-agent/test-session"
```

---

## 7. Test on a Real Device (Moto G 2026)

1. On the phone: **Settings → About Phone → tap Build Number 7 times**
2. Go to **Developer Options → enable USB Debugging**
3. Plug in via USB and accept the prompt on the phone
4. Verify it appears:

```bash
~/android-sdk/platform-tools/adb devices
```

5. Install the APK:

```bash
~/android-sdk/platform-tools/adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 8. Useful Commands

```bash
# Clean build
./gradlew clean

# Run all checks
./gradlew check

# Kill and restart adb if devices aren't showing
~/android-sdk/platform-tools/adb kill-server
~/android-sdk/platform-tools/adb start-server

# Watch emulator logs
~/android-sdk/platform-tools/adb logcat

# Filter logs to InsertaBot only
~/android-sdk/platform-tools/adb logcat | grep insertabot
```

---

## Terminal Layout (recommended)

| Terminal | Purpose |
|---|---|
| 1 | Emulator process (`emulator -avd ...`) |
| 2 | Gradle builds + adb installs |
| 3 | wscat WebSocket inspection |
| 4 | wrangler dev (if testing locally) |

---

## What's Not Done Yet

- `AgentWebSocket.kt` — wire protocol not yet implemented, pending frame capture from wscat
- `ChatViewModel.kt` — currently returns a placeholder error; needs wiring to `AgentWebSocket`
- `ServersScreen.kt` — MCP server list UI is a scaffold, needs wiring to `addServer`/`removeServer` RPC calls on the Worker

# InsertaBot Android — Developer User Guide

## Prerequisites

Make sure you have the following installed and available:

- JDK 17
- Android SDK at `~/android-sdk/`
- `cloudflared` installed (required for wrangler dev remote AI binding)
- Node.js + npm
- Access to `https://cfworker.insertabot.io` with your CF Access credentials

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

## 4. Configure the App in Settings

1. Open the app on the emulator
2. Authenticate with biometrics or device PIN when prompted
3. Tap **Settings** in the bottom navigation
4. Authenticate again (Settings re-prompts to protect credentials)
5. Fill in:
   - **Worker URL**: `https://cfworker.insertabot.io`
   - **Bearer token**: your bearer token (if applicable)
   - **CF Access Client ID**: your CF Access client ID
   - **CF Access Client Secret**: your CF Access client secret
   - **Model lane**: Auto, Research, or Coding
6. Tap **Save settings**
7. Tap **Check connection** — you should see `Connected to insertabot-cfworker · v0.2.0`

---

## 5. Inspect the WebSocket Protocol (for AgentWebSocket implementation)

To capture the live wire frames from the deployed Worker, use wscat in a separate terminal.

With bearer token:

```bash
npx wscat -H "Authorization: Bearer <your-token>" -c "wss://cfworker.insertabot.io/agents/chat-agent/test-session"
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

# Roadmap

Where the app is headed, how far along it is, and what to expect from a test build. This is a living document: it is updated as work lands, and dates are deliberately left out.

✅ done · 🔄 in progress · ⬜ planned

## 1. Core chat ✅

The foundation: talking to the Worker reliably.

- ✅ Cloudflare Agents WebSocket transport, captured from the Worker's own web client
- ✅ Replies stream in smoothly, batched so long answers don't stutter
- ✅ Conversations survive app restarts; **New chat** starts a fresh one
- ✅ Reconnects on its own after network changes, sleep and Worker restarts
- ✅ Model lane picker (Auto / Research / Coding)

## 2. Usable by anyone 🔄

The bar: someone who has never heard of MCP or Cloudflare can install the app, sign in and get help, without asking the developer anything.

- ✅ Email sign-in with a one-time code; no tokens to paste
- ✅ Default Worker address built in, so a fresh install needs no setup
- ✅ One-tap tool presets (Tavily, Exa, GitHub, Firecrawl, Context7), each with a "Get a key" link
- ✅ "Thinking…" / "Using Tavily · search…" while the bot works
- 🔄 Mistyk Media branding for the icon and splash screen
- ⬜ Conversation list: switch between chats, rename and delete them
- ⬜ Message actions: copy, retry and share a reply, and copy code blocks
- ⬜ Keep tools attached when starting a new chat (today each chat has its own set)
- ⬜ Accessibility pass: respect the system font size, label every icon, full-size tap targets
- ⬜ Verify the full tool flow on a physical device, not just an emulator

## 3. Release-ready ⬜

What has to be true before anyone outside the test group installs it.

- ⬜ Choose a license
- ⬜ Version every tester build (`versionCode` must go up, or it won't install over the last one)
- ⬜ Release signing with a dedicated key, and a first signed release on GitHub
- ⬜ Shrink and minify release builds
- ⬜ Store metadata in `fastlane/` (description, screenshots, per-release changelogs), which F-Droid reads
- ⬜ Check that builds are reproducible, so F-Droid can ship the developer-signed APK

## 4. Distribution ⬜

- ⬜ Self-hosted F-Droid repository on Cloudflare Pages, for testers: instant updates, signed with our own key
- ⬜ Submit to f-droid.org once stable. Expect Anti-Feature labels for depending on a network service; they're disclosures, not rejections.

## What testers should expect

- **It's early.** Screens and behaviour will change between builds, and some builds will have rough edges. That's what testing is for.
- **Sign-in** uses your email and a one-time code. Your email must be on the access list; ask if you're not let in. Sign-ins expire after a set time, and the app will ask you to sign in again.
- **Your conversations** live on the InsertaBot Worker, tied to your email, not on the phone.
- **Tools** belong to the conversation you added them in for now, so a new chat starts without them (see milestone 2).
- **Reinstalling:** builds signed with a different key won't install over each other. If Android refuses an update, uninstall and install again, then sign back in.
- **Reporting problems:** open an issue on [GitHub](https://github.com/M1ztick/insertabot-android/issues) with what you did, what you expected and what happened. A screenshot helps.

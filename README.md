# TabletPilot

> **Personal, always-on, ultra-fast AI automation assistant for Android tablets**  
> Optimized for **Redmi Pad Pro 5G** · CaramelOS 5v2 kernel · Snapdragon 7s Gen 2

---

## Overview

TabletPilot runs a persistent floating bubble on your tablet screen. Tap it (or trigger from the Quick Settings tile), speak or type a command, and the AI automatically opens apps, navigates the UI, clicks buttons, types text, and more — all in one fluid step.

```
┌─────────────────────────────────────────────────────────────────┐
│  Floating Bubble / QS Tile / Voice                              │
│            ↓                                                    │
│  Local Fast-Path Router  ─────────────────→  Direct action     │
│  (regex match, <50ms, no network)              (<50ms)          │
│            ↓ (no match)                                         │
│  Gemini REST API  ←── OkHttp + kotlinx.serialization           │
│  (gemini-2.0-flash-lite or gemini-2.5-flash)                    │
│            ↓ function_call                                      │
│  TabletAccessibilityService                                     │
│  open_app / get_screen_text / click / type / scroll / timer    │
│            ↓ (up to 5 agent loop steps)                         │
│  Live Action Log  (dashboard, color-coded, expandable)          │
└─────────────────────────────────────────────────────────────────┘
```

---

## Prerequisites

| Tool | Version |
|------|---------|
| Android Studio | Meerkat (2024.3+) |
| JDK | 17 |
| Android SDK | API 36 (compileSdk/targetSdk) |
| Minimum device Android | API 28 (Android 9) |
| Gemini API Key | [Get one free at ai.google.dev](https://ai.google.dev) |

---

## Setup

### 1. Clone the repository
```bash
git clone https://github.com/YOUR_USERNAME/TabletPilot.git
cd TabletPilot
```

### 2. Add your Gemini API key to `local.properties`
```properties
sdk.dir=/home/YOUR_USER/Android/Sdk
GEMINI_API_KEY=AIzaSy_YOUR_KEY_HERE
```
> ⚠️ `local.properties` is in `.gitignore` — it is **never** committed. The key is also stored in `EncryptedSharedPreferences` on-device at first launch.

### 3. Sync and build in Android Studio
```
File → Sync Project with Gradle Files
Build → Make Project
```

### 4. Install to your Redmi Pad Pro 5G
```bash
./gradlew installDebug
```
Or use **Run ▶** in Android Studio.

---

## First-Run Setup (Required Permissions)

Open TabletPilot. The dashboard shows a **Permission Checklist** — tap **Grant** next to each item:

| Permission | Why It's Needed |
|-----------|----------------|
| **Accessibility Service** | Lets TabletPilot read the screen and perform clicks/gestures on your behalf |
| **Display Over Other Apps** | Draws the draggable floating bubble and command bar |
| **Ignore Battery Optimization** | Keeps the foreground service alive all day on CaramelOS |
| **Notifications** | Shows the persistent "TabletPilot Active" notification |
| **Microphone** | Powers voice-to-text in the floating command bar |

### CaramelOS / MIUI-specific notes
- For **Battery Optimization**: if the standard dialog doesn't appear, go to `Settings → Battery → Battery saver → TabletPilot → No restrictions`
- For **Overlay**: CaramelOS may show a secondary MIUI permission dialog — approve both
- For **Accessibility**: Enable `TabletPilot Assistant` under `Settings → Accessibility → Downloaded apps`

### Add QS Tile
Swipe down twice → Edit tiles → drag **TabletPilot** into your Quick Settings row

---

## Usage Examples

| You say/type | What happens | Path |
|---|---|---|
| "Open Chrome" | Launches Chrome instantly | ⚡ Local |
| "Go home" | Returns to home screen | ⚡ Local |
| "Scroll down" | Swipes the screen up | ⚡ Local |
| "Volume up" | Raises media volume | ⚡ Local |
| "Set timer 10 min tea" | Opens clock app, sets 10 min timer | ⚡ Local |
| "Open my last Google doc" | Reads screen, navigates Docs | 🧠 Gemini |
| "Send a WhatsApp to John saying I'll be late" | Opens WhatsApp, finds John, types & sends | 🧠 Gemini |
| "What's on my screen?" | Returns readable summary of current UI | 🧠 Gemini |

---

## GitHub Actions CI

Every push to `main` automatically:
1. Sets up JDK 17 + Android SDK
2. Injects `GEMINI_API_KEY` from GitHub Secrets (`Settings → Secrets → GEMINI_API_KEY`)
3. Runs `./gradlew assembleDebug`
4. Uploads the `.apk` as a downloadable artifact named **TabletPilot-Debug-APK** (kept 30 days)

---

## Architecture

```
com.tabletpilot/
├── TabletPilotApplication.kt      ← App entry, seeds API key
├── ai/
│   ├── GeminiModels.kt            ← kotlinx.serialization data classes
│   ├── GeminiAgentClient.kt       ← OkHttp REST client, function declarations
│   ├── LocalFastPathRouter.kt     ← Regex-based instant command matcher
│   └── AgentOrchestrator.kt       ← Multi-step agent loop coordinator
├── automation/
│   └── TabletAccessibilityService.kt  ← All device automation functions
├── data/
│   ├── PreferencesManager.kt      ← DataStore preferences
│   ├── SecureKeyStore.kt          ← EncryptedSharedPreferences for API key
│   ├── ActionLogEntry.kt          ← Log entry data class
│   ├── ActionLogRepository.kt     ← In-memory log (last 100 entries)
│   └── AppContainer.kt            ← Simple DI container
├── service/
│   ├── AssistantForegroundService.kt  ← Persistent service, floating bubble
│   ├── PilotQSTileService.kt      ← Quick Settings tile
│   ├── BootReceiver.kt            ← Auto-restart after reboot
│   └── CommandBarReceiver.kt      ← Broadcast bridge for QS tile
└── ui/
    ├── MainActivity.kt
    ├── MainViewModel.kt
    ├── TabletPilotApp.kt          ← Responsive two-pane / single-pane layout
    ├── ControlPanel.kt            ← Permissions, service toggle, settings
    ├── ActionLogPanel.kt          ← Live log with expandable cards
    └── theme/
        ├── Theme.kt               ← Material 3, dynamic color
        └── Type.kt                ← Typography
```

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| Floating bubble doesn't appear | Grant **Display Over Other Apps** and restart the service |
| "Accessibility service not connected" | Enable under Settings → Accessibility → TabletPilot |
| Service stops after a while | Grant **Ignore Battery Optimization** (critical on CaramelOS) |
| Voice recognition error code 7 | No internet — SpeechRecognizer needs network. Type instead |
| Gemini returns no response | Check API key in Settings; verify quota at [ai.google.dev](https://ai.google.dev) |
| App crashes on first launch | Ensure `local.properties` has valid `sdk.dir` |

---

## License

MIT © TabletPilot Contributors

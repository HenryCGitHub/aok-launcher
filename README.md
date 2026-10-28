# AlwaysOn Minimal Keyboard (Android)

An Android keyboard designed to stay open at all times—mimicking the tactile presence and utility of a physical hardware keyboard on your display—while automatically hiding itself when watching full-screen videos or playing immersive games.

## Key Features

- **Always-On Physical Keyboard Feel**: Combines a native Android `InputMethodService` (`AlwaysOnInputMethodService`) with a synchronized persistent bottom dock (`AlwaysOnAccessibilityService`) so the keyboard stays docked across your Home screen and apps without ever rendering duplicate keyboards.
- **Smart Combined Idle Modes (`1 + 2 + 4`)**:
  - **Mode 1 — Quick-Launch & Universal Search**: When on the Home screen (or when no text field is focused), tap assigned `A–Z` shortcut keys (`C` → Chrome, `M` → Messages, `S` → Settings, `G` → Gmail, `P` → Phone, `Y` → YouTube) to launch apps immediately, or start typing to expand the inline Universal Search bar.
  - **Mode 2 — Capacitive Trackpad Scroll**: Glide your thumb vertically or horizontally across the keyboard keys to smoothly scroll social feeds, browsers, and lists (inspired by BlackBerry Passport / Key2 capacitive touch keyboards).
  - **Mode 4 — Passive Hardware Dock**: Inside apps when no text field is focused, the keyboard stays visibly docked at the bottom like physical hardware keys and ignores stray single key taps until you focus an input box.
- **Smart Full-Screen Auto-Hide**: Monitors system bar visibility (`WindowInsetsCompat.Type.statusBars()`) and active window geometry (`AccessibilityWindowInfo`) to automatically slide the keyboard away during full-screen media playback (YouTube, Netflix, games) or landscape orientation, restoring it automatically when exiting full-screen.
- **Clean Modern Minimal QWERTY UI**: 4-row minimal matte QWERTY layout with secondary number hints (`1–0`), Shift / Caps Lock (`⇧` / `⇪`), Symbol layer (`?123`), tactile keypress haptics, and a one-tap `Hide ▾` / `Restore ▴` edge pill.

## Installation & Setup

1. Download **`AlwaysOnKeyboard-v1.0.0.apk`** from the [Releases](../../releases) page and install it on your Android device (Android 8.0 / API 26+).
2. Open the **AlwaysOn Keyboard** app and complete the 3 quick setup steps on the **Setup & Modes** tab:
   - **Step 1**: Enable **AlwaysOn Minimal Keyboard** in Android Input Settings.
   - **Step 2**: Select **AlwaysOn Minimal Keyboard** as your active default keyboard.
   - **Step 3**: Enable **AlwaysOn Keyboard Dock & Trackpad** in Android Accessibility Settings (keeps the keyboard docked when no text field is focused, powers capacitive key-swipe scrolling, and detects full-screen video).

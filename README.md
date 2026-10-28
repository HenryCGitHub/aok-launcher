# AOK Launcher (Always-On Keyboard Launcher for Android)

**AOK Launcher** combines a custom Android Home Screen Launcher with an Always-On Minimal QWERTY Keyboard—giving your phone the tactile feel and speed of a physical hardware keyboard on your display, without cutting off your screen, while automatically hiding itself when watching full-screen video or playing games.

## Key Features

- **Uncut Home Display Above Keyboard (`AOK Home`)**:
  - Splits your display into two dedicated, non-overlapping zones:
    - **Top ~72%**: Live Clock & Date, **AOK Speed Dial** (`A–Z` key-badged app tiles), **All-Apps Drawer** (scrollable by gliding your thumb across the keyboard keys), and a **Bottom Home Dock** positioned *directly above* the top edge of the keyboard so nothing is ever cut off.
    - **Bottom ~28%**: Permanent Clean Modern Minimal QWERTY Keyboard.
- **Smart Combined Idle Modes (`1 + 2 + 4`)**:
  - **Mode 1 — Quick-Launch & Universal Search**: Press assigned `A–Z` shortcut keys (`C` → Chrome, `M` → Messages, `S` → Settings, `G` → Gmail, `P` → Phone, `Y` → YouTube) to launch apps immediately, or type any query to expand the inline Universal Search bar.
  - **Mode 2 — Capacitive Trackpad Scroll**: Swipe vertically or horizontally across the keyboard surface to scroll feeds, browsers, and lists without touching the upper screen.
  - **Mode 4 — Passive Hardware Dock**: Inside apps when no text field is focused, the keyboard stays visibly docked like physical hardware keys and ignores stray single key taps until an input box is focused.
- **Third-Party App Display Fitting (`⧉ Fit App`)**:
  - Uses `onComputeInsets` for native IME resizing when typing, plus a 1-tap **`⧉ Fit App`** button on the keyboard top bar (`GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN` + `FLAG_ACTIVITY_LAUNCH_ADJACENT`) to fit third-party apps into the upper display area above the keyboard.
- **Smart Full-Screen Auto-Hide**:
  - Automatically slides the keyboard away during full-screen media playback (YouTube, Netflix, games) or landscape orientation, restoring it automatically on exit.

## Download & Setup

1. Download **`AOK-Launcher-v2.1.0.apk`** from the [Releases](../../releases) page and install it on your Android device (Android 8.0 / API 26+).
2. Open **AOK Launcher** and complete the 4 quick setup steps on the **Setup & Modes** tab:
   - **Step 1**: Set **AOK Launcher** as your Default Home App.
   - **Step 2**: Enable **AOK Keyboard (IME)** in Android Keyboard settings.
   - **Step 3**: Select **AOK Keyboard** as your active keyboard.
   - **Step 4**: Enable **AOK Dock & Trackpad** in Android Accessibility settings.

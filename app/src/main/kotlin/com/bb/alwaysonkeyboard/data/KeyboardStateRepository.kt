package com.bb.alwaysonkeyboard.data

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Defines how the Always-On Keyboard behaves when NO text input field is focused.
 * Supports the user's selected modes:
 * - Mode 1: Quick-Launch Shortcuts & Universal Search
 * - Mode 2: Capacitive Trackpad Scroll (swipe across keys to scroll)
 * - Mode 4: Passive Dock (stay visibly docked like physical hardware keys, ignore taps until focused)
 * - Smart Combined (1 + 2 + 4): Swipe across keys always scrolls (Mode 2); key taps on the Home
 *   screen trigger Quick-Launch & Universal Search (Mode 1); key taps in other apps when no text box
 *   is focused act as a Passive Dock (Mode 4).
 */
enum class IdleBehaviorMode(
    val id: String,
    val shortTitle: String,
    val fullTitle: String,
    val description: String
) {
    SMART_COMBINED(
        id = "smart_1_2_4",
        shortTitle = "Smart (1+2+4)",
        fullTitle = "Smart Combined (Modes 1 + 2 + 4)",
        description = "Swipe keys to scroll anywhere (Mode 2). Tap keys on Home screen for Quick-Launch & Search (Mode 1). Stay passive inside apps until an input is tapped (Mode 4)."
    ),
    QUICK_LAUNCH_SEARCH(
        id = "mode_1_shortcuts",
        shortTitle = "1: Quick-Launch",
        fullTitle = "1. Quick-Launch & Universal Search",
        description = "When no text box is focused, press A–Z shortcut keys to launch assigned apps or type to open Universal Search."
    ),
    CAPACITIVE_TRACKPAD(
        id = "mode_2_trackpad",
        shortTitle = "2: Trackpad",
        fullTitle = "2. Capacitive Trackpad Scroll",
        description = "Glide your thumb vertically or horizontally across the keyboard surface to scroll feeds and pages without touching the upper screen."
    ),
    PASSIVE_DOCK(
        id = "mode_4_passive",
        shortTitle = "4: Passive Dock",
        fullTitle = "4. Passive Hardware Dock",
        description = "Keyboard stays permanently docked at the bottom like physical keys, ignoring key taps until a text field is focused."
    );

    companion object {
        fun fromId(id: String?): IdleBehaviorMode =
            entries.firstOrNull { it.id == id } ?: SMART_COMBINED
    }
}

data class AppShortcut(
    val key: Char,
    val label: String,
    val packageName: String? = null,
    val fallbackCategoryOrAction: String? = null
)

data class InstalledAppInfo(
    val label: String,
    val packageName: String
)

data class KeyboardUiState(
    val idleMode: IdleBehaviorMode = IdleBehaviorMode.SMART_COMBINED,
    val isImeWindowVisible: Boolean = false,
    val isAccessibilityServiceRunning: Boolean = false,
    val isLauncherInForeground: Boolean = true,
    val isInputFocused: Boolean = false,
    val isHomeScreenActive: Boolean = true,
    val isFullscreenDetected: Boolean = false,
    val isLandscapeDetected: Boolean = false,
    val isManuallyMinimized: Boolean = false,
    val autoHideInFullscreen: Boolean = true,
    val autoHideInLandscape: Boolean = true,
    val hapticFeedbackEnabled: Boolean = true,
    val launchInAdjacentSplitScreen: Boolean = true,
    val trackpadSensitivity: Float = 1.5f,
    val universalSearchQuery: String = "",
    val isUniversalSearchExpanded: Boolean = false,
    val currentForegroundPackage: String = "",
    val lastTelemetryEvent: String = "AOK Launcher Ready",
    val lastScrollDeltaPx: Int = 0,
    val shortcuts: Map<Char, AppShortcut> = defaultShortcuts()
) {
    /**
     * Determines whether the keyboard should automatically hide itself right now
     * (e.g., watching full-screen video, playing an immersive game, or landscape media).
     */
    val isAutoHiddenByFullscreen: Boolean
        get() = (autoHideInFullscreen && isFullscreenDetected) ||
            (autoHideInLandscape && isLandscapeDetected)

    /**
     * Whether the full QWERTY keyboard dock should be visible on screen.
     */
    val shouldShowKeyboardDock: Boolean
        get() = !isAutoHiddenByFullscreen && !isManuallyMinimized

    /**
     * Whether Capacitive Trackpad scrolling (Mode 2) is active on keyboard swipe gestures.
     */
    val isTrackpadSwipeEnabled: Boolean
        get() = idleMode == IdleBehaviorMode.SMART_COMBINED ||
            idleMode == IdleBehaviorMode.CAPACITIVE_TRACKPAD ||
            idleMode == IdleBehaviorMode.QUICK_LAUNCH_SEARCH

    /**
     * Whether Quick-Launch & Universal Search (Mode 1) is active for key taps when no input is focused.
     */
    val isQuickLaunchActiveForIdleTaps: Boolean
        get() = !isInputFocused && (
            idleMode == IdleBehaviorMode.QUICK_LAUNCH_SEARCH ||
                (idleMode == IdleBehaviorMode.SMART_COMBINED && (isHomeScreenActive || isLauncherInForeground))
            )
}

private fun defaultShortcuts(): Map<Char, AppShortcut> = mapOf(
    'B' to AppShortcut('B', "Browser", "com.android.chrome", Intent.CATEGORY_APP_BROWSER),
    'C' to AppShortcut('C', "Chrome", "com.android.chrome", Intent.CATEGORY_APP_BROWSER),
    'G' to AppShortcut('G', "Gmail", "com.google.android.gm", Intent.CATEGORY_APP_EMAIL),
    'K' to AppShortcut('K', "Calendar", "com.google.android.calendar", Intent.CATEGORY_APP_CALENDAR),
    'M' to AppShortcut('M', "Messages", "com.google.android.apps.messaging", Intent.CATEGORY_APP_MESSAGING),
    'N' to AppShortcut('N', "Notes / Keep", "com.google.android.keep", null),
    'P' to AppShortcut('P', "Phone", "com.google.android.dialer", Intent.ACTION_DIAL),
    'S' to AppShortcut('S', "Settings", "com.android.settings", android.provider.Settings.ACTION_SETTINGS),
    'Y' to AppShortcut('Y', "YouTube", "com.google.android.youtube", null)
)

object KeyboardStateRepository {
    private const val PREFS_NAME = "always_on_keyboard_prefs"
    private const val KEY_IDLE_MODE = "idle_mode"
    private const val KEY_AUTO_HIDE_FULLSCREEN = "auto_hide_fullscreen"
    private const val KEY_AUTO_HIDE_LANDSCAPE = "auto_hide_landscape"
    private const val KEY_HAPTICS = "haptics_enabled"
    private const val KEY_LAUNCH_ADJACENT = "launch_adjacent_split"
    private const val KEY_SENSITIVITY = "trackpad_sensitivity"
    private const val KEY_SHORTCUT_PREFIX = "shortcut_pkg_"
    private const val KEY_SHORTCUT_LABEL_PREFIX = "shortcut_lbl_"

    private val _state = MutableStateFlow(KeyboardUiState())
    val state: StateFlow<KeyboardUiState> = _state.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    // Callbacks registered by AlwaysOnInputMethodService and AlwaysOnAccessibilityService
    var onCommitTextCallback: ((String) -> Boolean)? = null
    var onDeleteTextCallback: (() -> Boolean)? = null
    var onEnterActionCallback: (() -> Boolean)? = null
    var onTrackpadScrollCallback: ((dx: Float, dy: Float) -> Unit)? = null
    var onToggleSplitScreenCallback: (() -> Boolean)? = null

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedMode = IdleBehaviorMode.fromId(prefs.getString(KEY_IDLE_MODE, null))
        val autoFullscreen = prefs.getBoolean(KEY_AUTO_HIDE_FULLSCREEN, true)
        val autoLandscape = prefs.getBoolean(KEY_AUTO_HIDE_LANDSCAPE, true)
        val haptics = prefs.getBoolean(KEY_HAPTICS, true)
        val launchAdjacent = prefs.getBoolean(KEY_LAUNCH_ADJACENT, true)
        val sensitivity = prefs.getFloat(KEY_SENSITIVITY, 1.5f)

        val mergedShortcuts = defaultShortcuts().toMutableMap()
        for (ch in 'A'..'Z') {
            val savedPkg = prefs.getString("$KEY_SHORTCUT_PREFIX$ch", null)
            val savedLbl = prefs.getString("$KEY_SHORTCUT_LABEL_PREFIX$ch", null)
            if (savedPkg != null && savedLbl != null) {
                mergedShortcuts[ch] = AppShortcut(ch, savedLbl, savedPkg, null)
            }
        }

        _state.update {
            it.copy(
                idleMode = savedMode,
                autoHideInFullscreen = autoFullscreen,
                autoHideInLandscape = autoLandscape,
                hapticFeedbackEnabled = haptics,
                launchInAdjacentSplitScreen = launchAdjacent,
                trackpadSensitivity = sensitivity,
                shortcuts = mergedShortcuts
            )
        }
        refreshInstalledApps(context)
    }

    fun refreshInstalledApps(context: Context) {
        try {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            val apps = resolveInfos
                .mapNotNull { info ->
                    val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                    if (pkg == context.packageName) return@mapNotNull null
                    val label = info.loadLabel(pm).toString().trim()
                    if (label.isNotEmpty()) InstalledAppInfo(label, pkg) else null
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
            _installedApps.value = apps
        } catch (_: Exception) {
        }
    }

    fun setIdleMode(context: Context, mode: IdleBehaviorMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_IDLE_MODE, mode.id)
            .apply()
        _state.update {
            it.copy(
                idleMode = mode,
                lastTelemetryEvent = "Mode switched to ${mode.shortTitle}"
            )
        }
    }

    fun cycleIdleMode(context: Context) {
        val entries = IdleBehaviorMode.entries
        val nextIndex = (entries.indexOf(_state.value.idleMode) + 1) % entries.size
        setIdleMode(context, entries[nextIndex])
    }

    fun setAutoHideInFullscreen(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_HIDE_FULLSCREEN, enabled)
            .apply()
        _state.update { it.copy(autoHideInFullscreen = enabled) }
    }

    fun setAutoHideInLandscape(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_HIDE_LANDSCAPE, enabled)
            .apply()
        _state.update { it.copy(autoHideInLandscape = enabled) }
    }

    fun setHapticFeedback(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HAPTICS, enabled)
            .apply()
        _state.update { it.copy(hapticFeedbackEnabled = enabled) }
    }

    fun setLaunchInAdjacentSplitScreen(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LAUNCH_ADJACENT, enabled)
            .apply()
        _state.update { it.copy(launchInAdjacentSplitScreen = enabled) }
    }

    fun assignShortcut(context: Context, key: Char, label: String, packageName: String) {
        val upperKey = key.uppercaseChar()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("$KEY_SHORTCUT_PREFIX$upperKey", packageName)
            .putString("$KEY_SHORTCUT_LABEL_PREFIX$upperKey", label)
            .apply()
        _state.update { current ->
            val updated = current.shortcuts.toMutableMap()
            updated[upperKey] = AppShortcut(upperKey, label, packageName, null)
            current.copy(
                shortcuts = updated,
                lastTelemetryEvent = "Assigned '$upperKey' → $label"
            )
        }
    }

    fun setImeWindowVisible(visible: Boolean) {
        _state.update { it.copy(isImeWindowVisible = visible) }
    }

    fun setAccessibilityServiceRunning(running: Boolean) {
        _state.update { it.copy(isAccessibilityServiceRunning = running) }
    }

    fun setLauncherInForeground(inForeground: Boolean) {
        _state.update {
            it.copy(
                isLauncherInForeground = inForeground,
                isHomeScreenActive = if (inForeground) true else it.isHomeScreenActive
            )
        }
    }

    fun setInputFocused(focused: Boolean, packageName: String? = null) {
        _state.update {
            it.copy(
                isInputFocused = focused,
                currentForegroundPackage = packageName ?: it.currentForegroundPackage,
                isUniversalSearchExpanded = if (focused) false else it.isUniversalSearchExpanded,
                universalSearchQuery = if (focused) "" else it.universalSearchQuery
            )
        }
    }

    fun setForegroundWindowContext(packageName: String, isHomeLauncher: Boolean) {
        _state.update {
            it.copy(
                currentForegroundPackage = packageName,
                isHomeScreenActive = isHomeLauncher
            )
        }
    }

    fun setFullscreenDetected(fullscreen: Boolean, reason: String = "") {
        _state.update { current ->
            if (current.isFullscreenDetected == fullscreen) return@update current
            current.copy(
                isFullscreenDetected = fullscreen,
                lastTelemetryEvent = if (fullscreen) {
                    "Fullscreen content detected ($reason) → Auto-hiding keyboard"
                } else {
                    "Exited fullscreen → Restoring Always-On Keyboard"
                }
            )
        }
    }

    fun setLandscapeDetected(landscape: Boolean) {
        _state.update { it.copy(isLandscapeDetected = landscape) }
    }

    fun setManuallyMinimized(minimized: Boolean) {
        _state.update {
            it.copy(
                isManuallyMinimized = minimized,
                lastTelemetryEvent = if (minimized) {
                    "Keyboard minimized to bottom edge bar"
                } else {
                    "Keyboard restored"
                }
            )
        }
    }

    fun updateUniversalSearchQuery(query: String) {
        _state.update {
            it.copy(
                universalSearchQuery = query,
                isUniversalSearchExpanded = query.isNotEmpty()
            )
        }
    }

    fun clearUniversalSearch() {
        _state.update {
            it.copy(
                universalSearchQuery = "",
                isUniversalSearchExpanded = false
            )
        }
    }

    fun recordScrollTelemetry(dyPx: Int) {
        _state.update {
            it.copy(
                lastScrollDeltaPx = dyPx,
                lastTelemetryEvent = "Capacitive Trackpad scroll (${if (dyPx >= 0) "+$dyPx" else "$dyPx"} px)"
            )
        }
    }

    fun recordTelemetry(message: String) {
        _state.update { it.copy(lastTelemetryEvent = message) }
    }

    fun requestSplitScreenDock(context: Context) {
        val triggered = onToggleSplitScreenCallback?.invoke() ?: false
        if (triggered) {
            recordTelemetry("Toggled Split-Screen Dock Mode (App fits above keyboard)")
        } else {
            recordTelemetry("Enable AlwaysOn Dock Service in Accessibility to use 1-tap Split-Screen Dock")
        }
    }

    fun handleKeyPress(context: Context, keyLabel: String, isLongPress: Boolean = false) {
        val current = _state.value

        // 1. If a text field is focused, always route to text input!
        if (current.isInputFocused) {
            when (keyLabel) {
                "⌫" -> {
                    onDeleteTextCallback?.invoke()
                    recordTelemetry("Backspace in active input")
                }
                "ENTER" -> {
                    onEnterActionCallback?.invoke()
                    recordTelemetry("Enter ↵ in active input")
                }
                "SPACE" -> {
                    onCommitTextCallback?.invoke(" ")
                    recordTelemetry("Space in active input")
                }
                else -> {
                    onCommitTextCallback?.invoke(keyLabel)
                    recordTelemetry("Typed '$keyLabel'")
                }
            }
            return
        }

        // 2. No text field is focused! Check the active IdleBehaviorMode:
        if (!current.isQuickLaunchActiveForIdleTaps) {
            recordTelemetry("Passive Dock: Ignored '$keyLabel' (No text field focused)")
            return
        }

        // 3. Mode 1: Quick-Launch & Universal Search
        val upperChar = keyLabel.uppercase().firstOrNull()
        if (upperChar != null && upperChar in 'A'..'Z' && current.universalSearchQuery.isEmpty()) {
            val shortcut = current.shortcuts[upperChar]
            if (shortcut != null && (isLongPress || current.universalSearchQuery.isEmpty())) {
                val launched = launchAppShortcut(context, shortcut)
                if (launched) {
                    recordTelemetry("Quick-Launched ${shortcut.label} (${shortcut.key})")
                    return
                }
            }
        }

        // Otherwise update Universal Search bar
        when (keyLabel) {
            "⌫" -> {
                val next = current.universalSearchQuery.dropLast(1)
                updateUniversalSearchQuery(next)
            }
            "SPACE" -> {
                if (current.universalSearchQuery.isNotEmpty()) {
                    updateUniversalSearchQuery(current.universalSearchQuery + " ")
                }
            }
            "ENTER" -> {
                if (current.universalSearchQuery.isNotBlank()) {
                    executeUniversalSearch(context, current.universalSearchQuery.trim())
                }
            }
            else -> {
                if (keyLabel.length == 1) {
                    val next = current.universalSearchQuery + keyLabel
                    updateUniversalSearchQuery(next)
                    recordTelemetry("Universal Search: \"$next\"")
                }
            }
        }
    }

    fun launchPackage(context: Context, packageName: String, label: String = packageName): Boolean {
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(packageName) ?: return false
        applyDockLaunchFlags(launchIntent)
        return try {
            context.startActivity(launchIntent)
            clearUniversalSearch()
            recordTelemetry("Launched $label")
            true
        } catch (_: Exception) {
            false
        }
    }

    fun launchAppShortcut(context: Context, shortcut: AppShortcut): Boolean {
        val pm = context.packageManager
        // 1. Try direct package launch
        if (!shortcut.packageName.isNullOrBlank()) {
            val launchIntent = pm.getLaunchIntentForPackage(shortcut.packageName)
            if (launchIntent != null) {
                applyDockLaunchFlags(launchIntent)
                return try {
                    context.startActivity(launchIntent)
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }

        // 2. Try matching installed app by label
        val matchedApp = _installedApps.value.firstOrNull {
            it.label.contains(shortcut.label, ignoreCase = true)
        }
        if (matchedApp != null) {
            val launchIntent = pm.getLaunchIntentForPackage(matchedApp.packageName)
            if (launchIntent != null) {
                applyDockLaunchFlags(launchIntent)
                return try {
                    context.startActivity(launchIntent)
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }

        // 3. Try fallback system category or action
        val fallback = shortcut.fallbackCategoryOrAction ?: return false
        return try {
            val intent = if (fallback.startsWith("android.intent.category.")) {
                Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, fallback)
            } else {
                Intent(fallback)
            }
            applyDockLaunchFlags(intent)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun executeUniversalSearch(context: Context, query: String) {
        clearUniversalSearch()
        val matchingApp = _installedApps.value.firstOrNull {
            it.label.startsWith(query, ignoreCase = true) ||
                it.label.contains(query, ignoreCase = true)
        }
        if (matchingApp != null) {
            if (launchPackage(context, matchingApp.packageName, matchingApp.label)) {
                return
            }
        }

        try {
            val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(SearchManager.QUERY, query)
            }
            applyDockLaunchFlags(searchIntent)
            context.startActivity(searchIntent)
            recordTelemetry("Searched web for \"$query\"")
        } catch (_: Exception) {
            try {
                val browserIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
                )
                applyDockLaunchFlags(browserIntent)
                context.startActivity(browserIntent)
                recordTelemetry("Opened browser search for \"$query\"")
            } catch (_: Exception) {
                recordTelemetry("Search executed: \"$query\"")
            }
        }
    }

    private fun applyDockLaunchFlags(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (_state.value.launchInAdjacentSplitScreen) {
            intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        }
    }
}

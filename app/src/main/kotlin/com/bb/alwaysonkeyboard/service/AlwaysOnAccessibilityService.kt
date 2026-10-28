package com.bb.alwaysonkeyboard.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bb.alwaysonkeyboard.data.KeyboardStateRepository
import com.bb.alwaysonkeyboard.ui.keyboard.ModernQwertyKeyboard
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Persistent Keyboard Dock, Capacitive Trackpad Scroll Engine, and Smart Fullscreen Detector.
 *
 * Works in tandem with [AlwaysOnInputMethodService]:
 * - Keeps the Clean Modern Minimal QWERTY Keyboard docked at the bottom of the screen even when
 *   no text input is focused (Home screen, social feeds, browsers).
 * - Automatically hides its overlay window whenever [AlwaysOnInputMethodService]'s native IME window
 *   is visible so there is never a duplicate keyboard.
 * - Detects full-screen video & immersive content via system bar insets and [AccessibilityWindowInfo]
 *   window geometry, automatically hiding the keyboard when watching full-screen content.
 * - Executes smooth Capacitive Trackpad scrolling across any app when swiping across the keys.
 */
class AlwaysOnAccessibilityService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var composeLifecycleOwner: ServiceComposeLifecycleOwner? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var cachedHomePackages: Set<String> = emptySet()
    private var lastScrollGestureTimeMs: Long = 0L
    private var accumulatedScrollDy: Float = 0f
    private var accumulatedScrollDx: Float = 0f

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeInstance = WeakReference(this)
        KeyboardStateRepository.initialize(applicationContext)
        KeyboardStateRepository.setAccessibilityServiceRunning(true)
        refreshHomePackages()
        updateOrientation(resources.configuration)

        // Register Capacitive Trackpad scroll handler (Mode 2)
        KeyboardStateRepository.onTrackpadScrollCallback = { dx, dy ->
            performCapacitiveScroll(dx, dy)
        }

        // Fallback text callbacks when typing into a focused AccessibilityNodeInfo before IME opens
        if (KeyboardStateRepository.onCommitTextCallback == null) {
            KeyboardStateRepository.onCommitTextCallback = { text ->
                injectTextIntoFocusedNode(text)
            }
        }
        if (KeyboardStateRepository.onDeleteTextCallback == null) {
            KeyboardStateRepository.onDeleteTextCallback = {
                deleteFromFocusedNode()
            }
        }

        setupPersistentOverlayDock()

        // Observe state changes to synchronize overlay visibility with IME window & Fullscreen state
        serviceScope.launch {
            KeyboardStateRepository.state.collectLatest { state ->
                val view = overlayView ?: return@collectLatest
                // Hide overlay when native IME window is already showing the keyboard OR when fullscreen content is active
                val shouldShowOverlay = !state.isImeWindowVisible && !state.isAutoHiddenByFullscreen
                view.visibility = if (shouldShowOverlay) View.VISIBLE else View.GONE
            }
        }
    }

    private fun setupPersistentOverlayDock() {
        if (overlayView != null) return
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val owner = ServiceComposeLifecycleOwner().also {
            it.onCreate()
            it.onStart()
            composeLifecycleOwner = it
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }

        val view = ComposeView(this).apply {
            owner.attachToView(this)
            setContent {
                ModernQwertyKeyboard()
            }
        }

        // Listen for WindowInsets changes on the overlay to detect when the foreground app
        // hides the status/navigation bars (e.g., YouTube/Netflix fullscreen video or immersive games)
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val statusBarsVisible = insets.isVisible(WindowInsetsCompat.Type.statusBars())
            if (!statusBarsVisible) {
                KeyboardStateRepository.setFullscreenDetected(true, "Immersive System Bars Hidden")
            } else {
                evaluateWindowsForFullscreen()
            }
            insets
        }

        try {
            wm.addView(view, params)
            overlayView = view
        } catch (_: Exception) {
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkg = event.packageName?.toString().orEmpty()
        if (pkg.isNotEmpty() && pkg != packageName) {
            val isHome = cachedHomePackages.contains(pkg) || pkg.contains("launcher", ignoreCase = true)
            KeyboardStateRepository.setForegroundWindowContext(pkg, isHome)
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                evaluateFocusedInputNode()
                evaluateWindowsForFullscreen()
            }
            else -> {}
        }
    }

    private fun evaluateFocusedInputNode() {
        try {
            val root = rootInActiveWindow ?: return
            val focusedInput = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            val isEditableFocused = focusedInput != null && focusedInput.isEditable && focusedInput.isFocused
            // Only override input focus if the IME window isn't already managing it
            if (!KeyboardStateRepository.state.value.isImeWindowVisible) {
                KeyboardStateRepository.setInputFocused(isEditableFocused, focusedInput?.packageName?.toString())
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Inspects interactive windows to determine whether the user is watching full-screen content
     * (e.g., full-screen video player or immersive game where the status bar window is hidden).
     */
    private fun evaluateWindowsForFullscreen() {
        try {
            val currentWindows = windows
            if (currentWindows.isNullOrEmpty()) return

            val metrics = resources.displayMetrics
            val screenWidth = metrics.widthPixels
            val screenHeight = metrics.heightPixels

            var hasTopSystemStatusBar = false
            var hasFullscreenAppWindow = false
            val rect = Rect()

            for (win in currentWindows) {
                win.getBoundsInScreen(rect)
                when (win.type) {
                    AccessibilityWindowInfo.TYPE_SYSTEM -> {
                        // A visible status bar sits at top == 0 and spans the width of the screen
                        if (rect.top == 0 && rect.width() >= screenWidth / 2 && rect.height() in 1..200) {
                            hasTopSystemStatusBar = true
                        }
                    }
                    AccessibilityWindowInfo.TYPE_APPLICATION -> {
                        if (rect.top <= 0 &&
                            rect.left <= 0 &&
                            rect.width() >= screenWidth &&
                            rect.height() >= screenHeight - 4
                        ) {
                            hasFullscreenAppWindow = true
                        }
                    }
                }
            }

            // If an app window spans 100% of the screen and the system status bar is hidden,
            // the app is in full-screen / immersive media mode!
            val isFullscreen = hasFullscreenAppWindow && !hasTopSystemStatusBar
            KeyboardStateRepository.setFullscreenDetected(
                fullscreen = isFullscreen,
                reason = if (isFullscreen) "Fullscreen Window" else ""
            )
        } catch (_: Exception) {
        }
    }

    /**
     * Performs Capacitive Trackpad scrolling (Mode 2) across the active app on screen.
     */
    private fun performCapacitiveScroll(dx: Float, dy: Float) {
        accumulatedScrollDx += dx
        accumulatedScrollDy += dy

        val now = SystemClock.uptimeMillis()
        if (now - lastScrollGestureTimeMs < 55L) {
            return
        }
        if (abs(accumulatedScrollDy) < 12f && abs(accumulatedScrollDx) < 12f) {
            return
        }

        val dispatchDy = accumulatedScrollDy.coerceIn(-420f, 420f)
        val dispatchDx = accumulatedScrollDx.coerceIn(-320f, 320f)
        accumulatedScrollDy = 0f
        accumulatedScrollDx = 0f
        lastScrollGestureTimeMs = now

        val metrics = resources.displayMetrics
        val centerX = metrics.widthPixels * 0.5f
        // Perform the swipe gesture in the upper 45% of the screen (well above the keyboard dock)
        val startY = metrics.heightPixels * 0.42f
        val endX = (centerX + dispatchDx).coerceIn(metrics.widthPixels * 0.1f, metrics.widthPixels * 0.9f)
        val endY = (startY + dispatchDy).coerceIn(metrics.heightPixels * 0.12f, metrics.heightPixels * 0.68f)

        if (abs(endY - startY) < 4f && abs(endX - centerX) < 4f) return

        val path = Path().apply {
            moveTo(centerX, startY)
            lineTo(endX, endY)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0L, 45L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val dispatched = dispatchGesture(gesture, null, null)
        if (!dispatched) {
            // Fallback to AccessibilityNodeInfo scroll action if gesture dispatch is busy
            scrollActiveNodeFallback(dispatchDy < 0)
        }
    }

    private fun scrollActiveNodeFallback(scrollForward: Boolean) {
        try {
            val root = rootInActiveWindow ?: return
            val scrollable = findScrollableNode(root) ?: return
            val action = if (scrollForward) {
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            } else {
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            }
            scrollable.performAction(action)
        } catch (_: Exception) {
        }
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val found = findScrollableNode(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    private fun refreshHomePackages() {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            val resolves = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            cachedHomePackages = resolves.mapNotNull { it.activityInfo?.packageName }.toSet()
        } catch (_: Exception) {
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateOrientation(newConfig)
    }

    private fun updateOrientation(config: Configuration) {
        val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
        KeyboardStateRepository.setLandscapeDetected(isLandscape)
    }

    override fun onInterrupt() {
        // Required override for AccessibilityService
    }

    override fun onDestroy() {
        KeyboardStateRepository.setAccessibilityServiceRunning(false)
        serviceScope.cancel()
        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {
            }
        }
        overlayView = null
        composeLifecycleOwner?.onStop()
        composeLifecycleOwner?.onDestroy()
        composeLifecycleOwner = null
        activeInstance = null
        super.onDestroy()
    }

    companion object {
        private var activeInstance: WeakReference<AlwaysOnAccessibilityService>? = null

        fun injectTextIntoFocusedNode(text: String): Boolean {
            val service = activeInstance?.get() ?: return false
            return try {
                val root = service.rootInActiveWindow ?: return false
                val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
                if (!focused.isEditable) return false
                val existing = focused.text?.toString().orEmpty()
                val args = Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        existing + text
                    )
                }
                focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            } catch (_: Exception) {
                false
            }
        }

        fun deleteFromFocusedNode(): Boolean {
            val service = activeInstance?.get() ?: return false
            return try {
                val root = service.rootInActiveWindow ?: return false
                val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
                if (!focused.isEditable) return false
                val existing = focused.text?.toString().orEmpty()
                if (existing.isEmpty()) return false
                val args = Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        existing.dropLast(1)
                    )
                }
                focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            } catch (_: Exception) {
                false
            }
        }
    }
}

package com.bb.alwaysonkeyboard.service

import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.platform.ComposeView
import com.bb.alwaysonkeyboard.data.KeyboardStateRepository
import com.bb.alwaysonkeyboard.ui.keyboard.ModernQwertyKeyboard
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * System InputMethodService (IME) for the Always-On Minimal Keyboard.
 *
 * To make apps resize so their bottom ends at the top of the keyboard (instead of being covered
 * by a floating overlay):
 * 1. Keeps the native IME window open at all times ([showWindow] + [onEvaluateInputViewShown] +
 *    intercepting [hideWindow] while [KeyboardStateRepository] indicates the dock should stay open).
 * 2. Explicitly reports the exact top Y coordinate of the keyboard in [onComputeInsets] as both
 *    [Insets.contentTopInsets] and [Insets.visibleTopInsets], instructing Android's WindowManager
 *    to shrink the foreground app's content frame above the keyboard.
 * 3. Releases the IME window (`super.hideWindow()`) as soon as fullscreen media/video is detected
 *    or when the user manually minimizes the keyboard.
 */
class AlwaysOnInputMethodService : InputMethodService() {

    private var composeLifecycleOwner: ServiceComposeLifecycleOwner? = null
    private var inputComposeView: ComposeView? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        activeInstance = WeakReference(this)
        KeyboardStateRepository.initialize(applicationContext)
        updateOrientationState(resources.configuration)

        KeyboardStateRepository.onCommitTextCallback = { text ->
            val ic = currentInputConnection
            if (ic != null) {
                ic.commitText(text, 1)
                true
            } else {
                AlwaysOnAccessibilityService.injectTextIntoFocusedNode(text)
            }
        }

        KeyboardStateRepository.onDeleteTextCallback = {
            val ic = currentInputConnection
            if (ic != null) {
                val selected = ic.getSelectedText(0)
                if (!selected.isNullOrEmpty()) {
                    ic.commitText("", 1)
                } else {
                    ic.deleteSurroundingText(1, 0)
                }
                true
            } else {
                AlwaysOnAccessibilityService.deleteFromFocusedNode()
            }
        }

        KeyboardStateRepository.onEnterActionCallback = {
            val ic = currentInputConnection
            val info = currentInputEditorInfo
            if (ic != null) {
                val actionId = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
                if (actionId != EditorInfo.IME_ACTION_NONE && actionId != EditorInfo.IME_ACTION_UNSPECIFIED) {
                    ic.performEditorAction(actionId)
                } else {
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                }
                true
            } else {
                false
            }
        }

        // Reactively hide or restore the native IME window when Fullscreen Video or Manual Minimize changes
        serviceScope.launch {
            KeyboardStateRepository.state.collectLatest { state ->
                if (!state.shouldShowKeyboardDock) {
                    // Fullscreen content detected or user tapped Hide -> actually close IME window
                    // so the app gets 100% of the screen!
                    if (isInputViewShown) {
                        super.hideWindow()
                        KeyboardStateRepository.setImeWindowVisible(false)
                    }
                } else {
                    // Keyboard should be docked -> force native IME window open so apps resize above it
                    ensureNativeImeShown()
                }
            }
        }
    }

    override fun onCreateInputView(): View {
        composeLifecycleOwner?.onDestroy()
        val owner = ServiceComposeLifecycleOwner().also {
            it.onCreate()
            it.onStart()
            composeLifecycleOwner = it
        }

        val composeView = ComposeView(this).apply {
            owner.attachToView(this)
            setContent {
                ModernQwertyKeyboard()
            }
            // Whenever the keyboard height changes, notify WindowManager so it recalculates
            // contentTopInsets and resizes the underlying app to match the keyboard height.
            addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
                if ((bottom - top) != (oldBottom - oldTop)) {
                    window?.window?.decorView?.requestLayout()
                }
            }
        }
        inputComposeView = composeView
        owner.attachToView(window?.window?.decorView ?: composeView)
        return composeView
    }

    /**
     * Critical hook that tells Android's WindowManager the exact Y coordinate where the keyboard starts,
     * forcing the foreground app's layout to end at the top of the keyboard instead of being covered.
     */
    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        val state = KeyboardStateRepository.state.value
        val view = inputComposeView

        if (state.shouldShowKeyboardDock && view != null && view.height > 0) {
            val loc = IntArray(2)
            view.getLocationInWindow(loc)
            val topY = loc[1]
            if (topY > 0) {
                outInsets.contentTopInsets = topY
                outInsets.visibleTopInsets = topY
                outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_CONTENT
                return
            }
        }

        if (!state.shouldShowKeyboardDock) {
            val decorHeight = window?.window?.decorView?.height ?: resources.displayMetrics.heightPixels
            outInsets.contentTopInsets = decorHeight
            outInsets.visibleTopInsets = decorHeight
            outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_CONTENT
        }
    }

    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        return KeyboardStateRepository.state.value.shouldShowKeyboardDock
    }

    override fun onShowInputRequested(flags: Int, configChange: Boolean): Boolean {
        if (KeyboardStateRepository.state.value.shouldShowKeyboardDock) {
            return true
        }
        return super.onShowInputRequested(flags, configChange)
    }

    override fun onEvaluateFullscreenMode(): Boolean {
        return false
    }

    override fun onBindInput() {
        super.onBindInput()
        if (KeyboardStateRepository.state.value.shouldShowKeyboardDock) {
            ensureNativeImeShown()
        }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        val hasValidInput = attribute != null && attribute.inputType != InputType.TYPE_NULL
        KeyboardStateRepository.setInputFocused(hasValidInput, attribute?.packageName)
        updateInputViewShown()

        if (KeyboardStateRepository.state.value.shouldShowKeyboardDock) {
            ensureNativeImeShown()
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        composeLifecycleOwner?.onStart()
        val hasValidInput = info != null && info.inputType != InputType.TYPE_NULL
        KeyboardStateRepository.setInputFocused(hasValidInput, info?.packageName)
        KeyboardStateRepository.setImeWindowVisible(true)
        window?.window?.decorView?.requestLayout()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        KeyboardStateRepository.setInputFocused(false)
    }

    override fun onWindowShown() {
        super.onWindowShown()
        composeLifecycleOwner?.onStart()
        KeyboardStateRepository.setImeWindowVisible(true)
        window?.window?.decorView?.requestLayout()
    }

    /**
     * Intercept system attempts to hide the soft keyboard when switching screens or tapping
     * outside a text field. As long as [KeyboardStateRepository.state.value.shouldShowKeyboardDock]
     * is true (not watching fullscreen video and not manually minimized), keep the native IME
     * window open so apps remain resized above the keyboard!
     */
    override fun hideWindow() {
        val state = KeyboardStateRepository.state.value
        if (state.shouldShowKeyboardDock) {
            // Refuse to hide: keep the native IME window shown so the screen stays resized above the keyboard
            KeyboardStateRepository.setInputFocused(false)
            ensureNativeImeShown()
            return
        }
        super.hideWindow()
        KeyboardStateRepository.setImeWindowVisible(false)
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        val state = KeyboardStateRepository.state.value
        if (state.shouldShowKeyboardDock) {
            ensureNativeImeShown()
        } else {
            KeyboardStateRepository.setImeWindowVisible(false)
            KeyboardStateRepository.setInputFocused(false)
        }
    }

    @Suppress("DEPRECATION")
    fun ensureNativeImeShown() {
        mainHandler.post {
            if (!KeyboardStateRepository.state.value.shouldShowKeyboardDock) return@post
            try {
                updateInputViewShown()
                showWindow(true)
                requestShowSelf(InputMethodManager.SHOW_FORCED)
                KeyboardStateRepository.setImeWindowVisible(true)
                window?.window?.decorView?.requestLayout()
            } catch (_: Exception) {
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateOrientationState(newConfig)
        updateInputViewShown()
    }

    private fun updateOrientationState(config: Configuration) {
        val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
        KeyboardStateRepository.setLandscapeDetected(isLandscape)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        KeyboardStateRepository.setImeWindowVisible(false)
        inputComposeView = null
        composeLifecycleOwner?.onStop()
        composeLifecycleOwner?.onDestroy()
        composeLifecycleOwner = null
        activeInstance = null
        super.onDestroy()
    }

    companion object {
        private var activeInstance: WeakReference<AlwaysOnInputMethodService>? = null

        fun requestShowNativeKeyboard() {
            activeInstance?.get()?.ensureNativeImeShown()
        }
    }
}

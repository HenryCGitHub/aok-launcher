package com.bb.alwaysonkeyboard.service

import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.platform.ComposeView
import com.bb.alwaysonkeyboard.data.KeyboardStateRepository
import com.bb.alwaysonkeyboard.ui.keyboard.ModernQwertyKeyboard

/**
 * System InputMethodService (IME) for the Always-On Minimal Keyboard.
 *
 * - Overrides [onEvaluateInputViewShown] to keep the soft keyboard view active like a physical keyboard.
 * - Routes key taps directly to [getCurrentInputConnection] when any text field is focused.
 * - Synchronizes visibility with [AlwaysOnAccessibilityService] so there is never a duplicate keyboard
 *   and so the keyboard remains persistently docked when navigating to screens without text inputs.
 * - Automatically hides when [KeyboardStateRepository] detects fullscreen content (videos/games).
 */
class AlwaysOnInputMethodService : InputMethodService() {

    private var composeLifecycleOwner: ServiceComposeLifecycleOwner? = null

    override fun onCreate() {
        super.onCreate()
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
        }
        owner.attachToView(window?.window?.decorView ?: composeView)
        return composeView
    }

    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        // Keep the input view shown unless fullscreen video/game auto-hide is active
        return !KeyboardStateRepository.state.value.isAutoHiddenByFullscreen
    }

    override fun onEvaluateFullscreenMode(): Boolean {
        // Never cover the screen with an opaque ExtractEditText view
        return false
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        val hasValidInput = attribute != null && attribute.inputType != InputType.TYPE_NULL
        KeyboardStateRepository.setInputFocused(hasValidInput, attribute?.packageName)
        updateInputViewShown()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        composeLifecycleOwner?.onStart()
        val hasValidInput = info != null && info.inputType != InputType.TYPE_NULL
        KeyboardStateRepository.setInputFocused(hasValidInput, info?.packageName)
        KeyboardStateRepository.setImeWindowVisible(true)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        KeyboardStateRepository.setInputFocused(false)
    }

    override fun onWindowShown() {
        super.onWindowShown()
        composeLifecycleOwner?.onStart()
        KeyboardStateRepository.setImeWindowVisible(true)
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        KeyboardStateRepository.setImeWindowVisible(false)
        KeyboardStateRepository.setInputFocused(false)

        // If the AccessibilityService dock is NOT running, try to keep the IME self-shown
        // unless fullscreen content is active or the user manually minimized it.
        val currentState = KeyboardStateRepository.state.value
        if (!currentState.isAccessibilityServiceRunning && currentState.shouldShowKeyboardDock) {
            try {
                requestShowSelf(InputMethodManager.SHOW_IMPLICIT)
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
        KeyboardStateRepository.setImeWindowVisible(false)
        composeLifecycleOwner?.onStop()
        composeLifecycleOwner?.onDestroy()
        composeLifecycleOwner = null
        super.onDestroy()
    }
}

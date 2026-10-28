package com.bb.alwaysonkeyboard.data

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test

class KeyboardStateRepositoryTest {

    @Test
    fun smartCombinedMode_enablesTrackpadAndQuickLaunchOnHome() {
        val homeState = KeyboardUiState(
            idleMode = IdleBehaviorMode.SMART_COMBINED,
            isInputFocused = false,
            isHomeScreenActive = true
        )
        assertTrue(homeState.isTrackpadSwipeEnabled)
        assertTrue(homeState.isQuickLaunchActiveForIdleTaps)

        // When inside a regular app with no input focused, Smart Combined acts as Passive Dock (Mode 4) for taps
        // while keeping Capacitive Trackpad scrolling (Mode 2) enabled!
        val inAppIdleState = homeState.copy(isHomeScreenActive = false, isLauncherInForeground = false)
        assertTrue(inAppIdleState.isTrackpadSwipeEnabled)
        assertFalse(inAppIdleState.isQuickLaunchActiveForIdleTaps)
    }

    @Test
    fun fullscreenDetection_autoHidesKeyboardWhenEnabled() {
        val normalState = KeyboardUiState(
            autoHideInFullscreen = true,
            isFullscreenDetected = false
        )
        assertFalse(normalState.isAutoHiddenByFullscreen)
        assertTrue(normalState.shouldShowKeyboardDock)

        val fullscreenVideoState = normalState.copy(isFullscreenDetected = true)
        assertTrue(fullscreenVideoState.isAutoHiddenByFullscreen)
        assertFalse(fullscreenVideoState.shouldShowKeyboardDock)
    }
}

package com.bb.alwaysonkeyboard.ui.keyboard

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bb.alwaysonkeyboard.data.InstalledAppInfo
import com.bb.alwaysonkeyboard.data.KeyboardStateRepository
import com.bb.alwaysonkeyboard.data.KeyboardUiState
import kotlin.math.abs

private enum class ShiftState {
    LOWER,
    SHIFTED,
    CAPS_LOCK
}

// Clean Modern Minimal Dark Palette (Physical-inspired matte finish)
private val DockBackground = Color(0xFF111318)
private val DockTopBorder = Color(0xFF252932)
private val KeySurface = Color(0xFF1D2028)
private val KeyActionSurface = Color(0xFF282C37)
private val KeyBorder = Color(0xFF2E3340)
private val KeyTextPrimary = Color(0xFFF3F4F6)
private val KeyTextSecondary = Color(0xFF8A919E)
private val AccentBlue = Color(0xFF4F8CFF)
private val AccentEmerald = Color(0xFF10B981)
private val AccentAmber = Color(0xFFF59E0B)

private val QwertyRow1 = listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P")
private val QwertyRow1Numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
private val QwertyRow2 = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L")
private val QwertyRow3 = listOf("Z", "X", "C", "V", "B", "N", "M")

private val SymbolsRow1 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
private val SymbolsRow2 = listOf("@", "#", "$", "_", "&", "-", "+", "(", ")")
private val SymbolsRow3 = listOf("*", "\"", "'", ":", ";", "!", "?")

/**
 * Clean Modern Minimal QWERTY Keyboard View.
 * Renders identically inside:
 * - [com.bb.alwaysonkeyboard.service.AlwaysOnInputMethodService] (when an input connection is active)
 * - [com.bb.alwaysonkeyboard.service.AlwaysOnAccessibilityService] (when persistently docked with no input focused)
 * - [com.bb.alwaysonkeyboard.ui.main.MainScreen] (live interactive test sandbox)
 */
@Composable
fun ModernQwertyKeyboard(
    modifier: Modifier = Modifier,
    isPreviewOrSandbox: Boolean = false,
    onSandboxKeyTap: ((String, Boolean) -> Unit)? = null,
    onSandboxScroll: ((Float, Float) -> Unit)? = null
) {
    val context = LocalContext.current
    val view = LocalView.current
    val state by KeyboardStateRepository.state.collectAsState()
    val installedApps by KeyboardStateRepository.installedApps.collectAsState()

    var shiftState by remember { mutableStateOf(ShiftState.LOWER) }
    var isSymbolsLayer by remember { mutableStateOf(false) }
    var isCurrentlySwipingTrackpad by remember { mutableStateOf(false) }

    val dragSlopPx = with(LocalDensity.current) { 18.dp.toPx() }

    // 1. If Fullscreen video/game is active and auto-hide is enabled, completely hide the keyboard!
    if (state.isAutoHiddenByFullscreen && !isPreviewOrSandbox) {
        return
    }

    // 2. If manually minimized via "Hide ▾", show a minimal edge restore pill at the bottom
    if (state.isManuallyMinimized) {
        CollapsedKeyboardRestorePill(
            state = state,
            onRestore = {
                if (state.hapticFeedbackEnabled) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
                KeyboardStateRepository.setManuallyMinimized(false)
            }
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DockBackground)
            .border(width = 1.dp, color = DockTopBorder)
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        // Top Utility & Mode Switcher Strip
        KeyboardUtilityBar(
            state = state,
            isSwipingTrackpad = isCurrentlySwipingTrackpad,
            onCycleMode = {
                if (state.hapticFeedbackEnabled) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
                KeyboardStateRepository.cycleIdleMode(context)
            },
            onSplitDock = {
                if (state.hapticFeedbackEnabled) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
                KeyboardStateRepository.requestSplitScreenDock(context)
            },
            onMinimize = {
                if (state.hapticFeedbackEnabled) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
                KeyboardStateRepository.setManuallyMinimized(true)
            }
        )

        // Mode 1: Expandable Inline Universal Search Bar when typing without a focused input
        AnimatedVisibility(
            visible = !state.isInputFocused && state.universalSearchQuery.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            UniversalSearchInlineBar(
                query = state.universalSearchQuery,
                installedApps = installedApps,
                onLaunchApp = { app ->
                    val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                    if (intent != null) {
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        try {
                            context.startActivity(intent)
                            KeyboardStateRepository.clearUniversalSearch()
                            KeyboardStateRepository.recordTelemetry("Launched ${app.label}")
                        } catch (_: Exception) {
                        }
                    }
                },
                onExecuteSearch = {
                    KeyboardStateRepository.executeUniversalSearch(context, state.universalSearchQuery)
                },
                onClear = {
                    KeyboardStateRepository.clearUniversalSearch()
                }
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Main QWERTY Key Grid + Capacitive Trackpad Gesture Surface (Mode 2)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(state.isTrackpadSwipeEnabled, state.trackpadSensitivity) {
                    if (!state.isTrackpadSwipeEnabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        var totalDx = 0f
                        var totalDy = 0f
                        var isDragging = false

                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val delta = change.positionChange()
                            totalDx += delta.x
                            totalDy += delta.y

                            if (!isDragging && (abs(totalDx) > dragSlopPx || abs(totalDy) > dragSlopPx)) {
                                isDragging = true
                                isCurrentlySwipingTrackpad = true
                            }

                            if (isDragging) {
                                change.consume()
                                val scaledDx = delta.x * state.trackpadSensitivity
                                val scaledDy = delta.y * state.trackpadSensitivity
                                if (abs(scaledDy) > 1.5f || abs(scaledDx) > 1.5f) {
                                    onSandboxScroll?.invoke(scaledDx, scaledDy)
                                    KeyboardStateRepository.onTrackpadScrollCallback?.invoke(scaledDx, scaledDy)
                                    KeyboardStateRepository.recordScrollTelemetry((-scaledDy).toInt())
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        isCurrentlySwipingTrackpad = false
                    }
                },
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            val row1 = if (isSymbolsLayer) SymbolsRow1 else QwertyRow1
            val row2 = if (isSymbolsLayer) SymbolsRow2 else QwertyRow2
            val row3 = if (isSymbolsLayer) SymbolsRow3 else QwertyRow3

            // Row 1 (10 Keys)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                row1.forEachIndexed { idx, rawKey ->
                    val displayKey = formatKeyCase(rawKey, shiftState, isSymbolsLayer)
                    val secondaryHint = if (!isSymbolsLayer) QwertyRow1Numbers.getOrNull(idx) else null
                    val hasShortcut = !isSymbolsLayer &&
                        state.isQuickLaunchActiveForIdleTaps &&
                        state.shortcuts.containsKey(rawKey.first())

                    ModernKeyCap(
                        modifier = Modifier.weight(1f),
                        label = displayKey,
                        secondaryHint = secondaryHint,
                        showShortcutDot = hasShortcut,
                        onTap = {
                            triggerHaptic(view, state.hapticFeedbackEnabled)
                            dispatchKey(
                                context = context,
                                key = displayKey,
                                isLongPress = false,
                                shiftState = shiftState,
                                onResetShift = { if (shiftState == ShiftState.SHIFTED) shiftState = ShiftState.LOWER },
                                onSandboxKeyTap = onSandboxKeyTap
                            )
                        },
                        onLongPress = {
                            triggerHaptic(view, state.hapticFeedbackEnabled)
                            if (!isSymbolsLayer && state.isInputFocused && secondaryHint != null) {
                                dispatchKey(
                                    context = context,
                                    key = secondaryHint,
                                    isLongPress = true,
                                    shiftState = shiftState,
                                    onResetShift = {},
                                    onSandboxKeyTap = onSandboxKeyTap
                                )
                            } else {
                                dispatchKey(
                                    context = context,
                                    key = rawKey,
                                    isLongPress = true,
                                    shiftState = shiftState,
                                    onResetShift = {},
                                    onSandboxKeyTap = onSandboxKeyTap
                                )
                            }
                        }
                    )
                }
            }

            // Row 2 (9 Keys, slightly inset for authentic ergonomic stagger)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                row2.forEach { rawKey ->
                    val displayKey = formatKeyCase(rawKey, shiftState, isSymbolsLayer)
                    val hasShortcut = !isSymbolsLayer &&
                        state.isQuickLaunchActiveForIdleTaps &&
                        state.shortcuts.containsKey(rawKey.first())

                    ModernKeyCap(
                        modifier = Modifier.weight(1f),
                        label = displayKey,
                        showShortcutDot = hasShortcut,
                        onTap = {
                            triggerHaptic(view, state.hapticFeedbackEnabled)
                            dispatchKey(
                                context = context,
                                key = displayKey,
                                isLongPress = false,
                                shiftState = shiftState,
                                onResetShift = { if (shiftState == ShiftState.SHIFTED) shiftState = ShiftState.LOWER },
                                onSandboxKeyTap = onSandboxKeyTap
                            )
                        },
                        onLongPress = {
                            triggerHaptic(view, state.hapticFeedbackEnabled)
                            dispatchKey(
                                context = context,
                                key = rawKey,
                                isLongPress = true,
                                shiftState = shiftState,
                                onResetShift = {},
                                onSandboxKeyTap = onSandboxKeyTap
                            )
                        }
                    )
                }
            }

            // Row 3 (Shift + 7 Keys + Backspace)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Shift Key
                val shiftLabel = when {
                    isSymbolsLayer -> "=#<"
                    shiftState == ShiftState.CAPS_LOCK -> "⇪"
                    shiftState == ShiftState.SHIFTED -> "⬆"
                    else -> "⇧"
                }
                ModernKeyCap(
                    modifier = Modifier.weight(1.35f),
                    label = shiftLabel,
                    isActionKey = true,
                    isHighlighted = !isSymbolsLayer && shiftState != ShiftState.LOWER,
                    onTap = {
                        triggerHaptic(view, state.hapticFeedbackEnabled)
                        if (!isSymbolsLayer) {
                            shiftState = when (shiftState) {
                                ShiftState.LOWER -> ShiftState.SHIFTED
                                ShiftState.SHIFTED -> ShiftState.CAPS_LOCK
                                ShiftState.CAPS_LOCK -> ShiftState.LOWER
                            }
                        }
                    },
                    onLongPress = {
                        triggerHaptic(view, state.hapticFeedbackEnabled)
                        shiftState = ShiftState.CAPS_LOCK
                    }
                )

                row3.forEach { rawKey ->
                    val displayKey = formatKeyCase(rawKey, shiftState, isSymbolsLayer)
                    val hasShortcut = !isSymbolsLayer &&
                        state.isQuickLaunchActiveForIdleTaps &&
                        state.shortcuts.containsKey(rawKey.first())

                    ModernKeyCap(
                        modifier = Modifier.weight(1f),
                        label = displayKey,
                        showShortcutDot = hasShortcut,
                        onTap = {
                            triggerHaptic(view, state.hapticFeedbackEnabled)
                            dispatchKey(
                                context = context,
                                key = displayKey,
                                isLongPress = false,
                                shiftState = shiftState,
                                onResetShift = { if (shiftState == ShiftState.SHIFTED) shiftState = ShiftState.LOWER },
                                onSandboxKeyTap = onSandboxKeyTap
                            )
                        },
                        onLongPress = {
                            triggerHaptic(view, state.hapticFeedbackEnabled)
                            dispatchKey(
                                context = context,
                                key = rawKey,
                                isLongPress = true,
                                shiftState = shiftState,
                                onResetShift = {},
                                onSandboxKeyTap = onSandboxKeyTap
                            )
                        }
                    )
                }

                // Backspace Key
                ModernKeyCap(
                    modifier = Modifier.weight(1.35f),
                    label = "⌫",
                    isActionKey = true,
                    onTap = {
                        triggerHaptic(view, state.hapticFeedbackEnabled)
                        onSandboxKeyTap?.invoke("⌫", false)
                        KeyboardStateRepository.handleKeyPress(context, "⌫", false)
                    },
                    onLongPress = {
                        triggerHaptic(view, state.hapticFeedbackEnabled)
                        KeyboardStateRepository.clearUniversalSearch()
                        repeat(5) {
                            onSandboxKeyTap?.invoke("⌫", true)
                            KeyboardStateRepository.handleKeyPress(context, "⌫", true)
                        }
                    }
                )
            }

            // Row 4 (Symbols ?123, Comma, Capacitive Spacebar, Period, Enter)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ModernKeyCap(
                    modifier = Modifier.weight(1.3f),
                    label = if (isSymbolsLayer) "ABC" else "?123",
                    isActionKey = true,
                    isHighlighted = isSymbolsLayer,
                    onTap = {
                        triggerHaptic(view, state.hapticFeedbackEnabled)
                        isSymbolsLayer = !isSymbolsLayer
                    }
                )

                ModernKeyCap(
                    modifier = Modifier.weight(0.85f),
                    label = ",",
                    isActionKey = true,
                    onTap = {
                        triggerHaptic(view, state.hapticFeedbackEnabled)
                        onSandboxKeyTap?.invoke(",", false)
                        KeyboardStateRepository.handleKeyPress(context, ",", false)
                    }
                )

                // Spacebar with live mode/trackpad indicator
                val spacebarSubtitle = when {
                    isCurrentlySwipingTrackpad -> "⇅ scrolling..."
                    state.isInputFocused -> "space"
                    state.isQuickLaunchActiveForIdleTaps -> "quick-launch • swipe to scroll"
                    state.isTrackpadSwipeEnabled -> "⇅ swipe keys to scroll"
                    else -> "passive dock (tap input)"
                }

                ModernKeyCap(
                    modifier = Modifier.weight(4.2f),
                    label = spacebarSubtitle,
                    isSpacebar = true,
                    isHighlighted = isCurrentlySwipingTrackpad,
                    onTap = {
                        triggerHaptic(view, state.hapticFeedbackEnabled)
                        onSandboxKeyTap?.invoke("SPACE", false)
                        KeyboardStateRepository.handleKeyPress(context, "SPACE", false)
                    }
                )

                ModernKeyCap(
                    modifier = Modifier.weight(0.85f),
                    label = ".",
                    isActionKey = true,
                    onTap = {
                        triggerHaptic(view, state.hapticFeedbackEnabled)
                        onSandboxKeyTap?.invoke(".", false)
                        KeyboardStateRepository.handleKeyPress(context, ".", false)
                    }
                )

                ModernKeyCap(
                    modifier = Modifier.weight(1.4f),
                    label = "↵",
                    isPrimaryAction = true,
                    onTap = {
                        triggerHaptic(view, state.hapticFeedbackEnabled)
                        onSandboxKeyTap?.invoke("ENTER", false)
                        KeyboardStateRepository.handleKeyPress(context, "ENTER", false)
                    }
                )
            }
        }
    }
}

@Composable
private fun KeyboardUtilityBar(
    state: KeyboardUiState,
    isSwipingTrackpad: Boolean,
    onCycleMode: () -> Unit,
    onSplitDock: () -> Unit,
    onMinimize: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF171A21))
            .border(1.dp, KeyBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Mode Switcher Pill (Cycles Smart 1+2+4 -> 1: Quick-Launch -> 2: Trackpad -> 4: Passive)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(KeyActionSurface)
                .clickable(onClick = onCycleMode)
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isSwipingTrackpad -> AccentAmber
                            state.isInputFocused -> AccentEmerald
                            state.isQuickLaunchActiveForIdleTaps -> AccentBlue
                            else -> KeyTextSecondary
                        }
                    )
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = state.idleMode.shortTitle,
                color = KeyTextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Center Live Context Status
        val statusText = when {
            isSwipingTrackpad -> "⇅ Trackpad Scroll (${state.lastScrollDeltaPx}px)"
            state.isInputFocused -> "● Typing Active"
            state.isQuickLaunchActiveForIdleTaps -> "Tap A–Z Shortcut or Search"
            state.isTrackpadSwipeEnabled -> "⇅ Swipe Keys to Scroll"
            else -> "○ Passive Hardware Dock"
        }

        Text(
            text = statusText,
            color = if (state.isInputFocused) AccentEmerald else KeyTextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 1-Tap Split-Screen Dock Button (snaps third-party app into upper display above keyboard)
            Text(
                text = "⧉ Fit App",
                color = KeyTextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(KeyActionSurface)
                    .clickable(onClick = onSplitDock)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )

            // Manual Collapse / Hide Button
            Text(
                text = "Hide ▾",
                color = AccentBlue,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onMinimize)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun UniversalSearchInlineBar(
    query: String,
    installedApps: List<InstalledAppInfo>,
    onLaunchApp: (InstalledAppInfo) -> Unit,
    onExecuteSearch: () -> Unit,
    onClear: () -> Unit
) {
    val matchingApps = remember(query, installedApps) {
        if (query.isBlank()) emptyList()
        else installedApps.filter {
            it.label.contains(query.trim(), ignoreCase = true)
        }.take(4)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A1E29))
            .border(1.dp, AccentBlue.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "🔍 $query",
                color = KeyTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Search Web ↵",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(AccentBlue)
                        .clickable(onClick = onExecuteSearch)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
                Text(
                    text = "✕",
                    color = KeyTextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .clickable(onClick = onClear)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }

        if (matchingApps.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 5.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                matchingApps.forEach { app ->
                    Surface(
                        color = KeyActionSurface,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.clickable { onLaunchApp(app) }
                    ) {
                        Text(
                            text = "🚀 ${app.label}",
                            color = KeyTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModernKeyCap(
    modifier: Modifier = Modifier,
    label: String,
    secondaryHint: String? = null,
    showShortcutDot: Boolean = false,
    isActionKey: Boolean = false,
    isPrimaryAction: Boolean = false,
    isSpacebar: Boolean = false,
    isHighlighted: Boolean = false,
    onTap: () -> Unit,
    onLongPress: (() -> Unit)? = null
) {
    val bgColor = when {
        isPrimaryAction -> AccentBlue
        isHighlighted -> AccentBlue.copy(alpha = 0.28f)
        isActionKey -> KeyActionSurface
        else -> KeySurface
    }

    val borderColor = when {
        isPrimaryAction -> AccentBlue
        isHighlighted -> AccentBlue
        else -> KeyBorder
    }

    Box(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(bgColor)
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(7.dp))
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            ),
        contentAlignment = Alignment.Center
    ) {
        if (secondaryHint != null) {
            Text(
                text = secondaryHint,
                color = KeyTextSecondary.copy(alpha = 0.75f),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 4.dp)
            )
        }

        Text(
            text = label,
            color = if (isSpacebar) KeyTextSecondary else KeyTextPrimary,
            fontSize = if (isSpacebar) 11.sp else if (label.length > 1) 12.sp else 15.sp,
            fontWeight = if (isSpacebar) FontWeight.Medium else FontWeight.SemiBold,
            maxLines = 1
        )

        if (showShortcutDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(AccentBlue)
            )
        }
    }
}

@Composable
private fun CollapsedKeyboardRestorePill(
    state: KeyboardUiState,
    onRestore: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(DockBackground.copy(alpha = 0.94f))
            .border(1.dp, DockTopBorder)
            .clickable(onClick = onRestore)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AOK Launcher (${state.idleMode.shortTitle})",
                color = KeyTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Restore Keyboard ▴",
                color = AccentBlue,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun formatKeyCase(rawKey: String, shiftState: ShiftState, isSymbols: Boolean): String {
    if (isSymbols) return rawKey
    return if (shiftState == ShiftState.LOWER) rawKey.lowercase() else rawKey.uppercase()
}

private fun triggerHaptic(view: android.view.View, enabled: Boolean) {
    if (enabled) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
}

private fun dispatchKey(
    context: android.content.Context,
    key: String,
    isLongPress: Boolean,
    shiftState: ShiftState,
    onResetShift: () -> Unit,
    onSandboxKeyTap: ((String, Boolean) -> Unit)?
) {
    onSandboxKeyTap?.invoke(key, isLongPress)
    KeyboardStateRepository.handleKeyPress(context, key, isLongPress)
    if (shiftState == ShiftState.SHIFTED) {
        onResetShift()
    }
}

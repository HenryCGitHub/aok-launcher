package com.bb.alwaysonkeyboard.ui.main

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.bb.alwaysonkeyboard.data.AppShortcut
import com.bb.alwaysonkeyboard.data.DefaultDataRepository
import com.bb.alwaysonkeyboard.data.IdleBehaviorMode
import com.bb.alwaysonkeyboard.data.InstalledAppInfo
import com.bb.alwaysonkeyboard.data.KeyboardStateRepository
import com.bb.alwaysonkeyboard.data.KeyboardUiState
import com.bb.alwaysonkeyboard.ui.keyboard.ModernQwertyKeyboard
import com.bb.alwaysonkeyboard.ui.theme.AlwaysOnKeyboardTheme
import kotlinx.coroutines.launch

private enum class DashboardTab(val title: String) {
    SETUP_AND_MODES("Setup & Modes"),
    SHORTCUTS("A–Z Shortcuts"),
    LIVE_SANDBOX("Test Keyboard")
}

private enum class SandboxContext(val title: String) {
    QUICK_LAUNCH("1. Quick-Launch"),
    TRACKPAD_FEED("2. Trackpad Feed"),
    TEXT_INPUT("Typing Input"),
    FULLSCREEN_VIDEO("Fullscreen Video")
}

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel { MainScreenViewModel(DefaultDataRepository()) },
) {
    AlwaysOnKeyboardDashboard(modifier = modifier)
}

@Composable
internal fun MainScreen(data: List<String>, modifier: Modifier = Modifier) {
    AlwaysOnKeyboardDashboard(modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlwaysOnKeyboardDashboard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val kbState by KeyboardStateRepository.state.collectAsState()
    val installedApps by KeyboardStateRepository.installedApps.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var isImeEnabled by remember { mutableStateOf(false) }
    var isImeSelected by remember { mutableStateOf(false) }
    var isAccessibilityEnabled by remember { mutableStateOf(false) }

    fun refreshSystemStatus() {
        isImeEnabled = checkImeEnabled(context)
        isImeSelected = checkImeSelected(context)
        isAccessibilityEnabled = checkAccessibilityServiceEnabled(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshSystemStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        refreshSystemStatus()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (!kbState.isFullscreenDetected) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "AlwaysOn Minimal Keyboard",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = kbState.lastTelemetryEvent,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!kbState.isFullscreenDetected) {
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    DashboardTab.entries.forEachIndexed { index, tab ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = tab.title,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        )
                    }
                }
            }

            // Main Content Area above the Always-On Keyboard Dock
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (DashboardTab.entries[selectedTab]) {
                    DashboardTab.SETUP_AND_MODES -> SetupAndModesSection(
                        kbState = kbState,
                        isImeEnabled = isImeEnabled,
                        isImeSelected = isImeSelected,
                        isAccessibilityEnabled = isAccessibilityEnabled,
                        onRefreshStatus = ::refreshSystemStatus
                    )

                    DashboardTab.SHORTCUTS -> ShortcutsCustomizationSection(
                        kbState = kbState,
                        installedApps = installedApps
                    )

                    DashboardTab.LIVE_SANDBOX -> InteractiveSandboxSection(
                        kbState = kbState,
                        onExitFullscreenToSetup = {
                            KeyboardStateRepository.setFullscreenDetected(false)
                        }
                    )
                }
            }

            // Built-in Live Always-On Keyboard Dock Preview & Controller
            // Shown at the bottom of the app whenever the system IME/Overlay isn't already covering it,
            // and automatically hides when Fullscreen Video mode is active!
            val showInAppDock = !kbState.isImeWindowVisible &&
                !kbState.isAccessibilityServiceRunning &&
                !kbState.isAutoHiddenByFullscreen

            AnimatedVisibility(visible = showInAppDock) {
                ModernQwertyKeyboard()
            }
        }
    }
}

@Composable
private fun SetupAndModesSection(
    kbState: KeyboardUiState,
    isImeEnabled: Boolean,
    isImeSelected: Boolean,
    isAccessibilityEnabled: Boolean,
    onRefreshStatus: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. System Activation Checklist Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "System Activation (Physical Keyboard Experience)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Enable both the Input Method (for typing in apps) and the Persistent Dock Service (so the keyboard stays open on the Home screen, scrolls feeds via capacitive touch, and auto-hides in fullscreen video).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SetupStepRow(
                    stepNumber = "1",
                    title = "Enable AlwaysOn Keyboard (IME)",
                    subtitle = if (isImeEnabled) "Enabled in system keyboard settings" else "Tap to enable in Android Keyboard settings",
                    isDone = isImeEnabled,
                    buttonLabel = if (isImeEnabled) "Settings" else "Enable",
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    }
                )

                SetupStepRow(
                    stepNumber = "2",
                    title = "Select AlwaysOn Keyboard as Active",
                    subtitle = if (isImeSelected) "Active default input method" else "Switch to AlwaysOn Minimal Keyboard",
                    isDone = isImeSelected,
                    buttonLabel = if (isImeSelected) "Switch" else "Select",
                    onClick = {
                        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showInputMethodPicker()
                        onRefreshStatus()
                    }
                )

                SetupStepRow(
                    stepNumber = "3",
                    title = "Enable Always-On Dock & Trackpad Service",
                    subtitle = if (isAccessibilityEnabled || kbState.isAccessibilityServiceRunning) {
                        "Persistent Dock, Capacitive Scroll & Fullscreen Detector active"
                    } else {
                        "Keeps keyboard open at all times & auto-hides in fullscreen video"
                    },
                    isDone = isAccessibilityEnabled || kbState.isAccessibilityServiceRunning,
                    buttonLabel = if (isAccessibilityEnabled) "Configured" else "Enable",
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    }
                )
            }
        }

        // 2. Idle Behavior Mode Selector (Modes 1, 2, 4 & Smart Combined)
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Idle Behavior When No Text Field is Focused",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Choose how the keyboard behaves when no text box is active (you can also cycle modes anytime from the top-left pill on the keyboard):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                IdleBehaviorMode.entries.forEach { mode ->
                    val selected = kbState.idleMode == mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                else MaterialTheme.colorScheme.surface
                            )
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { KeyboardStateRepository.setIdleMode(context, mode) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = { KeyboardStateRepository.setIdleMode(context, mode) }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mode.fullTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = mode.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 3. Smart Full-Screen Content Auto-Hide Settings
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Smart Full-Screen Auto-Hide & Tactile Feel",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                SettingToggleRow(
                    title = "Auto-Hide in Full-Screen Content (Videos & Games)",
                    subtitle = "Automatically hides the keyboard when an app enters immersive fullscreen mode (YouTube, Netflix, games) and restores it on exit.",
                    checked = kbState.autoHideInFullscreen,
                    onCheckedChange = { KeyboardStateRepository.setAutoHideInFullscreen(context, it) }
                )

                HorizontalDivider()

                SettingToggleRow(
                    title = "Auto-Hide in Landscape Orientation",
                    subtitle = "Automatically collapses the persistent keyboard dock when rotating the phone horizontally for widescreen media.",
                    checked = kbState.autoHideInLandscape,
                    onCheckedChange = { KeyboardStateRepository.setAutoHideInLandscape(context, it) }
                )

                HorizontalDivider()

                SettingToggleRow(
                    title = "Physical Key Haptic Feedback",
                    subtitle = "Crisp tactile vibration on key presses and mode switches.",
                    checked = kbState.hapticFeedbackEnabled,
                    onCheckedChange = { KeyboardStateRepository.setHapticFeedback(context, it) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShortcutsCustomizationSection(
    kbState: KeyboardUiState,
    installedApps: List<InstalledAppInfo>
) {
    val context = LocalContext.current
    var editingKey by remember { mutableStateOf<Char?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Mode 1: Quick-Launch Shortcuts (A–Z)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "When on the Home screen (or in Mode 1) with no text input focused, pressing a shortcut key launches its assigned app immediately. Tap any letter below to customize or test:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 2
        ) {
            ('A'..'Z').forEach { keyChar ->
                val shortcut = kbState.shortcuts[keyChar]
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { editingKey = keyChar },
                    colors = CardDefaults.cardColors(
                        containerColor = if (shortcut != null) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = keyChar.toString(),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = shortcut?.label ?: "Unassigned (Search)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (shortcut != null) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1
                                )
                                if (shortcut != null) {
                                    Text(
                                        text = "Tap to change",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (shortcut != null) {
                            TextButton(
                                onClick = {
                                    KeyboardStateRepository.launchAppShortcut(context, shortcut)
                                }
                            ) {
                                Text("Launch", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (editingKey != null) {
        val targetKey = editingKey!!
        AlertDialog(
            onDismissRequest = { editingKey = null },
            title = { Text("Assign App to '$targetKey' Key") },
            text = {
                LazyColumn(
                    modifier = Modifier.height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (installedApps.isEmpty()) {
                        item {
                            Text(
                                text = "Select a built-in system shortcut:",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    items(installedApps) { app ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    KeyboardStateRepository.assignShortcut(
                                        context = context,
                                        key = targetKey,
                                        label = app.label,
                                        packageName = app.packageName
                                    )
                                    editingKey = null
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(text = app.label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(
                                    text = app.packageName,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { editingKey = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun InteractiveSandboxSection(
    kbState: KeyboardUiState,
    onExitFullscreenToSetup: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()

    var sandboxContext by remember { mutableStateOf(SandboxContext.QUICK_LAUNCH) }
    var sandboxTypedText by remember { mutableStateOf("Try typing on the AlwaysOn Keyboard below! ") }
    val feedListState = rememberLazyListState()

    // Register in-app sandbox callbacks so the user can test typing & capacitive trackpad scrolling
    // directly inside the sandbox even before enabling the system-wide Accessibility Service!
    DisposableEffect(sandboxContext) {
        when (sandboxContext) {
            SandboxContext.QUICK_LAUNCH -> {
                KeyboardStateRepository.setInputFocused(false)
                KeyboardStateRepository.setForegroundWindowContext(context.packageName, isHomeLauncher = true)
                setActivityImmersiveFullscreen(context, view, fullscreen = false)
            }
            SandboxContext.TRACKPAD_FEED -> {
                KeyboardStateRepository.setInputFocused(false)
                KeyboardStateRepository.setForegroundWindowContext(context.packageName, isHomeLauncher = false)
                setActivityImmersiveFullscreen(context, view, fullscreen = false)
            }
            SandboxContext.TEXT_INPUT -> {
                KeyboardStateRepository.setInputFocused(true, context.packageName)
                setActivityImmersiveFullscreen(context, view, fullscreen = false)
            }
            SandboxContext.FULLSCREEN_VIDEO -> {
                KeyboardStateRepository.setInputFocused(false)
                setActivityImmersiveFullscreen(context, view, fullscreen = true)
                KeyboardStateRepository.setFullscreenDetected(true, "Fullscreen Media Player")
            }
        }

        val prevCommit = KeyboardStateRepository.onCommitTextCallback
        val prevDelete = KeyboardStateRepository.onDeleteTextCallback
        val prevEnter = KeyboardStateRepository.onEnterActionCallback
        val prevScroll = KeyboardStateRepository.onTrackpadScrollCallback

        KeyboardStateRepository.onCommitTextCallback = { text ->
            sandboxTypedText += text
            true
        }
        KeyboardStateRepository.onDeleteTextCallback = {
            if (sandboxTypedText.isNotEmpty()) {
                sandboxTypedText = sandboxTypedText.dropLast(1)
            }
            true
        }
        KeyboardStateRepository.onEnterActionCallback = {
            sandboxTypedText += "\n"
            true
        }
        KeyboardStateRepository.onTrackpadScrollCallback = { _, dy ->
            coroutineScope.launch {
                feedListState.scrollBy(-dy * 1.6f)
            }
        }

        onDispose {
            KeyboardStateRepository.onCommitTextCallback = prevCommit
            KeyboardStateRepository.onDeleteTextCallback = prevDelete
            KeyboardStateRepository.onEnterActionCallback = prevEnter
            KeyboardStateRepository.onTrackpadScrollCallback = prevScroll
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Context Switcher Pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SandboxContext.entries.forEach { ctx ->
                val selected = sandboxContext == ctx
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { sandboxContext = ctx },
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = ctx.title,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // Active Sandbox Viewport
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            when (sandboxContext) {
                SandboxContext.QUICK_LAUNCH -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Simulated Home Screen (No Text Input Focused)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "• Press C (Chrome), M (Messages), S (Settings), G (Gmail), or Y (YouTube) on the Always-On Keyboard below to launch apps immediately.\n• Or press any unassigned letter to open the inline Universal Search bar!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf('C', 'M', 'S', 'G', 'P').forEach { k ->
                                val sc = kbState.shortcuts[k]
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            if (sc != null) KeyboardStateRepository.launchAppShortcut(context, sc)
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    Column(
                                        modifier = Modifier.padding(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = k.toString(),
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = sc?.label ?: k.toString(),
                                            fontSize = 11.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                SandboxContext.TRACKPAD_FEED -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Mode 2: Capacitive Trackpad Feed (Swipe on Keys!)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Last Delta: ${kbState.lastScrollDeltaPx}px",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        LazyColumn(
                            state = feedListState,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(20) { index ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "Feed Post #${index + 1} • Capacitive Touch Scroll",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = "Glide your thumb up or down anywhere across the QWERTY keys below to smoothly scroll this list without touching the upper screen. Single key taps are ignored in Passive/Trackpad mode because no text box is focused.",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                SandboxContext.TEXT_INPUT -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Active Text Field (Standard QWERTY Typing)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Whenever a text box is focused, all modes automatically type directly into the active input:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = sandboxTypedText,
                            onValueChange = { sandboxTypedText = it },
                            label = { Text("Active Message Input") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        KeyboardStateRepository.setInputFocused(true, context.packageName)
                                    }
                                }
                        )
                    }
                }

                SandboxContext.FULLSCREEN_VIDEO -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0B0E14))
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                color = Color(0xFF1E293B),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Text(
                                    text = "▶ FULLSCREEN MEDIA ACTIVE",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                            Text(
                                text = "Always-On Keyboard Automatically Hidden!",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "System bars are hidden and full-screen content was detected. Notice how the Always-On Keyboard automatically slid out of the way so you get 100% of your display.",
                                color = Color(0xFF94A3B8),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Button(
                                onClick = {
                                    setActivityImmersiveFullscreen(context, view, fullscreen = false)
                                    onExitFullscreenToSetup()
                                    sandboxContext = SandboxContext.QUICK_LAUNCH
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                            ) {
                                Text("Exit Fullscreen (Restore Always-On Keyboard)")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupStepRow(
    stepNumber: String,
    title: String,
    subtitle: String,
    isDone: Boolean,
    buttonLabel: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(
                        if (isDone) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isDone) "✓" else stepNumber,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedButton(onClick = onClick) {
            Text(buttonLabel, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun checkImeEnabled(context: Context): Boolean {
    return try {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.enabledInputMethodList.any { it.packageName == context.packageName }
    } catch (_: Exception) {
        false
    }
}

private fun checkImeSelected(context: Context): Boolean {
    return try {
        val currentDefault = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        ).orEmpty()
        currentDefault.contains(context.packageName)
    } catch (_: Exception) {
        false
    }
}

private fun checkAccessibilityServiceEnabled(context: Context): Boolean {
    return try {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        enabledServices.contains(context.packageName)
    } catch (_: Exception) {
        false
    }
}

private fun setActivityImmersiveFullscreen(
    context: Context,
    view: android.view.View,
    fullscreen: Boolean
) {
    val activity = context as? Activity ?: return
    val window = activity.window ?: return
    val controller = WindowCompat.getInsetsController(window, view)
    if (fullscreen) {
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    } else {
        controller.show(WindowInsetsCompat.Type.systemBars())
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun MainScreenPreview() {
    AlwaysOnKeyboardTheme {
        AlwaysOnKeyboardDashboard()
    }
}

package com.bb.alwaysonkeyboard.ui.main

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class DashboardTab(val title: String) {
    HOME_LAUNCHER("BB Home"),
    SETUP_AND_MODES("Setup & Modes"),
    SHORTCUTS("A–Z Keys"),
    LIVE_SANDBOX("Test")
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
    var isDefaultLauncher by remember { mutableStateOf(false) }
    var isImeEnabled by remember { mutableStateOf(false) }
    var isImeSelected by remember { mutableStateOf(false) }
    var isAccessibilityEnabled by remember { mutableStateOf(false) }

    fun refreshSystemStatus() {
        isDefaultLauncher = checkIsDefaultLauncher(context)
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

    // Root Column splits the physical display into two non-overlapping zones:
    // 1. Top Zone (weight(1f)): Complete BB Home Launcher or Settings UI (never covered by keyboard!)
    // 2. Bottom Zone: Permanent Clean Modern Minimal QWERTY Keyboard Dock
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        if (!kbState.isFullscreenDetected) {
            // Compact Top Navigation Bar
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AlwaysOn Minimal Keyboard",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = kbState.lastTelemetryEvent,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }

                    PrimaryTabRow(selectedTabIndex = selectedTab) {
                        DashboardTab.entries.forEachIndexed { index, tab ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = {
                                    Text(
                                        text = tab.title,
                                        fontSize = 12.sp,
                                        fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // Upper Display Zone (Takes up 100% of the space ABOVE the keyboard — zero overlap!)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (DashboardTab.entries[selectedTab]) {
                DashboardTab.HOME_LAUNCHER -> CustomBbHomeLauncherSection(
                    kbState = kbState,
                    installedApps = installedApps,
                    isDefaultLauncher = isDefaultLauncher,
                    onOpenSetupTab = { selectedTab = DashboardTab.SETUP_AND_MODES.ordinal },
                    onOpenShortcutsTab = { selectedTab = DashboardTab.SHORTCUTS.ordinal }
                )

                DashboardTab.SETUP_AND_MODES -> SetupAndModesSection(
                    kbState = kbState,
                    isDefaultLauncher = isDefaultLauncher,
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

        // Bottom Display Zone: Dedicated Always-On Keyboard Dock
        // Rendered directly in the Column layout whenever the system IME window isn't already open
        // so that the upper screen stops cleanly at the top edge of the keyboard!
        val showBuiltInDock = !kbState.isImeWindowVisible && !kbState.isAutoHiddenByFullscreen
        AnimatedVisibility(visible = showBuiltInDock) {
            ModernQwertyKeyboard()
        }
    }
}

/**
 * Custom BB Keyboard Home Screen Launcher that fits 100% in the upper display zone above the keyboard.
 * - Includes Live Clock & Date, Default Home Launcher status banner, Speed-Dial A–Z tiles,
 *   All-Apps Drawer (scrollable via Capacitive Trackpad swipe on the keyboard keys!), and
 *   a dedicated Home Dock sitting right above the top edge of the keyboard.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CustomBbHomeLauncherSection(
    kbState: KeyboardUiState,
    installedApps: List<InstalledAppInfo>,
    isDefaultLauncher: Boolean,
    onOpenSetupTab: () -> Unit,
    onOpenShortcutsTab: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val appGridState = rememberLazyGridState()

    var showAllAppsDrawer by remember { mutableStateOf(false) }
    var currentTimeText by remember { mutableStateOf("") }
    var currentDateText by remember { mutableStateOf("") }

    // Update clock every 15 seconds
    LaunchedEffect(Unit) {
        val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
        val dateFmt = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
        while (true) {
            val now = Date()
            currentTimeText = timeFmt.format(now)
            currentDateText = dateFmt.format(now)
            delay(15_000L)
        }
    }

    // Connect Capacitive Trackpad scrolling (Mode 2) directly to the Home Launcher grid!
    DisposableEffect(Unit) {
        KeyboardStateRepository.setInputFocused(false)
        val prevScroll = KeyboardStateRepository.onTrackpadScrollCallback
        KeyboardStateRepository.onTrackpadScrollCallback = { _, dy ->
            coroutineScope.launch {
                appGridState.scrollBy(-dy * 1.6f)
            }
            prevScroll?.invoke(0f, dy)
        }
        onDispose {
            KeyboardStateRepository.onTrackpadScrollCallback = prevScroll
        }
    }

    val filteredApps = remember(installedApps, kbState.universalSearchQuery) {
        val q = kbState.universalSearchQuery.trim()
        if (q.isEmpty()) installedApps
        else installedApps.filter { it.label.contains(q, ignoreCase = true) }
    }

    // Automatically switch to filtered apps view when user starts typing a search query on the keyboard
    val isSearching = kbState.universalSearchQuery.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. Top Clock, Date & Launcher Mode Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = currentTimeText.ifEmpty { "12:00 PM" },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$currentDateText • ${kbState.idleMode.shortTitle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!isDefaultLauncher) {
                    OutlinedButton(
                        onClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                            } catch (_: Exception) {
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("Set as Home", fontSize = 11.sp)
                    }
                }

                Button(
                    onClick = { showAllAppsDrawer = !showAllAppsDrawer },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (showAllAppsDrawer || isSearching) "★ Speed Dial" else "Apps (${installedApps.size})",
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 2. Center Uncut Workspace (Speed Dial A-Z Shortcuts OR All-Apps Grid)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (!showAllAppsDrawer && !isSearching) {
                // BB Speed Dial Grid (Assigned A-Z Key Shortcuts)
                val assignedShortcuts = remember(kbState.shortcuts) {
                    kbState.shortcuts.values.sortedBy { it.key }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "BB Speed Dial (Press Key on Keyboard or Tap)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Customize A–Z ▸",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable(onClick = onOpenShortcutsTab)
                        )
                    }

                    LazyVerticalGrid(
                        state = appGridState,
                        columns = GridCells.Fixed(3),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(assignedShortcuts, key = { it.key }) { shortcut ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            KeyboardStateRepository.launchAppShortcut(context, shortcut)
                                        },
                                        onLongClick = onOpenShortcutsTab
                                    ),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(7.dp))
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = shortcut.key.toString(),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = shortcut.label,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // All Installed Apps Grid (Scrollable via Capacitive Trackpad Swipe on Keyboard!)
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isSearching) {
                                "Matching Apps for \"${kbState.universalSearchQuery}\""
                            } else {
                                "All Apps • Swipe Keyboard Keys to Scroll"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isSearching) {
                            Text(
                                text = "Clear",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { KeyboardStateRepository.clearUniversalSearch() }
                            )
                        }
                    }

                    LazyVerticalGrid(
                        state = appGridState,
                        columns = GridCells.Fixed(4),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        KeyboardStateRepository.launchPackage(
                                            context = context,
                                            packageName = app.packageName,
                                            label = app.label
                                        )
                                    },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = app.label.take(1).uppercase(),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(5.dp))
                                    Text(
                                        text = app.label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 3. Dedicated Bottom Home Dock (Positioned *Directly Above* the Keyboard — Never Covered!)
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DockActionPill(
                    badge = "P",
                    label = "Phone",
                    onClick = {
                        kbState.shortcuts['P']?.let { KeyboardStateRepository.launchAppShortcut(context, it) }
                    }
                )
                DockActionPill(
                    badge = "M",
                    label = "Messages",
                    onClick = {
                        kbState.shortcuts['M']?.let { KeyboardStateRepository.launchAppShortcut(context, it) }
                    }
                )
                DockActionPill(
                    badge = "⊞",
                    label = if (showAllAppsDrawer) "Speed Dial" else "All Apps",
                    isPrimary = true,
                    onClick = { showAllAppsDrawer = !showAllAppsDrawer }
                )
                DockActionPill(
                    badge = "C",
                    label = "Browser",
                    onClick = {
                        kbState.shortcuts['C']?.let { KeyboardStateRepository.launchAppShortcut(context, it) }
                    }
                )
                DockActionPill(
                    badge = "⚙",
                    label = "Setup",
                    onClick = onOpenSetupTab
                )
            }
        }
    }
}

@Composable
private fun DockActionPill(
    badge: String,
    label: String,
    isPrimary: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isPrimary) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = badge,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isPrimary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isPrimary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SetupAndModesSection(
    kbState: KeyboardUiState,
    isDefaultLauncher: Boolean,
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
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    text = "System Setup (Uncut Display + Physical Keyboard)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                SetupStepRow(
                    stepNumber = "1",
                    title = "Set BB Home as Default Launcher",
                    subtitle = if (isDefaultLauncher) {
                        "Active Default Home Launcher (Home screen fits 100% above keyboard)"
                    } else {
                        "Makes your Home screen sit cleanly above the keyboard with zero cut-off"
                    },
                    isDone = isDefaultLauncher,
                    buttonLabel = if (isDefaultLauncher) "Default" else "Set Home",
                    onClick = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        } catch (_: Exception) {
                        }
                    }
                )

                SetupStepRow(
                    stepNumber = "2",
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
                    stepNumber = "3",
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
                    stepNumber = "4",
                    title = "Enable Always-On Dock & Trackpad Service",
                    subtitle = if (isAccessibilityEnabled || kbState.isAccessibilityServiceRunning) {
                        "Persistent Dock, Capacitive Scroll, Split-Screen Fit & Fullscreen Detector active"
                    } else {
                        "Powers Capacitive Trackpad scroll, '⧉ Fit App' split-screen, and Fullscreen Auto-Hide"
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

        // 3. Display Sizing & Smart Full-Screen Auto-Hide Settings
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
                    text = "Display Sizing & Smart Full-Screen Auto-Hide",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                SettingToggleRow(
                    title = "Launch Apps in Upper Split-Screen Pane (Uncut App Display)",
                    subtitle = "Uses FLAG_ACTIVITY_LAUNCH_ADJACENT and the '⧉ Fit App' button on the keyboard top bar so third-party apps size themselves into the screen space above the keyboard.",
                    checked = kbState.launchInAdjacentSplitScreen,
                    onCheckedChange = { KeyboardStateRepository.setLaunchInAdjacentSplitScreen(context, it) }
                )

                HorizontalDivider()

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
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Mode 1: Quick-Launch Shortcuts (A–Z)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Tap any letter below to assign which app it launches when pressed on the BB Home Launcher:",
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
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = keyChar.toString(),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
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
                    modifier = Modifier.height(280.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
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

    var sandboxContext by remember { mutableStateOf(SandboxContext.TRACKPAD_FEED) }
    var sandboxTypedText by remember { mutableStateOf("Try typing on the AlwaysOn Keyboard below! ") }
    val feedListState = rememberLazyListState()

    DisposableEffect(sandboxContext) {
        when (sandboxContext) {
            SandboxContext.QUICK_LAUNCH -> {
                KeyboardStateRepository.setInputFocused(false)
                setActivityImmersiveFullscreen(context, view, fullscreen = false)
            }
            SandboxContext.TRACKPAD_FEED -> {
                KeyboardStateRepository.setInputFocused(false)
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
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
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
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 7.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

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
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Mode 1: Quick-Launch & Universal Search",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Press C, M, S, G, P, or Y on the keyboard below to launch apps, or type any query to expand Universal Search.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                                .padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Mode 2: Capacitive Trackpad Feed (Swipe Keys!)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${kbState.lastScrollDeltaPx}px",
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
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = "Feed Item #${index + 1} • Uncut Display Above Keyboard",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = "Notice how this entire list is contained in the upper display zone above the keyboard—nothing at the bottom is cut off! Glide your thumb across the keys below to scroll.",
                                            fontSize = 11.sp,
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
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = sandboxTypedText,
                            onValueChange = { sandboxTypedText = it },
                            label = { Text("Active Message Input (Fits Above Keyboard)") },
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
                            Text(
                                text = "▶ FULLSCREEN VIDEO ACTIVE",
                                color = Color(0xFF38BDF8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Always-On Keyboard Automatically Hidden!",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Button(
                                onClick = {
                                    setActivityImmersiveFullscreen(context, view, fullscreen = false)
                                    onExitFullscreenToSetup()
                                    sandboxContext = SandboxContext.TRACKPAD_FEED
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                            ) {
                                Text("Exit Fullscreen (Restore Keyboard)")
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

private fun checkIsDefaultLauncher(context: Context): Boolean {
    return try {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        resolveInfo?.activityInfo?.packageName == context.packageName
    } catch (_: Exception) {
        false
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

package com.tabletpilot.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun TabletPilotApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    // Init ViewModel with context (once)
    LaunchedEffect(Unit) {
        viewModel.init(context)
    }

    val permissionsState by viewModel.permissionsState.collectAsStateWithLifecycle()
    val serviceRunning by viewModel.serviceRunning.collectAsStateWithLifecycle()
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val fastPathEnabled by viewModel.fastPathEnabled.collectAsStateWithLifecycle()
    val actionLog by viewModel.actionLog.collectAsStateWithLifecycle()

    // Tablet landscape: width > 840dp → two-pane layout
    val isTabletLandscape = configuration.screenWidthDp > 840

    if (isTabletLandscape) {
        // ── Two-Pane Landscape Layout ────────────────────────────────────────
        Row(modifier = Modifier.fillMaxSize()) {
            // Left pane: controls (fixed 420dp)
            Surface(
                modifier = Modifier
                    .width(420.dp)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                ControlPanel(
                    permissionsState = permissionsState,
                    serviceRunning = serviceRunning,
                    apiKey = apiKey,
                    selectedModel = selectedModel,
                    fastPathEnabled = fastPathEnabled,
                    onCheckPermissions = { viewModel.checkPermissions(context) },
                    onStartService = { viewModel.startService(context) },
                    onStopService = { viewModel.stopService(context) },
                    onApiKeyChange = viewModel::updateApiKey,
                    onModelChange = viewModel::updateModel,
                    onFastPathToggle = viewModel::toggleFastPath
                )
            }

            // Vertical divider
            HorizontalDivider(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // Right pane: live log (fills remaining)
            ActionLogPanel(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                entries = actionLog,
                onClear = viewModel::clearLog
            )
        }
    } else {
        // ── Single-Pane Portrait Layout with Tabs ────────────────────────────
        var selectedTab by remember { mutableIntStateOf(0) }
        val tabs = listOf("Control", "Log")

        Column(modifier = Modifier.fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTab) {
                0 -> ControlPanel(
                    permissionsState = permissionsState,
                    serviceRunning = serviceRunning,
                    apiKey = apiKey,
                    selectedModel = selectedModel,
                    fastPathEnabled = fastPathEnabled,
                    onCheckPermissions = { viewModel.checkPermissions(context) },
                    onStartService = { viewModel.startService(context) },
                    onStopService = { viewModel.stopService(context) },
                    onApiKeyChange = viewModel::updateApiKey,
                    onModelChange = viewModel::updateModel,
                    onFastPathToggle = viewModel::toggleFastPath
                )
                1 -> ActionLogPanel(
                    modifier = Modifier.fillMaxSize(),
                    entries = actionLog,
                    onClear = viewModel::clearLog
                )
            }
        }
    }
}

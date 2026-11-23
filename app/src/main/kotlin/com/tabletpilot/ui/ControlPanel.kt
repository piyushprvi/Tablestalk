package com.tabletpilot.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlPanel(
    viewModel: MainViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Service Toggle Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Assistant Service",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (uiState.isServiceRunning) "Running in background" else "Stopped",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = uiState.isServiceRunning,
                    onCheckedChange = { viewModel.toggleService() }
                )
            }
        }

        // Permissions Checklist Card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "System Permissions",
                    style = MaterialTheme.typography.titleMedium
                )
                
                PermissionItem(
                    title = "Accessibility Service",
                    isGranted = uiState.isAccessibilityGranted,
                    onGrant = { viewModel.openAccessibilitySettings() }
                )
                PermissionItem(
                    title = "Display Over Other Apps",
                    isGranted = uiState.isOverlayGranted,
                    onGrant = { viewModel.openOverlaySettings() }
                )
                PermissionItem(
                    title = "Ignore Battery Optimization",
                    isGranted = uiState.isBatteryOptimizationIgnored,
                    onGrant = { viewModel.requestIgnoreBatteryOptimization() }
                )
            }
        }

        // Settings Card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "AI Model Settings",
                    style = MaterialTheme.typography.titleMedium
                )

                var apiKeyText by remember(uiState.apiKey) { mutableStateOf(uiState.apiKey) }
                OutlinedTextField(
                    value = apiKeyText,
                    onValueChange = { apiKeyText = it },
                    label = { Text("Gemini API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { viewModel.saveApiKey(apiKeyText) }) {
                            Icon(Icons.Default.Check, contentDescription = "Save Key")
                        }
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Model Selection")
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        Button(onClick = { expanded = true }) {
                            Text(uiState.selectedModel)
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("gemini-3.5-flash-lite") },
                                onClick = {
                                    viewModel.setSelectedModel("gemini-3.5-flash-lite")
                                    expanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("gemini-3.8-flash") },
                                onClick = {
                                    viewModel.setSelectedModel("gemini-3.8-flash")
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Local Fast-Path Router")
                        Text(
                            "Execute simple commands (<50ms) without cloud",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.isFastPathEnabled,
                        onCheckedChange = { viewModel.setFastPathEnabled(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionItem(
    title: String,
    isGranted: Boolean,
    onGrant: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = null,
                tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Text(title, style = MaterialTheme.typography.bodyMedium)
        }
        if (!isGranted) {
            Button(
                onClick = onGrant,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("Grant")
            }
        }
    }
}

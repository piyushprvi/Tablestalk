package com.tabletpilot.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun ControlPanel(
    permissionsState: PermissionsState,
    serviceRunning: Boolean,
    apiKey: String,
    selectedModel: String,
    fastPathEnabled: Boolean,
    onCheckPermissions: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onFastPathToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "TabletPilot",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Redmi Pad Pro 5G · CaramelOS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ── Permission Checklist ─────────────────────────────────────────────
        item {
            PermissionChecklistCard(
                state = permissionsState,
                onRefresh = onCheckPermissions
            )
        }

        // ── Service Toggle ───────────────────────────────────────────────────
        item {
            ServiceToggleCard(
                isRunning = serviceRunning,
                onStart = onStartService,
                onStop = onStopService,
                allPermissionsGranted = permissionsState.accessibilityGranted && permissionsState.overlayGranted
            )
        }

        // ── API & Model Settings ─────────────────────────────────────────────
        item {
            SettingsCard(
                apiKey = apiKey,
                selectedModel = selectedModel,
                fastPathEnabled = fastPathEnabled,
                onApiKeyChange = onApiKeyChange,
                onModelChange = onModelChange,
                onFastPathToggle = onFastPathToggle
            )
        }

        // Footer padding
        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ── Permission Checklist Card ────────────────────────────────────────────────

@Composable
private fun PermissionChecklistCard(
    state: PermissionsState,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Required Permissions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                }
            }

            Spacer(Modifier.height(8.dp))

            PermissionRow(
                icon = Icons.Filled.Accessibility,
                label = "Accessibility Service",
                granted = state.accessibilityGranted,
                onGrant = {
                    context.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                }
            )

            PermissionRow(
                icon = Icons.Filled.Layers,
                label = "Display Over Other Apps",
                granted = state.overlayGranted,
                onGrant = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    )
                }
            )

            PermissionRow(
                icon = Icons.Filled.BatteryFull,
                label = "Ignore Battery Optimization",
                granted = state.batteryOptIgnored,
                onGrant = {
                    // CaramelOS/MIUI specific: try both paths
                    try {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${context.packageName}")
                            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        )
                    } catch (e: Exception) {
                        // MIUI fallback: open battery settings directly
                        context.startActivity(
                            Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    }
                }
            )

            PermissionRow(
                icon = Icons.Filled.Notifications,
                label = "Notifications",
                granted = state.notificationsGranted,
                onGrant = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    }
                }
            )

            PermissionRow(
                icon = Icons.Filled.Mic,
                label = "Microphone",
                granted = state.microphoneGranted,
                onGrant = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    label: String,
    granted: Boolean,
    onGrant: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status dot
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (granted) Color(0xFF4CAF50) else Color(0xFFF44336))
        )
        Spacer(Modifier.width(10.dp))

        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (granted) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                if (granted) "Granted" else "Required",
                style = MaterialTheme.typography.labelSmall,
                color = if (granted) Color(0xFF4CAF50) else Color(0xFFF44336)
            )
        }

        if (!granted) {
            OutlinedButton(
                onClick = onGrant,
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Text("Grant", style = MaterialTheme.typography.labelSmall)
            }
        } else {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ── Service Toggle Card ──────────────────────────────────────────────────────

@Composable
private fun ServiceToggleCard(
    isRunning: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    allPermissionsGranted: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Pulsing indicator when running
                    if (isRunning) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4CAF50))
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        "Always-On Assistant",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (isRunning) "Running • Floating bubble active"
                    else "Stopped • Tap to start",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!allPermissionsGranted && !isRunning) {
                    Text(
                        "⚠ Grant Accessibility + Overlay first",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Switch(
                checked = isRunning,
                onCheckedChange = { checked ->
                    if (checked) onStart() else onStop()
                },
                modifier = Modifier.scale(1.3f)
            )
        }
    }
}

// ── Settings Card ────────────────────────────────────────────────────────────

@Composable
private fun SettingsCard(
    apiKey: String,
    selectedModel: String,
    fastPathEnabled: Boolean,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onFastPathToggle: (Boolean) -> Unit
) {
    var showKey by remember { mutableStateOf(false) }
    var localKey by remember(apiKey) { mutableStateOf(apiKey) }

    val models = listOf(
        "gemini-2.0-flash-lite" to "Flash Lite — fastest",
        "gemini-2.5-flash" to "Flash 2.5 — smarter"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "API & Model Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))

            // API Key field
            OutlinedTextField(
                value = localKey,
                onValueChange = { localKey = it },
                label = { Text("Gemini API Key") },
                placeholder = { Text("AIza…") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None
                else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    Row {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = null
                            )
                        }
                        // Save button
                        IconButton(onClick = { onApiKeyChange(localKey) }) {
                            Icon(Icons.Filled.Save, contentDescription = "Save")
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )

            if (localKey.isBlank()) {
                Text(
                    "Add your key at ai.google.dev → Get API key",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            // Model selector
            Text("AI Model", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                models.forEach { (modelId, modelLabel) ->
                    FilterChip(
                        selected = selectedModel == modelId,
                        onClick = { onModelChange(modelId) },
                        label = { Text(modelLabel, style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = if (selectedModel == modelId) {
                            { Icon(Icons.Filled.Check, null, Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Fast-path toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "⚡ Local Fast-Path Router",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "Instant commands without network (<50ms)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = fastPathEnabled,
                    onCheckedChange = onFastPathToggle
                )
            }
        }
    }
}

// ── Scale modifier helper ────────────────────────────────────────────────────

private fun Modifier.scale(scale: Float): Modifier = this.then(
    androidx.compose.ui.layout.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(
            (placeable.width * scale).toInt(),
            (placeable.height * scale).toInt()
        ) {
            val x = ((placeable.width * scale - placeable.width) / 2).toInt()
            val y = ((placeable.height * scale - placeable.height) / 2).toInt()
            placeable.placeRelative(x, y)
        }
    }
)

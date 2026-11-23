package com.tabletpilot.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabletpilot.data.ActionLogEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TIME_FMT = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

@Composable
fun ActionLogPanel(
    entries: List<ActionLogEntry>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Auto-scroll to top (newest first)
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(0)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // ── Header ───────────────────────────────────────────────────────────
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Timeline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Live Action Log",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (entries.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Badge { Text("${entries.size}") }
                    }
                }
                if (entries.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onClear,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.DeleteSweep, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Clear", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        HorizontalDivider()

        // ── Log entries ───────────────────────────────────────────────────────
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.ChatBubbleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No commands yet",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        "Tap the floating bubble, the QS tile, or type a command",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(entries, key = { it.id }) { entry ->
                    AnimatedVisibility(
                        visible = true,
                        enter = slideInVertically { -it } + fadeIn()
                    ) {
                        LogEntryCard(entry = entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogEntryCard(entry: ActionLogEntry) {
    var expanded by remember { mutableStateOf(false) }

    val pathColor = when (entry.executionPath) {
        "LOCAL" -> Color(0xFF4CAF50)
        "GEMINI" -> Color(0xFF2196F3)
        else -> Color(0xFFF44336)
    }

    val borderColor = when (entry.status) {
        "SUCCESS" -> pathColor.copy(alpha = 0.3f)
        else -> Color(0xFFF44336).copy(alpha = 0.5f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // ── Top row ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Command text
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Status icon
                        Icon(
                            imageVector = if (entry.status == "SUCCESS")
                                Icons.Filled.CheckCircle else Icons.Filled.Error,
                            contentDescription = null,
                            tint = if (entry.status == "SUCCESS") Color(0xFF4CAF50)
                            else Color(0xFFF44336),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = entry.command,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = if (expanded) Int.MAX_VALUE else 2
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Path badge + time
                Column(horizontalAlignment = Alignment.End) {
                    // Execution path badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(pathColor.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = when (entry.executionPath) {
                                "LOCAL" -> "⚡ LOCAL"
                                "GEMINI" -> "🧠 GEMINI"
                                else -> "❌ ERROR"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = pathColor,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    // Response time
                    Text(
                        text = "${entry.responseTimeMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Timestamp ─────────────────────────────────────────────────────
            Spacer(Modifier.height(4.dp))
            Text(
                text = TIME_FMT.format(Date(entry.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )

            // ── Expanded: actions list & error ────────────────────────────────
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(8.dp))

                    if (entry.actionsExecuted.isNotEmpty()) {
                        Text(
                            "Actions executed:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        entry.actionsExecuted.forEach { action ->
                            Row(
                                modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    "▸ ",
                                    color = pathColor,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    action,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    entry.errorMessage?.let { err ->
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                Icons.Filled.Warning,
                                null,
                                tint = Color(0xFFF44336),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                err,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFF44336)
                            )
                        }
                    }
                }
            }

            // Expand/collapse hint
            if (entry.actionsExecuted.isNotEmpty() || entry.errorMessage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess
                        else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

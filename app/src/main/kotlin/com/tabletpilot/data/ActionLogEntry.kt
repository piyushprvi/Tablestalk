package com.tabletpilot.data

import java.util.UUID

data class ActionLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val command: String,
    val executionPath: String, // "LOCAL" | "GEMINI"
    val responseTimeMs: Long,
    val actionsExecuted: List<String> = emptyList(),
    val status: String, // "SUCCESS" | "ERROR"
    val errorMessage: String? = null
)

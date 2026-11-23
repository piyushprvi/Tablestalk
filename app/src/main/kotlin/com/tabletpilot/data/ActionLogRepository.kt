package com.tabletpilot.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ActionLogRepository {

    private const val MAX_ENTRIES = 100

    private val _log = MutableStateFlow<List<ActionLogEntry>>(emptyList())
    val log: StateFlow<List<ActionLogEntry>> = _log.asStateFlow()

    fun addEntry(entry: ActionLogEntry) {
        val current = _log.value.toMutableList()
        current.add(0, entry) // newest first
        if (current.size > MAX_ENTRIES) {
            current.subList(MAX_ENTRIES, current.size).clear()
        }
        _log.value = current
    }

    fun clearLog() {
        _log.value = emptyList()
    }

    fun getLog(): StateFlow<List<ActionLogEntry>> = log
}

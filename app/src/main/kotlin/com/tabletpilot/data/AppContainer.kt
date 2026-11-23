package com.tabletpilot.data

import android.content.Context
import com.tabletpilot.ai.AgentOrchestrator
import com.tabletpilot.ai.GeminiAgentClient

/**
 * Simple dependency container. In a larger project this would be replaced
 * by Hilt/Dagger, but for self-contained compilation we keep it manual.
 */
object AppContainer {

    @Volatile
    private var preferencesManager: PreferencesManager? = null

    @Volatile
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            preferencesManager = PreferencesManager.getInstance(context)
            initialized = true
        }
    }

    fun getPreferencesManager(context: Context): PreferencesManager {
        return preferencesManager ?: PreferencesManager.getInstance(context).also {
            preferencesManager = it
        }
    }

    fun getSecureKeyStore(): SecureKeyStore = SecureKeyStore

    fun getActionLogRepository(): ActionLogRepository = ActionLogRepository

    fun getGeminiAgentClient(): GeminiAgentClient = GeminiAgentClient

    fun getAgentOrchestrator(): AgentOrchestrator = AgentOrchestrator
}

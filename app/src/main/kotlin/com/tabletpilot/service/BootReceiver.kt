package com.tabletpilot.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tabletpilot.data.PreferencesManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private const val TAG = "BootReceiver"

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        Log.d(TAG, "Boot completed — checking if service should restart")

        try {
            val prefs = PreferencesManager.getInstance(context)
            val wasRunning = runBlocking { prefs.serviceRunning.first() }
            if (wasRunning) {
                Log.i(TAG, "Restarting AssistantForegroundService after boot")
                AssistantForegroundService.startService(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "BootReceiver error: ${e.message}")
        }
    }
}

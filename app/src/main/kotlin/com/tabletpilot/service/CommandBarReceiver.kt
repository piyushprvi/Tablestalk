package com.tabletpilot.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives the SHOW_COMMAND_BAR broadcast forwarded by PilotQSTileService.
 * The service itself handles the action in its own receiver, but this exported
 * receiver exists for the manifest declaration.
 */
class CommandBarReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == AssistantForegroundService.ACTION_SHOW_COMMAND_BAR) {
            // Forward to the running service if alive
            val serviceIntent = Intent(context, AssistantForegroundService::class.java).apply {
                action = AssistantForegroundService.ACTION_SHOW_COMMAND_BAR
            }
            context.startService(serviceIntent)
        }
    }
}

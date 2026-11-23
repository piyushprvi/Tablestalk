package com.tabletpilot.service

import android.content.Intent
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.tabletpilot.automation.TabletAccessibilityService

class PilotQSTileService : TileService() {

    override fun onTileAdded() {
        super.onTileAdded()
        updateTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onStopListening() {
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()

        // Check overlay permission — required for the command bar
        if (!Settings.canDrawOverlays(this)) {
            // Open overlay settings
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivityAndCollapse(intent)
            return
        }

        // Start the service if not running
        AssistantForegroundService.startService(applicationContext)

        // Send broadcast to show command bar
        val broadcastIntent = Intent(AssistantForegroundService.ACTION_SHOW_COMMAND_BAR)
        broadcastIntent.setPackage(packageName)
        sendBroadcast(broadcastIntent)

        // Show accessibility warning if not connected
        if (!TabletAccessibilityService.isConnected()) {
            Toast.makeText(
                this,
                "Enable TabletPilot in Accessibility Settings for full automation",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        tile.state = Tile.STATE_ACTIVE
        tile.label = "TabletPilot"
        tile.subtitle = if (TabletAccessibilityService.isConnected()) "Ready" else "Setup needed"
        tile.updateTile()
    }
}

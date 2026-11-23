package com.tabletpilot.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.provider.AlarmClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

private const val TAG = "TabletAccessibilitySvc"

// Redmi Pad Pro 5G native resolution: 2560×1600
// Using relative midpoints for gestures
private const val SCREEN_WIDTH = 2560f
private const val SCREEN_HEIGHT = 1600f
private const val SCROLL_DURATION_MS = 300L

class TabletAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: TabletAccessibilityService? = null
            private set

        fun isConnected(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        instance = this
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = (
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            )
            notificationTimeout = 100
        }
        Log.i(TAG, "TabletAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Pull-based API — no processing needed here
    }

    override fun onInterrupt() {
        Log.w(TAG, "AccessibilityService interrupted")
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
        Log.i(TAG, "TabletAccessibilityService destroyed")
    }

    // ── 1. Open App ─────────────────────────────────────────────────────────

    fun openApp(context: Context, appName: String): Boolean {
        return try {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(0)

            // Exact match first, then partial
            val match = apps.firstOrNull { app ->
                pm.getApplicationLabel(app).toString().equals(appName, ignoreCase = true)
            } ?: apps.firstOrNull { app ->
                pm.getApplicationLabel(app).toString().contains(appName, ignoreCase = true)
            }

            if (match != null) {
                val intent = pm.getLaunchIntentForPackage(match.packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    context.startActivity(intent)
                    Log.d(TAG, "Opened app: ${match.packageName}")
                    true
                } else {
                    Log.w(TAG, "No launch intent for: ${match.packageName}")
                    false
                }
            } else {
                Log.w(TAG, "App not found: $appName")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "openApp error: ${e.message}")
            false
        }
    }

    // ── 2. Get Screen Text ───────────────────────────────────────────────────

    fun getScreenText(): String {
        val root = rootInActiveWindow ?: return "Screen unavailable (no active window)"
        val sb = StringBuilder()
        traverseNode(root, sb, depth = 0)
        return sb.toString().take(2000)
    }

    private fun traverseNode(node: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int) {
        if (node == null || depth > 10) return

        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        val hint = node.hintText?.toString()?.trim()

        val hasContent = !text.isNullOrBlank() || !desc.isNullOrBlank() || !hint.isNullOrBlank()

        if (hasContent) {
            val label = when {
                !text.isNullOrBlank() -> text
                !desc.isNullOrBlank() -> desc
                else -> hint!!
            }
            val attrs = buildString {
                if (node.isClickable) append("[CLICK] ")
                if (node.isEditable) append("[INPUT] ")
                if (node.isCheckable) append(if (node.isChecked) "[CHECKED] " else "[UNCHECKED] ")
                if (node.isScrollable) append("[SCROLL] ")
            }
            sb.appendLine("$attrs$label")
        }

        for (i in 0 until node.childCount) {
            traverseNode(node.getChild(i), sb, depth + 1)
        }
    }

    // ── 3. Click Element By Text ─────────────────────────────────────────────

    fun clickElementByText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false

        // Try findAccessibilityNodeInfosByText (fastest)
        val nodes = root.findAccessibilityNodeInfosByText(text)
        for (node in nodes) {
            if (node.isClickable) {
                val result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (result) return true
            }
            // Walk up to find clickable parent
            var parent = node.parent
            var depth = 0
            while (parent != null && depth < 5) {
                if (parent.isClickable) {
                    val result = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (result) return true
                }
                parent = parent.parent
                depth++
            }
            // Fallback: gesture tap at node center
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (!bounds.isEmpty) {
                return tapAt(bounds.exactCenterX(), bounds.exactCenterY())
            }
        }

        // Try by contentDescription
        val descNode = findByContentDescription(root, text)
        if (descNode != null) {
            if (descNode.isClickable) {
                return descNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            val bounds = Rect()
            descNode.getBoundsInScreen(bounds)
            if (!bounds.isEmpty) {
                return tapAt(bounds.exactCenterX(), bounds.exactCenterY())
            }
        }

        Log.w(TAG, "clickElementByText: element not found for '$text'")
        return false
    }

    private fun findByContentDescription(
        node: AccessibilityNodeInfo?,
        text: String,
        depth: Int = 0
    ): AccessibilityNodeInfo? {
        if (node == null || depth > 15) return null
        val desc = node.contentDescription?.toString() ?: ""
        if (desc.contains(text, ignoreCase = true)) return node
        for (i in 0 until node.childCount) {
            val found = findByContentDescription(node.getChild(i), text, depth + 1)
            if (found != null) return found
        }
        return null
    }

    // ── 4. Type Text ─────────────────────────────────────────────────────────

    fun typeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false

        // Find focused input field
        var inputNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

        // If no focused node, search for editable fields
        if (inputNode == null) {
            inputNode = findEditableNode(root)
        }

        if (inputNode == null) {
            Log.w(TAG, "typeText: no input field found")
            return false
        }

        // First click to focus
        inputNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)

        val args = Bundle()
        args.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            text
        )
        val result = inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        Log.d(TAG, "typeText result: $result")
        return result
    }

    private fun findEditableNode(
        node: AccessibilityNodeInfo?,
        depth: Int = 0
    ): AccessibilityNodeInfo? {
        if (node == null || depth > 15) return null
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val found = findEditableNode(node.getChild(i), depth + 1)
            if (found != null) return found
        }
        return null
    }

    // ── 5. Perform Global Action ─────────────────────────────────────────────

    fun performGlobalAction(action: String): Boolean {
        val id = when (action.uppercase()) {
            "HOME" -> GLOBAL_ACTION_HOME
            "BACK" -> GLOBAL_ACTION_BACK
            "RECENTS" -> GLOBAL_ACTION_RECENTS
            "NOTIFICATIONS" -> GLOBAL_ACTION_NOTIFICATIONS
            "SPLIT_SCREEN" -> GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN
            else -> {
                Log.w(TAG, "Unknown global action: $action")
                return false
            }
        }
        return performGlobalAction(id)
    }

    // ── 6. Scroll Screen ─────────────────────────────────────────────────────
    // Coordinates tuned for Redmi Pad Pro 5G (2560×1600)

    fun scrollScreen(direction: String): Boolean {
        val path = Path()
        val cx = SCREEN_WIDTH / 2f
        val cy = SCREEN_HEIGHT / 2f

        when (direction.uppercase()) {
            "DOWN" -> {
                // Swipe finger up = content scrolls down
                path.moveTo(cx, cy + 400f)
                path.lineTo(cx, cy - 400f)
            }
            "UP" -> {
                path.moveTo(cx, cy - 400f)
                path.lineTo(cx, cy + 400f)
            }
            "LEFT" -> {
                path.moveTo(cx + 500f, cy)
                path.lineTo(cx - 500f, cy)
            }
            "RIGHT" -> {
                path.moveTo(cx - 500f, cy)
                path.lineTo(cx + 500f, cy)
            }
            else -> {
                Log.w(TAG, "Unknown scroll direction: $direction")
                return false
            }
        }

        val stroke = GestureDescription.StrokeDescription(path, 0, SCROLL_DURATION_MS)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    // ── 7. Set Timer ─────────────────────────────────────────────────────────

    fun setTimer(minutes: Int, label: String): Boolean {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            applicationContext.startActivity(intent)
            Log.d(TAG, "Timer set: $minutes min, label=$label")
            true
        } catch (e: Exception) {
            Log.e(TAG, "setTimer error: ${e.message}")
            false
        }
    }

    // ── Gesture Helper ───────────────────────────────────────────────────────

    private fun tapAt(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }
}

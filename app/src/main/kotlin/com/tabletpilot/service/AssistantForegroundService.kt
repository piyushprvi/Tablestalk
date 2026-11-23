package com.tabletpilot.service

import android.app.*
import android.content.*
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.core.app.NotificationCompat
import com.tabletpilot.R
import com.tabletpilot.ai.AgentOrchestrator
import com.tabletpilot.data.ActionLogEntry
import com.tabletpilot.data.ActionLogRepository
import com.tabletpilot.data.PreferencesManager
import com.tabletpilot.data.SecureKeyStore
import com.tabletpilot.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

private const val TAG = "AssistantFgService"
private const val NOTIFICATION_ID = 1001
private const val CHANNEL_ID = "tabletpilot_channel"

class AssistantForegroundService : Service() {

    companion object {
        const val ACTION_STOP_SERVICE = "com.tabletpilot.STOP_SERVICE"
        const val ACTION_SHOW_COMMAND_BAR = "com.tabletpilot.SHOW_COMMAND_BAR"

        fun startService(context: Context) {
            val intent = Intent(context, AssistantForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, AssistantForegroundService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var windowManager: WindowManager? = null
    private var floatingBubbleView: View? = null
    private var commandBarView: View? = null
    private var speechRecognizer: SpeechRecognizer? = null

    private lateinit var prefsManager: PreferencesManager

    // Track bubble drag position
    private var bubbleInitX = 0
    private var bubbleInitY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    override fun onCreate() {
        super.onCreate()
        prefsManager = PreferencesManager.getInstance(applicationContext)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        // Show floating bubble if overlay permission is granted
        if (android.provider.Settings.canDrawOverlays(this)) {
            showFloatingBubble()
        }

        // Listen for command bar broadcasts (from QS Tile)
        val filter = IntentFilter(ACTION_SHOW_COMMAND_BAR)
        registerReceiver(commandBarReceiver, filter, RECEIVER_NOT_EXPORTED)

        Log.i(TAG, "AssistantForegroundService started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                serviceScope.launch {
                    prefsManager.saveServiceRunning(false)
                }
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SHOW_COMMAND_BAR -> {
                showFloatingCommandBar()
            }
        }
        serviceScope.launch {
            prefsManager.saveServiceRunning(true)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        removeFloatingViews()
        speechRecognizer?.destroy()
        try { unregisterReceiver(commandBarReceiver) } catch (_: Exception) {}
        super.onDestroy()
        Log.i(TAG, "AssistantForegroundService destroyed")
    }

    // ── Notification ─────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, com.tabletpilot.ui.MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, AssistantForegroundService::class.java).apply {
                action = ACTION_STOP_SERVICE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val showBarIntent = PendingIntent.getBroadcast(
            this, 2,
            Intent(ACTION_SHOW_COMMAND_BAR),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_menu_send, "Open Assistant", showBarIntent)
            .addAction(android.R.drawable.ic_delete, "Stop Service", stopIntent)
            .build()
    }

    // ── Floating Bubble ───────────────────────────────────────────────────────

    private fun showFloatingBubble() {
        if (floatingBubbleView != null) return

        val bubbleSize = dpToPx(60)
        val bubble = View(this).apply {
            background = createCircleDrawable(Color.argb(200, 103, 58, 183)) // Purple
        }

        val params = WindowManager.LayoutParams(
            bubbleSize, bubbleSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = windowManager!!.currentWindowMetrics.bounds.width() - bubbleSize - dpToPx(8)
            y = windowManager!!.currentWindowMetrics.bounds.height() / 2 - bubbleSize / 2
        }

        bubble.setOnTouchListener(object : View.OnTouchListener {
            private var downX = 0f
            private var downY = 0f
            private var startX = 0
            private var startY = 0

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isDragging = false
                        downX = event.rawX
                        downY = event.rawY
                        startX = params.x
                        startY = params.y
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - downX
                        val dy = event.rawY - downY
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) isDragging = true
                        if (isDragging) {
                            params.x = (startX + dx).toInt()
                            params.y = (startY + dy).toInt()
                            windowManager?.updateViewLayout(bubble, params)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isDragging) {
                            showFloatingCommandBar()
                        }
                        // Snap to nearest edge
                        val screenW = windowManager!!.currentWindowMetrics.bounds.width()
                        params.x = if (params.x + bubbleSize / 2 < screenW / 2) {
                            dpToPx(8)
                        } else {
                            screenW - bubbleSize - dpToPx(8)
                        }
                        windowManager?.updateViewLayout(bubble, params)
                        return true
                    }
                }
                return false
            }
        })

        floatingBubbleView = bubble
        windowManager?.addView(bubble, params)
    }

    // ── Floating Command Bar ──────────────────────────────────────────────────

    fun showFloatingCommandBar() {
        if (commandBarView != null) return // already showing

        val barWidth = dpToPx(480)
        val barHeight = WindowManager.LayoutParams.WRAP_CONTENT

        // Build the bar layout programmatically
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
            background = createRoundedDrawable(Color.argb(240, 30, 30, 30), dpToPx(16).toFloat())
            elevation = dpToPx(8).toFloat()
        }

        // Title row
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleText = TextView(this).apply {
            text = "TabletPilot"
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(0, 0, 0, dpToPx(4))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
            setOnClickListener { dismissCommandBar() }
        }
        titleRow.addView(titleText)
        titleRow.addView(closeBtn)
        container.addView(titleRow)

        // Input row
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpToPx(8), 0, 0)
        }

        val editText = EditText(this).apply {
            hint = getString(R.string.command_hint)
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(80, 255, 255, 255))
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            imeOptions = EditorInfo.IME_ACTION_SEND
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val micBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_btn_speak_now)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.argb(200, 103, 58, 183))
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
        }

        val sendBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_send)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.argb(200, 103, 58, 183))
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
        }

        inputRow.addView(editText)
        inputRow.addView(micBtn)
        inputRow.addView(sendBtn)
        container.addView(inputRow)

        // Status text
        val statusText = TextView(this).apply {
            text = ""
            setTextColor(Color.argb(180, 255, 255, 255))
            textSize = 12f
            setPadding(0, dpToPx(4), 0, 0)
        }
        container.addView(statusText)

        val barParams = WindowManager.LayoutParams(
            barWidth, barHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dpToPx(80)
        }

        // Send handler
        val onSend = {
            val cmd = editText.text.toString().trim()
            if (cmd.isNotBlank()) {
                statusText.text = "Processing…"
                editText.isEnabled = false
                dismissCommandBar()
                processCommand(cmd)
            }
        }

        sendBtn.setOnClickListener { onSend() }
        editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { onSend(); true } else false
        }

        // Mic button — with CaramelOS fallback
        micBtn.setOnClickListener { startVoiceRecognition(editText, statusText, onSend) }

        commandBarView = container
        windowManager?.addView(container, barParams)
    }

    private fun dismissCommandBar() {
        commandBarView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        commandBarView = null
    }

    // ── Voice Recognition (CaramelOS/MIUI compatible) ─────────────────────────

    private fun startVoiceRecognition(
        editText: EditText,
        statusText: TextView,
        onComplete: () -> Unit
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition unavailable", Toast.LENGTH_SHORT).show()
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        // CaramelOS / MIUI may need explicit recognizer package
        // Try system default first; if it fails, the error handler will show a toast
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        statusText.text = "Listening…"

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { statusText.text = "🎙 Speak now…" }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { statusText.text = "Processing…" }
            override fun onError(error: Int) {
                statusText.text = "Voice error $error — type instead"
                Log.w(TAG, "Speech error: $error")
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""
                editText.setText(text)
                statusText.text = ""
                if (text.isNotBlank()) onComplete()
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                editText.setText(partial?.firstOrNull() ?: "")
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speechRecognizer?.startListening(intent)
    }

    // ── Command Processing ────────────────────────────────────────────────────

    private fun processCommand(command: String) {
        serviceScope.launch {
            try {
                val apiKey = SecureKeyStore.getApiKey(applicationContext)
                    ?: BuildConfig.GEMINI_API_KEY.takeIf { it.isNotBlank() }
                    ?: prefsManager.geminiApiKey.first()

                val model = prefsManager.selectedModel.first()
                val fastPath = prefsManager.localFastPathEnabled.first()

                val result = AgentOrchestrator.execute(
                    command = command,
                    apiKey = apiKey,
                    model = model,
                    fastPathEnabled = fastPath,
                    context = applicationContext
                )

                ActionLogRepository.addEntry(
                    ActionLogEntry(
                        command = command,
                        executionPath = result.executionPath,
                        responseTimeMs = result.responseTimeMs,
                        actionsExecuted = result.actionsExecuted,
                        status = if (result.success) "SUCCESS" else "ERROR",
                        errorMessage = result.error
                    )
                )

                Log.d(TAG, "Command result: ${result.finalMessage} (${result.responseTimeMs}ms)")
            } catch (e: Exception) {
                Log.e(TAG, "processCommand error: ${e.message}")
                ActionLogRepository.addEntry(
                    ActionLogEntry(
                        command = command,
                        executionPath = "ERROR",
                        responseTimeMs = 0,
                        status = "ERROR",
                        errorMessage = e.message
                    )
                )
            }
        }
    }

    // ── Broadcast Receiver ────────────────────────────────────────────────────

    private val commandBarReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_SHOW_COMMAND_BAR) {
                showFloatingCommandBar()
            }
        }
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    private fun removeFloatingViews() {
        floatingBubbleView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        floatingBubbleView = null
        dismissCommandBar()
    }

    // ── Drawing Helpers ───────────────────────────────────────────────────────

    private fun createCircleDrawable(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            // Subtle white border
            setStroke(dpToPx(2), Color.argb(80, 255, 255, 255))
        }
    }

    private fun createRoundedDrawable(color: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}

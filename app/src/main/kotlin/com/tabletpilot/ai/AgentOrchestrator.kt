package com.tabletpilot.ai

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.tabletpilot.automation.TabletAccessibilityService
import com.tabletpilot.data.ActionLogEntry
import com.tabletpilot.data.ActionLogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val TAG = "AgentOrchestrator"
private const val MAX_AGENT_STEPS = 5

object AgentOrchestrator {

    data class OrchestratorResult(
        val success: Boolean,
        val executionPath: String,  // "LOCAL" | "GEMINI"
        val responseTimeMs: Long,
        val actionsExecuted: List<String>,
        val finalMessage: String,
        val error: String? = null
    )

    suspend fun execute(
        command: String,
        apiKey: String,
        model: String,
        fastPathEnabled: Boolean,
        context: Context
    ): OrchestratorResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val actionsExecuted = mutableListOf<String>()

        // ── Step 1: Try Local Fast-Path ──────────────────────────────────────
        if (fastPathEnabled) {
            val localAction = LocalFastPathRouter.route(command)
            if (localAction != null) {
                Log.d(TAG, "Local fast-path matched: ${localAction.actionType}")
                val success = executeLocalAction(localAction, context, actionsExecuted)
                val elapsed = System.currentTimeMillis() - startTime
                return@withContext OrchestratorResult(
                    success = success,
                    executionPath = "LOCAL",
                    responseTimeMs = elapsed,
                    actionsExecuted = actionsExecuted,
                    finalMessage = if (success) "Done (local, ${elapsed}ms)" else "Local action failed",
                    error = if (!success) "Local execution returned false" else null
                )
            }
        }

        // ── Step 2: Gemini Agent Loop (max 5 steps) ──────────────────────────
        if (apiKey.isBlank()) {
            val elapsed = System.currentTimeMillis() - startTime
            return@withContext OrchestratorResult(
                success = false,
                executionPath = "GEMINI",
                responseTimeMs = elapsed,
                actionsExecuted = emptyList(),
                finalMessage = "No API key configured",
                error = "API key is empty. Please set your Gemini API key in Settings."
            )
        }

        val conversationHistory = mutableListOf<Content>()
        var finalMessage = ""
        var success = false

        try {
            var stepCount = 0
            while (stepCount < MAX_AGENT_STEPS) {
                stepCount++
                Log.d(TAG, "Gemini agent step $stepCount")

                val response = GeminiAgentClient.sendRequest(
                    prompt = if (stepCount == 1) command else "(continue with the next action)",
                    model = model,
                    apiKey = apiKey,
                    conversationHistory = conversationHistory
                )

                val candidate = response.candidates.firstOrNull()
                val content = candidate?.content

                if (content == null) {
                    finalMessage = "No response from Gemini"
                    break
                }

                // Add model response to history
                conversationHistory.add(content)

                // Check for function calls in the response
                val functionCallPart = content.parts.firstOrNull { it.functionCall != null }

                if (functionCallPart?.functionCall != null) {
                    val funcCall = functionCallPart.functionCall!!
                    Log.d(TAG, "Gemini requested function: ${funcCall.name}")
                    actionsExecuted.add("${funcCall.name}(${funcCall.args})")

                    // Execute the function
                    val (funcSuccess, funcResult) = executeGeminiFunction(
                        funcCall.name,
                        funcCall.args,
                        context
                    )

                    // Build function response content and add to history
                    val responseObj = buildJsonObject {
                        put("result", funcResult)
                        put("success", funcSuccess.toString())
                    }

                    val funcResponseContent = Content(
                        role = "user",
                        parts = listOf(
                            Part(
                                functionResponse = FunctionResponsePart(
                                    name = funcCall.name,
                                    response = responseObj
                                )
                            )
                        )
                    )
                    conversationHistory.add(funcResponseContent)

                } else {
                    // Model gave a text response — task is complete
                    finalMessage = content.parts
                        .mapNotNull { it.text }
                        .joinToString(" ")
                        .trim()
                    success = true
                    break
                }
            }

            if (stepCount >= MAX_AGENT_STEPS && !success) {
                finalMessage = "Task completed after $MAX_AGENT_STEPS steps"
                success = true
            }

        } catch (e: GeminiException) {
            Log.e(TAG, "Gemini API error: ${e.message}")
            finalMessage = "API error: ${e.message}"
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error: ${e.message}")
            finalMessage = "Error: ${e.message}"
        }

        val elapsed = System.currentTimeMillis() - startTime
        OrchestratorResult(
            success = success,
            executionPath = "GEMINI",
            responseTimeMs = elapsed,
            actionsExecuted = actionsExecuted,
            finalMessage = finalMessage,
            error = if (!success) finalMessage else null
        )
    }

    // ── Local Action Execution ───────────────────────────────────────────────

    private fun executeLocalAction(
        action: LocalFastPathRouter.LocalAction,
        context: Context,
        log: MutableList<String>
    ): Boolean {
        val svc = TabletAccessibilityService.instance
        return when (action.actionType) {
            "OPEN_APP" -> {
                val appName = action.params["app"] ?: return false
                log.add("open_app($appName)")
                svc?.openApp(context, appName) ?: tryOpenAppDirect(context, appName)
            }
            "GLOBAL" -> {
                val globalAction = action.params["action"] ?: return false
                log.add("global_action($globalAction)")
                svc?.performGlobalAction(globalAction) ?: false
            }
            "SCROLL" -> {
                val direction = action.params["direction"] ?: return false
                log.add("scroll($direction)")
                svc?.scrollScreen(direction) ?: false
            }
            "VOLUME" -> {
                val dir = action.params["direction"] ?: return false
                log.add("volume($dir)")
                adjustVolume(context, dir)
            }
            "TIMER" -> {
                val minutes = action.params["minutes"]?.toIntOrNull() ?: return false
                val label = action.params["label"] ?: "Timer"
                log.add("set_timer(${minutes}min, $label)")
                svc?.setTimer(minutes, label) ?: setTimerDirect(context, minutes, label)
            }
            else -> false
        }
    }

    private fun tryOpenAppDirect(context: Context, appName: String): Boolean {
        return try {
            val pm = context.packageManager
            val installedApps = pm.getInstalledApplications(0)
            val match = installedApps.firstOrNull { app ->
                pm.getApplicationLabel(app).toString().equals(appName, ignoreCase = true)
            } ?: installedApps.firstOrNull { app ->
                pm.getApplicationLabel(app).toString().contains(appName, ignoreCase = true)
            }
            if (match != null) {
                val intent = pm.getLaunchIntentForPackage(match.packageName)
                if (intent != null) {
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    true
                } else false
            } else false
        } catch (e: Exception) {
            false
        }
    }

    private fun adjustVolume(context: Context, direction: String): Boolean {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val dir = if (direction == "UP") AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun setTimerDirect(context: Context, minutes: Int, label: String): Boolean {
        return try {
            val intent = android.content.Intent(android.provider.AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(android.provider.AlarmClock.EXTRA_LENGTH, minutes * 60)
                putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, label)
                putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    // ── Gemini Function Execution ────────────────────────────────────────────

    private suspend fun executeGeminiFunction(
        name: String,
        args: JsonObject,
        context: Context
    ): Pair<Boolean, String> {
        val svc = TabletAccessibilityService.instance
        return withContext(Dispatchers.Main) {
            when (name) {
                "open_app" -> {
                    val appName = args["app_name"]?.jsonPrimitive?.content
                        ?: return@withContext Pair(false, "Missing app_name parameter")
                    val result = svc?.openApp(context, appName) ?: tryOpenAppDirect(context, appName)
                    Pair(result, if (result) "Opened $appName" else "Could not find app: $appName")
                }
                "get_screen_text" -> {
                    val text = svc?.getScreenText()
                        ?: "Accessibility service not connected. Ask user to enable it."
                    Pair(true, text)
                }
                "click_element_by_text" -> {
                    val text = args["text"]?.jsonPrimitive?.content
                        ?: return@withContext Pair(false, "Missing text parameter")
                    val result = svc?.clickElementByText(text) ?: false
                    Pair(result, if (result) "Clicked '$text'" else "Element not found: '$text'")
                }
                "type_text" -> {
                    val text = args["text"]?.jsonPrimitive?.content
                        ?: return@withContext Pair(false, "Missing text parameter")
                    val result = svc?.typeText(text) ?: false
                    Pair(result, if (result) "Typed text successfully" else "No focused input field found")
                }
                "perform_global_action" -> {
                    val action = args["action"]?.jsonPrimitive?.content
                        ?: return@withContext Pair(false, "Missing action parameter")
                    val result = svc?.performGlobalAction(action) ?: false
                    Pair(result, if (result) "Performed $action" else "Unknown global action: $action")
                }
                "scroll_screen" -> {
                    val direction = args["direction"]?.jsonPrimitive?.content
                        ?: return@withContext Pair(false, "Missing direction parameter")
                    val result = svc?.scrollScreen(direction) ?: false
                    Pair(result, if (result) "Scrolled $direction" else "Scroll failed")
                }
                "set_timer" -> {
                    val minutes = args["minutes"]?.jsonPrimitive?.int
                        ?: return@withContext Pair(false, "Missing minutes parameter")
                    val label = args["label"]?.jsonPrimitive?.content ?: "Timer"
                    val result = svc?.setTimer(minutes, label)
                        ?: setTimerDirect(context, minutes, label)
                    Pair(result, if (result) "Timer set for $minutes minutes ($label)" else "Failed to set timer")
                }
                else -> Pair(false, "Unknown function: $name")
            }
        }
    }
}

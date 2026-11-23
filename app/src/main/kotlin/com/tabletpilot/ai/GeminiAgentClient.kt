package com.tabletpilot.ai

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.addJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object GeminiAgentClient {

    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
        explicitNulls = false
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val SYSTEM_PROMPT = """
You are TabletPilot, an ultra-fast Android device automation assistant.

RULES:
1. ALWAYS prefer calling a device tool function over giving text explanations.
2. Be CONCISE. Never return long prose unless the user explicitly asks for information.
3. After executing an action, return a SHORT confirmation (1 line max).
4. When unsure which UI element to click, first call get_screen_text() to see the screen.
5. For multi-step tasks, chain tool calls logically: read screen → click → type → confirm.
6. If a task is impossible (permission denied, element not found), say so in one sentence.
7. Never ask clarifying questions unless the command is completely ambiguous.
    """.trimIndent()

    // ─── Tool Declarations ──────────────────────────────────────────────────

    private fun buildToolDeclarations(): List<FunctionDeclaration> = listOf(
        FunctionDeclaration(
            name = "open_app",
            description = "Open/launch an installed Android application by name.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("app_name") {
                        put("type", "string")
                        put("description", "The display name of the app to open (e.g. 'Chrome', 'Settings', 'YouTube')")
                    }
                }
                putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("app_name")) }
            }
        ),
        FunctionDeclaration(
            name = "get_screen_text",
            description = "Read the current visible screen content — all text, buttons, and interactive elements. Call this before clicking to understand what's on screen.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {}
            }
        ),
        FunctionDeclaration(
            name = "click_element_by_text",
            description = "Tap/click a UI element that contains the given text or content description.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("text") {
                        put("type", "string")
                        put("description", "The visible text or content description of the element to click")
                    }
                }
                putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("text")) }
            }
        ),
        FunctionDeclaration(
            name = "type_text",
            description = "Type/insert text into the currently focused or active input field.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("text") {
                        put("type", "string")
                        put("description", "The text to type into the focused input field")
                    }
                }
                putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("text")) }
            }
        ),
        FunctionDeclaration(
            name = "perform_global_action",
            description = "Perform a device-level navigation action.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("action") {
                        put("type", "string")
                        put("description", "One of: HOME, BACK, RECENTS, NOTIFICATIONS, SPLIT_SCREEN")
                    }
                }
                putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("action")) }
            }
        ),
        FunctionDeclaration(
            name = "scroll_screen",
            description = "Scroll the screen in a direction.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("direction") {
                        put("type", "string")
                        put("description", "One of: UP, DOWN, LEFT, RIGHT")
                    }
                }
                putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("direction")) }
            }
        ),
        FunctionDeclaration(
            name = "set_timer",
            description = "Set a countdown timer using the system clock app.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("minutes") {
                        put("type", "integer")
                        put("description", "Timer duration in minutes")
                    }
                    putJsonObject("label") {
                        put("type", "string")
                        put("description", "Optional label for the timer (e.g. 'Tea', 'Workout')")
                    }
                }
                putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("minutes")) }
            }
        )
    )

    // ─── Main Request Function ──────────────────────────────────────────────

    suspend fun sendRequest(
        prompt: String,
        model: String,
        apiKey: String,
        conversationHistory: List<Content> = emptyList()
    ): GeminiResponse {
        val systemContent = Content(
            role = "user",
            parts = listOf(Part(text = SYSTEM_PROMPT))
        )
        val systemAck = Content(
            role = "model",
            parts = listOf(Part(text = "Understood. I am TabletPilot, ready to automate your device."))
        )

        val allContents = buildList {
            add(systemContent)
            add(systemAck)
            addAll(conversationHistory)
            add(Content(role = "user", parts = listOf(Part(text = prompt))))
        }

        val requestBody = GeminiRequest(
            contents = allContents,
            tools = listOf(Tool(functionDeclarations = buildToolDeclarations())),
            generationConfig = GenerationConfig(
                maxOutputTokens = 1024,
                temperature = 0.1
            )
        )

        val requestJson = json.encodeToString(requestBody)
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = requestJson.toRequestBody(mediaType)

        val url = "$BASE_URL$model:generateContent?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .build()

        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()
                ?: throw GeminiException("Empty response body", response.code)

            if (!response.isSuccessful) {
                throw GeminiException(
                    "API request failed: ${response.code} - $responseBody",
                    response.code
                )
            }

            json.decodeFromString<GeminiResponse>(responseBody)
        }
    }
}

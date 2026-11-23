package com.tabletpilot.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// ─── Request Models ───────────────────────────────────────────────────────────

@Serializable
data class GeminiRequest(
    val contents: List<Content>,
    val tools: List<Tool>? = null,
    @SerialName("system_instruction") val systemInstruction: Content? = null,
    @SerialName("generation_config") val generationConfig: GenerationConfig? = null
)

@Serializable
data class Content(
    val role: String,
    val parts: List<Part>
)

@Serializable
data class Part(
    val text: String? = null,
    @SerialName("function_call") val functionCall: FunctionCallPart? = null,
    @SerialName("function_response") val functionResponse: FunctionResponsePart? = null
)

@Serializable
data class FunctionCallPart(
    val name: String,
    val args: JsonObject = JsonObject(emptyMap())
)

@Serializable
data class FunctionResponsePart(
    val name: String,
    val response: JsonObject
)

@Serializable
data class Tool(
    @SerialName("function_declarations") val functionDeclarations: List<FunctionDeclaration>
)

@Serializable
data class FunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: JsonObject
)

@Serializable
data class GenerationConfig(
    @SerialName("max_output_tokens") val maxOutputTokens: Int = 1024,
    val temperature: Double = 0.1,
    @SerialName("response_mime_type") val responseMimeType: String = "text/plain"
)

// ─── Response Models ──────────────────────────────────────────────────────────

@Serializable
data class GeminiResponse(
    val candidates: List<Candidate> = emptyList(),
    @SerialName("usage_metadata") val usageMetadata: UsageMetadata? = null
)

@Serializable
data class Candidate(
    val content: Content? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class UsageMetadata(
    @SerialName("prompt_token_count") val promptTokenCount: Int = 0,
    @SerialName("candidates_token_count") val candidatesTokenCount: Int = 0,
    @SerialName("total_token_count") val totalTokenCount: Int = 0
)

// ─── Error ────────────────────────────────────────────────────────────────────

class GeminiException(message: String, val code: Int = -1) : Exception(message)

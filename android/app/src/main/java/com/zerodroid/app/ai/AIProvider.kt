package com.zerodroid.app.ai

import kotlinx.coroutines.flow.Flow

/**
 * Base interface for all AI providers (local NPU, Gemini, OpenRouter, etc.)
 */
interface AIProvider {
    val name: String
    val isLocal: Boolean

    /** Check if this provider is available and ready */
    suspend fun isAvailable(): Boolean

    /** Send a message and get a complete response */
    suspend fun complete(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>? = null,
        temperature: Float = 0.4f,
        maxTokens: Int = 4096,
    ): CompletionResult

    /** Send a message and stream the response token by token */
    fun stream(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>? = null,
        temperature: Float = 0.4f,
        maxTokens: Int = 4096,
    ): Flow<StreamChunk>
}

// ─── Data Classes ───────────────────────────────

data class ChatMessage(
    val role: String,      // "system", "user", "assistant", "tool"
    val content: String,
    val toolCalls: List<ToolCall>? = null,
    val toolCallId: String? = null,
)

data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: Map<String, Any>,
)

data class ToolCall(
    val id: String,
    val name: String,
    val arguments: Map<String, Any>,
)

data class CompletionResult(
    val content: String?,
    val toolCalls: List<ToolCall>?,
    val finishReason: String?,
)

sealed class StreamChunk {
    data class Text(val content: String) : StreamChunk()
    data class ToolCallStart(val call: ToolCall) : StreamChunk()
    data class Done(val finishReason: String) : StreamChunk()
    data class Error(val message: String) : StreamChunk()
}

// ─── Model Info ─────────────────────────────────

data class ModelInfo(
    val id: String,
    val name: String,
    val description: String,
    val sizeBytes: Long,
    val ramRequired: String,
    val supportsTools: Boolean,
    val supportsVision: Boolean,
    val isDownloaded: Boolean = false,
    val downloadUrl: String? = null,
)

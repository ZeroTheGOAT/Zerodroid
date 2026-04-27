package com.zerodroid.app.data

/**
 * AI Provider configuration — supports 7 cloud providers + local
 */
data class AiProviderConfig(
    val id: String,
    val name: String,
    val icon: String,  // emoji
    val description: String,
    val apiBaseUrl: String,
    val requiresApiKey: Boolean = true,
    val models: List<String>,
    val defaultModel: String,
    var apiKey: String = "",
    var isEnabled: Boolean = false,
)

object ProviderCatalog {
    fun getProviders(): List<AiProviderConfig> = listOf(
        AiProviderConfig(
            id = "local", name = "Local (On-Device)", icon = "📱",
            description = "NPU/CPU inference. No internet. Full privacy.",
            apiBaseUrl = "", requiresApiKey = false,
            models = listOf("gemma4:e2b", "gemma4:e4b", "qwen3.5:4b", "qwen3.5:0.8b", "phi4-mini", "llama3.2:3b"),
            defaultModel = "gemma4:e2b", isEnabled = true,
        ),
        AiProviderConfig(
            id = "gemini", name = "Google Gemini", icon = "✨",
            description = "Free tier available. 1M token context.",
            apiBaseUrl = "https://generativelanguage.googleapis.com/v1beta",
            models = listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.0-flash"),
            defaultModel = "gemini-2.5-flash",
        ),
        AiProviderConfig(
            id = "openai", name = "OpenAI", icon = "🤖",
            description = "GPT-4o, o3, o4-mini. Industry standard.",
            apiBaseUrl = "https://api.openai.com/v1",
            models = listOf("gpt-4o", "gpt-4o-mini", "o3", "o4-mini"),
            defaultModel = "gpt-4o",
        ),
        AiProviderConfig(
            id = "anthropic", name = "Anthropic (Claude)", icon = "🧠",
            description = "Claude Sonnet 4, Opus 4. Best for coding.",
            apiBaseUrl = "https://api.anthropic.com/v1",
            models = listOf("claude-sonnet-4-20250514", "claude-opus-4-20250514", "claude-haiku-3-20250307"),
            defaultModel = "claude-sonnet-4-20250514",
        ),
        AiProviderConfig(
            id = "openrouter", name = "OpenRouter", icon = "🔀",
            description = "Access 200+ models. One API key.",
            apiBaseUrl = "https://openrouter.ai/api/v1",
            models = listOf("anthropic/claude-sonnet-4", "openai/gpt-4o", "google/gemini-2.5-flash", "meta-llama/llama-3.3-70b"),
            defaultModel = "anthropic/claude-sonnet-4",
        ),
        AiProviderConfig(
            id = "groq", name = "Groq", icon = "⚡",
            description = "Ultra-fast inference. Free tier available.",
            apiBaseUrl = "https://api.groq.com/openai/v1",
            models = listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "mixtral-8x7b-32768"),
            defaultModel = "llama-3.3-70b-versatile",
        ),
        AiProviderConfig(
            id = "together", name = "Together AI", icon = "🤝",
            description = "Open-source models. Competitive pricing.",
            apiBaseUrl = "https://api.together.xyz/v1",
            models = listOf("meta-llama/Llama-3.3-70B-Instruct-Turbo", "Qwen/Qwen2.5-72B-Instruct-Turbo", "deepseek-ai/DeepSeek-V3"),
            defaultModel = "meta-llama/Llama-3.3-70B-Instruct-Turbo",
        ),
        AiProviderConfig(
            id = "kimi", name = "Kimi (Moonshot)", icon = "🌙",
            description = "Moonshot AI. Long context, strong reasoning.",
            apiBaseUrl = "https://api.moonshot.cn/v1",
            models = listOf("moonshot-v1-8k", "moonshot-v1-32k", "moonshot-v1-128k", "kimi-latest"),
            defaultModel = "moonshot-v1-32k",
        ),
    )
}


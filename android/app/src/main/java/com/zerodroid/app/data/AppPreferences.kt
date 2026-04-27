package com.zerodroid.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "zerodroid_settings")

/**
 * Persistent app preferences using DataStore
 */
class AppPreferences(private val context: Context) {

    companion object {
        // Per-provider API keys
        val KEY_GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val KEY_OPENAI_API_KEY = stringPreferencesKey("openai_api_key")
        val KEY_ANTHROPIC_API_KEY = stringPreferencesKey("anthropic_api_key")
        val KEY_OPENROUTER_API_KEY = stringPreferencesKey("openrouter_api_key")
        val KEY_GROQ_API_KEY = stringPreferencesKey("groq_api_key")
        val KEY_TOGETHER_API_KEY = stringPreferencesKey("together_api_key")
        val KEY_KIMI_API_KEY = stringPreferencesKey("kimi_api_key")

        val KEY_SELECTED_MODEL = stringPreferencesKey("selected_model")
        val KEY_THEME = stringPreferencesKey("theme")
        val KEY_FONT_SIZE = stringPreferencesKey("font_size")
        val KEY_AI_PROVIDER = stringPreferencesKey("ai_provider")
        val KEY_TERMINAL_CWD = stringPreferencesKey("terminal_cwd")
    }

    // Provider API keys
    val geminiApiKey: Flow<String> = context.dataStore.data.map { it[KEY_GEMINI_API_KEY] ?: "" }
    val openAiApiKey: Flow<String> = context.dataStore.data.map { it[KEY_OPENAI_API_KEY] ?: "" }
    val anthropicApiKey: Flow<String> = context.dataStore.data.map { it[KEY_ANTHROPIC_API_KEY] ?: "" }
    val openRouterApiKey: Flow<String> = context.dataStore.data.map { it[KEY_OPENROUTER_API_KEY] ?: "" }
    val groqApiKey: Flow<String> = context.dataStore.data.map { it[KEY_GROQ_API_KEY] ?: "" }
    val togetherApiKey: Flow<String> = context.dataStore.data.map { it[KEY_TOGETHER_API_KEY] ?: "" }
    val kimiApiKey: Flow<String> = context.dataStore.data.map { it[KEY_KIMI_API_KEY] ?: "" }

    val selectedModel: Flow<String> = context.dataStore.data.map { it[KEY_SELECTED_MODEL] ?: "gemma4:e2b" }
    val theme: Flow<String> = context.dataStore.data.map { it[KEY_THEME] ?: "amoled_dark" }
    val fontSize: Flow<String> = context.dataStore.data.map { it[KEY_FONT_SIZE] ?: "medium" }
    val aiProvider: Flow<String> = context.dataStore.data.map { it[KEY_AI_PROVIDER] ?: "local" }
    val terminalCwd: Flow<String> = context.dataStore.data.map { it[KEY_TERMINAL_CWD] ?: "/storage/emulated/0" }

    suspend fun setApiKey(providerId: String, key: String) {
        val prefKey = when (providerId) {
            "gemini" -> KEY_GEMINI_API_KEY
            "openai" -> KEY_OPENAI_API_KEY
            "anthropic" -> KEY_ANTHROPIC_API_KEY
            "openrouter" -> KEY_OPENROUTER_API_KEY
            "groq" -> KEY_GROQ_API_KEY
            "together" -> KEY_TOGETHER_API_KEY
            "kimi" -> KEY_KIMI_API_KEY
            else -> return
        }
        context.dataStore.edit { it[prefKey] = key }
    }

    fun getApiKey(providerId: String): Flow<String> = when (providerId) {
        "gemini" -> geminiApiKey
        "openai" -> openAiApiKey
        "anthropic" -> anthropicApiKey
        "openrouter" -> openRouterApiKey
        "groq" -> groqApiKey
        "together" -> togetherApiKey
        "kimi" -> kimiApiKey
        else -> kotlinx.coroutines.flow.flowOf("")
    }

    suspend fun setGeminiApiKey(key: String) { context.dataStore.edit { it[KEY_GEMINI_API_KEY] = key } }
    suspend fun setOpenRouterApiKey(key: String) { context.dataStore.edit { it[KEY_OPENROUTER_API_KEY] = key } }
    suspend fun setSelectedModel(model: String) { context.dataStore.edit { it[KEY_SELECTED_MODEL] = model } }
    suspend fun setTheme(theme: String) { context.dataStore.edit { it[KEY_THEME] = theme } }
    suspend fun setFontSize(size: String) { context.dataStore.edit { it[KEY_FONT_SIZE] = size } }
    suspend fun setAiProvider(provider: String) { context.dataStore.edit { it[KEY_AI_PROVIDER] = provider } }
    suspend fun setTerminalCwd(cwd: String) { context.dataStore.edit { it[KEY_TERMINAL_CWD] = cwd } }
}

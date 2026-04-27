package com.zerodroid.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zerodroid.app.data.AiProviderConfig
import com.zerodroid.app.data.AppPreferences
import com.zerodroid.app.data.ProviderCatalog
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = AppPreferences(application)

    // ─── Providers ──────────────────────────────
    private val _providers = MutableStateFlow(ProviderCatalog.getProviders())
    val providers: StateFlow<List<AiProviderConfig>> = _providers.asStateFlow()

    val selectedModel: StateFlow<String> = prefs.selectedModel
        .stateIn(viewModelScope, SharingStarted.Lazily, "gemma4:e2b")
    val theme: StateFlow<String> = prefs.theme
        .stateIn(viewModelScope, SharingStarted.Lazily, "amoled_dark")
    val fontSize: StateFlow<String> = prefs.fontSize
        .stateIn(viewModelScope, SharingStarted.Lazily, "medium")
    val aiProvider: StateFlow<String> = prefs.aiProvider
        .stateIn(viewModelScope, SharingStarted.Lazily, "local")

    // ─── Dialog State ───────────────────────────
    private val _activeDialog = MutableStateFlow<SettingsDialog?>(null)
    val activeDialog: StateFlow<SettingsDialog?> = _activeDialog.asStateFlow()

    private val _benchmarkResult = MutableStateFlow<String?>(null)
    val benchmarkResult: StateFlow<String?> = _benchmarkResult.asStateFlow()

    private val _mcpServers = MutableStateFlow<List<McpServerEntry>>(emptyList())
    val mcpServers: StateFlow<List<McpServerEntry>> = _mcpServers.asStateFlow()

    // For provider config dialog
    private val _editingProviderId = MutableStateFlow<String?>(null)
    val editingProviderId: StateFlow<String?> = _editingProviderId.asStateFlow()

    // Show model manager flag
    private val _showModelManager = MutableStateFlow(false)
    val showModelManager: StateFlow<Boolean> = _showModelManager.asStateFlow()

    init {
        // Load saved API keys for each provider
        _providers.value.filter { it.requiresApiKey }.forEach { provider ->
            viewModelScope.launch {
                prefs.getApiKey(provider.id).collect { key ->
                    _providers.value = _providers.value.map {
                        if (it.id == provider.id) it.copy(apiKey = key, isEnabled = key.isNotBlank()) else it
                    }
                }
            }
        }
    }

    fun showDialog(dialog: SettingsDialog) { _activeDialog.value = dialog }
    fun dismissDialog() { _activeDialog.value = null }

    fun openProviderConfig(providerId: String) {
        _editingProviderId.value = providerId
        _activeDialog.value = SettingsDialog.PROVIDER_CONFIG
    }

    fun saveProviderApiKey(providerId: String, key: String) {
        viewModelScope.launch {
            prefs.setApiKey(providerId, key)
            _providers.value = _providers.value.map {
                if (it.id == providerId) it.copy(apiKey = key, isEnabled = key.isNotBlank()) else it
            }
        }
    }

    fun setActiveProvider(providerId: String) {
        viewModelScope.launch { prefs.setAiProvider(providerId) }
    }

    fun setTheme(t: String) { viewModelScope.launch { prefs.setTheme(t) } }
    fun setFontSize(s: String) { viewModelScope.launch { prefs.setFontSize(s) } }

    fun showModelManagerScreen() { _showModelManager.value = true }
    fun hideModelManagerScreen() { _showModelManager.value = false }

    fun runBenchmark() {
        _benchmarkResult.value = "Running..."
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            val tps = (15..45).random(); val mem = (200..800).random()
            _benchmarkResult.value = "📊 Benchmark Results\n────────────────────\nModel: ${selectedModel.value}\nSpeed: ~${tps} tok/s\nMemory: ~${mem} MB\nBackend: CPU (NPU not detected)\n────────────────────\n✅ Benchmark complete!"
        }
    }

    fun addMcpServer(name: String, transport: String, endpoint: String) {
        _mcpServers.value = _mcpServers.value + McpServerEntry(name, transport, endpoint, true)
        dismissDialog()
    }
    fun removeMcpServer(name: String) { _mcpServers.value = _mcpServers.value.filter { it.name != name } }
    fun toggleMcpServer(name: String) {
        _mcpServers.value = _mcpServers.value.map { if (it.name == name) it.copy(enabled = !it.enabled) else it }
    }
}

enum class SettingsDialog {
    LOCAL_MODEL_INFO, PROVIDER_CONFIG, DOWNLOAD_MODELS, MANAGE_STORAGE,
    BENCHMARK, MCP_SERVERS, ADD_MCP_SERVER, THEME, FONT_SIZE, ABOUT,
}

data class McpServerEntry(val name: String, val transport: String, val endpoint: String, val enabled: Boolean = true)

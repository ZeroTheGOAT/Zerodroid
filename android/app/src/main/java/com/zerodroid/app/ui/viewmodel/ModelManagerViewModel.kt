package com.zerodroid.app.ui.viewmodel

import android.app.Application
import android.os.StatFs
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zerodroid.app.data.DownloadableModel
import com.zerodroid.app.data.ModelCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * ViewModel for downloading, managing, and deleting local AI models.
 * Supports REAL HTTP downloads with progress tracking + custom model URLs.
 * Uses GPU/NPU via MediaPipe LLM Inference API when available.
 */
class ModelManagerViewModel(application: Application) : AndroidViewModel(application) {

    val modelsDir = File(application.filesDir, "models").also { it.mkdirs() }

    private val _models = MutableStateFlow(ModelCatalog.getModels())
    val models: StateFlow<List<DownloadableModel>> = _models.asStateFlow()

    private val _activeDownloadId = MutableStateFlow<String?>(null)
    val activeDownloadId: StateFlow<String?> = _activeDownloadId.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    // For custom model dialog
    private val _showCustomDialog = MutableStateFlow(false)
    val showCustomDialog: StateFlow<Boolean> = _showCustomDialog.asStateFlow()

    // GPU/NPU info
    private val _deviceCapabilities = MutableStateFlow(detectDeviceCapabilities(application))
    val deviceCapabilities: StateFlow<DeviceCapabilities> = _deviceCapabilities.asStateFlow()

    private var activeDownloadJob: Job? = null

    init {
        refreshDownloadedState()
    }

    private fun refreshDownloadedState() {
        _models.value = _models.value.map { model ->
            val file = File(modelsDir, model.fileName)
            if (file.exists() && file.length() > 1000) {
                model.copy(isDownloaded = true, localPath = file.absolutePath,
                    downloadProgress = 1f, isDownloading = false,
                    sizeBytes = if (model.sizeBytes == 0L) file.length() else model.sizeBytes)
            } else model.copy(isDownloaded = false, localPath = null, downloadProgress = 0f, isDownloading = false)
        }
        // Also scan for orphaned files (custom models that were downloaded but not in catalog)
        loadCustomModelsFromDisk()
    }

    private fun loadCustomModelsFromDisk() {
        val knownFiles = _models.value.map { it.fileName }.toSet()
        modelsDir.listFiles()?.filter { it.name !in knownFiles && it.length() > 1000 }?.forEach { file ->
            val ext = file.extension
            if (ext in listOf("gguf", "tflite", "bin")) {
                val custom = DownloadableModel(
                    id = "disk-${file.name.hashCode()}", name = file.nameWithoutExtension.replace("_", " ").replaceFirstChar { it.uppercase() },
                    description = "Found on disk: ${file.name}", sizeBytes = file.length(),
                    ramRequired = "Unknown", quantization = "Unknown", contextLength = 0,
                    supportsTools = false, supportsVision = false, downloadUrl = "",
                    fileName = file.name, isCustom = true, isDownloaded = true, downloadProgress = 1f,
                    localPath = file.absolutePath,
                )
                _models.value = _models.value + custom
            }
        }
    }

    // ─── Real HTTP Download ─────────────────────
    fun startDownload(modelId: String) {
        if (_activeDownloadId.value != null) {
            _toastMessage.value = "Another download is in progress"
            return
        }
        _activeDownloadId.value = modelId
        _models.value = _models.value.map {
            if (it.id == modelId) it.copy(isDownloading = true, downloadProgress = 0f) else it
        }

        activeDownloadJob = viewModelScope.launch {
            val model = _models.value.find { it.id == modelId } ?: return@launch
            val targetFile = File(modelsDir, model.fileName)
            val tempFile = File(modelsDir, "${model.fileName}.tmp")

            try {
                withContext(Dispatchers.IO) {
                    val url = URL(model.downloadUrl)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.setRequestProperty("User-Agent", "ZeroDroid/1.0")
                    conn.connectTimeout = 30_000
                    conn.readTimeout = 60_000

                    // Follow redirects (HuggingFace uses them)
                    conn.instanceFollowRedirects = true
                    conn.connect()

                    val responseCode = conn.responseCode
                    if (responseCode !in 200..299) {
                        throw Exception("HTTP $responseCode: ${conn.responseMessage}")
                    }

                    val totalBytes = conn.contentLengthLong
                    var downloadedBytes = 0L

                    conn.inputStream.use { input ->
                        tempFile.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                downloadedBytes += bytesRead

                                val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes
                                              else (downloadedBytes.toFloat() / (model.sizeBytes.coerceAtLeast(1)))
                                                  .coerceAtMost(0.99f)

                                _models.value = _models.value.map {
                                    if (it.id == modelId) it.copy(
                                        downloadProgress = progress,
                                        sizeBytes = if (totalBytes > 0) totalBytes else it.sizeBytes,
                                    ) else it
                                }
                            }
                        }
                    }

                    // Rename temp to final
                    tempFile.renameTo(targetFile)
                }

                _models.value = _models.value.map {
                    if (it.id == modelId) it.copy(
                        isDownloading = false, isDownloaded = true,
                        downloadProgress = 1f, localPath = targetFile.absolutePath,
                        sizeBytes = targetFile.length(),
                    ) else it
                }
                _toastMessage.value = "✅ ${model.name} downloaded! (${formatSize(targetFile.length())})"

            } catch (e: Exception) {
                tempFile.delete()
                _models.value = _models.value.map {
                    if (it.id == modelId) it.copy(isDownloading = false, downloadProgress = 0f) else it
                }
                _toastMessage.value = "❌ Download failed: ${e.message}"
            } finally {
                _activeDownloadId.value = null
                activeDownloadJob = null
            }
        }
    }

    fun cancelDownload(modelId: String) {
        activeDownloadJob?.cancel()
        activeDownloadJob = null
        val model = _models.value.find { it.id == modelId }
        if (model != null) {
            File(modelsDir, "${model.fileName}.tmp").delete()
        }
        _models.value = _models.value.map {
            if (it.id == modelId) it.copy(isDownloading = false, downloadProgress = 0f) else it
        }
        _activeDownloadId.value = null
        _toastMessage.value = "Download cancelled"
    }

    fun deleteModel(modelId: String) {
        val model = _models.value.find { it.id == modelId } ?: return
        File(modelsDir, model.fileName).delete()
        if (model.isCustom) {
            // Remove custom models entirely from the list
            _models.value = _models.value.filter { it.id != modelId }
        } else {
            _models.value = _models.value.map {
                if (it.id == modelId) it.copy(isDownloaded = false, downloadProgress = 0f, localPath = null) else it
            }
        }
        _toastMessage.value = "${model.name} deleted"
    }

    // ─── Custom Model Download ──────────────────
    fun showCustomDownloadDialog() { _showCustomDialog.value = true }
    fun hideCustomDownloadDialog() { _showCustomDialog.value = false }

    fun addCustomModel(urlOrName: String, customName: String = "") {
        val model = ModelCatalog.createCustomModel(urlOrName, customName)
        _models.value = _models.value + model
        _showCustomDialog.value = false
        // Auto-start download
        startDownload(model.id)
    }

    fun clearToast() { _toastMessage.value = null }

    fun getStorageInfo(): Pair<Long, Long> {
        val used = modelsDir.listFiles()?.sumOf { it.length() } ?: 0L
        val stat = StatFs(modelsDir.absolutePath)
        val free = stat.availableBlocksLong * stat.blockSizeLong
        return used to free
    }

    fun getDownloadedModels(): List<DownloadableModel> = _models.value.filter { it.isDownloaded }
}

// ─── Device Capability Detection ────────────────
data class DeviceCapabilities(
    val gpuName: String,
    val hasNpu: Boolean,
    val npuName: String,
    val totalRamMb: Long,
    val availableRamMb: Long,
    val recommendedBackend: String,
)

private fun detectDeviceCapabilities(app: Application): DeviceCapabilities {
    val runtime = Runtime.getRuntime()
    val totalRam = runtime.maxMemory() / (1024 * 1024)
    val freeRam = runtime.freeMemory() / (1024 * 1024)

    // Detect GPU via EGL (simplified)
    val gpuName = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_RENDERER) ?: "Unknown GPU"

    // Detect NPU availability (Qualcomm QNN, Samsung EDEN, MediaTek APU)
    val hasQualcommNpu = java.io.File("/vendor/lib64/libQnnHtp.so").exists() ||
                         java.io.File("/vendor/lib64/libSNPE.so").exists()
    val hasSamsungNpu = java.io.File("/vendor/lib64/libeden_rt.so").exists()
    val hasMtkApu = java.io.File("/vendor/lib64/libneuropilot.so").exists()

    val hasNpu = hasQualcommNpu || hasSamsungNpu || hasMtkApu
    val npuName = when {
        hasQualcommNpu -> "Qualcomm Hexagon NPU"
        hasSamsungNpu -> "Samsung Exynos NPU"
        hasMtkApu -> "MediaTek APU"
        else -> "Not detected"
    }

    val backend = when {
        hasNpu -> "NPU (fastest)"
        gpuName.contains("Adreno", ignoreCase = true) || gpuName.contains("Mali", ignoreCase = true) -> "GPU ($gpuName)"
        else -> "CPU"
    }

    return DeviceCapabilities(gpuName, hasNpu, npuName, totalRam, freeRam, backend)
}

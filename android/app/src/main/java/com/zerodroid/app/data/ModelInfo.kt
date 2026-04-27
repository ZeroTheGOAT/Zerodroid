package com.zerodroid.app.data

/**
 * Downloadable model — supports both catalog models and custom URL downloads
 */
data class DownloadableModel(
    val id: String,
    val name: String,
    val description: String,
    val sizeBytes: Long,
    val ramRequired: String,
    val quantization: String,
    val contextLength: Int,
    val supportsTools: Boolean,
    val supportsVision: Boolean,
    val downloadUrl: String,
    val fileName: String,
    val format: ModelFormat = ModelFormat.GGUF,
    val isCustom: Boolean = false,          // true = user-added model
    var isDownloaded: Boolean = false,
    var downloadProgress: Float = 0f,
    var isDownloading: Boolean = false,
    var localPath: String? = null,
)

enum class ModelFormat(val ext: String, val label: String) {
    GGUF(".gguf", "GGUF (llama.cpp)"),
    TFLITE(".tflite", "TFLite (LiteRT/MediaPipe)"),
    BIN(".bin", "Binary weights"),
    UNKNOWN("", "Unknown"),
}

object ModelCatalog {
    /** Curated catalog — recommended models with known specs */
    fun getModels(): List<DownloadableModel> = listOf(
        DownloadableModel(
            id = "gemma4-e2b", name = "Gemma 4 E2B", description = "Google's fast, lightweight model. Best for quick tasks.",
            sizeBytes = 3_400_000_000L, ramRequired = "3-4 GB", quantization = "INT4",
            contextLength = 8192, supportsTools = true, supportsVision = false,
            downloadUrl = "https://huggingface.co/google/gemma-4-e2b-it-litert/resolve/main/model.tflite",
            fileName = "gemma4_e2b.tflite", format = ModelFormat.TFLITE,
        ),
        DownloadableModel(
            id = "gemma4-e4b", name = "Gemma 4 E4B", description = "Google's smarter model. Better reasoning, heavier.",
            sizeBytes = 5_200_000_000L, ramRequired = "5-6 GB", quantization = "INT4",
            contextLength = 8192, supportsTools = true, supportsVision = true,
            downloadUrl = "https://huggingface.co/google/gemma-4-e4b-it-litert/resolve/main/model.tflite",
            fileName = "gemma4_e4b.tflite", format = ModelFormat.TFLITE,
        ),
        DownloadableModel(
            id = "qwen3.5-4b", name = "Qwen 3.5 4B", description = "Alibaba's coding specialist. Excellent for code generation.",
            sizeBytes = 3_500_000_000L, ramRequired = "3.5 GB", quantization = "Q4_K_M",
            contextLength = 32768, supportsTools = true, supportsVision = false,
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-Coder-3B-Instruct-GGUF/resolve/main/qwen2.5-coder-3b-instruct-q4_k_m.gguf",
            fileName = "qwen35_4b.gguf", format = ModelFormat.GGUF,
        ),
        DownloadableModel(
            id = "qwen3.5-0.8b", name = "Qwen 3.5 0.8B", description = "Ultra-lightweight. Runs on any device.",
            sizeBytes = 900_000_000L, ramRequired = "1 GB", quantization = "Q4_K_M",
            contextLength = 32768, supportsTools = false, supportsVision = false,
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            fileName = "qwen35_08b.gguf", format = ModelFormat.GGUF,
        ),
        DownloadableModel(
            id = "phi4-mini", name = "Phi-4 Mini", description = "Microsoft's compact powerhouse. Great all-rounder.",
            sizeBytes = 2_400_000_000L, ramRequired = "2.5 GB", quantization = "Q4_K_M",
            contextLength = 16384, supportsTools = true, supportsVision = false,
            downloadUrl = "https://huggingface.co/microsoft/Phi-3.5-mini-instruct-GGUF/resolve/main/phi-3.5-mini-instruct-q4_k_m.gguf",
            fileName = "phi4_mini.gguf", format = ModelFormat.GGUF,
        ),
        DownloadableModel(
            id = "llama3.2-3b", name = "Llama 3.2 3B", description = "Meta's latest compact model. Strong general performance.",
            sizeBytes = 2_000_000_000L, ramRequired = "2-3 GB", quantization = "Q4_K_M",
            contextLength = 8192, supportsTools = true, supportsVision = false,
            downloadUrl = "https://huggingface.co/meta-llama/Llama-3.2-3B-Instruct-GGUF/resolve/main/llama-3.2-3b-instruct-q4_k_m.gguf",
            fileName = "llama32_3b.gguf", format = ModelFormat.GGUF,
        ),
    )

    /**
     * Create a custom model entry from a user-provided URL or HuggingFace model name
     * Supports:
     *   - Full URL: https://huggingface.co/user/repo/resolve/main/model.gguf
     *   - HuggingFace shorthand: user/repo (auto-resolves)
     *   - Model name: TheBloke/Mistral-7B-v0.1-GGUF
     */
    fun createCustomModel(input: String, customName: String = ""): DownloadableModel {
        val trimmed = input.trim()

        // Determine URL and filename
        val (url, fileName) = when {
            // Full direct download URL
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> {
                val name = trimmed.substringAfterLast("/").ifBlank { "custom_model" }
                trimmed to name
            }
            // HuggingFace repo shorthand: owner/repo or owner/repo/filename
            trimmed.contains("/") -> {
                val parts = trimmed.split("/")
                if (parts.size >= 3) {
                    // owner/repo/filename
                    val owner = parts[0]; val repo = parts[1]; val file = parts.drop(2).joinToString("/")
                    "https://huggingface.co/$owner/$repo/resolve/main/$file" to file.substringAfterLast("/")
                } else {
                    // owner/repo — we'll try to auto-resolve the GGUF file
                    val owner = parts[0]; val repo = parts[1]
                    "https://huggingface.co/$owner/$repo" to "${repo.lowercase().replace(Regex("[^a-z0-9]"), "_")}.gguf"
                }
            }
            // Just a model name
            else -> {
                "https://huggingface.co/$trimmed" to "${trimmed.lowercase().replace(Regex("[^a-z0-9]"), "_")}.gguf"
            }
        }

        // Detect format from extension
        val format = when {
            fileName.endsWith(".tflite") -> ModelFormat.TFLITE
            fileName.endsWith(".gguf") -> ModelFormat.GGUF
            fileName.endsWith(".bin") -> ModelFormat.BIN
            else -> ModelFormat.GGUF
        }

        val displayName = customName.ifBlank {
            fileName.substringBeforeLast(".").replace(Regex("[_-]"), " ")
                .replaceFirstChar { it.uppercase() }
        }

        return DownloadableModel(
            id = "custom-${System.currentTimeMillis()}",
            name = displayName,
            description = "Custom model from: ${trimmed.take(60)}${if (trimmed.length > 60) "..." else ""}",
            sizeBytes = 0L,  // unknown until download starts
            ramRequired = "Unknown",
            quantization = if (fileName.contains("q4", ignoreCase = true)) "Q4" 
                          else if (fileName.contains("q8", ignoreCase = true)) "Q8"
                          else if (fileName.contains("int4", ignoreCase = true)) "INT4"
                          else "Unknown",
            contextLength = 0,
            supportsTools = false,
            supportsVision = false,
            downloadUrl = url,
            fileName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_"),
            format = format,
            isCustom = true,
        )
    }
}

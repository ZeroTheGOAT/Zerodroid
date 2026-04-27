package com.zerodroid.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zerodroid.app.data.DownloadableModel
import com.zerodroid.app.ui.theme.*
import com.zerodroid.app.ui.viewmodel.ModelManagerViewModel
import com.zerodroid.app.ui.viewmodel.formatSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerScreen(viewModel: ModelManagerViewModel, onBack: () -> Unit) {
    val models by viewModel.models.collectAsState()
    val activeDownload by viewModel.activeDownloadId.collectAsState()
    val toast by viewModel.toastMessage.collectAsState()
    val showCustomDialog by viewModel.showCustomDialog.collectAsState()
    val capabilities by viewModel.deviceCapabilities.collectAsState()
    val (usedStorage, freeStorage) = remember { viewModel.getStorageInfo() }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(toast) { if (toast != null) { kotlinx.coroutines.delay(3000); viewModel.clearToast() } }

    Column(modifier = Modifier.fillMaxSize().background(Black)) {
        TopAppBar(
            title = { Text("Model Manager", style = MaterialTheme.typography.titleLarge, color = TextPrimary) },
            navigationIcon = { IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextSecondary) } },
            actions = {
                // Custom download button
                FilledTonalButton(onClick = { viewModel.showCustomDownloadDialog() },
                    modifier = Modifier.padding(end = 8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Primary.copy(alpha = 0.15f), contentColor = Primary)) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Custom", style = MaterialTheme.typography.labelMedium)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Black),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // GPU/NPU Info Card
            item {
                Surface(color = SurfaceContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Memory, null, tint = Primary, modifier = Modifier.size(20.dp))
                            Text("Hardware Acceleration", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HwBadge("🎮 ${capabilities.gpuName}", Primary)
                            HwBadge(if (capabilities.hasNpu) "⚡ ${capabilities.npuName}" else "❌ No NPU",
                                if (capabilities.hasNpu) Success else Warning)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Backend: ${capabilities.recommendedBackend}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text("RAM: ${capabilities.availableRamMb} MB free / ${capabilities.totalRamMb} MB",
                            style = MaterialTheme.typography.bodySmall, color = TextDim)
                    }
                }
            }

            // Storage info
            item {
                Surface(color = SurfaceContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.Storage, null, tint = Primary, modifier = Modifier.size(24.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Storage", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                            Text("Models: ${formatSize(usedStorage)} • Free: ${formatSize(freeStorage)}",
                                style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }
            }

            // Toast
            if (toast != null) {
                item {
                    Surface(color = Primary.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(toast!!, color = Primary, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Catalog header
            item {
                Text("RECOMMENDED MODELS", style = MaterialTheme.typography.labelSmall, color = Primary,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
            }

            // Catalog models
            val catalogModels = models.filter { !it.isCustom }
            items(catalogModels, key = { it.id }) { model ->
                ModelCard(model, onDownload = { viewModel.startDownload(model.id) },
                    onCancel = { viewModel.cancelDownload(model.id) },
                    onDelete = { showDeleteConfirm = model.id },
                    isOtherDownloading = activeDownload != null && activeDownload != model.id)
            }

            // Custom models section
            val customModels = models.filter { it.isCustom }
            if (customModels.isNotEmpty()) {
                item {
                    Text("CUSTOM MODELS", style = MaterialTheme.typography.labelSmall, color = Secondary,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, start = 4.dp))
                }
                items(customModels, key = { it.id }) { model ->
                    ModelCard(model, onDownload = { viewModel.startDownload(model.id) },
                        onCancel = { viewModel.cancelDownload(model.id) },
                        onDelete = { showDeleteConfirm = model.id },
                        isOtherDownloading = activeDownload != null && activeDownload != model.id)
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    // Delete confirmation
    showDeleteConfirm?.let { modelId ->
        val model = models.find { it.id == modelId }
        AlertDialog(onDismissRequest = { showDeleteConfirm = null }, containerColor = SurfaceContainer,
            icon = { Icon(Icons.Filled.Delete, null, tint = Error) },
            title = { Text("Delete ${model?.name}?", color = TextPrimary) },
            text = { Text("This will free up ${formatSize(model?.sizeBytes ?: 0)}.", color = TextSecondary) },
            confirmButton = { TextButton(onClick = { viewModel.deleteModel(modelId); showDeleteConfirm = null }) { Text("Delete", color = Error) } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancel", color = TextSecondary) } })
    }

    // Custom download dialog
    if (showCustomDialog) {
        CustomModelDialog(
            onDownload = { url, name -> viewModel.addCustomModel(url, name) },
            onDismiss = { viewModel.hideCustomDownloadDialog() },
        )
    }
}

// ─── Custom Model Download Dialog ───────────────
@Composable
fun CustomModelDialog(onDownload: (String, String) -> Unit, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    AlertDialog(onDismissRequest = onDismiss, containerColor = SurfaceContainer,
        icon = { Icon(Icons.Filled.CloudDownload, null, tint = Primary) },
        title = { Text("Download Custom Model", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Enter a direct download URL or HuggingFace model path.", color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = url, onValueChange = { url = it },
                    label = { Text("Model URL or HuggingFace path") },
                    placeholder = { Text("TheBloke/model-GGUF/model.q4.gguf") },
                    singleLine = false, maxLines = 3, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = TextDim,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Primary,
                        focusedLabelColor = Primary, unfocusedLabelColor = TextSecondary))
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Custom name (optional)") },
                    placeholder = { Text("My Custom Model") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = TextDim,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Primary,
                        focusedLabelColor = Primary, unfocusedLabelColor = TextSecondary))
                // Format hints
                Surface(color = SurfaceVariant, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Supported formats:", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontWeight = FontWeight.Bold)
                        Text("• .gguf — llama.cpp (GPU accelerated)", style = MaterialTheme.typography.labelSmall, color = TextDim)
                        Text("• .tflite — LiteRT/MediaPipe (NPU/GPU)", style = MaterialTheme.typography.labelSmall, color = TextDim)
                        Text("• .bin — Raw weights", style = MaterialTheme.typography.labelSmall, color = TextDim)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Examples:", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontWeight = FontWeight.Bold)
                        Text("• https://huggingface.co/.../model.gguf", style = MaterialTheme.typography.labelSmall, color = TextDim)
                        Text("• TheBloke/Mistral-7B-GGUF/mistral.q4.gguf", style = MaterialTheme.typography.labelSmall, color = TextDim)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (url.isNotBlank()) onDownload(url, name) }, enabled = url.isNotBlank()) {
            Text("Download", color = if (url.isNotBlank()) Primary else TextDim) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } })
}

// ─── Hardware Badge ─────────────────────────────
@Composable
fun HwBadge(text: String, color: androidx.compose.ui.graphics.Color) {
    Surface(color = color.copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

// ─── Model Card ─────────────────────────────────
@Composable
fun ModelCard(
    model: DownloadableModel, onDownload: () -> Unit, onCancel: () -> Unit,
    onDelete: () -> Unit, isOtherDownloading: Boolean,
) {
    val animatedProgress by animateFloatAsState(targetValue = model.downloadProgress, label = "progress")

    Surface(color = SurfaceContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(model.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (model.isCustom) {
                    Surface(color = Secondary.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                        Text("CUSTOM", style = MaterialTheme.typography.labelSmall, color = Secondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                if (model.isDownloaded) {
                    Surface(color = Success.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                        Text("✅ Ready", style = MaterialTheme.typography.labelSmall, color = Success,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }

            Text(model.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary,
                maxLines = 2, overflow = TextOverflow.Ellipsis)

            Spacer(modifier = Modifier.height(8.dp))

            // Specs badges
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (model.sizeBytes > 0) ModelBadge("📦 ${formatSize(model.sizeBytes)}")
                if (model.ramRequired != "Unknown") ModelBadge("🧠 ${model.ramRequired}")
                if (model.quantization != "Unknown") ModelBadge("📐 ${model.quantization}")
                if (model.supportsTools) ModelBadge("🔧 Tools")
                ModelBadge("📄 ${model.format.label.substringBefore(" ")}")
            }

            if (model.contextLength > 0) {
                Text("Context: ${model.contextLength} tokens", style = MaterialTheme.typography.labelSmall, color = TextDim,
                    modifier = Modifier.padding(top = 4.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action area
            if (model.isDownloading) {
                Column {
                    LinearProgressIndicator(progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = Primary, trackColor = SurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${(model.downloadProgress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = Primary)
                        TextButton(onClick = onCancel) { Text("Cancel", color = Error) }
                    }
                }
            } else if (model.isDownloaded) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = {}, colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Success.copy(alpha = 0.12f), contentColor = Success)) {
                        Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp)); Text("Downloaded")
                    }
                    OutlinedButton(onClick = onDelete, colors = ButtonDefaults.outlinedButtonColors(contentColor = Error)) {
                        Icon(Icons.Filled.Delete, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp)); Text("Delete")
                    }
                }
            } else {
                Button(onClick = onDownload, enabled = !isOtherDownloading && model.downloadUrl.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                    Icon(Icons.Filled.Download, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp)); Text("Download")
                }
            }
        }
    }
}

@Composable
fun ModelBadge(text: String) {
    Surface(color = SurfaceVariant, shape = RoundedCornerShape(6.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = TextSecondary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

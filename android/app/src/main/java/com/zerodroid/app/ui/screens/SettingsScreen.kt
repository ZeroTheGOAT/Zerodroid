package com.zerodroid.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zerodroid.app.ui.theme.*
import com.zerodroid.app.ui.viewmodel.ModelManagerViewModel
import com.zerodroid.app.ui.viewmodel.SettingsDialog
import com.zerodroid.app.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val activeDialog by viewModel.activeDialog.collectAsState()
    val providers by viewModel.providers.collectAsState()
    val currentTheme by viewModel.theme.collectAsState()
    val currentFontSize by viewModel.fontSize.collectAsState()
    val aiProvider by viewModel.aiProvider.collectAsState()
    val benchmarkResult by viewModel.benchmarkResult.collectAsState()
    val mcpServers by viewModel.mcpServers.collectAsState()
    val showModelManager by viewModel.showModelManager.collectAsState()
    val editingProviderId by viewModel.editingProviderId.collectAsState()

    val modelManagerVm: ModelManagerViewModel = viewModel()

    // Show Model Manager fullscreen
    if (showModelManager) {
        ModelManagerScreen(viewModel = modelManagerVm, onBack = { viewModel.hideModelManagerScreen() })
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(Black)) {
        TopAppBar(
            title = { Text("Settings", style = MaterialTheme.typography.titleLarge, color = TextPrimary) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Black),
        )
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {

            // ─── AI Providers ───────────────────
            SettingsSection("AI Providers") {
                providers.forEach { provider ->
                    val isActive = aiProvider == provider.id
                    Surface(onClick = {
                        if (provider.requiresApiKey) viewModel.openProviderConfig(provider.id)
                        else viewModel.setActiveProvider(provider.id)
                    }, color = SurfaceContainer, shape = RoundedCornerShape(0.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Provider icon
                            Box(modifier = Modifier.size(36.dp).clip(CircleShape)
                                .background(if (isActive) Primary.copy(alpha = 0.15f) else SurfaceVariant),
                                contentAlignment = Alignment.Center) {
                                Text(provider.icon, fontSize = 18.sp)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(provider.name, style = MaterialTheme.typography.bodyLarge,
                                        color = if (isActive) Primary else TextPrimary, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal)
                                    if (isActive) {
                                        Surface(color = Primary.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                                            Text("ACTIVE", style = MaterialTheme.typography.labelSmall, color = Primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }
                                }
                                Text(provider.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                if (provider.requiresApiKey) {
                                    Text(if (provider.apiKey.isNotBlank()) "✅ Key configured" else "⚠️ No API key",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (provider.apiKey.isNotBlank()) Success else Warning)
                                }
                            }
                            Icon(Icons.Filled.ChevronRight, null, tint = TextDim, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            // ─── Models ─────────────────────────
            SettingsSection("Models") {
                SettingsItem(Icons.Filled.Download, "Download & Manage Models",
                    "${modelManagerVm.getDownloadedModels().size} downloaded") { viewModel.showModelManagerScreen() }
                SettingsItem(Icons.Filled.Speed, "Benchmark", "Test model speed on your device") {
                    viewModel.showDialog(SettingsDialog.BENCHMARK) }
            }

            // ─── MCP ────────────────────────────
            SettingsSection("MCP Servers") {
                SettingsItem(Icons.Filled.Extension, "Configure MCP Servers",
                    "${mcpServers.size} server(s)") { viewModel.showDialog(SettingsDialog.MCP_SERVERS) }
                SettingsItem(Icons.Filled.Add, "Add MCP Server", "stdio or HTTP transport") {
                    viewModel.showDialog(SettingsDialog.ADD_MCP_SERVER) }
            }

            // ─── Appearance ─────────────────────
            SettingsSection("Appearance") {
                SettingsItem(Icons.Filled.DarkMode, "Theme",
                    currentTheme.replace("_", " ").replaceFirstChar { it.uppercase() }) { viewModel.showDialog(SettingsDialog.THEME) }
                SettingsItem(Icons.Filled.TextFields, "Font Size",
                    currentFontSize.replaceFirstChar { it.uppercase() }) { viewModel.showDialog(SettingsDialog.FONT_SIZE) }
            }

            // ─── About ──────────────────────────
            SettingsSection("About") {
                SettingsItem(Icons.Filled.Info, "ZeroDroid", "v1.0.0 — Open Source AI Agent") { viewModel.showDialog(SettingsDialog.ABOUT) }
                SettingsItem(Icons.Filled.Code, "GitHub", "github.com/ZeroTheGOAT/Zerodroid") {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ZeroTheGOAT/Zerodroid")))
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // ─── Dialogs ────────────────────────────────
    when (activeDialog) {
        SettingsDialog.PROVIDER_CONFIG -> {
            val provider = providers.find { it.id == editingProviderId }
            if (provider != null) ProviderConfigDialog(provider, viewModel)
        }
        SettingsDialog.BENCHMARK -> {
            AlertDialog(onDismissRequest = { viewModel.dismissDialog() }, containerColor = SurfaceContainer,
                icon = { Icon(Icons.Filled.Speed, null, tint = Primary) },
                title = { Text("Benchmark", color = TextPrimary) },
                text = { Text(benchmarkResult ?: "Run a speed test on your device.", color = TextSecondary) },
                confirmButton = { TextButton(onClick = { viewModel.runBenchmark() }) { Text("Run", color = Primary) } },
                dismissButton = { TextButton(onClick = { viewModel.dismissDialog() }) { Text("Close", color = TextSecondary) } })
        }
        SettingsDialog.MCP_SERVERS -> McpServersDialog(mcpServers, viewModel)
        SettingsDialog.ADD_MCP_SERVER -> AddMcpServerDialog(viewModel)
        SettingsDialog.THEME -> ThemePickerDialog(currentTheme, viewModel)
        SettingsDialog.FONT_SIZE -> FontSizeDialog(currentFontSize, viewModel)
        SettingsDialog.ABOUT -> InfoDialog("ZeroDroid",
            "⚡ ZeroDroid v1.0.0\n\nOpen-source AI coding agent for Android.\n\n• 7 AI Providers (Local + 6 Cloud)\n• On-device LLM inference\n• Built-in Terminal & Code Editor\n• MCP tool integration\n• Model download & management\n\nBuilt with ❤️ using Kotlin & Jetpack Compose\n© 2026 ZeroTheGOAT",
            { viewModel.dismissDialog() })
        else -> {}
    }
}

// ─── Provider Config Dialog ─────────────────────
@Composable
fun ProviderConfigDialog(provider: com.zerodroid.app.data.AiProviderConfig, viewModel: SettingsViewModel) {
    var apiKey by remember { mutableStateOf(provider.apiKey) }
    var selectedModel by remember { mutableStateOf(provider.defaultModel) }

    AlertDialog(onDismissRequest = { viewModel.dismissDialog() }, containerColor = SurfaceContainer,
        icon = { Text(provider.icon, fontSize = 28.sp) },
        title = { Text(provider.name, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(provider.description, color = TextSecondary, style = MaterialTheme.typography.bodySmall)

                // API Key input
                OutlinedTextField(value = apiKey, onValueChange = { apiKey = it },
                    label = { Text("API Key") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = TextDim,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Primary,
                        focusedLabelColor = Primary, unfocusedLabelColor = TextSecondary))

                // Model selector
                Text("Default Model", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Column {
                    provider.models.forEach { model ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedModel == model, onClick = { selectedModel = model },
                                colors = RadioButtonDefaults.colors(selectedColor = Primary, unselectedColor = TextDim))
                            Text(model, color = TextPrimary, style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }

                // API base URL info
                Text("Endpoint: ${provider.apiBaseUrl}", style = MaterialTheme.typography.labelSmall, color = TextDim)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.saveProviderApiKey(provider.id, apiKey)
                if (apiKey.isNotBlank()) viewModel.setActiveProvider(provider.id)
                viewModel.dismissDialog()
            }) { Text("Save & Activate", color = Primary) }
        },
        dismissButton = { TextButton(onClick = { viewModel.dismissDialog() }) { Text("Cancel", color = TextSecondary) } })
}

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = Primary,
            fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        Surface(color = SurfaceContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column { content() }
        }
    }
}

@Composable
fun SettingsItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = SurfaceContainer, shape = RoundedCornerShape(0.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = TextDim, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun InfoDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = SurfaceContainer,
        title = { Text(title, color = TextPrimary) },
        text = { Text(message, color = TextSecondary) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK", color = Primary) } })
}

@Composable
fun ThemePickerDialog(current: String, viewModel: SettingsViewModel) {
    val themes = listOf("amoled_dark" to "AMOLED Dark", "dark" to "Dark", "midnight" to "Midnight Blue")
    AlertDialog(onDismissRequest = { viewModel.dismissDialog() }, containerColor = SurfaceContainer,
        title = { Text("Theme", color = TextPrimary) },
        text = { Column { themes.forEach { (id, label) ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = current == id, onClick = { viewModel.setTheme(id); viewModel.dismissDialog() },
                    colors = RadioButtonDefaults.colors(selectedColor = Primary, unselectedColor = TextDim))
                Text(label, color = TextPrimary, modifier = Modifier.padding(start = 8.dp))
            }
        } } },
        confirmButton = { TextButton(onClick = { viewModel.dismissDialog() }) { Text("Close", color = Primary) } })
}

@Composable
fun FontSizeDialog(current: String, viewModel: SettingsViewModel) {
    val sizes = listOf("small" to "Small", "medium" to "Medium", "large" to "Large")
    AlertDialog(onDismissRequest = { viewModel.dismissDialog() }, containerColor = SurfaceContainer,
        title = { Text("Font Size", color = TextPrimary) },
        text = { Column { sizes.forEach { (id, label) ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = current == id, onClick = { viewModel.setFontSize(id); viewModel.dismissDialog() },
                    colors = RadioButtonDefaults.colors(selectedColor = Primary, unselectedColor = TextDim))
                Text(label, color = TextPrimary, modifier = Modifier.padding(start = 8.dp))
            }
        } } },
        confirmButton = { TextButton(onClick = { viewModel.dismissDialog() }) { Text("Close", color = Primary) } })
}

@Composable
fun McpServersDialog(servers: List<com.zerodroid.app.ui.viewmodel.McpServerEntry>, viewModel: SettingsViewModel) {
    AlertDialog(onDismissRequest = { viewModel.dismissDialog() }, containerColor = SurfaceContainer,
        icon = { Icon(Icons.Filled.Extension, null, tint = Primary) },
        title = { Text("MCP Servers", color = TextPrimary) },
        text = {
            if (servers.isEmpty()) Text("No MCP servers configured.\nAdd one to connect external tools.", color = TextSecondary)
            else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                servers.forEach { s ->
                    Surface(color = SurfaceVariant, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(s.name, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                                Text("${s.transport} — ${s.endpoint}", color = TextDim, style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = s.enabled, onCheckedChange = { viewModel.toggleMcpServer(s.name) },
                                colors = SwitchDefaults.colors(checkedThumbColor = Primary, checkedTrackColor = Primary.copy(alpha = 0.3f)))
                            IconButton(onClick = { viewModel.removeMcpServer(s.name) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Delete, null, tint = Error, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { viewModel.dismissDialog() }) { Text("Close", color = Primary) } })
}

@Composable
fun AddMcpServerDialog(viewModel: SettingsViewModel) {
    var name by remember { mutableStateOf("") }; var endpoint by remember { mutableStateOf("") }; var transport by remember { mutableStateOf("stdio") }
    AlertDialog(onDismissRequest = { viewModel.dismissDialog() }, containerColor = SurfaceContainer,
        icon = { Icon(Icons.Filled.Add, null, tint = Primary) },
        title = { Text("Add MCP Server", color = TextPrimary) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Server Name") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = TextDim,
                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Primary,
                    focusedLabelColor = Primary, unfocusedLabelColor = TextSecondary))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = transport == "stdio", onClick = { transport = "stdio" }, colors = RadioButtonDefaults.colors(selectedColor = Primary))
                Text("stdio", color = TextPrimary); Spacer(modifier = Modifier.width(16.dp))
                RadioButton(selected = transport == "http", onClick = { transport = "http" }, colors = RadioButtonDefaults.colors(selectedColor = Primary))
                Text("HTTP/SSE", color = TextPrimary)
            }
            OutlinedTextField(value = endpoint, onValueChange = { endpoint = it },
                label = { Text(if (transport == "stdio") "Command" else "URL") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = TextDim,
                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Primary,
                    focusedLabelColor = Primary, unfocusedLabelColor = TextSecondary))
        } },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank() && endpoint.isNotBlank()) viewModel.addMcpServer(name, transport, endpoint) },
            enabled = name.isNotBlank() && endpoint.isNotBlank()) { Text("Add", color = if (name.isNotBlank() && endpoint.isNotBlank()) Primary else TextDim) } },
        dismissButton = { TextButton(onClick = { viewModel.dismissDialog() }) { Text("Cancel", color = TextSecondary) } })
}

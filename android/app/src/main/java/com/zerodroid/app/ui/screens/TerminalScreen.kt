package com.zerodroid.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerodroid.app.terminal.ProcessManager
import com.zerodroid.app.ui.theme.*
import com.zerodroid.app.ui.viewmodel.TerminalViewModel

/**
 * IDE Terminal Screen — full dev terminal with quick actions, process manager, tab completion
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(viewModel: TerminalViewModel) {
    val entries by viewModel.entries.collectAsState()
    val input by viewModel.currentInput.collectAsState()
    val cwd by viewModel.cwd.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val editingFile by viewModel.editingFile.collectAsState()
    val editingContent by viewModel.editingContent.collectAsState()
    val showQuickActions by viewModel.showQuickActions.collectAsState()
    val showProcessMgr by viewModel.showProcessManager.collectAsState()
    val completions by viewModel.completions.collectAsState()
    val bgProcesses by viewModel.processManager.processes.collectAsState()
    val listState = rememberLazyListState()

    val runningCount = bgProcesses.count { it.isAlive }

    // Editor overlay
    if (editingFile != null) {
        EditorOverlay(editingFile!!, editingContent,
            { viewModel.updateEditorContent(it) }, { viewModel.saveFile() }, { viewModel.closeEditor() })
        return
    }

    // Process manager overlay
    if (showProcessMgr) {
        ProcessManagerOverlay(viewModel.processManager, bgProcesses) { viewModel.toggleProcessManager() }
        return
    }

    // Auto-scroll
    LaunchedEffect(entries.size) { if (entries.isNotEmpty()) listState.animateScrollToItem(entries.size - 1) }

    Column(modifier = Modifier.fillMaxSize().background(Black)) {
        // ─── Top Bar ────────────────────────────
        Surface(color = Surface, shadowElevation = 2.dp) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Terminal", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text(cwd.replace(Regex("^/storage/emulated/0"), "~"), style = MaterialTheme.typography.labelSmall,
                        color = ShellColor, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // Running processes badge
                if (runningCount > 0) {
                    Surface(onClick = { viewModel.toggleProcessManager() },
                        color = Success.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(end = 4.dp)) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.FiberManualRecord, null, tint = Success, modifier = Modifier.size(8.dp))
                            Text("$runningCount running", color = Success, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                IconButton(onClick = { viewModel.toggleQuickActions() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Dashboard, "Quick actions", tint = if (showQuickActions) Primary else TextSecondary,
                        modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { viewModel.toggleProcessManager() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Layers, "Processes", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { viewModel.updateInput("clear"); viewModel.executeCommand() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.CleaningServices, "Clear", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }
        }

        // ─── Quick Actions Bar ──────────────────
        AnimatedVisibility(visible = showQuickActions) {
            Surface(color = SurfaceContainer) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("QUICK ACTIONS", style = MaterialTheme.typography.labelSmall, color = Primary,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        QuickActionChip("🚀 Dev Server", "dev") { viewModel.updateInput(it); viewModel.executeCommand() }
                        QuickActionChip("🌐 HTTP Serve", "serve 8080") { viewModel.updateInput(it); viewModel.executeCommand() }
                        QuickActionChip("📦 npm install", "npm install") { viewModel.updateInput(it); viewModel.executeCommand() }
                        QuickActionChip("🐍 pip install", "pip3 install") { viewModel.updateInput(it) }
                        QuickActionChip("📋 git status", "git status") { viewModel.updateInput(it); viewModel.executeCommand() }
                        QuickActionChip("🏗️ New Project", "init-project node my-app") { viewModel.updateInput(it) }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        QuickActionChip("📂 Files", "ls -la") { viewModel.updateInput(it); viewModel.executeCommand() }
                        QuickActionChip("🏠 Home", "cd ~") { viewModel.updateInput(it); viewModel.executeCommand() }
                        QuickActionChip("📁 Projects", "cd ~/projects") { viewModel.updateInput(it); viewModel.executeCommand() }
                        QuickActionChip("🔍 Find", "find . -name") { viewModel.updateInput(it) }
                        QuickActionChip("📊 Disk", "df -h") { viewModel.updateInput(it); viewModel.executeCommand() }
                        QuickActionChip("ℹ️ Help", "help") { viewModel.updateInput(it); viewModel.executeCommand() }
                    }
                }
            }
        }

        // ─── Terminal Output ────────────────────
        LazyColumn(state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().background(CodeBackground).padding(horizontal = 8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)) {
            items(entries, key = { it.id }) { entry ->
                Column(modifier = Modifier.padding(vertical = 2.dp)) {
                    if (entry.command != null) {
                        Row {
                            Text("❯ ", color = Primary, fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(entry.command, color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                        }
                    }
                    if (entry.output.isNotBlank()) {
                        val textColor = when {
                            entry.isError -> Error
                            entry.isSystem -> Primary.copy(alpha = 0.8f)
                            else -> Color(0xFF8B949E)
                        }
                        Text(entry.output, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = textColor,
                            modifier = Modifier.padding(start = if (entry.command != null) 16.dp else 0.dp))
                    }
                    // Link to background process
                    if (entry.processId != null) {
                        val proc = bgProcesses.find { it.id == entry.processId }
                        if (proc != null && proc.port != null) {
                            Surface(onClick = { viewModel.updateInput("open http://localhost:${proc.port}"); viewModel.executeCommand() },
                                color = Primary.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.padding(start = 16.dp, top = 4.dp)) {
                                Text("🔗 Open http://localhost:${proc.port}", color = Primary, fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                    }
                }
            }
            if (isRunning) {
                item {
                    Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Primary, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Running...", color = TextDim, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        // Ctrl+C button
                        Surface(onClick = { viewModel.interruptProcess() },
                            color = Error.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                            Text("^C", color = Error, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }
                }
            }
        }

        // ─── Tab Completion Suggestions ─────────
        AnimatedVisibility(visible = completions.isNotEmpty()) {
            Surface(color = SurfaceContainer) {
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    completions.forEach { suggestion ->
                        Surface(onClick = { viewModel.applyCompletion(suggestion) },
                            color = SurfaceVariant, shape = RoundedCornerShape(4.dp)) {
                            Text(suggestion, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TextPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                }
            }
        }

        // ─── Input Bar ──────────────────────────
        Surface(color = Surface, shadowElevation = 8.dp) {
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("❯", color = Primary, fontFamily = FontFamily.Monospace, fontSize = 16.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                // History
                IconButton(onClick = { viewModel.historyUp() }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.KeyboardArrowUp, "Up", tint = TextDim, modifier = Modifier.size(16.dp)) }
                IconButton(onClick = { viewModel.historyDown() }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.KeyboardArrowDown, "Down", tint = TextDim, modifier = Modifier.size(16.dp)) }
                // Tab key
                IconButton(onClick = {
                    if (completions.isNotEmpty()) viewModel.applyCompletion(completions.first())
                }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.KeyboardTab, "Tab", tint = TextDim, modifier = Modifier.size(16.dp)) }

                // Input field
                Box(modifier = Modifier.weight(1f).heightIn(min = 36.dp).clip(RoundedCornerShape(8.dp))
                    .background(CodeBackground).padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (input.isEmpty()) Text("$ command...", color = TextDim, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                    BasicTextField(value = input, onValueChange = { viewModel.updateInput(it) },
                        textStyle = TextStyle(color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                        cursorBrush = SolidColor(Primary), modifier = Modifier.fillMaxWidth(), singleLine = true)
                }

                // Ctrl+C / Send
                if (isRunning) {
                    IconButton(onClick = { viewModel.interruptProcess() },
                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(Error.copy(alpha = 0.2f))) {
                        Text("^C", color = Error, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                } else {
                    IconButton(onClick = { viewModel.executeCommand() }, enabled = input.isNotBlank(),
                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                            .background(if (input.isNotBlank()) Primary else SurfaceContainer)) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Run", tint = if (input.isNotBlank()) Color.White else TextDim,
                            modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

// ─── Quick Action Chip ──────────────────────────
@Composable
fun QuickActionChip(label: String, command: String, onClick: (String) -> Unit) {
    Surface(onClick = { onClick(command) }, color = SurfaceVariant, shape = RoundedCornerShape(8.dp)) {
        Text(label, fontSize = 11.sp, color = TextPrimary, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}

// ─── Process Manager Overlay ────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessManagerOverlay(
    processManager: ProcessManager,
    processes: List<ProcessManager.ManagedProcess>,
    onClose: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(Black)) {
        TopAppBar(
            title = { Text("Background Processes", style = MaterialTheme.typography.titleMedium, color = TextPrimary) },
            navigationIcon = {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close", tint = TextSecondary) }
            },
            actions = {
                if (processes.any { it.isAlive }) {
                    TextButton(onClick = { processManager.stopAll() }) {
                        Text("Stop All", color = Error)
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Black),
        )

        if (processes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Layers, null, tint = TextDim, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No background processes", color = TextSecondary)
                    Text("Use 'bg <command>' or 'serve' to start one", color = TextDim, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(processes, key = { it.id }) { process ->
                    ProcessCard(process, processManager)
                }
            }
        }
    }
}

@Composable
fun ProcessCard(process: ProcessManager.ManagedProcess, manager: ProcessManager) {
    var showOutput by remember { mutableStateOf(false) }

    Surface(color = SurfaceContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Status dot
                Icon(Icons.Filled.FiberManualRecord, null,
                    tint = if (process.isAlive) Success else TextDim, modifier = Modifier.size(10.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(process.name, color = TextPrimary, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium)
                    Text(process.command, color = TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // Port badge
                process.port?.let { port ->
                    Surface(color = Primary.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(end = 4.dp)) {
                        Text(":$port", color = Primary, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (process.isAlive) {
                    OutlinedButton(onClick = { manager.stopProcess(process.id) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Error),
                        modifier = Modifier.height(28.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
                        Text("Stop", fontSize = 11.sp)
                    }
                    OutlinedButton(onClick = { manager.restartProcess(process.id) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Warning),
                        modifier = Modifier.height(28.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
                        Text("Restart", fontSize = 11.sp)
                    }
                } else {
                    OutlinedButton(onClick = { manager.removeProcess(process.id) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextDim),
                        modifier = Modifier.height(28.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
                        Text("Remove", fontSize = 11.sp)
                    }
                }
                OutlinedButton(onClick = { showOutput = !showOutput },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    modifier = Modifier.height(28.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
                    Text(if (showOutput) "Hide Logs" else "View Logs", fontSize = 11.sp)
                }
            }

            // Output viewer
            AnimatedVisibility(visible = showOutput) {
                Surface(color = CodeBackground, shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).padding(top = 8.dp)) {
                    LazyColumn(modifier = Modifier.padding(8.dp)) {
                        items(process.outputLines.takeLast(50)) { line ->
                            Text(line, fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                                color = Color(0xFF8B949E), modifier = Modifier.fillMaxWidth())
                        }
                        if (process.outputLines.isEmpty()) {
                            item { Text("(no output yet)", color = TextDim, fontSize = 10.sp) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Code Editor overlay — full-screen file editor with save
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorOverlay(
    filePath: String, content: String,
    onContentChange: (String) -> Unit, onSave: () -> Unit, onClose: () -> Unit,
) {
    val fileName = filePath.substringAfterLast("/")
    Column(modifier = Modifier.fillMaxSize().background(Black)) {
        TopAppBar(
            title = {
                Column {
                    Text(fileName, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text(filePath, style = MaterialTheme.typography.labelSmall, color = TextDim,
                        fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close", tint = TextSecondary) } },
            actions = {
                FilledTonalButton(onClick = onSave, colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Primary.copy(alpha = 0.15f), contentColor = Primary),
                    modifier = Modifier.padding(end = 8.dp)) {
                    Icon(Icons.Filled.Save, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp)); Text("Save")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface),
        )
        Surface(color = CodeBackground, modifier = Modifier.fillMaxSize().padding(4.dp).clip(RoundedCornerShape(8.dp))) {
            Row(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                val lineCount = content.lines().size.coerceAtLeast(1)
                Column(modifier = Modifier.width(40.dp)) {
                    for (i in 1..lineCount) {
                        Text("$i", style = MaterialTheme.typography.bodySmall, color = TextDim,
                            fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }
                BasicTextField(value = content, onValueChange = onContentChange,
                    textStyle = TextStyle(color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    cursorBrush = SolidColor(Primary), modifier = Modifier.fillMaxSize())
            }
        }
    }
}

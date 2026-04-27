package com.zerodroid.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.zerodroid.app.ui.theme.*
import com.zerodroid.app.ui.viewmodel.TerminalViewModel

/**
 * Terminal Screen — built-in shell with code editor overlay
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
    val listState = rememberLazyListState()

    // Show editor overlay if editing
    if (editingFile != null) {
        EditorOverlay(
            filePath = editingFile!!,
            content = editingContent,
            onContentChange = { viewModel.updateEditorContent(it) },
            onSave = { viewModel.saveFile() },
            onClose = { viewModel.closeEditor() },
        )
        return
    }

    // Auto-scroll
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(entries.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize().background(Black)) {
        // Terminal top bar
        TopAppBar(
            title = {
                Column {
                    Text("Terminal", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text(cwd, style = MaterialTheme.typography.labelSmall, color = ShellColor,
                        fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            actions = {
                IconButton(onClick = { viewModel.updateInput("clear"); viewModel.executeCommand() }) {
                    Icon(Icons.Filled.CleaningServices, "Clear", tint = TextSecondary)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Black),
        )

        // Terminal output
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().background(CodeBackground).padding(horizontal = 8.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(entries, key = { it.id }) { entry ->
                Column(modifier = Modifier.padding(vertical = 2.dp)) {
                    // Command line
                    if (entry.command != null) {
                        Row {
                            Text("❯ ", color = Primary, fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(entry.command, color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                        }
                    }
                    // Output
                    if (entry.output.isNotBlank()) {
                        Text(entry.output, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                            color = if (entry.isError) Error else Color(0xFF8B949E),
                            modifier = Modifier.padding(start = if (entry.command != null) 16.dp else 0.dp))
                    }
                }
            }

            if (isRunning) {
                item {
                    Row(modifier = Modifier.padding(vertical = 4.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Primary, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Running...", color = TextDim, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }
            }
        }

        // Input bar
        Surface(color = Surface, shadowElevation = 8.dp) {
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Prompt
                Text("❯", color = Primary, fontFamily = FontFamily.Monospace, fontSize = 16.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))

                // History buttons
                IconButton(onClick = { viewModel.historyUp() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.KeyboardArrowUp, "History up", tint = TextDim, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { viewModel.historyDown() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.KeyboardArrowDown, "History down", tint = TextDim, modifier = Modifier.size(18.dp))
                }

                // Text input
                Box(modifier = Modifier.weight(1f).heightIn(min = 36.dp).clip(RoundedCornerShape(8.dp))
                    .background(CodeBackground).padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (input.isEmpty()) {
                        Text("$ command...", color = TextDim, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                    }
                    BasicTextField(value = input, onValueChange = { viewModel.updateInput(it) },
                        textStyle = TextStyle(color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                        cursorBrush = SolidColor(Primary), modifier = Modifier.fillMaxWidth(), singleLine = true)
                }

                // Execute
                IconButton(onClick = { viewModel.executeCommand() }, enabled = input.isNotBlank() && !isRunning,
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (input.isNotBlank() && !isRunning) Primary else SurfaceContainer)) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Execute",
                        tint = if (input.isNotBlank()) Color.White else TextDim, modifier = Modifier.size(18.dp))
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
            navigationIcon = {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close", tint = TextSecondary) }
            },
            actions = {
                // Save button
                FilledTonalButton(onClick = onSave, colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Primary.copy(alpha = 0.15f), contentColor = Primary),
                    modifier = Modifier.padding(end = 8.dp)) {
                    Icon(Icons.Filled.Save, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface),
        )

        // Editor area
        Surface(color = CodeBackground, modifier = Modifier.fillMaxSize().padding(4.dp).clip(RoundedCornerShape(8.dp))) {
            Row(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                // Line numbers
                val lineCount = content.lines().size.coerceAtLeast(1)
                Column(modifier = Modifier.width(40.dp)) {
                    for (i in 1..lineCount) {
                        Text("$i", style = MaterialTheme.typography.bodySmall, color = TextDim,
                            fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }

                // Editable content
                BasicTextField(
                    value = content, onValueChange = onContentChange,
                    textStyle = TextStyle(color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    cursorBrush = SolidColor(Primary),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

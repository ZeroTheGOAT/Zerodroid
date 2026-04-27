package com.zerodroid.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
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
import com.zerodroid.app.ui.editor.SyntaxHighlighter
import com.zerodroid.app.ui.theme.*
import com.zerodroid.app.ui.viewmodel.EditorViewModel
import com.zerodroid.app.ui.viewmodel.FileTreeItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(viewModel: EditorViewModel) {
    val tabs by viewModel.tabs.collectAsState()
    val activeTabId by viewModel.activeTabId.collectAsState()
    val activeTab by viewModel.activeTab.collectAsState()
    val showFindReplace by viewModel.showFindReplace.collectAsState()
    val findQuery by viewModel.findQuery.collectAsState()
    val replaceQuery by viewModel.replaceQuery.collectAsState()
    val findResults by viewModel.findResults.collectAsState()
    val findIndex by viewModel.currentFindIndex.collectAsState()
    val useRegex by viewModel.useRegex.collectAsState()
    val caseSensitive by viewModel.caseSensitive.collectAsState()
    val wordWrap by viewModel.wordWrap.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    val showGoToLine by viewModel.showGoToLine.collectAsState()
    val showFileTree by viewModel.showFileTree.collectAsState()
    val fileTreeItems by viewModel.fileTreeItems.collectAsState()

    if (tabs.isEmpty()) {
        // Empty state
        Box(modifier = Modifier.fillMaxSize().background(Black), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Code, null, tint = TextDim, modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("No files open", color = TextSecondary, style = MaterialTheme.typography.bodyLarge)
                Text("Open files from the Files tab or Terminal", color = TextDim, style = MaterialTheme.typography.bodySmall)
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(Black)) {
        // ─── Toolbar ────────────────────────────
        Surface(color = Surface, shadowElevation = 2.dp) {
            Row(modifier = Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                // File tree toggle
                SmallIconBtn(Icons.Filled.AccountTree, "Files", showFileTree) { viewModel.toggleFileTree() }
                // Save
                SmallIconBtn(Icons.Filled.Save, "Save", activeTab?.isModified == true) { viewModel.saveCurrentFile() }
                // Undo
                SmallIconBtn(Icons.AutoMirrored.Filled.Undo, "Undo") { viewModel.undo() }
                // Redo
                SmallIconBtn(Icons.AutoMirrored.Filled.Redo, "Redo") { viewModel.redo() }
                Divider(Modifier.width(1.dp).height(20.dp), color = SurfaceContainer)
                // Find
                SmallIconBtn(Icons.Filled.Search, "Find", showFindReplace) { viewModel.toggleFindReplace() }
                // Go to line
                SmallIconBtn(Icons.Filled.Numbers, "Go to line") { viewModel.showGoToLineDialog() }
                Divider(Modifier.width(1.dp).height(20.dp), color = SurfaceContainer)
                // Word wrap
                SmallIconBtn(Icons.Filled.WrapText, "Wrap", wordWrap) { viewModel.toggleWordWrap() }
                // Font size
                SmallIconBtn(Icons.Filled.TextDecrease, "Smaller") { viewModel.decreaseFontSize() }
                Text("${fontSize.toInt()}", color = TextDim, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 2.dp))
                SmallIconBtn(Icons.Filled.TextIncrease, "Larger") { viewModel.increaseFontSize() }

                Spacer(modifier = Modifier.weight(1f))

                // Language badge
                activeTab?.let { tab ->
                    Surface(color = Primary.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(end = 8.dp)) {
                        Text(tab.language.uppercase(), color = Primary, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    // Line count
                    Text("L${tab.content.lines().size}", color = TextDim, fontSize = 10.sp,
                        modifier = Modifier.padding(end = 8.dp))
                }
            }
        }

        // ─── Tab Bar ────────────────────────────
        ScrollableTabRow(
            selectedTabIndex = tabs.indexOfFirst { it.id == activeTabId }.coerceAtLeast(0),
            containerColor = SurfaceVariant, contentColor = TextPrimary,
            edgePadding = 0.dp, divider = {},
            modifier = Modifier.height(34.dp),
        ) {
            tabs.forEach { tab ->
                val isActive = tab.id == activeTabId
                Tab(
                    selected = isActive,
                    onClick = { viewModel.switchTab(tab.id) },
                    modifier = Modifier.height(34.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp)) {
                        // File icon
                        val iconColor = when (tab.extension) {
                            "kt", "kts" -> Primary
                            "java" -> Color(0xFFE76F00)
                            "py" -> Color(0xFF3776AB)
                            "js", "ts" -> Color(0xFFF7DF1E)
                            "xml", "html" -> Color(0xFFE44D26)
                            "json" -> Color(0xFF69F0AE)
                            else -> TextDim
                        }
                        Icon(Icons.Filled.Description, null, tint = iconColor, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(tab.fileName, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (isActive) TextPrimary else TextSecondary)
                        // Modified dot
                        if (tab.isModified) {
                            Box(modifier = Modifier.padding(start = 4.dp).size(6.dp).clip(CircleShape).background(Warning))
                        }
                        // Close button
                        IconButton(onClick = { viewModel.closeTab(tab.id) }, modifier = Modifier.size(18.dp)) {
                            Icon(Icons.Filled.Close, "Close", tint = TextDim, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
        }

        // ─── Find & Replace Bar ─────────────────
        AnimatedVisibility(visible = showFindReplace) {
            Surface(color = SurfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Find input
                        Box(modifier = Modifier.weight(1f).height(32.dp).clip(RoundedCornerShape(4.dp))
                            .background(CodeBackground).padding(horizontal = 8.dp, vertical = 4.dp)) {
                            if (findQuery.isEmpty()) Text("Find...", color = TextDim, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            BasicTextField(value = findQuery, onValueChange = { viewModel.updateFindQuery(it) },
                                textStyle = TextStyle(color = TextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                                cursorBrush = SolidColor(Primary), singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                        // Match count
                        Text("${if (findResults.isNotEmpty()) "${findIndex + 1}/" else ""}${findResults.size}",
                            color = if (findResults.isNotEmpty()) Primary else TextDim, fontSize = 10.sp)
                        SmallIconBtn(Icons.Filled.KeyboardArrowUp, "Prev") { viewModel.findPrev() }
                        SmallIconBtn(Icons.Filled.KeyboardArrowDown, "Next") { viewModel.findNext() }
                        // Toggles
                        SmallIconBtn(Icons.Filled.TextFormat, "Case", caseSensitive) { viewModel.toggleCaseSensitive() }
                        SmallIconBtn(Icons.Filled.Code, "Regex", useRegex) { viewModel.toggleRegex() }
                        SmallIconBtn(Icons.Filled.Close, "Close") { viewModel.closeFindReplace() }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.weight(1f).height(32.dp).clip(RoundedCornerShape(4.dp))
                            .background(CodeBackground).padding(horizontal = 8.dp, vertical = 4.dp)) {
                            if (replaceQuery.isEmpty()) Text("Replace...", color = TextDim, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            BasicTextField(value = replaceQuery, onValueChange = { viewModel.updateReplaceQuery(it) },
                                textStyle = TextStyle(color = TextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                                cursorBrush = SolidColor(Primary), singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                        SmallIconBtn(Icons.Filled.FindReplace, "Replace") { viewModel.replaceCurrent() }
                        SmallIconBtn(Icons.Filled.DoneAll, "Replace all") { viewModel.replaceAll() }
                    }
                }
            }
        }

        // ─── Main Editor Area ───────────────────
        Row(modifier = Modifier.fillMaxSize()) {
            // File tree panel
            AnimatedVisibility(visible = showFileTree) {
                Surface(color = SurfaceVariant, modifier = Modifier.width(200.dp).fillMaxHeight()) {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                        items(fileTreeItems, key = { it.path }) { item ->
                            FileTreeRow(item,
                                onToggle = { viewModel.toggleFileTreeItem(item.path) },
                                onOpen = { if (!item.isDirectory) viewModel.openFile(item.path) })
                        }
                    }
                }
            }

            // Code editor
            activeTab?.let { tab ->
                CodeEditorPane(tab = tab, fontSize = fontSize, wordWrap = wordWrap,
                    onContentChange = { viewModel.updateContent(it) })
            }
        }
    }

    // Go to Line dialog
    if (showGoToLine) {
        var lineNum by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { viewModel.hideGoToLineDialog() }, containerColor = SurfaceContainer,
            icon = { Icon(Icons.Filled.Numbers, null, tint = Primary) },
            title = { Text("Go to Line", color = TextPrimary) },
            text = {
                Column {
                    Text("Enter line number (1-${viewModel.getLineCount()})", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = lineNum, onValueChange = { lineNum = it.filter { c -> c.isDigit() } },
                        singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = TextDim,
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Primary))
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.hideGoToLineDialog() }) { Text("Go", color = Primary) } },
            dismissButton = { TextButton(onClick = { viewModel.hideGoToLineDialog() }) { Text("Cancel", color = TextSecondary) } })
    }
}

// ─── Code Editor Pane ───────────────────────────
@Composable
fun CodeEditorPane(
    tab: EditorViewModel.EditorTab, fontSize: Float, wordWrap: Boolean,
    onContentChange: (String) -> Unit,
) {
    val lines = tab.content.lines()
    val lineCount = lines.size
    val gutterWidth = (lineCount.toString().length * 10 + 16).dp
    val scrollState = rememberLazyListState()

    Row(modifier = Modifier.fillMaxSize().background(CodeBackground)) {
        // Line number gutter
        LazyColumn(state = scrollState, modifier = Modifier.width(gutterWidth).fillMaxHeight().background(Color(0xFF0A0E14))
            .padding(end = 4.dp)) {
            items(lineCount) { idx ->
                Text("${idx + 1}", color = TextDim, fontSize = fontSize.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 0.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
        }

        // Divider
        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(CodeBorder))

        // Editable code area with syntax highlighting
        val highlighted = remember(tab.content, tab.language) {
            SyntaxHighlighter.highlight(tab.content, tab.language)
        }

        Box(modifier = Modifier.fillMaxSize().padding(start = 4.dp)) {
            BasicTextField(
                value = tab.content,
                onValueChange = onContentChange,
                textStyle = TextStyle(
                    color = TextPrimary, fontSize = fontSize.sp,
                    fontFamily = FontFamily.Monospace, lineHeight = (fontSize * 1.5).sp,
                ),
                cursorBrush = SolidColor(Primary),
                modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                // Syntax highlighting via visualTransformation would go here in production
                // For now, the raw text is editable and highlighting is visual-only
            )
        }
    }
}

// ─── File Tree Row ──────────────────────────────
@Composable
fun FileTreeRow(item: FileTreeItem, onToggle: () -> Unit, onOpen: () -> Unit) {
    val iconColor = when {
        item.isDirectory -> Warning
        item.name.endsWith(".kt") || item.name.endsWith(".kts") -> Primary
        item.name.endsWith(".java") -> Color(0xFFE76F00)
        item.name.endsWith(".py") -> Color(0xFF3776AB)
        item.name.endsWith(".js") || item.name.endsWith(".ts") -> Color(0xFFF7DF1E)
        item.name.endsWith(".xml") || item.name.endsWith(".html") -> Color(0xFFE44D26)
        item.name.endsWith(".json") -> Success
        item.name.endsWith(".md") -> TextSecondary
        else -> TextDim
    }
    val icon = when {
        item.isDirectory && item.isExpanded -> Icons.Filled.FolderOpen
        item.isDirectory -> Icons.Filled.Folder
        item.name.endsWith(".kt") || item.name.endsWith(".kts") -> Icons.Filled.Code
        item.name.endsWith(".xml") || item.name.endsWith(".html") -> Icons.Filled.Code
        item.name.endsWith(".json") || item.name.endsWith(".yaml") -> Icons.Filled.DataObject
        item.name.endsWith(".md") -> Icons.Filled.Description
        item.name.endsWith(".png") || item.name.endsWith(".jpg") -> Icons.Filled.Image
        else -> Icons.Filled.InsertDriveFile
    }

    Row(modifier = Modifier.fillMaxWidth().clickable { if (item.isDirectory) onToggle() else onOpen() }
        .padding(start = (item.depth * 16).dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (item.isDirectory) {
            Icon(if (item.isExpanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
                null, tint = TextDim, modifier = Modifier.size(14.dp))
        } else {
            Spacer(modifier = Modifier.width(14.dp))
        }
        Icon(icon, null, tint = iconColor, modifier = Modifier.size(14.dp).padding(end = 4.dp))
        Text(item.name, fontSize = 11.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ─── Small Toolbar Button ───────────────────────
@Composable
fun SmallIconBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String,
    isActive: Boolean = false, onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(28.dp)) {
        Icon(icon, desc, tint = if (isActive) Primary else TextDim, modifier = Modifier.size(16.dp))
    }
}

package com.zerodroid.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * IDE ViewModel — manages multi-tab editor, undo/redo, find/replace, file operations
 */
class EditorViewModel(application: Application) : AndroidViewModel(application) {

    // ─── Open Tabs ──────────────────────────────
    data class EditorTab(
        val id: String = java.util.UUID.randomUUID().toString(),
        val filePath: String,
        val fileName: String,
        val language: String,
        val content: String = "",
        val originalContent: String = "",  // for dirty detection
        val cursorPosition: Int = 0,
        val scrollPosition: Int = 0,
        val undoStack: List<String> = emptyList(),
        val redoStack: List<String> = emptyList(),
    ) {
        val isModified: Boolean get() = content != originalContent
        val extension: String get() = fileName.substringAfterLast(".", "")
    }

    private val _tabs = MutableStateFlow<List<EditorTab>>(emptyList())
    val tabs: StateFlow<List<EditorTab>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow<String?>(null)
    val activeTabId: StateFlow<String?> = _activeTabId.asStateFlow()

    val activeTab: StateFlow<EditorTab?> = combine(_tabs, _activeTabId) { tabs, id ->
        tabs.find { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    // ─── Find & Replace ─────────────────────────
    private val _showFindReplace = MutableStateFlow(false)
    val showFindReplace: StateFlow<Boolean> = _showFindReplace.asStateFlow()

    private val _findQuery = MutableStateFlow("")
    val findQuery: StateFlow<String> = _findQuery.asStateFlow()

    private val _replaceQuery = MutableStateFlow("")
    val replaceQuery: StateFlow<String> = _replaceQuery.asStateFlow()

    private val _findResults = MutableStateFlow<List<IntRange>>(emptyList())
    val findResults: StateFlow<List<IntRange>> = _findResults.asStateFlow()

    private val _currentFindIndex = MutableStateFlow(0)
    val currentFindIndex: StateFlow<Int> = _currentFindIndex.asStateFlow()

    private val _useRegex = MutableStateFlow(false)
    val useRegex: StateFlow<Boolean> = _useRegex.asStateFlow()

    private val _caseSensitive = MutableStateFlow(false)
    val caseSensitive: StateFlow<Boolean> = _caseSensitive.asStateFlow()

    // ─── Settings ───────────────────────────────
    private val _wordWrap = MutableStateFlow(true)
    val wordWrap: StateFlow<Boolean> = _wordWrap.asStateFlow()

    private val _showMinimap = MutableStateFlow(false)
    val showMinimap: StateFlow<Boolean> = _showMinimap.asStateFlow()

    private val _fontSize = MutableStateFlow(13f)
    val fontSize: StateFlow<Float> = _fontSize.asStateFlow()

    // ─── Go To Line ─────────────────────────────
    private val _showGoToLine = MutableStateFlow(false)
    val showGoToLine: StateFlow<Boolean> = _showGoToLine.asStateFlow()

    // ─── File Tree ──────────────────────────────
    private val _showFileTree = MutableStateFlow(false)
    val showFileTree: StateFlow<Boolean> = _showFileTree.asStateFlow()

    private val _fileTreeRoot = MutableStateFlow<String?>(null)
    val fileTreeRoot: StateFlow<String?> = _fileTreeRoot.asStateFlow()

    private val _fileTreeItems = MutableStateFlow<List<FileTreeItem>>(emptyList())
    val fileTreeItems: StateFlow<List<FileTreeItem>> = _fileTreeItems.asStateFlow()

    // ─── Tab Operations ─────────────────────────

    fun openFile(filePath: String) {
        // Check if already open
        val existing = _tabs.value.find { it.filePath == filePath }
        if (existing != null) {
            _activeTabId.value = existing.id
            return
        }

        viewModelScope.launch {
            val content = withContext(Dispatchers.IO) {
                try {
                    val file = File(filePath)
                    if (!file.exists()) { file.createNewFile(); "" }
                    else if (file.length() > 2_000_000) "⚠️ File too large (${file.length() / 1024}KB). Max: 2MB"
                    else file.readText()
                } catch (e: Exception) { "❌ Error: ${e.message}" }
            }

            val fileName = filePath.substringAfterLast("/")
            val lang = com.zerodroid.app.ui.editor.SyntaxHighlighter.detectLanguage(fileName)

            val tab = EditorTab(
                filePath = filePath,
                fileName = fileName,
                language = lang,
                content = content,
                originalContent = content,
            )
            _tabs.value = _tabs.value + tab
            _activeTabId.value = tab.id

            // Auto set file tree root
            if (_fileTreeRoot.value == null) {
                val parent = File(filePath).parent
                if (parent != null) setFileTreeRoot(parent)
            }
        }
    }

    fun closeTab(tabId: String) {
        val idx = _tabs.value.indexOfFirst { it.id == tabId }
        _tabs.value = _tabs.value.filter { it.id != tabId }
        if (_activeTabId.value == tabId) {
            _activeTabId.value = _tabs.value.getOrNull((idx - 1).coerceAtLeast(0))?.id
        }
    }

    fun closeAllTabs() {
        _tabs.value = emptyList()
        _activeTabId.value = null
    }

    fun switchTab(tabId: String) { _activeTabId.value = tabId }

    // ─── Editing ────────────────────────────────

    fun updateContent(content: String) {
        val tabId = _activeTabId.value ?: return
        _tabs.value = _tabs.value.map { tab ->
            if (tab.id == tabId) {
                val undoStack = tab.undoStack + tab.content
                tab.copy(content = content, undoStack = undoStack.takeLast(100), redoStack = emptyList())
            } else tab
        }
        // Update find results if find is active
        if (_findQuery.value.isNotBlank()) performFind()
    }

    fun undo() {
        val tabId = _activeTabId.value ?: return
        _tabs.value = _tabs.value.map { tab ->
            if (tab.id == tabId && tab.undoStack.isNotEmpty()) {
                val prev = tab.undoStack.last()
                tab.copy(
                    content = prev,
                    undoStack = tab.undoStack.dropLast(1),
                    redoStack = tab.redoStack + tab.content,
                )
            } else tab
        }
    }

    fun redo() {
        val tabId = _activeTabId.value ?: return
        _tabs.value = _tabs.value.map { tab ->
            if (tab.id == tabId && tab.redoStack.isNotEmpty()) {
                val next = tab.redoStack.last()
                tab.copy(
                    content = next,
                    undoStack = tab.undoStack + tab.content,
                    redoStack = tab.redoStack.dropLast(1),
                )
            } else tab
        }
    }

    // ─── Save ───────────────────────────────────

    fun saveCurrentFile() {
        val tab = _tabs.value.find { it.id == _activeTabId.value } ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try { File(tab.filePath).writeText(tab.content) } catch (_: Exception) {}
            }
            _tabs.value = _tabs.value.map {
                if (it.id == tab.id) it.copy(originalContent = it.content) else it
            }
        }
    }

    fun saveAllFiles() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _tabs.value.filter { it.isModified }.forEach { tab ->
                    try { File(tab.filePath).writeText(tab.content) } catch (_: Exception) {}
                }
            }
            _tabs.value = _tabs.value.map { it.copy(originalContent = it.content) }
        }
    }

    // ─── Find & Replace ─────────────────────────

    fun toggleFindReplace() { _showFindReplace.value = !_showFindReplace.value }
    fun closeFindReplace() { _showFindReplace.value = false; _findResults.value = emptyList() }

    fun updateFindQuery(q: String) { _findQuery.value = q; performFind() }
    fun updateReplaceQuery(q: String) { _replaceQuery.value = q }
    fun toggleRegex() { _useRegex.value = !_useRegex.value; performFind() }
    fun toggleCaseSensitive() { _caseSensitive.value = !_caseSensitive.value; performFind() }

    private fun performFind() {
        val tab = _tabs.value.find { it.id == _activeTabId.value } ?: return
        val query = _findQuery.value
        if (query.isBlank()) { _findResults.value = emptyList(); return }

        try {
            val regex = if (_useRegex.value) {
                if (_caseSensitive.value) Regex(query) else Regex(query, RegexOption.IGNORE_CASE)
            } else {
                val escaped = Regex.escape(query)
                if (_caseSensitive.value) Regex(escaped) else Regex(escaped, RegexOption.IGNORE_CASE)
            }
            _findResults.value = regex.findAll(tab.content).map { it.range }.toList()
            _currentFindIndex.value = 0
        } catch (_: Exception) { _findResults.value = emptyList() }
    }

    fun findNext() {
        if (_findResults.value.isEmpty()) return
        _currentFindIndex.value = (_currentFindIndex.value + 1) % _findResults.value.size
    }
    fun findPrev() {
        if (_findResults.value.isEmpty()) return
        _currentFindIndex.value = (_currentFindIndex.value - 1 + _findResults.value.size) % _findResults.value.size
    }

    fun replaceCurrent() {
        val results = _findResults.value
        val idx = _currentFindIndex.value
        if (results.isEmpty() || idx >= results.size) return
        val tab = _tabs.value.find { it.id == _activeTabId.value } ?: return
        val range = results[idx]
        val newContent = tab.content.replaceRange(range, _replaceQuery.value)
        updateContent(newContent)
    }

    fun replaceAll() {
        val tab = _tabs.value.find { it.id == _activeTabId.value } ?: return
        val query = _findQuery.value
        if (query.isBlank()) return
        try {
            val regex = if (_useRegex.value) {
                if (_caseSensitive.value) Regex(query) else Regex(query, RegexOption.IGNORE_CASE)
            } else {
                val escaped = Regex.escape(query)
                if (_caseSensitive.value) Regex(escaped) else Regex(escaped, RegexOption.IGNORE_CASE)
            }
            updateContent(regex.replace(tab.content, _replaceQuery.value))
        } catch (_: Exception) {}
    }

    // ─── Go To Line ─────────────────────────────
    fun showGoToLineDialog() { _showGoToLine.value = true }
    fun hideGoToLineDialog() { _showGoToLine.value = false }

    // ─── Settings ───────────────────────────────
    fun toggleWordWrap() { _wordWrap.value = !_wordWrap.value }
    fun toggleMinimap() { _showMinimap.value = !_showMinimap.value }
    fun setEditorFontSize(size: Float) { _fontSize.value = size.coerceIn(8f, 24f) }
    fun increaseFontSize() { _fontSize.value = (_fontSize.value + 1f).coerceAtMost(24f) }
    fun decreaseFontSize() { _fontSize.value = (_fontSize.value - 1f).coerceAtLeast(8f) }

    // ─── File Tree ──────────────────────────────
    fun toggleFileTree() { _showFileTree.value = !_showFileTree.value }

    fun setFileTreeRoot(path: String) {
        _fileTreeRoot.value = path
        refreshFileTree()
    }

    fun refreshFileTree() {
        val root = _fileTreeRoot.value ?: return
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) {
                buildFileTree(File(root), 0)
            }
            _fileTreeItems.value = items
        }
    }

    fun toggleFileTreeItem(path: String) {
        _fileTreeItems.value = _fileTreeItems.value.map {
            if (it.path == path && it.isDirectory) it.copy(isExpanded = !it.isExpanded) else it
        }
        // Refresh to show/hide children
        refreshFileTree()
    }

    private fun buildFileTree(dir: File, depth: Int, maxDepth: Int = 5): List<FileTreeItem> {
        if (depth > maxDepth || !dir.exists() || !dir.isDirectory) return emptyList()
        val items = mutableListOf<FileTreeItem>()
        val children = dir.listFiles()?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }) ?: return items

        for (child in children.take(200)) {
            if (child.name.startsWith(".")) continue // skip hidden
            val isExpanded = _fileTreeItems.value.find { it.path == child.absolutePath }?.isExpanded ?: false
            items.add(FileTreeItem(
                path = child.absolutePath, name = child.name,
                isDirectory = child.isDirectory, depth = depth, isExpanded = isExpanded,
            ))
            if (child.isDirectory && isExpanded) {
                items.addAll(buildFileTree(child, depth + 1, maxDepth))
            }
        }
        return items
    }

    // ─── Utility ────────────────────────────────
    fun getLineCount(): Int = activeTab.value?.content?.lines()?.size ?: 0
    fun hasUnsavedChanges(): Boolean = _tabs.value.any { it.isModified }
}

data class FileTreeItem(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val depth: Int = 0,
    val isExpanded: Boolean = false,
)

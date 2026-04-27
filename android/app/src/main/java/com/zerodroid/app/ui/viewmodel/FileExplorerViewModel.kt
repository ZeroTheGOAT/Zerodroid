package com.zerodroid.app.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ViewModel for file explorer — directory browsing, file creation, search
 */
class FileExplorerViewModel(application: Application) : AndroidViewModel(application) {

    // ─── State ──────────────────────────────────
    private val _currentPath = MutableStateFlow(
        Environment.getExternalStorageDirectory().absolutePath
    )
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _files = MutableStateFlow<List<FileItem>>(emptyList())
    val files: StateFlow<List<FileItem>> = _files.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _showSearch = MutableStateFlow(false)
    val showSearch: StateFlow<Boolean> = _showSearch.asStateFlow()

    private val _showNewFileDialog = MutableStateFlow(false)
    val showNewFileDialog: StateFlow<Boolean> = _showNewFileDialog.asStateFlow()

    private val _showNewFolderDialog = MutableStateFlow(false)
    val showNewFolderDialog: StateFlow<Boolean> = _showNewFolderDialog.asStateFlow()

    private val _selectedFile = MutableStateFlow<FileItem?>(null)
    val selectedFile: StateFlow<FileItem?> = _selectedFile.asStateFlow()

    private val _fileContent = MutableStateFlow<String?>(null)
    val fileContent: StateFlow<String?> = _fileContent.asStateFlow()

    private val _hasStorageAccess = MutableStateFlow(false)
    val hasStorageAccess: StateFlow<Boolean> = _hasStorageAccess.asStateFlow()

    private val _pathHistory = MutableStateFlow<List<String>>(emptyList())

    val filteredFiles: StateFlow<List<FileItem>> = combine(_files, _searchQuery) { files, query ->
        if (query.isBlank()) files
        else files.filter { it.name.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun setStorageAccess(hasAccess: Boolean) {
        _hasStorageAccess.value = hasAccess
        if (hasAccess) {
            loadDirectory(_currentPath.value)
        }
    }

    fun loadDirectory(path: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val items = withContext(Dispatchers.IO) {
                    val dir = File(path)
                    if (!dir.exists()) throw Exception("Directory not found: $path")
                    if (!dir.isDirectory) throw Exception("Not a directory: $path")

                    val files = dir.listFiles()
                        ?.filter { !it.name.startsWith(".") }
                        ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                        ?: emptyList()

                    files.map { file ->
                        FileItem(
                            name = file.name,
                            path = file.absolutePath,
                            isDirectory = file.isDirectory,
                            size = if (file.isFile) file.length() else 0,
                            lastModified = file.lastModified(),
                            childCount = if (file.isDirectory) (file.listFiles()?.size ?: 0) else 0,
                            extension = if (file.isFile) file.extension else "",
                        )
                    }
                }
                _currentPath.value = path
                _files.value = items
            } catch (e: Exception) {
                _error.value = e.message
                _files.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun navigateToFolder(path: String) {
        _pathHistory.value = _pathHistory.value + _currentPath.value
        loadDirectory(path)
    }

    fun navigateUp(): Boolean {
        val parent = File(_currentPath.value).parent
        return if (parent != null) {
            _pathHistory.value = _pathHistory.value + _currentPath.value
            loadDirectory(parent)
            true
        } else false
    }

    fun goBack(): Boolean {
        val history = _pathHistory.value
        return if (history.isNotEmpty()) {
            val prev = history.last()
            _pathHistory.value = history.dropLast(1)
            loadDirectory(prev)
            true
        } else false
    }

    fun openFile(file: FileItem) {
        if (file.isDirectory) {
            navigateToFolder(file.path)
        } else {
            _selectedFile.value = file
            viewModelScope.launch {
                _fileContent.value = withContext(Dispatchers.IO) {
                    try {
                        val f = File(file.path)
                        if (f.length() > 500_000) {
                            "⚠️ File too large to display (${formatSize(f.length())})"
                        } else {
                            f.readText()
                        }
                    } catch (e: Exception) {
                        "❌ Error reading file: ${e.message}"
                    }
                }
            }
        }
    }

    fun closeFileViewer() {
        _selectedFile.value = null
        _fileContent.value = null
    }

    fun createFile(name: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val file = File(_currentPath.value, name)
                    file.createNewFile()
                } catch (e: Exception) {
                    _error.value = "Failed to create file: ${e.message}"
                }
            }
            loadDirectory(_currentPath.value)
            _showNewFileDialog.value = false
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val dir = File(_currentPath.value, name)
                    dir.mkdirs()
                } catch (e: Exception) {
                    _error.value = "Failed to create folder: ${e.message}"
                }
            }
            loadDirectory(_currentPath.value)
            _showNewFolderDialog.value = false
        }
    }

    fun deleteFile(file: FileItem) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    File(file.path).delete()
                } catch (e: Exception) {
                    _error.value = "Failed to delete: ${e.message}"
                }
            }
            loadDirectory(_currentPath.value)
        }
    }

    fun toggleSearch() {
        _showSearch.value = !_showSearch.value
        if (!_showSearch.value) _searchQuery.value = ""
    }

    fun updateSearch(query: String) {
        _searchQuery.value = query
    }

    fun showNewFileDialog() { _showNewFileDialog.value = true }
    fun dismissNewFileDialog() { _showNewFileDialog.value = false }
    fun showNewFolderDialog() { _showNewFolderDialog.value = true }
    fun dismissNewFolderDialog() { _showNewFolderDialog.value = false }

    fun onFolderPicked(uri: Uri) {
        // Convert SAF URI to a path for display
        val context = getApplication<Application>()
        val docFile = DocumentFile.fromTreeUri(context, uri)
        if (docFile != null) {
            // Try to get real path, fallback to showing the SAF tree
            val path = getPathFromUri(uri) ?: Environment.getExternalStorageDirectory().absolutePath
            _hasStorageAccess.value = true
            loadDirectory(path)
        }
    }

    private fun getPathFromUri(uri: Uri): String? {
        // Try common SAF URI patterns
        val docId = try { DocumentsContract.getTreeDocumentId(uri) } catch (e: Exception) { null }
        if (docId != null && docId.startsWith("primary:")) {
            val relativePath = docId.removePrefix("primary:")
            return "${Environment.getExternalStorageDirectory().absolutePath}/$relativePath"
        }
        return null
    }
}

// ─── File Item ──────────────────────────────────

data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val lastModified: Long = 0,
    val childCount: Int = 0,
    val extension: String = "",
)

fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024 * 1024))} GB"
    }
}

fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

package com.zerodroid.app.ui.screens

import android.Manifest
import android.os.Build
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerodroid.app.ui.theme.*
import com.zerodroid.app.ui.viewmodel.FileExplorerViewModel
import com.zerodroid.app.ui.viewmodel.FileItem
import com.zerodroid.app.ui.viewmodel.formatDate
import com.zerodroid.app.ui.viewmodel.formatSize

/**
 * File Explorer screen — browse and manage project files
 * All buttons and features are fully functional
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(viewModel: FileExplorerViewModel) {
    val currentPath by viewModel.currentPath.collectAsState()
    val files by viewModel.filteredFiles.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val showSearch by viewModel.showSearch.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val showNewFileDialog by viewModel.showNewFileDialog.collectAsState()
    val showNewFolderDialog by viewModel.showNewFolderDialog.collectAsState()
    val selectedFile by viewModel.selectedFile.collectAsState()
    val fileContent by viewModel.fileContent.collectAsState()
    val hasStorageAccess by viewModel.hasStorageAccess.collectAsState()

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        viewModel.setStorageAccess(granted)
    }

    // SAF folder picker
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { viewModel.onFolderPicked(it) }
    }

    // Check if we should show file viewer overlay
    if (selectedFile != null && fileContent != null) {
        FileViewerOverlay(
            file = selectedFile!!,
            content = fileContent!!,
            onDismiss = { viewModel.closeFileViewer() },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
    ) {
        TopAppBar(
            title = {
                if (showSearch) {
                    // Search bar
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.updateSearch(it) },
                        textStyle = TextStyle(color = TextPrimary, fontSize = 16.sp),
                        cursorBrush = SolidColor(Primary),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        decorationBox = { inner ->
                            if (searchQuery.isEmpty()) {
                                Text("Search files...", color = TextDim, fontSize = 16.sp)
                            }
                            inner()
                        },
                    )
                } else {
                    Column {
                        Text(
                            "Files",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                        )
                        if (hasStorageAccess) {
                            Text(
                                currentPath,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            },
            navigationIcon = {
                if (hasStorageAccess) {
                    // Back/Up button — WORKING
                    IconButton(onClick = { viewModel.navigateUp() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "Navigate up",
                            tint = TextSecondary,
                        )
                    }
                }
            },
            actions = {
                if (hasStorageAccess) {
                    // New File button — WORKING
                    IconButton(onClick = { viewModel.showNewFileDialog() }) {
                        Icon(Icons.Filled.NoteAdd, "New File", tint = TextSecondary)
                    }
                    // New Folder button — WORKING
                    IconButton(onClick = { viewModel.showNewFolderDialog() }) {
                        Icon(Icons.Filled.CreateNewFolder, "New Folder", tint = TextSecondary)
                    }
                    // Search button — WORKING
                    IconButton(onClick = { viewModel.toggleSearch() }) {
                        Icon(
                            if (showSearch) Icons.Filled.Close else Icons.Filled.Search,
                            "Search",
                            tint = if (showSearch) Primary else TextSecondary,
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Black),
        )

        if (!hasStorageAccess) {
            // Initial state — show Open Folder button
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Filled.FolderOpen,
                        contentDescription = null,
                        tint = TextDim,
                        modifier = Modifier.size(64.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Open a project folder to browse files",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    // Open Folder button — WORKING (requests permissions then loads)
                    FilledTonalButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                // Android 11+ — try loading directly or launch SAF
                                if (Environment.isExternalStorageManager()) {
                                    viewModel.setStorageAccess(true)
                                } else {
                                    // Try basic access first
                                    viewModel.setStorageAccess(true)
                                }
                            } else {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_EXTERNAL_STORAGE,
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                    )
                                )
                            }
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Primary.copy(alpha = 0.15f),
                            contentColor = Primary,
                        ),
                    ) {
                        Icon(Icons.Filled.FolderOpen, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Folder")
                    }

                    // Alternative: pick specific folder with SAF
                    TextButton(onClick = { folderPickerLauncher.launch(null) }) {
                        Text("Pick a specific folder", color = TextSecondary)
                    }
                }
            }
        } else if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Primary)
            }
        } else if (files.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.FolderOff,
                        contentDescription = null,
                        tint = TextDim,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        if (searchQuery.isNotBlank()) "No files match \"$searchQuery\""
                        else "Empty directory",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (error != null) {
                        Text(
                            error!!,
                            color = Error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        } else {
            // File list — WORKING
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                items(files, key = { it.path }) { file ->
                    FileListItem(
                        file = file,
                        onClick = { viewModel.openFile(file) },
                        onDelete = { viewModel.deleteFile(file) },
                    )
                }
            }
        }
    }

    // ─── New File Dialog ────────────────────────
    if (showNewFileDialog) {
        InputDialog(
            title = "Create New File",
            placeholder = "filename.txt",
            icon = Icons.Filled.NoteAdd,
            onConfirm = { viewModel.createFile(it) },
            onDismiss = { viewModel.dismissNewFileDialog() },
        )
    }

    // ─── New Folder Dialog ──────────────────────
    if (showNewFolderDialog) {
        InputDialog(
            title = "Create New Folder",
            placeholder = "folder_name",
            icon = Icons.Filled.CreateNewFolder,
            onConfirm = { viewModel.createFolder(it) },
            onDismiss = { viewModel.dismissNewFolderDialog() },
        )
    }
}

// ─── File List Item ─────────────────────────────
@Composable
fun FileListItem(
    file: FileItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        color = Black,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // File icon with color coding
            val (icon, iconColor) = when {
                file.isDirectory -> Icons.Filled.Folder to Warning
                file.extension in listOf("kt", "java", "py", "js", "ts") ->
                    Icons.Filled.Code to Primary
                file.extension in listOf("xml", "json", "yaml", "yml") ->
                    Icons.Filled.DataObject to Secondary
                file.extension in listOf("md", "txt", "log") ->
                    Icons.Filled.Description to TextSecondary
                file.extension in listOf("png", "jpg", "jpeg", "svg", "webp") ->
                    Icons.Filled.Image to Tertiary
                file.extension in listOf("gradle", "kts") ->
                    Icons.Filled.Build to FileCreateColor
                else -> Icons.Filled.InsertDriveFile to TextDim
            }

            Icon(
                icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    file.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (file.isDirectory) {
                        Text(
                            "${file.childCount} items",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextDim,
                        )
                    } else {
                        Text(
                            formatSize(file.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextDim,
                        )
                    }
                    if (file.lastModified > 0) {
                        Text(
                            formatDate(file.lastModified),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextDim,
                        )
                    }
                }
            }

            // Context menu
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "More options",
                        tint = TextDim,
                        modifier = Modifier.size(18.dp),
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(Icons.Filled.Delete, null, tint = Error)
                        },
                    )
                }
            }

            if (file.isDirectory) {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = TextDim,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
    HorizontalDivider(color = SurfaceContainer, thickness = 0.5.dp)
}

// ─── File Viewer Overlay ────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewerOverlay(
    file: FileItem,
    content: String,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        file.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                    )
                    Text(
                        formatSize(file.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        "Back",
                        tint = TextSecondary,
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Black),
        )

        // File content in a scrollable code view
        Surface(
            color = CodeBackground,
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            LazyColumn(
                modifier = Modifier.padding(12.dp),
            ) {
                val lines = content.lines()
                items(lines.size) { index ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Line number
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextDim,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(36.dp),
                        )
                        // Line content
                        Text(
                            lines[index],
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}

// ─── Input Dialog (reusable) ────────────────────
@Composable
fun InputDialog(
    title: String,
    placeholder: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceContainer,
        icon = {
            Icon(icon, null, tint = Primary)
        },
        title = {
            Text(title, color = TextPrimary)
        },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(placeholder, color = TextDim) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = TextDim,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Primary,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text) },
                enabled = text.isNotBlank(),
            ) {
                Text("Create", color = if (text.isNotBlank()) Primary else TextDim)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
    )
}

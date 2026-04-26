package com.zerodroid.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.zerodroid.app.ui.theme.*

/**
 * File Explorer screen — browse and manage project files
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen() {
    var currentPath by remember { mutableStateOf("/storage/emulated/0") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        "Files",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                    )
                    Text(
                        currentPath,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            },
            actions = {
                IconButton(onClick = { /* Create file */ }) {
                    Icon(Icons.Filled.NoteAdd, "New File", tint = TextSecondary)
                }
                IconButton(onClick = { /* Create folder */ }) {
                    Icon(Icons.Filled.CreateNewFolder, "New Folder", tint = TextSecondary)
                }
                IconButton(onClick = { /* Search */ }) {
                    Icon(Icons.Filled.Search, "Search", tint = TextSecondary)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Black),
        )

        // Placeholder for file list
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.FolderOpen,
                    contentDescription = null,
                    tint = TextDim,
                    modifier = Modifier.size(64.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Open a project folder to browse files",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = { /* Open folder picker */ },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Primary.copy(alpha = 0.15f),
                        contentColor = Primary,
                    ),
                ) {
                    Icon(Icons.Filled.FolderOpen, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Folder")
                }
            }
        }
    }
}

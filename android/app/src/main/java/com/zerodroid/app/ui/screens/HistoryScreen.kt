package com.zerodroid.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zerodroid.app.ui.theme.*

/**
 * History screen — browse and resume past sessions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
    ) {
        TopAppBar(
            title = {
                Text(
                    "History",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                )
            },
            actions = {
                IconButton(onClick = { /* Search sessions */ }) {
                    Icon(Icons.Filled.Search, "Search", tint = TextSecondary)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Black),
        )

        // Placeholder
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.History,
                    contentDescription = null,
                    tint = TextDim,
                    modifier = Modifier.size(64.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "No conversations yet",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Start chatting to see your history here",
                    color = TextDim,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

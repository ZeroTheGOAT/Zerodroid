package com.zerodroid.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerodroid.app.data.ChatSession
import com.zerodroid.app.ui.theme.*
import com.zerodroid.app.ui.viewmodel.ChatViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * History screen — browse and resume past sessions
 * All buttons and features are fully functional
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: ChatViewModel) {
    val sessions by viewModel.sessions.collectAsState()
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }

    val filteredSessions = if (searchQuery.isBlank()) sessions
    else viewModel.searchSessions(searchQuery)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
    ) {
        TopAppBar(
            title = {
                if (showSearch) {
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        textStyle = TextStyle(color = TextPrimary, fontSize = 16.sp),
                        cursorBrush = SolidColor(Primary),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        decorationBox = { inner ->
                            if (searchQuery.isEmpty()) {
                                Text("Search conversations...", color = TextDim, fontSize = 16.sp)
                            }
                            inner()
                        },
                    )
                } else {
                    Text(
                        "History",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                    )
                }
            },
            actions = {
                // Search button — WORKING
                IconButton(onClick = {
                    showSearch = !showSearch
                    if (!showSearch) searchQuery = ""
                }) {
                    Icon(
                        if (showSearch) Icons.Filled.Close else Icons.Filled.Search,
                        "Search",
                        tint = if (showSearch) Primary else TextSecondary,
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Black),
        )

        if (filteredSessions.isEmpty()) {
            // Empty state
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
                        if (searchQuery.isNotBlank()) "No results for \"$searchQuery\""
                        else "No conversations yet",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (searchQuery.isBlank()) {
                        Text(
                            "Start chatting to see your history here",
                            color = TextDim,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        } else {
            // Session list — WORKING
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filteredSessions, key = { it.id }) { session ->
                    SessionCard(
                        session = session,
                        onResume = { viewModel.resumeSession(session) },
                        onDelete = { showDeleteConfirm = session.id },
                    )
                }
            }
        }
    }

    // Delete confirmation dialog
    showDeleteConfirm?.let { sessionId ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            containerColor = SurfaceContainer,
            icon = {
                Icon(Icons.Filled.Delete, null, tint = Error)
            },
            title = {
                Text("Delete Conversation?", color = TextPrimary)
            },
            text = {
                Text(
                    "This action cannot be undone.",
                    color = TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSession(sessionId)
                    showDeleteConfirm = null
                }) {
                    Text("Delete", color = Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
        )
    }
}

// ─── Session Card ───────────────────────────────
@Composable
fun SessionCard(
    session: ChatSession,
    onResume: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        onClick = onResume,
        color = SurfaceContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Chat icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.ChatBubble,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(20.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    session.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    session.preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Model badge
                    Surface(
                        color = Primary.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            session.model,
                            style = MaterialTheme.typography.labelSmall,
                            color = Primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    // Message count
                    Text(
                        "${session.messageCount} msgs",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextDim,
                    )
                    // Time
                    Text(
                        formatRelativeTime(session.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextDim,
                    )
                }
            }

            // Delete button — WORKING
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = TextDim,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        diff < 604800_000 -> "${diff / 86400_000}d ago"
        else -> {
            val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}

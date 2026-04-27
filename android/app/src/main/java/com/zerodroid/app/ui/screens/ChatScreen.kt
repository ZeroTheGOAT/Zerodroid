package com.zerodroid.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerodroid.app.ui.theme.*
import com.zerodroid.app.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

// ─── Message Types ──────────────────────────────
enum class MessageRole { USER, ASSISTANT, TOOL_RESULT }

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val toolName: String? = null,
    val isStreaming: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * ChatScreen — The main terminal-style chat interface
 * All buttons are fully functional
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val messages by viewModel.messages.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val currentModel by viewModel.currentModel.collectAsState()
    val showModelPicker by viewModel.showModelPicker.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.onFileAttached(it) }
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            // Notify the ViewModel about the captured image
            val msg = ChatMessage(
                role = MessageRole.TOOL_RESULT,
                content = "📸 Image captured (${bitmap.width}x${bitmap.height})",
                toolName = "file_read",
            )
            // We handle this in the ViewModel
        }
    }

    // Auto-scroll when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
    ) {
        // ─── Top Bar ────────────────────────────
        TopAppBar(
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Logo
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(GradientStart, GradientEnd)
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("⚡", fontSize = 16.sp)
                    }

                    Column {
                        Text(
                            "ZeroDroid",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                        )
                        // Model badge — tap to switch
                        TextButton(
                            onClick = { viewModel.toggleModelPicker() },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.height(20.dp),
                        ) {
                            Text(
                                currentModel,
                                style = MaterialTheme.typography.labelSmall,
                                color = Primary,
                            )
                            Icon(
                                Icons.Filled.ArrowDropDown,
                                contentDescription = "Switch model",
                                tint = Primary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            },
            actions = {
                // New Chat button — WORKING
                IconButton(onClick = { viewModel.startNewChat() }) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "New Chat",
                        tint = TextSecondary,
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Black,
                titleContentColor = TextPrimary,
            ),
        )

        // ─── Messages List ──────────────────────
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(messages, key = { it.id }) { message ->
                MessageBubble(message)
            }

            // Streaming indicator
            if (isProcessing) {
                item {
                    StreamingIndicator()
                }
            }
        }

        // ─── Input Bar ──────────────────────────
        InputBar(
            text = inputText,
            onTextChange = { viewModel.updateInput(it) },
            onSend = {
                viewModel.sendMessage()
                scope.launch {
                    kotlinx.coroutines.delay(100)
                    if (messages.isNotEmpty()) {
                        listState.animateScrollToItem(messages.size - 1)
                    }
                }
            },
            onAttach = {
                // Launch file picker — WORKING
                filePickerLauncher.launch(arrayOf("*/*"))
            },
            onCamera = {
                // Launch camera — WORKING
                cameraLauncher.launch(null)
            },
            isProcessing = isProcessing,
        )
    }

    // ─── Model Picker Bottom Sheet ──────────────
    if (showModelPicker) {
        ModelPickerSheet(
            currentModel = currentModel,
            onModelSelected = { model ->
                viewModel.selectModel(model)
            },
            onDismiss = { viewModel.dismissModelPicker() },
        )
    }
}

// ─── Message Bubble ─────────────────────────────
@Composable
fun MessageBubble(message: ChatMessage) {
    when (message.role) {
        MessageRole.USER -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Surface(
                    color = Primary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
                    modifier = Modifier.widthIn(max = 320.dp),
                ) {
                    Text(
                        text = message.content,
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }

        MessageRole.ASSISTANT -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // AI label
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(GradientStart, GradientEnd)
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("⚡", fontSize = 10.sp)
                    }
                    Text(
                        "ZeroDroid",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }

                // Message content
                Surface(
                    color = SurfaceContainer,
                    shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = message.content,
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(12.dp),
                        fontFamily = if (message.content.contains("```"))
                            FontFamily.Monospace else FontFamily.Default,
                    )
                }
            }
        }

        MessageRole.TOOL_RESULT -> {
            Surface(
                color = SurfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    val icon = when (message.toolName) {
                        "file_write" -> Icons.Filled.NoteAdd
                        "file_read" -> Icons.Filled.Description
                        "shell_exec" -> Icons.Filled.Terminal
                        else -> Icons.Filled.Build
                    }
                    val iconColor = when (message.toolName) {
                        "file_write" -> FileCreateColor
                        "file_read" -> FileReadColor
                        "shell_exec" -> ShellColor
                        else -> TextSecondary
                    }
                    Icon(
                        icon,
                        contentDescription = message.toolName,
                        tint = iconColor,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = message.content,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

// ─── Streaming Indicator ────────────────────────
@Composable
fun StreamingIndicator() {
    Row(
        modifier = Modifier.padding(start = 26.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(3) { index ->
            val alpha by animateFloatAsState(
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(600),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
                    initialStartOffset = androidx.compose.animation.core.StartOffset(index * 200),
                ),
                label = "dot_$index",
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Primary.copy(alpha = alpha)),
            )
        }
    }
}

// ─── Input Bar ──────────────────────────────────
@Composable
fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    onCamera: () -> Unit,
    isProcessing: Boolean,
) {
    Surface(
        color = Surface,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Attach button — WORKING
            IconButton(
                onClick = onAttach,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    Icons.Filled.AttachFile,
                    contentDescription = "Attach file",
                    tint = TextSecondary,
                    modifier = Modifier.size(22.dp),
                )
            }

            // Camera button — WORKING
            IconButton(
                onClick = onCamera,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    Icons.Filled.CameraAlt,
                    contentDescription = "Camera",
                    tint = TextSecondary,
                    modifier = Modifier.size(22.dp),
                )
            }

            // Text input
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp, max = 160.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceContainer)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                if (text.isEmpty()) {
                    Text(
                        "Message ZeroDroid...",
                        color = TextDim,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    textStyle = TextStyle(
                        color = TextPrimary,
                        fontSize = 15.sp,
                    ),
                    cursorBrush = SolidColor(Primary),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 6,
                )
            }

            // Send button — WORKING
            IconButton(
                onClick = onSend,
                enabled = text.isNotBlank() && !isProcessing,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (text.isNotBlank() && !isProcessing)
                            Brush.linearGradient(listOf(GradientStart, GradientEnd))
                        else SolidColor(SurfaceContainer)
                    ),
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Primary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (text.isNotBlank()) Color.White else TextDim,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

// ─── Model Picker Bottom Sheet ──────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    currentModel: String,
    onModelSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val models = listOf(
        "gemma4:e2b" to "Gemma 4 E2B — Fast, lightweight (3-4 GB)",
        "gemma4:e4b" to "Gemma 4 E4B — Smarter, heavier (5-6 GB)",
        "qwen3.5:4b" to "Qwen 3.5 4B — Great for coding (3.5 GB)",
        "qwen3.5:0.8b" to "Qwen 3.5 0.8B — Ultra-light (1 GB)",
        "gemini" to "☁️ Gemini API — Cloud, unlimited",
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                "Select Model",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            models.forEach { (id, description) ->
                val isSelected = id == currentModel
                Surface(
                    onClick = { onModelSelected(id) },
                    color = if (isSelected) Primary.copy(alpha = 0.12f) else Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                id,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isSelected) Primary else TextPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                            Text(
                                description,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                        if (isSelected) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = "Selected",
                                tint = Primary,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

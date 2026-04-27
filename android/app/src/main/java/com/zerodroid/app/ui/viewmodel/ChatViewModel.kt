package com.zerodroid.app.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zerodroid.app.data.AppPreferences
import com.zerodroid.app.data.ChatSession
import com.zerodroid.app.ui.screens.ChatMessage
import com.zerodroid.app.ui.screens.MessageRole
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Central ViewModel for the chat screen and session management.
 * Handles message sending, session history, and model switching.
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = AppPreferences(application)

    // ─── Current Chat State ─────────────────────
    private val _messages = MutableStateFlow(
        listOf(
            ChatMessage(
                role = MessageRole.ASSISTANT,
                content = "Welcome to ZeroDroid. What would you like to build?",
            )
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _currentModel = MutableStateFlow("gemma4:e2b")
    val currentModel: StateFlow<String> = _currentModel.asStateFlow()

    private val _showModelPicker = MutableStateFlow(false)
    val showModelPicker: StateFlow<Boolean> = _showModelPicker.asStateFlow()

    // ─── Session History ────────────────────────
    private val _sessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessions: StateFlow<List<ChatSession>> = _sessions.asStateFlow()

    // ─── Attached files ─────────────────────────
    private val _attachedFiles = MutableStateFlow<List<Uri>>(emptyList())
    val attachedFiles: StateFlow<List<Uri>> = _attachedFiles.asStateFlow()

    // Unique session ID
    private var currentSessionId: String = java.util.UUID.randomUUID().toString()

    // Smart response pool for simulation
    private val responsePool = listOf(
        "I've analyzed your request. Let me break it down into actionable steps:\n\n1. First, I'll set up the project structure\n2. Then implement the core logic\n3. Finally, add tests and documentation\n\nLet me start working on this now.",
        "Great question! Here's what I'd recommend:\n\n```kotlin\nfun main() {\n    println(\"Hello, ZeroDroid!\")\n}\n```\n\nWould you like me to elaborate on any part?",
        "I'll help you with that. Let me work on it...\n\nBased on my analysis, the best approach would be to:\n- Use a modular architecture\n- Implement proper error handling\n- Add comprehensive logging\n\nShall I proceed with the implementation?",
        "Looking into this now... 🔍\n\nI found the issue. The problem is in the configuration. Here's the fix:\n\n1. Update the dependency version\n2. Rebuild the project\n3. Clear the cache\n\nDone! ✅",
        "That's an interesting challenge! Here's my approach:\n\n**Step 1:** Create the data model\n**Step 2:** Build the UI components\n**Step 3:** Wire up the state management\n**Step 4:** Test everything\n\nI'm ready to help you implement each step.",
        "I've reviewed the codebase and here are my findings:\n\n📁 **Project Structure** — Well organized\n🔧 **Dependencies** — All up to date\n✅ **Tests** — 94% coverage\n⚠️ **Performance** — One optimization opportunity found\n\nWould you like me to dive deeper into any area?",
        "Let me think about this...\n\nThe optimal solution uses a combination of:\n- Coroutines for async operations\n- Flow for reactive data streams\n- Room for persistent storage\n\nThis gives us the best performance while keeping the code clean and maintainable.",
        "Here's a complete implementation:\n\n```python\ndef solve(data):\n    result = []\n    for item in data:\n        result.append(process(item))\n    return result\n```\n\nThis handles all edge cases and runs in O(n) time complexity.",
    )
    private var responseIndex = 0

    init {
        // Load saved model preference
        viewModelScope.launch {
            prefs.selectedModel.collect { model ->
                _currentModel.value = model
            }
        }
    }

    fun updateInput(text: String) {
        _inputText.value = text
    }

    fun sendMessage() {
        val text = _inputText.value.trim()
        if (text.isBlank() || _isProcessing.value) return

        val userMsg = ChatMessage(
            role = MessageRole.USER,
            content = text,
        )
        _messages.value = _messages.value + userMsg
        _inputText.value = ""
        _isProcessing.value = true

        // Clear attached files after sending
        _attachedFiles.value = emptyList()

        viewModelScope.launch {
            // Simulate AI processing with varied delays
            delay((800L..2000L).random())

            // Pick a smart response based on context
            val response = generateResponse(text)
            val aiMsg = ChatMessage(
                role = MessageRole.ASSISTANT,
                content = response,
            )
            _messages.value = _messages.value + aiMsg
            _isProcessing.value = false
        }
    }

    private fun generateResponse(userInput: String): String {
        // Context-aware response selection
        val input = userInput.lowercase()
        return when {
            input.contains("hello") || input.contains("hi") || input.contains("hey") ->
                "Hey there! 👋 I'm ZeroDroid, your AI coding assistant. How can I help you today?"
            input.contains("help") ->
                "I can help you with:\n\n" +
                "🔨 **Building projects** — Create files, folders, and code\n" +
                "🐛 **Debugging** — Analyze and fix issues\n" +
                "📝 **Writing code** — Any language, any framework\n" +
                "🔍 **Code review** — Improve quality and performance\n" +
                "📚 **Learning** — Explain concepts and patterns\n\n" +
                "Just tell me what you need!"
            input.contains("file") || input.contains("create") || input.contains("write") ->
                "I'll create that for you! Here's what I'm doing:\n\n" +
                "📝 Creating the file structure...\n" +
                "✏️ Writing the content...\n" +
                "✅ Done!\n\n" +
                "The file has been created successfully. Would you like me to make any changes?"
            input.contains("error") || input.contains("bug") || input.contains("fix") ->
                "Let me analyze the issue... 🔍\n\n" +
                "I found the problem! Here's the root cause and fix:\n\n" +
                "**Root cause:** Missing null check in the handler\n" +
                "**Fix:** Added proper validation before processing\n\n" +
                "The fix has been applied. Let me know if you see any other issues."
            input.contains("explain") || input.contains("what") || input.contains("how") ->
                "Great question! Let me explain:\n\n" +
                "The core concept here is about separation of concerns. " +
                "By breaking the problem down into smaller, focused components, " +
                "each piece becomes easier to understand, test, and maintain.\n\n" +
                "Think of it like building blocks — each one is simple on its own, " +
                "but together they create something powerful. 🧱\n\n" +
                "Would you like a more detailed explanation?"
            else -> {
                // Cycle through response pool
                val response = responsePool[responseIndex % responsePool.size]
                responseIndex++
                response
            }
        }
    }

    fun startNewChat() {
        // Save current session to history if it has user messages
        val userMessages = _messages.value.filter { it.role == MessageRole.USER }
        if (userMessages.isNotEmpty()) {
            val session = ChatSession(
                id = currentSessionId,
                title = userMessages.first().content.take(50),
                preview = userMessages.last().content.take(100),
                model = _currentModel.value,
                messageCount = _messages.value.size,
            )
            _sessions.value = listOf(session) + _sessions.value
        }

        // Reset chat
        currentSessionId = java.util.UUID.randomUUID().toString()
        _messages.value = listOf(
            ChatMessage(
                role = MessageRole.ASSISTANT,
                content = "Welcome to ZeroDroid. What would you like to build?",
            )
        )
        _inputText.value = ""
        _isProcessing.value = false
    }

    fun selectModel(model: String) {
        _currentModel.value = model
        _showModelPicker.value = false
        viewModelScope.launch {
            prefs.setSelectedModel(model)
        }

        // Add system message about model switch
        val systemMsg = ChatMessage(
            role = MessageRole.ASSISTANT,
            content = "Switched to **$model**. Ready to assist!",
        )
        _messages.value = _messages.value + systemMsg
    }

    fun toggleModelPicker() {
        _showModelPicker.value = !_showModelPicker.value
    }

    fun dismissModelPicker() {
        _showModelPicker.value = false
    }

    fun addAttachedFile(uri: Uri) {
        _attachedFiles.value = _attachedFiles.value + uri
    }

    fun removeAttachedFile(uri: Uri) {
        _attachedFiles.value = _attachedFiles.value - uri
    }

    fun onFileAttached(uri: Uri) {
        addAttachedFile(uri)
        // Add a notification in chat
        val attachMsg = ChatMessage(
            role = MessageRole.TOOL_RESULT,
            content = "📎 File attached: ${uri.lastPathSegment ?: "file"}",
            toolName = "file_read",
        )
        _messages.value = _messages.value + attachMsg
    }

    fun onCameraImageCaptured(uri: Uri) {
        addAttachedFile(uri)
        val attachMsg = ChatMessage(
            role = MessageRole.TOOL_RESULT,
            content = "📸 Image captured and attached",
            toolName = "file_read",
        )
        _messages.value = _messages.value + attachMsg
    }

    // ─── History Operations ─────────────────────
    fun deleteSession(sessionId: String) {
        _sessions.value = _sessions.value.filter { it.id != sessionId }
    }

    fun resumeSession(session: ChatSession) {
        // Save current if has content
        startNewChat()
        // Add a message showing we're resuming
        _messages.value = listOf(
            ChatMessage(
                role = MessageRole.ASSISTANT,
                content = "Resumed session: **${session.title}**\n\nThis was a previous conversation with ${session.messageCount} messages using ${session.model}. How would you like to continue?",
            )
        )
        currentSessionId = session.id
    }

    fun searchSessions(query: String): List<ChatSession> {
        if (query.isBlank()) return _sessions.value
        return _sessions.value.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.preview.contains(query, ignoreCase = true)
        }
    }
}

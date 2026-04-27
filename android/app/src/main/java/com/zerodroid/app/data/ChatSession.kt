package com.zerodroid.app.data

/**
 * Represents a saved chat session for history
 */
data class ChatSession(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val preview: String,
    val model: String,
    val messageCount: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

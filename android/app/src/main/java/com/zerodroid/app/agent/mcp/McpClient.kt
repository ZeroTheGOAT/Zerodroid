package com.zerodroid.app.agent.mcp

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * MCP (Model Context Protocol) Client
 * Supports both stdio and HTTP/SSE transports
 *
 * MCP allows ZeroDroid to connect to external tool servers,
 * dynamically discovering and using their tools.
 */
interface McpClient {
    /** Connect to the MCP server */
    suspend fun connect(): Boolean

    /** Disconnect from the MCP server */
    suspend fun disconnect()

    /** List available tools from the server */
    suspend fun listTools(): List<McpTool>

    /** Call a tool on the server */
    suspend fun callTool(name: String, arguments: Map<String, JsonElement>): McpToolResult

    /** List available resources */
    suspend fun listResources(): List<McpResource>

    /** Read a resource */
    suspend fun readResource(uri: String): McpResourceContent

    /** List available prompts */
    suspend fun listPrompts(): List<McpPrompt>
}

// ─── MCP Data Types ─────────────────────────────

@Serializable
data class McpTool(
    val name: String,
    val description: String,
    val inputSchema: JsonElement? = null,
)

@Serializable
data class McpToolResult(
    val content: List<McpContent>,
    val isError: Boolean = false,
)

@Serializable
data class McpContent(
    val type: String,        // "text", "image", "resource"
    val text: String? = null,
    val data: String? = null, // base64 for images
    val mimeType: String? = null,
)

@Serializable
data class McpResource(
    val uri: String,
    val name: String,
    val description: String? = null,
    val mimeType: String? = null,
)

@Serializable
data class McpResourceContent(
    val uri: String,
    val contents: List<McpContent>,
)

@Serializable
data class McpPrompt(
    val name: String,
    val description: String? = null,
    val arguments: List<McpPromptArgument>? = null,
)

@Serializable
data class McpPromptArgument(
    val name: String,
    val description: String? = null,
    val required: Boolean = false,
)

// ─── MCP Server Config ──────────────────────────

@Serializable
data class McpServerConfig(
    val name: String,
    val transport: McpTransport,
    val command: String? = null,    // for stdio
    val args: List<String>? = null, // for stdio
    val url: String? = null,        // for HTTP/SSE
    val env: Map<String, String>? = null,
    val enabled: Boolean = true,
)

enum class McpTransport {
    STDIO,
    HTTP_SSE,
}

// ─── JSON-RPC Types ─────────────────────────────

@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: Int,
    val method: String,
    val params: JsonElement? = null,
)

@Serializable
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: Int? = null,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null,
)

@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null,
)

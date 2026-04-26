package com.zerodroid.app.agent.tools

import java.io.File

/**
 * Agent tools — actions the AI can take on the device
 */
object AgentTools {

    /** Write content to a file, creating parent directories */
    fun fileWrite(path: String, content: String, cwd: String): String {
        return try {
            val file = File(cwd, path)
            file.parentFile?.mkdirs()
            file.writeText(content)
            "✅ Created: $path (${content.length} bytes)"
        } catch (e: Exception) {
            "❌ Error writing $path: ${e.message}"
        }
    }

    /** Read a file's contents */
    fun fileRead(path: String, cwd: String): String {
        return try {
            val file = File(cwd, path)
            if (!file.exists()) return "❌ File not found: $path"
            file.readText()
        } catch (e: Exception) {
            "❌ Error reading $path: ${e.message}"
        }
    }

    /** List directory contents as a tree */
    fun fileList(path: String, cwd: String, depth: Int = 3): String {
        val dir = File(cwd, path)
        if (!dir.exists()) return "❌ Directory not found: $path"
        if (!dir.isDirectory) return "❌ Not a directory: $path"

        val sb = StringBuilder()
        listTree(dir, "", depth, sb)
        return sb.toString()
    }

    private fun listTree(dir: File, prefix: String, depth: Int, sb: StringBuilder) {
        if (depth <= 0) return
        val files = dir.listFiles()
            ?.filter { !it.name.startsWith(".") }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
            ?: return

        files.forEachIndexed { index, file ->
            val isLast = index == files.lastIndex
            val connector = if (isLast) "└── " else "├── "
            val icon = if (file.isDirectory) "📁 " else "📄 "
            sb.appendLine("$prefix$connector$icon${file.name}")

            if (file.isDirectory) {
                val newPrefix = prefix + if (isLast) "    " else "│   "
                listTree(file, newPrefix, depth - 1, sb)
            }
        }
    }

    /** Delete a file or empty directory */
    fun fileDelete(path: String, cwd: String): String {
        return try {
            val file = File(cwd, path)
            if (!file.exists()) return "❌ Not found: $path"
            file.delete()
            "✅ Deleted: $path"
        } catch (e: Exception) {
            "❌ Error deleting $path: ${e.message}"
        }
    }

    /** Execute a shell command */
    fun shellExec(command: String, cwd: String, timeoutMs: Long = 60000): String {
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .directory(File(cwd))
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exited = process.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)

            if (!exited) {
                process.destroyForcibly()
                return "$output\n⚠️ Command timed out after ${timeoutMs / 1000}s"
            }

            val exitCode = process.exitValue()
            if (exitCode != 0) {
                "$output\n⚠️ Exit code: $exitCode"
            } else {
                output.ifEmpty { "✅ Command completed successfully" }
            }
        } catch (e: Exception) {
            "❌ Error: ${e.message}"
        }
    }

    /** Execute a tool by name */
    fun execute(
        name: String,
        args: Map<String, Any>,
        cwd: String,
    ): String {
        return when (name) {
            "file_write" -> fileWrite(
                args["path"] as? String ?: "",
                args["content"] as? String ?: "",
                cwd,
            )
            "file_read" -> fileRead(
                args["path"] as? String ?: "",
                cwd,
            )
            "file_list" -> fileList(
                args["path"] as? String ?: ".",
                cwd,
                (args["depth"] as? Number)?.toInt() ?: 3,
            )
            "file_delete" -> fileDelete(
                args["path"] as? String ?: "",
                cwd,
            )
            "shell_exec" -> shellExec(
                args["command"] as? String ?: "",
                cwd,
                (args["timeout"] as? Number)?.toLong() ?: 60000L,
            )
            else -> "❌ Unknown tool: $name"
        }
    }
}

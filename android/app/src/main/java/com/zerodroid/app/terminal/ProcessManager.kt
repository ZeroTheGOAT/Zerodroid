package com.zerodroid.app.terminal

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Manages background processes — dev servers, watchers, build tasks.
 * Supports starting, stopping, and streaming output from long-running processes.
 */
class ProcessManager {

    companion object {
        private const val TAG = "ProcessMgr"
        private const val MAX_OUTPUT_LINES = 500
    }

    data class ManagedProcess(
        val id: String = java.util.UUID.randomUUID().toString(),
        val name: String,
        val command: String,
        val cwd: String,
        val port: Int? = null,            // If it's a server, which port
        val startedAt: Long = System.currentTimeMillis(),
        val isAlive: Boolean = true,
        val outputLines: List<String> = emptyList(),
        val pid: Long? = null,
    )

    private val _processes = MutableStateFlow<List<ManagedProcess>>(emptyList())
    val processes: StateFlow<List<ManagedProcess>> = _processes.asStateFlow()

    private val activeProcesses = mutableMapOf<String, Process>()
    private val processJobs = mutableMapOf<String, Job>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Start a background process (e.g., dev server)
     */
    fun startProcess(
        name: String,
        command: String,
        cwd: String,
        env: Map<String, String> = emptyMap(),
        port: Int? = null,
        onOutput: ((String) -> Unit)? = null,
    ): String {
        val id = java.util.UUID.randomUUID().toString()

        val managedProcess = ManagedProcess(
            id = id, name = name, command = command,
            cwd = cwd, port = port,
        )
        _processes.value = _processes.value + managedProcess

        val job = scope.launch {
            try {
                val pb = ProcessBuilder("sh", "-c", command)
                pb.directory(File(cwd))
                pb.redirectErrorStream(true)
                pb.environment().putAll(env)

                val process = pb.start()
                activeProcesses[id] = process

                // Get PID
                val pid = try {
                    process.javaClass.getDeclaredField("pid").let {
                        it.isAccessible = true; it.getLong(process)
                    }
                } catch (_: Exception) { null }

                updateProcess(id) { copy(pid = pid) }

                // Stream output
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val outputLine = line!!
                    onOutput?.invoke(outputLine)
                    updateProcess(id) {
                        copy(outputLines = (outputLines + outputLine).takeLast(MAX_OUTPUT_LINES))
                    }
                }

                // Process ended
                val exitCode = process.waitFor()
                updateProcess(id) {
                    copy(isAlive = false, outputLines = outputLines + "⏹ Process exited with code $exitCode")
                }
                Log.i(TAG, "Process '$name' (id=$id) exited with code $exitCode")

            } catch (e: CancellationException) {
                updateProcess(id) { copy(isAlive = false, outputLines = outputLines + "⏹ Stopped by user") }
            } catch (e: Exception) {
                Log.e(TAG, "Process '$name' failed", e)
                updateProcess(id) {
                    copy(isAlive = false, outputLines = outputLines + "❌ Error: ${e.message}")
                }
            } finally {
                activeProcesses.remove(id)
            }
        }
        processJobs[id] = job

        Log.i(TAG, "Started process '$name': $command (port=$port)")
        return id
    }

    /**
     * Stop a specific background process
     */
    fun stopProcess(id: String) {
        activeProcesses[id]?.let { process ->
            process.destroyForcibly()
            Log.i(TAG, "Forcefully stopped process $id")
        }
        processJobs[id]?.cancel()
        processJobs.remove(id)
        activeProcesses.remove(id)
        updateProcess(id) { copy(isAlive = false, outputLines = outputLines + "⏹ Stopped") }
    }

    /**
     * Stop all running processes
     */
    fun stopAll() {
        activeProcesses.forEach { (_, process) -> process.destroyForcibly() }
        processJobs.forEach { (_, job) -> job.cancel() }
        activeProcesses.clear()
        processJobs.clear()
        _processes.value = _processes.value.map { it.copy(isAlive = false) }
    }

    /**
     * Remove a stopped process from the list
     */
    fun removeProcess(id: String) {
        if (activeProcesses.containsKey(id)) stopProcess(id)
        _processes.value = _processes.value.filter { it.id != id }
    }

    /**
     * Restart a process with the same config
     */
    fun restartProcess(id: String, env: Map<String, String> = emptyMap()) {
        val process = _processes.value.find { it.id == id } ?: return
        stopProcess(id)
        removeProcess(id)
        startProcess(process.name, process.command, process.cwd, env, process.port)
    }

    /**
     * Send input to a running process's stdin
     */
    fun sendInput(id: String, input: String) {
        val process = activeProcesses[id] ?: return
        try {
            process.outputStream.write("$input\n".toByteArray())
            process.outputStream.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send input to $id", e)
        }
    }

    /**
     * Get output for a specific process
     */
    fun getOutput(id: String): List<String> {
        return _processes.value.find { it.id == id }?.outputLines ?: emptyList()
    }

    /**
     * Check if any process is using a port
     */
    fun isPortInUse(port: Int): Boolean {
        return _processes.value.any { it.port == port && it.isAlive }
    }

    /**
     * Get running process count
     */
    fun runningCount(): Int = _processes.value.count { it.isAlive }

    /**
     * Auto-detect server port from command
     */
    fun detectPort(command: String): Int? {
        val portPatterns = listOf(
            Regex("""-p\s+(\d+)"""),
            Regex("""--port\s+(\d+)"""),
            Regex("""PORT=(\d+)"""),
            Regex(""":(\d{4,5})"""),
            Regex("""localhost:(\d+)"""),
        )
        for (pattern in portPatterns) {
            pattern.find(command)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        }
        // Default ports by command
        return when {
            command.contains("npm run dev") || command.contains("vite") -> 5173
            command.contains("next dev") -> 3000
            command.contains("flask") || command.contains("app.py") -> 5000
            command.contains("django") || command.contains("manage.py") -> 8000
            command.contains("http.server") || command.contains("serve") -> 8080
            command.contains("rails") -> 3000
            command.contains("cargo") -> 8080
            else -> null
        }
    }

    private fun updateProcess(id: String, update: ManagedProcess.() -> ManagedProcess) {
        _processes.value = _processes.value.map { if (it.id == id) it.update() else it }
    }

    fun cleanup() {
        stopAll()
        scope.cancel()
    }
}

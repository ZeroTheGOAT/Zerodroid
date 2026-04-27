package com.zerodroid.app.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zerodroid.app.data.AppPreferences
import com.zerodroid.app.terminal.LinuxEnvironment
import com.zerodroid.app.terminal.ProcessManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Full IDE Terminal ViewModel.
 *
 * Not just a basic shell — a complete development terminal:
 *   • Linux environment with PATH, HOME, env vars
 *   • Background process management (dev servers)
 *   • Package installation & dependency management
 *   • Local web server hosting
 *   • Live output streaming for long-running processes
 *   • Ctrl+C support for process interruption
 *   • Tab completion (basic)
 *   • Multiple sessions
 */
class TerminalViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = AppPreferences(application)
    val linuxEnv = LinuxEnvironment(application)
    val processManager = ProcessManager()

    // ─── Terminal output ────────────────────────
    data class TerminalEntry(
        val id: String = java.util.UUID.randomUUID().toString(),
        val command: String? = null,
        val output: String = "",
        val isError: Boolean = false,
        val exitCode: Int? = null,
        val timestamp: Long = System.currentTimeMillis(),
        val isSystem: Boolean = false,      // system messages (env bootstrap, etc.)
        val processId: String? = null,       // linked background process
    )

    private val _entries = MutableStateFlow<List<TerminalEntry>>(emptyList())
    val entries: StateFlow<List<TerminalEntry>> = _entries.asStateFlow()

    private val _currentInput = MutableStateFlow("")
    val currentInput: StateFlow<String> = _currentInput.asStateFlow()

    private val _cwd = MutableStateFlow(Environment.getExternalStorageDirectory().absolutePath)
    val cwd: StateFlow<String> = _cwd.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _commandHistory = MutableStateFlow<List<String>>(emptyList())
    private var historyIndex = -1

    // Active foreground process (can be Ctrl+C'd)
    private var activeProcess: Process? = null
    private var activeJob: Job? = null

    // ─── Quick actions panel ────────────────────
    private val _showQuickActions = MutableStateFlow(false)
    val showQuickActions: StateFlow<Boolean> = _showQuickActions.asStateFlow()

    // ─── Process viewer ─────────────────────────
    private val _showProcessManager = MutableStateFlow(false)
    val showProcessManager: StateFlow<Boolean> = _showProcessManager.asStateFlow()

    // File editor (kept from before)
    private val _editingFile = MutableStateFlow<String?>(null)
    val editingFile: StateFlow<String?> = _editingFile.asStateFlow()
    private val _editingContent = MutableStateFlow("")
    val editingContent: StateFlow<String> = _editingContent.asStateFlow()

    // ─── Tab completions ────────────────────────
    private val _completions = MutableStateFlow<List<String>>(emptyList())
    val completions: StateFlow<List<String>> = _completions.asStateFlow()

    init {
        viewModelScope.launch {
            prefs.terminalCwd.collect { _cwd.value = it }
        }
        // Bootstrap environment on first launch
        viewModelScope.launch {
            if (!linuxEnv.checkBootstrapped()) {
                addSystemEntry("⚡ Setting up ZeroDroid dev environment...")
                val ok = linuxEnv.bootstrap()
                if (ok) addSystemEntry("✅ Dev environment ready! Type 'help' for commands.")
                else addSystemEntry("⚠️ Environment setup had issues. Basic shell available.")
            } else {
                addSystemEntry(buildString {
                    appendLine("⚡ ZeroDroid IDE Terminal v2.0")
                    appendLine("  Type 'help' for commands • 'pkg install' for packages")
                    appendLine("  Type 'dev' to auto-start dev server • 'serve' for HTTP server")
                    appendLine("  Background processes: 'bg' command • View: 'processes'")
                })
            }
        }
    }

    fun updateInput(text: String) {
        _currentInput.value = text
        historyIndex = -1
        // Basic tab completion
        if (text.isNotBlank()) updateCompletions(text) else _completions.value = emptyList()
    }

    fun applyCompletion(completion: String) {
        _currentInput.value = completion
        _completions.value = emptyList()
    }

    fun historyUp() {
        val h = _commandHistory.value; if (h.isEmpty()) return
        historyIndex = (historyIndex + 1).coerceAtMost(h.size - 1)
        _currentInput.value = h[h.size - 1 - historyIndex]
    }
    fun historyDown() {
        if (historyIndex <= 0) { historyIndex = -1; _currentInput.value = ""; return }
        historyIndex--; _currentInput.value = _commandHistory.value[_commandHistory.value.size - 1 - historyIndex]
    }

    fun toggleQuickActions() { _showQuickActions.value = !_showQuickActions.value }
    fun toggleProcessManager() { _showProcessManager.value = !_showProcessManager.value }

    /**
     * Ctrl+C — interrupt the running foreground process
     */
    fun interruptProcess() {
        activeProcess?.destroyForcibly()
        activeJob?.cancel()
        _isRunning.value = false
        addEntry(TerminalEntry(output = "^C", isError = true))
    }

    fun executeCommand() {
        val cmd = _currentInput.value.trim()
        if (cmd.isBlank()) return

        // Allow Ctrl+C if running
        if (_isRunning.value) { interruptProcess(); return }

        _currentInput.value = ""
        _commandHistory.value = _commandHistory.value + cmd
        _completions.value = emptyList()
        historyIndex = -1

        // ─── Built-in commands ──────────────────
        when {
            cmd == "help" -> { showHelp(); return }
            cmd == "clear" || cmd == "cls" -> { _entries.value = emptyList(); return }
            cmd == "pwd" -> { addEntry(TerminalEntry(command = cmd, output = _cwd.value)); return }
            cmd == "env" -> { showEnvVars(cmd); return }
            cmd == "processes" || cmd == "ps-bg" -> { _showProcessManager.value = true; return }
            cmd == "exit" -> { addEntry(TerminalEntry(command = cmd, output = "Use back button or switch tabs")); return }

            cmd.startsWith("cd ") -> { handleCd(cmd); return }
            cmd.startsWith("edit ") -> { handleEdit(cmd); return }
            cmd.startsWith("open ") -> { handleOpen(cmd); return }

            // ─── Background process commands ────
            cmd.startsWith("bg ") -> { startBackgroundProcess(cmd.removePrefix("bg ").trim()); return }
            cmd.startsWith("serve") -> { startDevServer(cmd); return }
            cmd == "dev" -> { startSmartDev(); return }
            cmd.startsWith("stop ") -> { stopBgProcess(cmd.removePrefix("stop ").trim()); return }
            cmd == "stop-all" -> { processManager.stopAll(); addEntry(TerminalEntry(command = cmd, output = "⏹ All processes stopped")); return }

            // ─── Package management ─────────────
            cmd.startsWith("pkg ") || cmd.startsWith("apt ") -> { handlePackageManager(cmd); return }
            cmd.startsWith("npm ") -> { executeShellCommand(cmd); return }
            cmd.startsWith("pip ") || cmd.startsWith("pip3 ") -> { executeShellCommand(cmd); return }
            cmd.startsWith("git ") -> { executeShellCommand(cmd); return }
            cmd.startsWith("cargo ") -> { executeShellCommand(cmd); return }

            // ─── Project scaffolding ────────────
            cmd.startsWith("init-project ") || cmd.startsWith("newproject ") -> { executeShellCommand(cmd); return }
        }

        // Default: execute via shell with full env
        executeShellCommand(cmd)
    }

    // ─── Shell Execution with streaming output ──
    private fun executeShellCommand(cmd: String) {
        _isRunning.value = true
        addEntry(TerminalEntry(command = cmd, output = ""))

        activeJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val pb = linuxEnv.buildShellCommand(cmd, _cwd.value)
                    val process = pb.start()
                    activeProcess = process

                    // Stream output line by line
                    val output = StringBuilder()
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    var line: String?

                    while (reader.readLine().also { line = it } != null) {
                        output.appendLine(line)
                        // Update the last entry with growing output
                        updateLastEntry { copy(output = output.toString().trimEnd()) }
                    }

                    val exited = process.waitFor(120, java.util.concurrent.TimeUnit.SECONDS)
                    if (!exited) {
                        process.destroyForcibly()
                        output.appendLine("\n⚠️ Timed out after 120s")
                    }

                    val code = if (exited) process.exitValue() else -1
                    val finalOutput = output.toString().trimEnd().ifBlank {
                        if (code == 0) "✅ Done" else "⚠️ Exit: $code"
                    }

                    TerminalEntry(command = cmd, output = finalOutput, isError = code != 0, exitCode = code)
                } catch (e: Exception) {
                    TerminalEntry(command = cmd, output = "❌ ${e.message}", isError = true, exitCode = -1)
                } finally {
                    activeProcess = null
                }
            }

            // Replace the placeholder entry
            val list = _entries.value.toMutableList()
            if (list.isNotEmpty()) list[list.lastIndex] = result
            _entries.value = list
            _isRunning.value = false
        }
    }

    // ─── Background Process Commands ────────────

    private fun startBackgroundProcess(cmd: String) {
        val port = processManager.detectPort(cmd)
        val name = cmd.split(" ").firstOrNull() ?: "process"
        val id = processManager.startProcess(
            name = name, command = cmd, cwd = _cwd.value,
            env = linuxEnv.environmentVars, port = port,
        )
        addEntry(TerminalEntry(command = "bg $cmd",
            output = "🚀 Started '$name' in background${if (port != null) " → http://localhost:$port" else ""}\n   Process ID: ${id.take(8)}...\n   View: 'processes' | Stop: 'stop ${id.take(8)}'",
            processId = id))
    }

    private fun startDevServer(cmd: String) {
        val parts = cmd.split(" ")
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 8080
        val dir = parts.getOrNull(2) ?: _cwd.value

        if (processManager.isPortInUse(port)) {
            addEntry(TerminalEntry(command = cmd, output = "⚠️ Port $port already in use", isError = true))
            return
        }

        val serveCmd = "sh ${linuxEnv.binDir.absolutePath}/serve $port $dir"
        val id = processManager.startProcess(
            name = "HTTP Server", command = serveCmd, cwd = _cwd.value,
            env = linuxEnv.environmentVars, port = port,
        )
        addEntry(TerminalEntry(command = cmd,
            output = "🌐 HTTP Server started!\n   URL: http://localhost:$port\n   Dir: $dir\n   Stop: 'stop ${id.take(8)}'"))
    }

    private fun startSmartDev() {
        val cwdFile = File(_cwd.value)
        val devCmd = "sh ${linuxEnv.binDir.absolutePath}/dev"
        val port = when {
            File(cwdFile, "package.json").exists() -> 3000
            File(cwdFile, "manage.py").exists() -> 8000
            File(cwdFile, "app.py").exists() -> 5000
            File(cwdFile, "Cargo.toml").exists() -> 8080
            File(cwdFile, "index.html").exists() -> 8080
            else -> null
        }

        if (port != null) {
            val id = processManager.startProcess(
                name = "Dev Server", command = devCmd, cwd = _cwd.value,
                env = linuxEnv.environmentVars, port = port,
            )
            addEntry(TerminalEntry(command = "dev",
                output = "🚀 Dev server starting...\n   URL: http://localhost:$port\n   Stop: 'stop ${id.take(8)}'"))
        } else {
            // Run in foreground if not a server
            executeShellCommand(devCmd)
        }
    }

    private fun stopBgProcess(idPrefix: String) {
        val process = processManager.processes.value.find { it.id.startsWith(idPrefix) || it.name == idPrefix }
        if (process != null) {
            processManager.stopProcess(process.id)
            addEntry(TerminalEntry(command = "stop $idPrefix", output = "⏹ Stopped '${process.name}'"))
        } else {
            addEntry(TerminalEntry(command = "stop $idPrefix", output = "❌ No process found: $idPrefix", isError = true))
        }
    }

    // ─── Package Manager ────────────────────────
    private fun handlePackageManager(cmd: String) {
        val parts = cmd.split(" ")
        val action = parts.getOrNull(1) ?: "help"
        val packages = parts.drop(2)

        when (action) {
            "install" -> {
                if (packages.isEmpty()) {
                    addEntry(TerminalEntry(command = cmd, output = "Usage: pkg install <package> [package2...]", isError = true))
                    return
                }
                addEntry(TerminalEntry(command = cmd, output = "📦 Installing: ${packages.joinToString(", ")}..."))
                // Try real package installation via shell
                executeShellCommand(buildInstallCommand(packages))
            }
            "remove", "uninstall" -> {
                addEntry(TerminalEntry(command = cmd, output = "🗑️ Removing: ${packages.joinToString(", ")}"))
                executeShellCommand("rm -rf ${linuxEnv.binDir}/${packages.firstOrNull()}")
            }
            "list" -> executeShellCommand("ls -1 ${linuxEnv.binDir}")
            "search" -> {
                addEntry(TerminalEntry(command = cmd, output = "🔍 Available packages:\n" +
                    "  node, npm, python3, pip3, git, curl, wget, vim, nano,\n" +
                    "  gcc, g++, make, cmake, rust/cargo, go, ruby, php,\n" +
                    "  ffmpeg, imagemagick, sqlite3, redis, postgresql"))
            }
            else -> addEntry(TerminalEntry(command = cmd, output = "Usage: pkg [install|remove|list|search] [package]"))
        }
    }

    private fun buildInstallCommand(packages: List<String>): String {
        // Try different package managers in order of preference
        return """
            if command -v apt-get >/dev/null 2>&1; then
                apt-get install -y ${packages.joinToString(" ")}
            elif command -v pkg >/dev/null 2>&1; then
                pkg install -y ${packages.joinToString(" ")}
            elif command -v apk >/dev/null 2>&1; then
                apk add ${packages.joinToString(" ")}
            else
                echo "⚠️ No package manager found. Manual install required."
                echo "Try downloading binaries to ${linuxEnv.binDir.absolutePath}"
            fi
        """.trimIndent()
    }

    // ─── Built-in Commands ──────────────────────

    private fun handleCd(cmd: String) {
        val target = cmd.removePrefix("cd ").trim()
        val newDir = when {
            target == "~" -> linuxEnv.homeDir
            target == "-" -> File(Environment.getExternalStorageDirectory().absolutePath)
            target.startsWith("~/") -> File(linuxEnv.homeDir, target.removePrefix("~/"))
            target.startsWith("/") -> File(target)
            else -> File(_cwd.value, target).canonicalFile
        }
        if (newDir.exists() && newDir.isDirectory) {
            _cwd.value = newDir.absolutePath
            viewModelScope.launch { prefs.setTerminalCwd(newDir.absolutePath) }
            addEntry(TerminalEntry(command = cmd, output = "→ ${newDir.absolutePath}"))
        } else {
            addEntry(TerminalEntry(command = cmd, output = "cd: no such directory: $target", isError = true))
        }
    }

    private fun handleEdit(cmd: String) {
        val path = cmd.removePrefix("edit ").trim()
        val file = if (path.startsWith("/")) File(path) else File(_cwd.value, path)
        openEditor(file.absolutePath)
        addEntry(TerminalEntry(command = cmd, output = "📝 Opening editor: ${file.name}"))
    }

    private fun handleOpen(cmd: String) {
        val arg = cmd.removePrefix("open ").trim()
        if (arg.startsWith("http")) {
            // Open URL in browser
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(arg))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                getApplication<Application>().startActivity(intent)
                addEntry(TerminalEntry(command = cmd, output = "🌐 Opening in browser: $arg"))
            } catch (e: Exception) {
                addEntry(TerminalEntry(command = cmd, output = "❌ Can't open: ${e.message}", isError = true))
            }
        } else {
            handleEdit("edit $arg")
        }
    }

    private fun showHelp() {
        addEntry(TerminalEntry(command = "help", output = buildString {
            appendLine("═══════════════════════════════════════════")
            appendLine("  ⚡ ZeroDroid IDE Terminal — Commands")
            appendLine("═══════════════════════════════════════════")
            appendLine()
            appendLine("📁 FILE SYSTEM")
            appendLine("  cd <path>         Change directory (~ for home)")
            appendLine("  ls, ll, la        List files")
            appendLine("  pwd               Print working directory")
            appendLine("  cat <file>        View file")
            appendLine("  edit <file>       Open in editor")
            appendLine("  touch / mkdir     Create file / directory")
            appendLine("  rm / cp / mv      Remove / copy / move")
            appendLine("  find / grep       Search files / content")
            appendLine()
            appendLine("📦 PACKAGES")
            appendLine("  pkg install <p>   Install package")
            appendLine("  pkg remove <p>    Remove package")
            appendLine("  pkg list          List installed")
            appendLine("  pkg search        Search available")
            appendLine("  npm install       Install Node.js deps")
            appendLine("  pip install       Install Python deps")
            appendLine()
            appendLine("🚀 DEV SERVERS")
            appendLine("  dev               Auto-detect & start dev server")
            appendLine("  serve [port]      HTTP file server (default: 8080)")
            appendLine("  bg <command>      Run command in background")
            appendLine("  processes         View running processes")
            appendLine("  stop <id|name>    Stop background process")
            appendLine("  stop-all          Stop all processes")
            appendLine("  open <url>        Open URL in browser")
            appendLine()
            appendLine("🏗️ PROJECT")
            appendLine("  init-project <type> <name>")
            appendLine("    Types: node, python, web, basic")
            appendLine("  git-init          Initialize git repo")
            appendLine("  git <command>     Git version control")
            appendLine()
            appendLine("⌨️ TERMINAL")
            appendLine("  clear / cls       Clear screen")
            appendLine("  env               Show environment variables")
            appendLine("  history           Command history")
            appendLine("  Ctrl+C            Interrupt running process")
            appendLine("  ↑ ↓               Navigate history")
            appendLine()
            appendLine("💡 Any other command runs via /system/bin/sh")
            appendLine("═══════════════════════════════════════════")
        }))
    }

    private fun showEnvVars(cmd: String) {
        addEntry(TerminalEntry(command = cmd, output = linuxEnv.environmentVars.entries
            .sortedBy { it.key }
            .joinToString("\n") { "${it.key}=${it.value}" }))
    }

    // ─── Tab Completion ─────────────────────────
    private fun updateCompletions(input: String) {
        viewModelScope.launch {
            val suggestions = withContext(Dispatchers.IO) {
                val parts = input.split(" ")
                when {
                    parts.size == 1 -> {
                        // Command completion
                        val builtins = listOf("cd", "ls", "pwd", "cat", "edit", "mkdir", "touch", "rm", "cp", "mv",
                            "find", "grep", "clear", "help", "env", "serve", "dev", "bg", "stop", "stop-all",
                            "processes", "pkg", "npm", "pip", "pip3", "git", "open", "init-project",
                            "python3", "node", "cargo", "go", "curl", "wget", "history")
                        builtins.filter { it.startsWith(input) }.take(8)
                    }
                    parts[0] in listOf("cd", "cat", "edit", "open", "rm", "cp", "mv") -> {
                        // Path completion
                        val partial = parts.last()
                        val dir = if (partial.contains("/")) {
                            val base = partial.substringBeforeLast("/")
                            if (base.startsWith("/")) File(base) else File(_cwd.value, base)
                        } else File(_cwd.value)
                        val prefix = partial.substringAfterLast("/")
                        try {
                            dir.listFiles()?.filter { it.name.startsWith(prefix, ignoreCase = true) }
                                ?.take(8)?.map {
                                    val path = parts.dropLast(1).joinToString(" ") + " " +
                                        partial.substringBeforeLast("/", "").let { p ->
                                            if (p.isNotEmpty()) "$p/${it.name}" else it.name
                                        } + if (it.isDirectory) "/" else ""
                                    path.trim()
                                } ?: emptyList()
                        } catch (_: Exception) { emptyList() }
                    }
                    parts[0] == "pkg" && parts.size == 2 -> {
                        listOf("install", "remove", "list", "search", "update")
                            .filter { it.startsWith(parts[1]) }.map { "pkg $it" }
                    }
                    parts[0] == "git" && parts.size == 2 -> {
                        listOf("status", "add", "commit", "push", "pull", "log", "diff", "branch", "checkout", "merge", "stash", "init", "clone")
                            .filter { it.startsWith(parts[1]) }.map { "git $it" }
                    }
                    parts[0] == "npm" && parts.size == 2 -> {
                        listOf("install", "run", "start", "init", "test", "build", "dev", "uninstall", "list", "outdated")
                            .filter { it.startsWith(parts[1]) }.map { "npm $it" }
                    }
                    else -> emptyList()
                }
            }
            _completions.value = suggestions
        }
    }

    // ─── File Editor ────────────────────────────
    fun openEditor(path: String) {
        viewModelScope.launch {
            val content = withContext(Dispatchers.IO) {
                try {
                    val f = File(path); if (!f.exists()) { f.createNewFile(); "" }
                    else if (f.length() > 500_000) "⚠️ File too large to edit"
                    else f.readText()
                } catch (e: Exception) { "❌ Error: ${e.message}" }
            }
            _editingFile.value = path; _editingContent.value = content
        }
    }
    fun updateEditorContent(content: String) { _editingContent.value = content }
    fun saveFile() {
        val path = _editingFile.value ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try { File(path).writeText(_editingContent.value) }
                catch (e: Exception) { addEntry(TerminalEntry(output = "❌ Save failed: ${e.message}", isError = true)) }
            }
            addEntry(TerminalEntry(output = "💾 Saved: ${File(path).name}"))
            closeEditor()
        }
    }
    fun closeEditor() { _editingFile.value = null; _editingContent.value = "" }

    // ─── Helpers ────────────────────────────────
    private fun addEntry(entry: TerminalEntry) { _entries.value = _entries.value + entry }
    private fun addSystemEntry(msg: String) { _entries.value = _entries.value + TerminalEntry(output = msg, isSystem = true) }
    private fun updateLastEntry(transform: TerminalEntry.() -> TerminalEntry) {
        val list = _entries.value.toMutableList()
        if (list.isNotEmpty()) list[list.lastIndex] = list.last().transform()
        _entries.value = list
    }

    override fun onCleared() {
        super.onCleared()
        processManager.cleanup()
    }
}

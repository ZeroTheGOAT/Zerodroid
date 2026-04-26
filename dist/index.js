#!/usr/bin/env node

// src/cli/index.ts
import { Command } from "commander";

// src/utils/logger.ts
import chalk from "chalk";
var BRAND = chalk.bold.hex("#00E5FF");
var DIM = chalk.dim;
var SUCCESS = chalk.green;
var ERROR = chalk.red;
var WARN = chalk.yellow;
var INFO = chalk.cyan;
var AI = chalk.hex("#A78BFA");
var log = {
  brand(msg) {
    console.log(BRAND(`\u26A1 ${msg}`));
  },
  info(msg) {
    console.log(INFO(`\u2139 ${msg}`));
  },
  success(msg) {
    console.log(SUCCESS(`\u2705 ${msg}`));
  },
  error(msg) {
    console.log(ERROR(`\u274C ${msg}`));
  },
  warn(msg) {
    console.log(WARN(`\u26A0\uFE0F  ${msg}`));
  },
  dim(msg) {
    console.log(DIM(msg));
  },
  ai(msg) {
    console.log(AI(`\u{1F916} ${msg}`));
  },
  /** Print a step in the agent's execution */
  step(action, detail) {
    console.log(`  ${chalk.hex("#FFD600")("\u25B6")} ${chalk.bold(action)} ${DIM(detail)}`);
  },
  /** Print file operation */
  file(action, path) {
    const icons = {
      create: "\u{1F4C4}",
      edit: "\u270F\uFE0F",
      delete: "\u{1F5D1}\uFE0F",
      read: "\u{1F441}\uFE0F"
    };
    console.log(`  ${icons[action]} ${chalk.bold(action)} ${DIM(path)}`);
  },
  /** Print shell command */
  shell(cmd) {
    console.log(`  ${chalk.hex("#FF6B6B")("$")} ${chalk.italic(cmd)}`);
  },
  /** Print a divider line */
  divider() {
    console.log(DIM("\u2500".repeat(50)));
  },
  /** Blank line */
  blank() {
    console.log("");
  },
  /** Stream a chunk of AI response text (no newline) */
  stream(chunk) {
    process.stdout.write(AI(chunk));
  }
};

// src/utils/config.ts
import { existsSync as existsSync2, mkdirSync, readFileSync, writeFileSync } from "fs";
import { join } from "path";

// src/utils/detect-env.ts
import { execSync } from "child_process";
import { existsSync } from "fs";
import { platform, arch, homedir, totalmem } from "os";
function commandExists(cmd) {
  try {
    execSync(`which ${cmd}`, { stdio: "ignore" });
    return true;
  } catch {
    try {
      execSync(`where ${cmd}`, { stdio: "ignore" });
      return true;
    } catch {
      return false;
    }
  }
}
function isTermux() {
  return existsSync("/data/data/com.termux") || !!process.env.TERMUX_VERSION || !!process.env.PREFIX?.includes("com.termux");
}
function isWSL() {
  if (platform() !== "linux") return false;
  try {
    const release = execSync("uname -r", { encoding: "utf-8" }).toLowerCase();
    return release.includes("microsoft") || release.includes("wsl");
  } catch {
    return false;
  }
}
function getShell() {
  return process.env.SHELL || process.env.COMSPEC || "/bin/sh";
}
function detectEnvironment() {
  let env;
  if (isTermux()) {
    env = "termux";
  } else if (platform() === "darwin") {
    env = "macos";
  } else if (platform() === "win32") {
    env = "windows";
  } else if (isWSL()) {
    env = "wsl";
  } else {
    env = "linux";
  }
  const totalRAM = Math.round(totalmem() / 1024 / 1024 / 1024 * 10) / 10;
  return {
    env,
    arch: arch(),
    platform: platform(),
    home: homedir(),
    shell: getShell(),
    totalRAM,
    is64bit: arch() === "arm64" || arch() === "x64",
    hasNode: commandExists("node"),
    hasPython: commandExists("python3") || commandExists("python"),
    hasGit: commandExists("git"),
    hasOllama: commandExists("ollama"),
    termuxStorage: env === "termux" && existsSync(`${homedir()}/storage`)
  };
}
function getConfigDir() {
  return `${homedir()}/.zerodroid`;
}

// src/utils/config.ts
var DEFAULT_CONFIG = {
  provider: "gemini",
  ollama: {
    host: "http://localhost:11434",
    model: "gemma4:e2b"
  },
  gemini: {
    apiKey: "",
    model: "gemini-2.5-flash"
  },
  claude: {
    apiKey: "",
    model: "claude-sonnet-4-20250514"
  },
  openai: {
    apiKey: "",
    model: "gpt-4.1"
  },
  openrouter: {
    apiKey: "",
    model: "google/gemini-2.5-flash"
  },
  projectsDir: "~/zerodroid-projects",
  userName: "",
  autoSummarize: true,
  maxTurnsBeforeSummary: 50
};
function getConfigPath() {
  return join(getConfigDir(), "config.json");
}
function ensureConfigDir() {
  const dir = getConfigDir();
  if (!existsSync2(dir)) {
    mkdirSync(dir, { recursive: true });
  }
  const memoryDir = join(dir, "memory");
  if (!existsSync2(memoryDir)) {
    mkdirSync(memoryDir, { recursive: true });
  }
  const projectsMemoryDir = join(memoryDir, "projects");
  if (!existsSync2(projectsMemoryDir)) {
    mkdirSync(projectsMemoryDir, { recursive: true });
  }
}
function loadConfig() {
  ensureConfigDir();
  const configPath = getConfigPath();
  if (!existsSync2(configPath)) {
    saveConfig(DEFAULT_CONFIG);
    return { ...DEFAULT_CONFIG };
  }
  try {
    const raw = readFileSync(configPath, "utf-8");
    const parsed = JSON.parse(raw);
    return { ...DEFAULT_CONFIG, ...parsed };
  } catch {
    return { ...DEFAULT_CONFIG };
  }
}
function saveConfig(config) {
  ensureConfigDir();
  const configPath = getConfigPath();
  writeFileSync(configPath, JSON.stringify(config, null, 2), "utf-8");
}

// src/cli/chat.ts
import { createInterface } from "readline";

// src/agent/prompts/system.ts
function getSystemPrompt(cwd, projectContext, userName) {
  const userRef = userName ? `The user's name is ${userName}. ` : "";
  return `You are ZeroDroid, an open-source AI coding agent. You help users build complete software projects \u2014 websites, APIs, mobile apps, scripts, and anything else \u2014 directly from the terminal.

${userRef}You are currently working in: ${cwd}

## Your Capabilities
- Create, read, edit, and delete files
- Run any shell command (npm, pip, git, etc.)
- Install dependencies
- Start dev servers
- Build complete projects from scratch
- Debug and fix errors
- Manage git repositories

## Rules
1. ALWAYS use tools to take action. Never just describe what you would do \u2014 DO IT.
2. When creating a project, create ALL necessary files (package.json, config files, source code, etc.)
3. After writing code, run the appropriate install commands (npm install, pip install, etc.)
4. If a command fails, read the error, fix the issue, and retry.
5. Keep responses concise. Focus on actions, not explanations.
6. When you create a web project, tell the user how to run it (the command and the URL).
7. Create production-quality code with proper error handling, modern patterns, and clean structure.
8. Use modern frameworks and best practices (React 19, Next.js 15, Vite 6, etc.)

## File Structure Convention
- Place projects in the current working directory
- Use standard project structures for each framework
- Always include a README.md with setup instructions

${projectContext ? `## Project Context (from memory)
${projectContext}
` : ""}

Respond in this format:
1. Briefly state what you'll do (1-2 sentences max)
2. Use tools to execute the plan
3. Summarize what was done and next steps`;
}
var TOOL_DEFINITIONS = [
  {
    type: "function",
    function: {
      name: "file_write",
      description: "Create or overwrite a file with the given content. Parent directories are created automatically.",
      parameters: {
        type: "object",
        properties: {
          path: {
            type: "string",
            description: "File path relative to the current working directory"
          },
          content: {
            type: "string",
            description: "The complete file content to write"
          }
        },
        required: ["path", "content"]
      }
    }
  },
  {
    type: "function",
    function: {
      name: "file_read",
      description: "Read the contents of a file.",
      parameters: {
        type: "object",
        properties: {
          path: {
            type: "string",
            description: "File path relative to the current working directory"
          }
        },
        required: ["path"]
      }
    }
  },
  {
    type: "function",
    function: {
      name: "file_list",
      description: "List the contents of a directory as a tree structure. Use this to understand the project structure.",
      parameters: {
        type: "object",
        properties: {
          path: {
            type: "string",
            description: 'Directory path relative to the current working directory. Use "." for current directory.'
          },
          depth: {
            type: "number",
            description: "Maximum depth to recurse into subdirectories. Default is 3."
          }
        },
        required: ["path"]
      }
    }
  },
  {
    type: "function",
    function: {
      name: "shell_exec",
      description: "Execute a shell command and return the output. Use for installing packages, running scripts, git operations, starting servers, etc.",
      parameters: {
        type: "object",
        properties: {
          command: {
            type: "string",
            description: "The shell command to execute"
          },
          timeout: {
            type: "number",
            description: "Timeout in milliseconds. Default is 60000 (60 seconds). Use higher values for npm install, builds, etc."
          }
        },
        required: ["command"]
      }
    }
  }
];

// src/agent/tools/file-read.ts
import { readFileSync as readFileSync2, existsSync as existsSync3 } from "fs";
import { resolve } from "path";
function fileRead(filePath, cwd) {
  const fullPath = resolve(cwd, filePath);
  if (!existsSync3(fullPath)) {
    return `Error: File not found: ${fullPath}`;
  }
  try {
    const content = readFileSync2(fullPath, "utf-8");
    return content;
  } catch (err) {
    return `Error reading file: ${err.message}`;
  }
}

// src/agent/tools/file-write.ts
import { writeFileSync as writeFileSync2, mkdirSync as mkdirSync2, existsSync as existsSync4 } from "fs";
import { resolve as resolve2, dirname } from "path";
function fileWrite(filePath, content, cwd) {
  const fullPath = resolve2(cwd, filePath);
  try {
    const dir = dirname(fullPath);
    if (!existsSync4(dir)) {
      mkdirSync2(dir, { recursive: true });
    }
    writeFileSync2(fullPath, content, "utf-8");
    return `File written: ${fullPath}`;
  } catch (err) {
    return `Error writing file: ${err.message}`;
  }
}

// src/agent/tools/file-list.ts
import { readdirSync, statSync, existsSync as existsSync5 } from "fs";
import { join as join2, relative } from "path";
var IGNORED_DIRS = /* @__PURE__ */ new Set([
  "node_modules",
  ".git",
  "__pycache__",
  ".next",
  ".cache",
  "dist",
  "build",
  ".expo",
  ".vscode",
  ".idea",
  "venv",
  "env",
  ".env",
  "coverage"
]);
function listRecursive(dirPath, depth, maxDepth) {
  if (depth >= maxDepth) return [];
  try {
    const entries = readdirSync(dirPath, { withFileTypes: true });
    const result = [];
    for (const entry of entries) {
      if (entry.name.startsWith(".") && depth > 0) continue;
      if (IGNORED_DIRS.has(entry.name)) continue;
      const fullPath = join2(dirPath, entry.name);
      if (entry.isDirectory()) {
        result.push({
          name: entry.name,
          type: "directory",
          children: listRecursive(fullPath, depth + 1, maxDepth)
        });
      } else {
        try {
          const stats = statSync(fullPath);
          result.push({
            name: entry.name,
            type: "file",
            size: stats.size
          });
        } catch {
          result.push({ name: entry.name, type: "file" });
        }
      }
    }
    return result.sort((a, b) => {
      if (a.type !== b.type) return a.type === "directory" ? -1 : 1;
      return a.name.localeCompare(b.name);
    });
  } catch {
    return [];
  }
}
function formatTree(entries, prefix = "") {
  const lines = [];
  for (let i = 0; i < entries.length; i++) {
    const entry = entries[i];
    const isLast = i === entries.length - 1;
    const connector = isLast ? "\u2514\u2500\u2500 " : "\u251C\u2500\u2500 ";
    const childPrefix = isLast ? "    " : "\u2502   ";
    if (entry.type === "directory") {
      lines.push(`${prefix}${connector}\u{1F4C1} ${entry.name}/`);
      if (entry.children && entry.children.length > 0) {
        lines.push(formatTree(entry.children, prefix + childPrefix));
      }
    } else {
      lines.push(`${prefix}${connector}${entry.name}`);
    }
  }
  return lines.join("\n");
}
function fileList(dirPath, cwd, maxDepth = 3) {
  const fullPath = join2(cwd, dirPath);
  if (!existsSync5(fullPath)) {
    return `Error: Directory not found: ${fullPath}`;
  }
  const entries = listRecursive(fullPath, 0, maxDepth);
  if (entries.length === 0) {
    return `Directory is empty: ${fullPath}`;
  }
  const header = `\u{1F4C2} ${relative(cwd, fullPath) || "."}`;
  return `${header}
${formatTree(entries)}`;
}

// src/agent/tools/shell-exec.ts
import { execSync as execSync2, spawn } from "child_process";
import treeKill from "tree-kill";
var MAX_OUTPUT_LENGTH = 8e3;
function shellExec(command, cwd, timeoutMs = 6e4) {
  try {
    const stdout = execSync2(command, {
      cwd,
      encoding: "utf-8",
      timeout: timeoutMs,
      maxBuffer: 1024 * 1024 * 10,
      // 10MB
      stdio: ["pipe", "pipe", "pipe"],
      shell: true
    });
    const trimmed = stdout.length > MAX_OUTPUT_LENGTH ? stdout.slice(0, MAX_OUTPUT_LENGTH) + "\n... (output truncated)" : stdout;
    return {
      stdout: trimmed,
      stderr: "",
      exitCode: 0,
      success: true
    };
  } catch (err) {
    const error = err;
    const stdout = (error.stdout || "").toString();
    const stderr = (error.stderr || error.message || "").toString();
    const trimmedStdout = stdout.length > MAX_OUTPUT_LENGTH ? stdout.slice(0, MAX_OUTPUT_LENGTH) + "\n... (output truncated)" : stdout;
    const trimmedStderr = stderr.length > MAX_OUTPUT_LENGTH ? stderr.slice(0, MAX_OUTPUT_LENGTH) + "\n... (output truncated)" : stderr;
    return {
      stdout: trimmedStdout,
      stderr: trimmedStderr,
      exitCode: error.status ?? 1,
      success: false
    };
  }
}

// src/memory/store.ts
import { existsSync as existsSync6, mkdirSync as mkdirSync3, readFileSync as readFileSync3, writeFileSync as writeFileSync3 } from "fs";
import { join as join3, basename } from "path";
var DEFAULT_GLOBAL = {
  userName: "",
  preferences: [],
  facts: [],
  lastUpdated: (/* @__PURE__ */ new Date()).toISOString()
};
function getProjectMemoryDir() {
  return join3(getConfigDir(), "memory", "projects");
}
function getProjectMemoryPath(projectDir) {
  const projectName = basename(projectDir);
  return join3(getProjectMemoryDir(), `${projectName}.json`);
}
function loadProjectMemory(projectDir) {
  const path = getProjectMemoryPath(projectDir);
  if (!existsSync6(path)) return null;
  try {
    return JSON.parse(readFileSync3(path, "utf-8"));
  } catch {
    return null;
  }
}
function saveProjectMemory(memory) {
  const dir = getProjectMemoryDir();
  if (!existsSync6(dir)) mkdirSync3(dir, { recursive: true });
  const path = getProjectMemoryPath(memory.rootDir);
  memory.lastAccessed = (/* @__PURE__ */ new Date()).toISOString();
  writeFileSync3(path, JSON.stringify(memory, null, 2), "utf-8");
}
function createProjectMemory(projectDir, description = "") {
  const memory = {
    name: basename(projectDir),
    rootDir: projectDir,
    description,
    techStack: [],
    decisions: [],
    lastAccessed: (/* @__PURE__ */ new Date()).toISOString(),
    history: []
  };
  saveProjectMemory(memory);
  return memory;
}
function appendToHistory(projectDir, messages) {
  let memory = loadProjectMemory(projectDir);
  if (!memory) {
    memory = createProjectMemory(projectDir);
  }
  memory.history.push(...messages);
  if (memory.history.length > 100) {
    memory.history = memory.history.slice(-100);
  }
  saveProjectMemory(memory);
}
function getProjectContext(projectDir) {
  const memory = loadProjectMemory(projectDir);
  if (!memory) return void 0;
  const parts = [];
  if (memory.description) {
    parts.push(`Project: ${memory.name} \u2014 ${memory.description}`);
  }
  if (memory.techStack.length > 0) {
    parts.push(`Tech Stack: ${memory.techStack.join(", ")}`);
  }
  if (memory.decisions.length > 0) {
    parts.push(`Key Decisions:
${memory.decisions.map((d) => `  - ${d}`).join("\n")}`);
  }
  if (memory.history.length > 0) {
    const recent = memory.history.slice(-6);
    const summary = recent.filter((m) => m.role === "user" || m.role === "assistant").map((m) => `  ${m.role}: ${m.content.slice(0, 200)}`).join("\n");
    parts.push(`Recent conversation:
${summary}`);
  }
  return parts.length > 0 ? parts.join("\n\n") : void 0;
}

// src/agent/core.ts
var MAX_TOOL_ITERATIONS = 15;
function executeTool(name, args, cwd) {
  switch (name) {
    case "file_write": {
      const path = args.path;
      const content = args.content;
      log.file("create", path);
      return fileWrite(path, content, cwd);
    }
    case "file_read": {
      const path = args.path;
      log.file("read", path);
      return fileRead(path, cwd);
    }
    case "file_list": {
      const path = args.path || ".";
      const depth = args.depth || 3;
      return fileList(path, cwd, depth);
    }
    case "shell_exec": {
      const command = args.command;
      const timeout = args.timeout || 6e4;
      log.shell(command);
      const result = shellExec(command, cwd, timeout);
      const output = result.stdout + (result.stderr ? `
STDERR: ${result.stderr}` : "");
      if (!result.success) {
        log.error(`Command failed (exit ${result.exitCode})`);
      }
      return output || "(no output)";
    }
    default:
      return `Unknown tool: ${name}`;
  }
}
function processToolCalls(toolCalls, cwd) {
  const toolMessages = [];
  for (const tc of toolCalls) {
    let args;
    try {
      args = JSON.parse(tc.function.arguments);
    } catch {
      args = {};
    }
    const result = executeTool(tc.function.name, args, cwd);
    toolMessages.push({
      role: "tool",
      content: result,
      tool_call_id: tc.id
    });
  }
  return toolMessages;
}
async function runAgent(userPrompt, options) {
  const { provider, cwd } = options;
  const config = loadConfig();
  const projectContext = getProjectContext(cwd);
  const systemPrompt = getSystemPrompt(cwd, projectContext, config.userName);
  const messages = [
    { role: "system", content: systemPrompt },
    { role: "user", content: userPrompt }
  ];
  let iterations = 0;
  let finalResponse = "";
  while (iterations < MAX_TOOL_ITERATIONS) {
    iterations++;
    try {
      const result = await provider.complete({
        messages,
        tools: TOOL_DEFINITIONS,
        temperature: 0.4,
        maxTokens: 8192
      });
      if (result.content) {
        log.blank();
        log.ai(result.content);
        finalResponse = result.content;
      }
      if (result.toolCalls && result.toolCalls.length > 0) {
        messages.push({
          role: "assistant",
          content: result.content || "",
          tool_calls: result.toolCalls
        });
        log.blank();
        const toolResults = processToolCalls(result.toolCalls, cwd);
        messages.push(...toolResults);
        continue;
      }
      break;
    } catch (err) {
      log.error(`Agent error: ${err.message}`);
      finalResponse = `Error: ${err.message}`;
      break;
    }
  }
  if (iterations >= MAX_TOOL_ITERATIONS) {
    log.warn("Reached maximum tool iterations. Stopping.");
  }
  try {
    let memory = loadProjectMemory(cwd);
    if (!memory) {
      memory = createProjectMemory(cwd);
    }
    appendToHistory(cwd, [
      { role: "user", content: userPrompt },
      { role: "assistant", content: finalResponse }
    ]);
    detectTechStack(messages, memory);
    saveProjectMemory(memory);
  } catch {
  }
  return finalResponse;
}
function detectTechStack(messages, memory) {
  if (!memory) return;
  const allContent = messages.map((m) => m.content).join(" ").toLowerCase();
  const techMap = {
    "create-react-app": "React",
    "create-vite": "Vite",
    "create-next-app": "Next.js",
    "npx expo": "React Native (Expo)",
    "flask": "Flask",
    "fastapi": "FastAPI",
    "express": "Express.js",
    "django": "Django",
    "tailwindcss": "TailwindCSS",
    "typescript": "TypeScript",
    "prisma": "Prisma",
    "mongodb": "MongoDB",
    "postgresql": "PostgreSQL"
  };
  for (const [keyword, tech] of Object.entries(techMap)) {
    if (allContent.includes(keyword) && !memory.techStack.includes(tech)) {
      memory.techStack.push(tech);
    }
  }
}

// src/cli/chat.ts
async function startChat(provider, cwd) {
  log.blank();
  log.brand("ZeroDroid \u2014 AI Coding Agent");
  log.dim(`Provider: ${provider.name} | Working in: ${cwd}`);
  log.dim('Type your request, or "exit" to quit. Use "clear" to reset context.');
  log.divider();
  log.blank();
  const rl = createInterface({
    input: process.stdin,
    output: process.stdout,
    prompt: "\x1B[38;2;0;229;255m\u276F \x1B[0m"
  });
  rl.prompt();
  rl.on("line", async (line) => {
    const input = line.trim();
    if (!input) {
      rl.prompt();
      return;
    }
    if (input.toLowerCase() === "exit" || input.toLowerCase() === "quit") {
      log.blank();
      log.brand("See you later! \u{1F44B}");
      process.exit(0);
    }
    if (input.toLowerCase() === "clear") {
      console.clear();
      log.brand("Context cleared.");
      log.blank();
      rl.prompt();
      return;
    }
    if (input.toLowerCase() === "help") {
      log.blank();
      log.info("Commands:");
      log.dim("  exit / quit    \u2014 Exit ZeroDroid");
      log.dim("  clear          \u2014 Clear the screen");
      log.dim("  help           \u2014 Show this help");
      log.dim("");
      log.info("Usage:");
      log.dim("  Just type what you want to build or do!");
      log.dim('  Example: "Create a React portfolio with dark mode"');
      log.dim('  Example: "Fix the bug in server.js"');
      log.dim('  Example: "Add authentication to this Express app"');
      log.blank();
      rl.prompt();
      return;
    }
    try {
      await runAgent(input, { provider, cwd });
    } catch (err) {
      log.error(`Error: ${err.message}`);
    }
    log.blank();
    rl.prompt();
  });
  rl.on("close", () => {
    log.blank();
    log.brand("See you later! \u{1F44B}");
    process.exit(0);
  });
}

// src/agent/providers/ollama.ts
var OllamaProvider = class {
  name = "ollama";
  host;
  model;
  constructor(host = "http://localhost:11434", model = "gemma4:e2b") {
    this.host = host.replace(/\/$/, "");
    this.model = model;
  }
  async isAvailable() {
    try {
      const res = await fetch(`${this.host}/api/version`);
      return res.ok;
    } catch {
      return false;
    }
  }
  formatMessages(messages) {
    return messages.map((m) => ({
      role: m.role === "tool" ? "user" : m.role,
      content: m.content
    }));
  }
  formatTools(tools) {
    if (!tools || tools.length === 0) return void 0;
    return tools.map((t) => ({
      type: "function",
      function: {
        name: t.function.name,
        description: t.function.description,
        parameters: t.function.parameters
      }
    }));
  }
  async complete(options) {
    const body = {
      model: this.model,
      messages: this.formatMessages(options.messages),
      stream: false,
      options: {
        temperature: options.temperature ?? 0.7,
        num_predict: options.maxTokens ?? 4096
      }
    };
    const formattedTools = this.formatTools(options.tools);
    if (formattedTools) {
      body.tools = formattedTools;
    }
    const res = await fetch(`${this.host}/api/chat`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body)
    });
    if (!res.ok) {
      const errText = await res.text();
      throw new Error(`Ollama error (${res.status}): ${errText}`);
    }
    const data = await res.json();
    const toolCalls = [];
    if (data.message?.tool_calls) {
      for (const tc of data.message.tool_calls) {
        toolCalls.push({
          id: `call_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
          type: "function",
          function: {
            name: tc.function.name,
            arguments: JSON.stringify(tc.function.arguments)
          }
        });
      }
    }
    return {
      content: data.message?.content || "",
      toolCalls: toolCalls.length > 0 ? toolCalls : void 0,
      finishReason: toolCalls.length > 0 ? "tool_calls" : "stop",
      usage: {
        promptTokens: data.prompt_eval_count || 0,
        completionTokens: data.eval_count || 0,
        totalTokens: (data.prompt_eval_count || 0) + (data.eval_count || 0)
      }
    };
  }
  async *stream(options) {
    const body = {
      model: this.model,
      messages: this.formatMessages(options.messages),
      stream: true,
      options: {
        temperature: options.temperature ?? 0.7,
        num_predict: options.maxTokens ?? 4096
      }
    };
    const formattedTools = this.formatTools(options.tools);
    if (formattedTools) {
      body.tools = formattedTools;
    }
    const res = await fetch(`${this.host}/api/chat`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body)
    });
    if (!res.ok) {
      const errText = await res.text();
      throw new Error(`Ollama stream error (${res.status}): ${errText}`);
    }
    if (!res.body) {
      throw new Error("No response body for streaming");
    }
    const decoder = new TextDecoder();
    const reader = res.body.getReader();
    try {
      let buffer = "";
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split("\n");
        buffer = lines.pop() || "";
        for (const line of lines) {
          if (!line.trim()) continue;
          try {
            const parsed = JSON.parse(line);
            yield {
              content: parsed.message?.content || "",
              done: parsed.done || false
            };
          } catch {
          }
        }
      }
    } finally {
      reader.releaseLock();
    }
  }
};

// src/agent/providers/gemini.ts
var GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta/models";
var GeminiProvider = class {
  name = "gemini";
  apiKey;
  model;
  constructor(apiKey, model = "gemini-2.5-flash") {
    this.apiKey = apiKey;
    this.model = model;
  }
  async isAvailable() {
    if (!this.apiKey) return false;
    try {
      const res = await fetch(
        `${GEMINI_BASE}/${this.model}?key=${this.apiKey}`
      );
      return res.ok;
    } catch {
      return false;
    }
  }
  convertMessages(messages) {
    const systemParts = [];
    const contents = [];
    for (const msg of messages) {
      if (msg.role === "system") {
        systemParts.push(msg.content);
        continue;
      }
      const role = msg.role === "assistant" ? "model" : "user";
      contents.push({
        role,
        parts: [{ text: msg.content }]
      });
    }
    return {
      systemInstruction: systemParts.length > 0 ? { parts: [{ text: systemParts.join("\n\n") }] } : void 0,
      contents
    };
  }
  convertTools(tools) {
    if (!tools || tools.length === 0) return void 0;
    return [
      {
        functionDeclarations: tools.map((t) => ({
          name: t.function.name,
          description: t.function.description,
          parameters: t.function.parameters
        }))
      }
    ];
  }
  async complete(options) {
    const { systemInstruction, contents } = this.convertMessages(options.messages);
    const body = {
      contents,
      generationConfig: {
        temperature: options.temperature ?? 0.7,
        maxOutputTokens: options.maxTokens ?? 8192
      }
    };
    if (systemInstruction) {
      body.systemInstruction = systemInstruction;
    }
    const geminiTools = this.convertTools(options.tools);
    if (geminiTools) {
      body.tools = geminiTools;
    }
    const url = `${GEMINI_BASE}/${this.model}:generateContent?key=${this.apiKey}`;
    const res = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body)
    });
    if (!res.ok) {
      const errText = await res.text();
      throw new Error(`Gemini error (${res.status}): ${errText}`);
    }
    const data = await res.json();
    const candidate = data.candidates?.[0];
    const parts = candidate?.content?.parts || [];
    let content = "";
    const toolCalls = [];
    for (const part of parts) {
      if (part.text) {
        content += part.text;
      }
      if (part.functionCall) {
        toolCalls.push({
          id: `call_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
          type: "function",
          function: {
            name: part.functionCall.name,
            arguments: JSON.stringify(part.functionCall.args)
          }
        });
      }
    }
    return {
      content,
      toolCalls: toolCalls.length > 0 ? toolCalls : void 0,
      finishReason: toolCalls.length > 0 ? "tool_calls" : "stop",
      usage: {
        promptTokens: data.usageMetadata?.promptTokenCount || 0,
        completionTokens: data.usageMetadata?.candidatesTokenCount || 0,
        totalTokens: data.usageMetadata?.totalTokenCount || 0
      }
    };
  }
  async *stream(options) {
    const { systemInstruction, contents } = this.convertMessages(options.messages);
    const body = {
      contents,
      generationConfig: {
        temperature: options.temperature ?? 0.7,
        maxOutputTokens: options.maxTokens ?? 8192
      }
    };
    if (systemInstruction) {
      body.systemInstruction = systemInstruction;
    }
    const geminiTools = this.convertTools(options.tools);
    if (geminiTools) {
      body.tools = geminiTools;
    }
    const url = `${GEMINI_BASE}/${this.model}:streamGenerateContent?alt=sse&key=${this.apiKey}`;
    const res = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body)
    });
    if (!res.ok) {
      const errText = await res.text();
      throw new Error(`Gemini stream error (${res.status}): ${errText}`);
    }
    if (!res.body) {
      throw new Error("No response body for streaming");
    }
    const decoder = new TextDecoder();
    const reader = res.body.getReader();
    try {
      let buffer = "";
      while (true) {
        const { done, value } = await reader.read();
        if (done) {
          yield { done: true };
          break;
        }
        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split("\n");
        buffer = lines.pop() || "";
        for (const line of lines) {
          if (!line.startsWith("data: ")) continue;
          const jsonStr = line.slice(6).trim();
          if (!jsonStr || jsonStr === "[DONE]") continue;
          try {
            const parsed = JSON.parse(jsonStr);
            const text = parsed.candidates?.[0]?.content?.parts?.[0]?.text || "";
            if (text) {
              yield { content: text, done: false };
            }
          } catch {
          }
        }
      }
    } finally {
      reader.releaseLock();
    }
  }
};

// src/cli/provider-factory.ts
function createProvider(config, modelOverride) {
  switch (config.provider) {
    case "ollama":
      return new OllamaProvider(
        config.ollama.host,
        modelOverride || config.ollama.model
      );
    case "gemini":
      return new GeminiProvider(
        config.gemini.apiKey,
        modelOverride || config.gemini.model
      );
    case "claude":
      if (!config.claude.apiKey) {
        throw new Error("Claude API key not configured. Run: zerodroid config");
      }
      throw new Error('Claude provider coming in v0.2. Use "gemini" or "ollama" for now.');
    case "openai":
      if (!config.openai.apiKey) {
        throw new Error("OpenAI API key not configured. Run: zerodroid config");
      }
      throw new Error('OpenAI provider coming in v0.2. Use "gemini" or "ollama" for now.');
    case "openrouter":
      if (!config.openrouter.apiKey) {
        throw new Error("OpenRouter API key not configured. Run: zerodroid config");
      }
      throw new Error('OpenRouter provider coming in v0.2. Use "gemini" or "ollama" for now.');
    default:
      throw new Error(`Unknown provider: ${config.provider}`);
  }
}

// src/cli/index.ts
function createCLI() {
  const program2 = new Command();
  program2.name("zerodroid").description("\u26A1 ZeroDroid \u2014 Open-source AI coding agent. Vibe code anywhere.").version("0.1.0").option("-p, --provider <provider>", "AI provider (ollama, gemini, claude, openai, openrouter)").option("-m, --model <model>", "Model name to use").argument("[prompt...]", "Direct prompt to execute").action(async (promptParts, opts) => {
    const config = loadConfig();
    const cwd = process.cwd();
    if (opts.provider) {
      config.provider = opts.provider;
    }
    const provider = createProvider(config, opts.model);
    const available = await provider.isAvailable();
    if (!available) {
      log.error(`Provider "${provider.name}" is not available.`);
      if (provider.name === "ollama") {
        log.info("Make sure Ollama is running: ollama serve");
      } else {
        log.info(`Make sure your API key is configured: zerodroid config`);
      }
      process.exit(1);
    }
    const prompt = promptParts.join(" ");
    if (prompt) {
      log.brand("ZeroDroid");
      log.dim(`Provider: ${provider.name} | Working in: ${cwd}`);
      log.divider();
      await runAgent(prompt, { provider, cwd });
      log.blank();
    } else {
      await startChat(provider, cwd);
    }
  });
  program2.command("chat").description("Start interactive chat mode").action(async () => {
    const config = loadConfig();
    const provider = createProvider(config);
    const cwd = process.cwd();
    const available = await provider.isAvailable();
    if (!available) {
      log.error(`Provider "${provider.name}" is not available.`);
      process.exit(1);
    }
    await startChat(provider, cwd);
  });
  program2.command("setup").description("Auto-install development tools (Node.js, Python, Git)").action(async () => {
    log.brand("ZeroDroid Setup");
    log.blank();
    const env = detectEnvironment();
    log.info(`Environment: ${env.env}`);
    log.info(`Architecture: ${env.arch} (${env.is64bit ? "64-bit" : "32-bit"})`);
    log.info(`RAM: ${env.totalRAM} GB`);
    log.info(`Shell: ${env.shell}`);
    log.blank();
    log.info("Checking tools...");
    log.dim(`  Node.js: ${env.hasNode ? "\u2705 installed" : "\u274C missing"}`);
    log.dim(`  Python:  ${env.hasPython ? "\u2705 installed" : "\u274C missing"}`);
    log.dim(`  Git:     ${env.hasGit ? "\u2705 installed" : "\u274C missing"}`);
    log.dim(`  Ollama:  ${env.hasOllama ? "\u2705 installed" : "\u274C not installed (optional for offline AI)"}`);
    log.blank();
    if (env.env === "termux") {
      if (!env.hasNode) {
        log.step("Installing", "Node.js...");
        shellExec("pkg install nodejs -y", process.cwd(), 12e4);
      }
      if (!env.hasPython) {
        log.step("Installing", "Python...");
        shellExec("pkg install python -y", process.cwd(), 12e4);
      }
      if (!env.hasGit) {
        log.step("Installing", "Git...");
        shellExec("pkg install git -y", process.cwd(), 12e4);
      }
      if (!env.termuxStorage) {
        log.step("Setting up", "storage access...");
        log.info("Please grant storage permission when prompted.");
        shellExec("termux-setup-storage", process.cwd(), 3e4);
      }
    } else if (env.env === "linux" || env.env === "wsl") {
      if (!env.hasNode) {
        log.step("Installing", "Node.js...");
        shellExec("sudo apt install -y nodejs npm", process.cwd(), 12e4);
      }
      if (!env.hasPython) {
        log.step("Installing", "Python...");
        shellExec("sudo apt install -y python3 python3-pip", process.cwd(), 12e4);
      }
      if (!env.hasGit) {
        log.step("Installing", "Git...");
        shellExec("sudo apt install -y git", process.cwd(), 12e4);
      }
    } else if (env.env === "macos") {
      log.info("On macOS, use Homebrew to install missing tools:");
      if (!env.hasNode) log.dim("  brew install node");
      if (!env.hasPython) log.dim("  brew install python3");
      if (!env.hasGit) log.dim("  brew install git");
    } else {
      log.info("On Windows, please install tools manually or use winget/scoop.");
    }
    log.blank();
    log.success("Setup complete!");
    log.info('Run "zerodroid config" to set up your AI provider.');
  });
  program2.command("config").description("Configure AI provider and API keys").option("--set <key=value>", "Set a config value").option("--show", "Show current config").action(async (opts) => {
    const config = loadConfig();
    if (opts.show) {
      log.brand("ZeroDroid Config");
      log.blank();
      const display = { ...config };
      if (display.gemini.apiKey) display.gemini.apiKey = "***" + display.gemini.apiKey.slice(-4);
      if (display.claude.apiKey) display.claude.apiKey = "***" + display.claude.apiKey.slice(-4);
      if (display.openai.apiKey) display.openai.apiKey = "***" + display.openai.apiKey.slice(-4);
      if (display.openrouter.apiKey) display.openrouter.apiKey = "***" + display.openrouter.apiKey.slice(-4);
      console.log(JSON.stringify(display, null, 2));
      return;
    }
    if (opts.set) {
      const [key, ...valueParts] = opts.set.split("=");
      const value = valueParts.join("=");
      const keys = key.split(".");
      let target = config;
      for (let i = 0; i < keys.length - 1; i++) {
        target = target[keys[i]];
      }
      target[keys[keys.length - 1]] = value;
      saveConfig(config);
      log.success(`Set ${key} = ${key.includes("apiKey") ? "***" : value}`);
      return;
    }
    const inquirer = await import("inquirer");
    const answers = await inquirer.default.prompt([
      {
        type: "list",
        name: "provider",
        message: "Select AI provider:",
        choices: [
          { name: "\u{1F512} Ollama (Local/Offline \u2014 free)", value: "ollama" },
          { name: "\u2601\uFE0F  Gemini (Google \u2014 free tier)", value: "gemini" },
          { name: "\u2601\uFE0F  Claude (Anthropic \u2014 paid)", value: "claude" },
          { name: "\u2601\uFE0F  OpenAI (GPT \u2014 paid)", value: "openai" },
          { name: "\u2601\uFE0F  OpenRouter (Any model)", value: "openrouter" }
        ],
        default: config.provider
      }
    ]);
    config.provider = answers.provider;
    if (answers.provider !== "ollama") {
      const keyAnswer = await inquirer.default.prompt([
        {
          type: "password",
          name: "apiKey",
          message: `Enter your ${answers.provider} API key:`,
          mask: "*"
        }
      ]);
      const providerConfig = config[answers.provider];
      if (providerConfig && typeof providerConfig === "object") {
        providerConfig.apiKey = keyAnswer.apiKey;
      }
    } else {
      const ollamaAnswer = await inquirer.default.prompt([
        {
          type: "input",
          name: "model",
          message: "Ollama model name:",
          default: config.ollama.model
        }
      ]);
      config.ollama.model = ollamaAnswer.model;
    }
    const nameAnswer = await inquirer.default.prompt([
      {
        type: "input",
        name: "userName",
        message: "Your name (so ZeroDroid can address you):",
        default: config.userName || ""
      }
    ]);
    config.userName = nameAnswer.userName;
    saveConfig(config);
    log.blank();
    log.success("Config saved!");
    log.info('Run "zerodroid" to start coding.');
  });
  return program2;
}

// src/index.ts
ensureConfigDir();
var program = createCLI();
program.parse(process.argv);
//# sourceMappingURL=index.js.map
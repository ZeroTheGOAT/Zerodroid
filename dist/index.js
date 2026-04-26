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
function getSystemPrompt(cwd, projectContext, userName, textToolMode = false) {
  const userRef = userName ? `The user's name is ${userName}. ` : "";
  const toolInstructions = textToolMode ? `
## How to Use Tools
You have access to these tools. To use them, output a tool call block in EXACTLY this format:

<tool_call>
{"name": "tool_name", "arguments": {"arg1": "value1"}}
</tool_call>

Available tools:

1. **file_write** \u2014 Create or overwrite a file
   <tool_call>
   {"name": "file_write", "arguments": {"path": "hello.js", "content": "console.log('hello');"}}
   </tool_call>

2. **file_read** \u2014 Read a file's contents
   <tool_call>
   {"name": "file_read", "arguments": {"path": "package.json"}}
   </tool_call>

3. **file_list** \u2014 List files in a directory
   <tool_call>
   {"name": "file_list", "arguments": {"path": ".", "depth": 3}}
   </tool_call>

4. **shell_exec** \u2014 Run a shell command
   <tool_call>
   {"name": "shell_exec", "arguments": {"command": "npm install express"}}
   </tool_call>

IMPORTANT RULES:
- You MUST use <tool_call> blocks to take action. Do NOT just describe what to do.
- You can use multiple tool calls in one response.
- After I show you the tool results, continue your work or respond to the user.
- The JSON inside <tool_call> must be valid JSON on a single line or multiple lines.
` : "";
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
${toolInstructions}
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
  const { provider, cwd, sessionMessages } = options;
  const config = loadConfig();
  const projectContext = getProjectContext(cwd);
  const systemPrompt = getSystemPrompt(cwd, projectContext, config.userName);
  const messages = [
    { role: "system", content: systemPrompt }
  ];
  if (sessionMessages && sessionMessages.length > 0) {
    const historyMsgs = sessionMessages.filter(
      (m) => m.role === "user" || m.role === "assistant"
    );
    const recentHistory = historyMsgs.slice(-20);
    messages.push(...recentHistory);
  }
  messages.push({ role: "user", content: userPrompt });
  let iterations = 0;
  let finalResponse = "";
  let useTextTools = false;
  if (provider.name === "ollama") {
    try {
      await provider.complete({
        messages: [{ role: "user", content: "hi" }],
        tools: TOOL_DEFINITIONS,
        temperature: 0,
        maxTokens: 10
      });
    } catch {
      useTextTools = true;
      log.dim("Using text-based tool mode (model doesn't support native tools)");
      messages[0] = {
        role: "system",
        content: getSystemPrompt(cwd, projectContext, config.userName, true)
      };
    }
  }
  while (iterations < MAX_TOOL_ITERATIONS) {
    iterations++;
    try {
      const result = await provider.complete({
        messages,
        tools: useTextTools ? void 0 : TOOL_DEFINITIONS,
        temperature: 0.4,
        maxTokens: 8192
      });
      const content = result.content || "";
      if (useTextTools && content) {
        const textToolCalls = parseTextToolCalls(content);
        if (textToolCalls.length > 0) {
          const cleanText = content.replace(/<tool_call>[\s\S]*?<\/tool_call>/g, "").trim();
          if (cleanText) {
            log.blank();
            log.ai(cleanText);
          }
          log.blank();
          const toolResultTexts = [];
          for (const tc of textToolCalls) {
            const toolResult = executeTool(tc.name, tc.arguments, cwd);
            toolResultTexts.push(`[Tool: ${tc.name}] Result:
${toolResult}`);
          }
          messages.push({ role: "assistant", content });
          messages.push({
            role: "user",
            content: `Tool results:
${toolResultTexts.join("\n\n")}

Continue your work based on these results. Use more tool calls if needed, or respond to the user if done.`
          });
          finalResponse = cleanText || content;
          continue;
        }
        log.blank();
        log.ai(content);
        finalResponse = content;
        break;
      }
      if (content) {
        log.blank();
        log.ai(content);
        finalResponse = content;
      }
      if (result.toolCalls && result.toolCalls.length > 0) {
        messages.push({
          role: "assistant",
          content,
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
function parseTextToolCalls(text) {
  const calls = [];
  const regex = /<tool_call>\s*([\s\S]*?)\s*<\/tool_call>/g;
  let match;
  while ((match = regex.exec(text)) !== null) {
    const jsonStr = match[1].trim();
    try {
      const parsed = JSON.parse(jsonStr);
      if (parsed.name && parsed.arguments) {
        calls.push({
          name: parsed.name,
          arguments: parsed.arguments
        });
      }
    } catch {
      try {
        const nameMatch = jsonStr.match(/"name"\s*:\s*"([^"]+)"/);
        const argsMatch = jsonStr.match(/"arguments"\s*:\s*(\{[\s\S]*\})/);
        if (nameMatch && argsMatch) {
          calls.push({
            name: nameMatch[1],
            arguments: JSON.parse(argsMatch[1])
          });
        }
      } catch {
      }
    }
  }
  return calls;
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

// src/agent/providers/ollama.ts
import { spawn as spawn2, execSync as execSync3 } from "child_process";
import { totalmem as totalmem2 } from "os";
var OllamaProvider = class {
  name = "ollama";
  host;
  model;
  /**
   * Context window size — THIS IS THE KEY TO MOBILE PERFORMANCE
   * Default Gemma4 context is 128K which allocates GIGABYTES of KV cache RAM.
   * On mobile (12GB), that alone causes OOM crashes.
   * We limit to 4096 by default which uses only ~50-100MB of KV cache.
   */
  numCtx;
  /**
   * Thread count — limits CPU cores to prevent thermal throttling on mobile.
   * Mobile chips throttle aggressively under sustained load.
   * Using 4 threads instead of all cores keeps the device cool and actually faster.
   */
  numThread;
  constructor(host = "http://localhost:11434", model = "gemma4:e2b") {
    this.host = host.replace(/\/$/, "");
    this.model = model;
    const isTermux2 = !!(process.env.TERMUX_VERSION || process.env.PREFIX?.includes("com.termux"));
    const totalRAM = Math.round(totalmem2() / 1024 / 1024 / 1024);
    if (isTermux2 || totalRAM <= 16) {
      this.numCtx = 4096;
    } else {
      this.numCtx = 8192;
    }
    this.numThread = 0;
  }
  /**
   * Check if Ollama server is reachable
   */
  async ping() {
    try {
      const res = await fetch(`${this.host}/api/version`);
      return res.ok;
    } catch {
      return false;
    }
  }
  /**
   * Check if the `ollama` binary exists on this system
   */
  ollamaInstalled() {
    try {
      execSync3("which ollama", { stdio: "ignore" });
      return true;
    } catch {
      try {
        execSync3("where ollama", { stdio: "ignore" });
        return true;
      } catch {
        return false;
      }
    }
  }
  /**
   * Auto-start Ollama server in the background if it's not running.
   * Waits up to 10 seconds for it to become ready.
   */
  async autoStart() {
    if (!this.ollamaInstalled()) {
      return false;
    }
    const child = spawn2("ollama", ["serve"], {
      stdio: "ignore",
      detached: true,
      shell: true
    });
    child.unref();
    for (let i = 0; i < 20; i++) {
      await new Promise((r) => setTimeout(r, 500));
      if (await this.ping()) {
        return true;
      }
    }
    return false;
  }
  /**
   * Check if a model is already downloaded locally
   */
  async hasModel(modelName) {
    const name = modelName || this.model;
    try {
      const res = await fetch(`${this.host}/api/tags`);
      if (!res.ok) return false;
      const data = await res.json();
      return data.models?.some((m) => m.name === name || m.name.startsWith(name.split(":")[0])) || false;
    } catch {
      return false;
    }
  }
  /**
   * Pull (download) a model via the Ollama API
   * No need for a second terminal — this uses the HTTP API directly!
   * @param onProgress - Callback for download progress
   */
  async pullModel(modelName, onProgress) {
    const name = modelName || this.model;
    try {
      const res = await fetch(`${this.host}/api/pull`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name, stream: true })
      });
      if (!res.ok) {
        const errText = await res.text();
        throw new Error(`Failed to pull model: ${errText}`);
      }
      if (!res.body) {
        throw new Error("No response body for pull stream");
      }
      const decoder = new TextDecoder();
      const reader = res.body.getReader();
      let lastPercent = -1;
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
              if (parsed.status && onProgress) {
                let percent;
                if (parsed.total && parsed.completed) {
                  percent = Math.round(parsed.completed / parsed.total * 100);
                  if (percent !== lastPercent && percent % 10 === 0) {
                    lastPercent = percent;
                    onProgress(parsed.status, percent);
                  }
                } else if (parsed.status !== "pulling manifest") {
                  onProgress(parsed.status);
                }
              }
            } catch {
            }
          }
        }
      } finally {
        reader.releaseLock();
      }
      return true;
    } catch {
      return false;
    }
  }
  /**
   * Ensure a model is available — auto-pull if missing
   */
  async ensureModel(onProgress) {
    if (await this.hasModel()) return true;
    return this.pullModel(void 0, onProgress);
  }
  /**
   * List all locally available models
   */
  async listModels() {
    try {
      const res = await fetch(`${this.host}/api/tags`);
      if (!res.ok) return [];
      const data = await res.json();
      return data.models?.map((m) => m.name) || [];
    } catch {
      return [];
    }
  }
  /**
   * Check if Ollama is available — auto-starts it if not running
   */
  async isAvailable() {
    if (await this.ping()) return true;
    return this.autoStart();
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
        num_predict: options.maxTokens ?? 4096,
        num_ctx: this.numCtx,
        num_thread: this.numThread
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
        num_predict: options.maxTokens ?? 4096,
        num_ctx: this.numCtx,
        num_thread: this.numThread
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

// src/sessions/manager.ts
import { existsSync as existsSync7, mkdirSync as mkdirSync4, readFileSync as readFileSync4, writeFileSync as writeFileSync4, readdirSync as readdirSync2, unlinkSync } from "fs";
import { join as join4 } from "path";
function getSessionsDir() {
  return join4(getConfigDir(), "sessions");
}
function ensureSessionsDir() {
  const dir = getSessionsDir();
  if (!existsSync7(dir)) {
    mkdirSync4(dir, { recursive: true });
  }
}
function sessionPath(id) {
  return join4(getSessionsDir(), `${id}.json`);
}
function generateId() {
  const now = /* @__PURE__ */ new Date();
  const date = now.toISOString().slice(0, 10).replace(/-/g, "");
  const rand = Math.random().toString(36).slice(2, 8);
  return `${date}_${rand}`;
}
function generateTitle(firstMessage) {
  let title = firstMessage.replace(/\n/g, " ").trim();
  if (title.length > 60) {
    title = title.slice(0, 60);
    const lastSpace = title.lastIndexOf(" ");
    if (lastSpace > 30) {
      title = title.slice(0, lastSpace);
    }
    title += "...";
  }
  return title;
}
function createSession(provider, model, cwd) {
  ensureSessionsDir();
  const session = {
    id: generateId(),
    title: "New conversation",
    provider,
    model,
    cwd,
    messages: [],
    createdAt: (/* @__PURE__ */ new Date()).toISOString(),
    updatedAt: (/* @__PURE__ */ new Date()).toISOString(),
    turns: 0
  };
  saveSession(session);
  return session;
}
function saveSession(session) {
  ensureSessionsDir();
  session.updatedAt = (/* @__PURE__ */ new Date()).toISOString();
  writeFileSync4(sessionPath(session.id), JSON.stringify(session, null, 2), "utf-8");
}
function listSessions(limit = 20) {
  ensureSessionsDir();
  const dir = getSessionsDir();
  const files = readdirSync2(dir).filter((f) => f.endsWith(".json"));
  const sessions = [];
  for (const file of files) {
    try {
      const data = JSON.parse(readFileSync4(join4(dir, file), "utf-8"));
      sessions.push(data);
    } catch {
    }
  }
  sessions.sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime());
  return sessions.slice(0, limit);
}
function getLastSession() {
  const sessions = listSessions(1);
  return sessions.length > 0 ? sessions[0] : null;
}
function addTurn(session, userMessage, assistantMessage) {
  if (session.turns === 0) {
    session.title = generateTitle(userMessage);
  }
  session.messages.push({ role: "user", content: userMessage });
  session.messages.push({ role: "assistant", content: assistantMessage });
  session.turns++;
  if (session.messages.length > 200) {
    const systemMsgs = session.messages.filter((m) => m.role === "system");
    const recentMsgs = session.messages.slice(-100);
    session.messages = [...systemMsgs, ...recentMsgs];
  }
  saveSession(session);
}
function timeAgo(dateStr) {
  const date = new Date(dateStr);
  const now = /* @__PURE__ */ new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffMins = Math.floor(diffMs / 6e4);
  const diffHours = Math.floor(diffMs / 36e5);
  const diffDays = Math.floor(diffMs / 864e5);
  if (diffMins < 1) return "just now";
  if (diffMins < 60) return `${diffMins}m ago`;
  if (diffHours < 24) return `${diffHours}h ago`;
  if (diffDays < 7) return `${diffDays}d ago`;
  return date.toLocaleDateString();
}

// src/cli/chat.ts
function showBanner(state) {
  log.blank();
  console.log("\x1B[38;2;0;229;255m\x1B[1m");
  console.log("  \u2554\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2557");
  console.log("  \u2551         \u26A1 Z E R O D R O I D          \u2551");
  console.log("  \u2551       AI Coding Agent v0.1.0          \u2551");
  console.log("  \u255A\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u255D");
  console.log("\x1B[0m");
  log.dim(`  Provider: ${state.session.provider} (${state.session.model})`);
  log.dim(`  Session:  ${state.session.title}`);
  log.dim(`  Working:  ${state.cwd}`);
  log.blank();
  log.dim("  Type what you want to build, or /help for commands");
  log.divider();
  log.blank();
}
function showHelp() {
  log.blank();
  console.log("\x1B[1m  Commands:\x1B[0m");
  log.dim("  /new                Start a new conversation");
  log.dim("  /history            List past conversations");
  log.dim("  /continue           Resume most recent conversation");
  log.dim("  /resume <id>        Resume a specific conversation");
  log.dim("  /model <name>       Switch model (e.g., /model gemma4:e2b)");
  log.dim("  /provider <name>    Switch provider (ollama, gemini, etc.)");
  log.dim("  /status             Show current session info");
  log.dim("  /compact            Summarize conversation to save context");
  log.dim("  /clear              Clear screen");
  log.dim("  /exit               Quit ZeroDroid");
  log.blank();
  console.log("\x1B[1m  Usage:\x1B[0m");
  log.dim("  Just type what you want in plain English!");
  log.dim('  "Create a React portfolio with dark mode"');
  log.dim('  "Fix the bug in server.js"');
  log.dim('  "Add authentication to this Express app"');
  log.blank();
}
function showHistory() {
  const sessions = listSessions(15);
  if (sessions.length === 0) {
    log.info("No past conversations found.");
    return;
  }
  log.blank();
  console.log("\x1B[1m  Past Conversations:\x1B[0m");
  log.blank();
  for (const s of sessions) {
    const time = timeAgo(s.updatedAt);
    const turns = `${s.turns} turn${s.turns !== 1 ? "s" : ""}`;
    const id = `\x1B[2m${s.id}\x1B[0m`;
    const title = s.title;
    const provider = `\x1B[2m[${s.provider}]\x1B[0m`;
    console.log(`  ${id}  ${title}`);
    log.dim(`  ${"".padEnd(s.id.length)}  ${turns} \xB7 ${time} \xB7 ${s.provider}`);
    log.blank();
  }
  log.dim("  Resume with: /resume <id>  or  /continue (most recent)");
  log.blank();
}
function showStatus(state) {
  log.blank();
  console.log("\x1B[1m  Session Status:\x1B[0m");
  log.dim(`  ID:        ${state.session.id}`);
  log.dim(`  Title:     ${state.session.title}`);
  log.dim(`  Provider:  ${state.session.provider}`);
  log.dim(`  Model:     ${state.session.model}`);
  log.dim(`  Turns:     ${state.session.turns}`);
  log.dim(`  Messages:  ${state.session.messages.length}`);
  log.dim(`  Directory: ${state.cwd}`);
  log.dim(`  Created:   ${timeAgo(state.session.createdAt)}`);
  log.dim(`  Updated:   ${timeAgo(state.session.updatedAt)}`);
  log.blank();
}
async function handleSlashCommand(input, state) {
  const parts = input.slice(1).split(/\s+/);
  const cmd = parts[0].toLowerCase();
  const arg = parts.slice(1).join(" ");
  switch (cmd) {
    case "help":
    case "h":
    case "?": {
      showHelp();
      return true;
    }
    case "exit":
    case "quit":
    case "q": {
      log.blank();
      log.brand("See you later! \u{1F44B}");
      log.dim(`Session saved: ${state.session.id}`);
      process.exit(0);
    }
    case "clear":
    case "cls": {
      console.clear();
      showBanner(state);
      return true;
    }
    case "new":
    case "n": {
      saveSession(state.session);
      const config = loadConfig();
      state.session = createSession(
        config.provider,
        config[config.provider] && typeof config[config.provider] === "object" ? config[config.provider].model || state.session.model : state.session.model,
        state.cwd
      );
      log.success("Started new conversation");
      log.dim(`Session: ${state.session.id}`);
      log.blank();
      return true;
    }
    case "history":
    case "ls": {
      showHistory();
      return true;
    }
    case "continue":
    case "c": {
      const last = getLastSession();
      if (!last) {
        log.info("No previous sessions found.");
        return true;
      }
      state.session = last;
      const config = loadConfig();
      config.provider = last.provider;
      state.provider = createProvider(config);
      state.cwd = last.cwd;
      log.success(`Resumed: ${last.title}`);
      log.dim(`${last.turns} turns \xB7 ${last.provider} (${last.model})`);
      log.blank();
      return true;
    }
    case "resume":
    case "r": {
      if (!arg) {
        log.error("Usage: /resume <session-id>");
        log.dim("Use /history to see available sessions");
        return true;
      }
      const sessions = listSessions(50);
      const match = sessions.find((s) => s.id === arg || s.id.startsWith(arg));
      if (!match) {
        log.error(`Session not found: ${arg}`);
        log.dim("Use /history to see available sessions");
        return true;
      }
      state.session = match;
      const config = loadConfig();
      config.provider = match.provider;
      state.provider = createProvider(config);
      state.cwd = match.cwd;
      log.success(`Resumed: ${match.title}`);
      log.dim(`${match.turns} turns \xB7 ${match.provider} (${match.model})`);
      log.blank();
      return true;
    }
    case "model":
    case "m": {
      if (!arg) {
        log.info(`Current model: ${state.session.model}`);
        log.dim("Usage: /model <model-name>");
        log.dim("Examples: /model gemma4:e2b, /model gemini-2.5-flash");
        return true;
      }
      state.session.model = arg;
      const config = loadConfig();
      const providerConfig = config[config.provider];
      if (providerConfig && typeof providerConfig === "object" && "model" in providerConfig) {
        providerConfig.model = arg;
      }
      saveConfig(config);
      state.provider = createProvider(config);
      log.success(`Model switched to: ${arg}`);
      saveSession(state.session);
      return true;
    }
    case "provider":
    case "p": {
      if (!arg) {
        log.info(`Current provider: ${state.session.provider}`);
        log.dim("Available: ollama, gemini, claude, openai, openrouter");
        return true;
      }
      const validProviders = ["ollama", "gemini", "claude", "openai", "openrouter"];
      if (!validProviders.includes(arg)) {
        log.error(`Unknown provider: ${arg}`);
        log.dim(`Available: ${validProviders.join(", ")}`);
        return true;
      }
      const config = loadConfig();
      config.provider = arg;
      saveConfig(config);
      try {
        state.provider = createProvider(config);
        state.session.provider = arg;
        const providerConfig = config[arg];
        if (providerConfig && typeof providerConfig === "object" && "model" in providerConfig) {
          state.session.model = providerConfig.model;
        }
        if (state.provider.name === "ollama") {
          log.info("Starting Ollama...");
        }
        const available = await state.provider.isAvailable();
        if (!available) {
          log.error(`Provider "${arg}" is not available.`);
          if (arg === "ollama") {
            log.dim("Install Ollama: pkg install tur-repo && pkg install ollama");
            log.dim("Then pull a model: ollama pull gemma4:e2b");
          } else {
            log.dim(`Set API key: zerodroid config --set ${arg}.apiKey=YOUR_KEY`);
          }
          return true;
        }
        log.success(`Switched to ${arg} (${state.session.model})`);
        saveSession(state.session);
      } catch (err) {
        log.error(err.message);
      }
      return true;
    }
    case "status":
    case "s": {
      showStatus(state);
      return true;
    }
    case "compact": {
      if (state.session.messages.length < 10) {
        log.info("Conversation is already short, no need to compact.");
        return true;
      }
      const systemMsgs = state.session.messages.filter((m) => m.role === "system");
      const recentMsgs = state.session.messages.slice(-10);
      state.session.messages = [...systemMsgs, ...recentMsgs];
      saveSession(state.session);
      log.success(`Compacted to ${state.session.messages.length} messages`);
      return true;
    }
    default: {
      log.error(`Unknown command: /${cmd}`);
      log.dim("Type /help for available commands");
      return true;
    }
  }
}
function getPrompt(state) {
  const provider = state.session.provider;
  const model = state.session.model;
  const shortModel = model.length > 20 ? model.slice(0, 20) + "\u2026" : model;
  return `\x1B[2m${provider}:${shortModel}\x1B[0m \x1B[38;2;0;229;255m\u276F\x1B[0m `;
}
async function startChat(provider, cwd, resumeSession) {
  const config = loadConfig();
  let session;
  if (resumeSession) {
    session = resumeSession;
  } else {
    session = createSession(
      config.provider,
      config[config.provider] && typeof config[config.provider] === "object" ? config[config.provider].model || "unknown" : "unknown",
      cwd
    );
  }
  const rl = createInterface({
    input: process.stdin,
    output: process.stdout,
    prompt: ""
  });
  const state = { provider, session, cwd, rl };
  showBanner(state);
  if (resumeSession && resumeSession.turns > 0) {
    log.info(`Resumed conversation: ${resumeSession.title}`);
    log.dim(`${resumeSession.turns} turns \xB7 started ${timeAgo(resumeSession.createdAt)}`);
    log.blank();
  }
  rl.setPrompt(getPrompt(state));
  rl.prompt();
  rl.on("line", async (line) => {
    const input = line.trim();
    if (!input) {
      rl.setPrompt(getPrompt(state));
      rl.prompt();
      return;
    }
    if (input.startsWith("/")) {
      await handleSlashCommand(input, state);
      rl.setPrompt(getPrompt(state));
      rl.prompt();
      return;
    }
    try {
      const response = await runAgent(input, {
        provider: state.provider,
        cwd: state.cwd,
        sessionMessages: state.session.messages
      });
      addTurn(state.session, input, response);
    } catch (err) {
      log.error(`Error: ${err.message}`);
    }
    log.blank();
    rl.setPrompt(getPrompt(state));
    rl.prompt();
  });
  rl.on("close", () => {
    saveSession(state.session);
    log.blank();
    log.brand("See you later! \u{1F44B}");
    log.dim(`Session saved: ${state.session.id}`);
    process.exit(0);
  });
}

// src/cli/index.ts
function createCLI() {
  const program2 = new Command();
  program2.name("zerodroid").description("\u26A1 ZeroDroid \u2014 Open-source AI coding agent. Vibe code anywhere.").version("0.1.0").option("-p, --provider <provider>", "AI provider (ollama, gemini, claude, openai, openrouter)").option("-m, --model <model>", "Model name to use").option("-c, --continue", "Resume the most recent conversation").option("--resume <id>", "Resume a specific conversation by ID").argument("[prompt...]", "Direct prompt to execute").action(async (promptParts, opts) => {
    const config = loadConfig();
    const cwd = process.cwd();
    if (opts.provider) {
      config.provider = opts.provider;
    }
    const provider = createProvider(config, opts.model);
    if (provider.name === "ollama") {
      const ollama = provider;
      log.info("Checking Ollama...");
      const available = await ollama.isAvailable();
      if (!available) {
        log.error("Ollama is not installed.");
        log.info("Install it with one of these commands:");
        log.dim("  Termux:  pkg install tur-repo && pkg install ollama");
        log.dim("  Linux:   curl -fsSL https://ollama.com/install.sh | sh");
        log.dim("  macOS:   brew install ollama");
        log.blank();
        log.info('Then run "zerodroid" again \u2014 everything else is automatic.');
        process.exit(1);
      }
      log.success("Ollama server running");
      const hasModel = await ollama.hasModel();
      if (!hasModel) {
        const modelName = config.ollama.model;
        log.warn(`Model "${modelName}" not found locally.`);
        log.info(`Downloading ${modelName}... (this only happens once)`);
        log.blank();
        const pulled = await ollama.pullModel(void 0, (status, percent) => {
          if (percent !== void 0) {
            process.stdout.write(`\r  \u2B07\uFE0F  ${status} ${percent}%   `);
          } else {
            console.log(`  \u2B07\uFE0F  ${status}`);
          }
        });
        if (pulled) {
          console.log("");
          log.success(`Model "${modelName}" ready!`);
        } else {
          console.log("");
          log.error(`Failed to download "${modelName}".`);
          log.info("Try a smaller model:");
          log.dim("  zerodroid config --set ollama.model=gemma3:1b");
          process.exit(1);
        }
      } else {
        log.success(`Model "${config.ollama.model}" ready`);
      }
    } else {
      const available = await provider.isAvailable();
      if (!available) {
        log.error(`Provider "${provider.name}" is not available.`);
        log.info(`Make sure your API key is configured: zerodroid config`);
        process.exit(1);
      }
    }
    let resumeSession = void 0;
    if (opts.continue) {
      resumeSession = getLastSession() || void 0;
      if (resumeSession) {
        log.info(`Resuming: ${resumeSession.title}`);
      }
    } else if (opts.resume) {
      const sessions = listSessions(50);
      const match = sessions.find((s) => s.id === opts.resume || s.id.startsWith(opts.resume));
      if (match) {
        resumeSession = match;
        log.info(`Resuming: ${match.title}`);
      } else {
        log.warn(`Session not found: ${opts.resume}`);
      }
    }
    const prompt = promptParts.join(" ");
    if (prompt) {
      log.brand("ZeroDroid");
      log.dim(`Provider: ${provider.name} | Working in: ${cwd}`);
      log.divider();
      await runAgent(prompt, {
        provider,
        cwd,
        sessionMessages: resumeSession?.messages
      });
      log.blank();
    } else {
      await startChat(provider, cwd, resumeSession);
    }
  });
  program2.command("chat").description("Start interactive chat mode").option("-c, --continue", "Resume the most recent conversation").action(async (opts) => {
    const config = loadConfig();
    const provider = createProvider(config);
    const cwd = process.cwd();
    const available = await provider.isAvailable();
    if (!available) {
      log.error(`Provider "${provider.name}" is not available.`);
      process.exit(1);
    }
    let resumeSession = void 0;
    if (opts.continue) {
      resumeSession = getLastSession() || void 0;
    }
    await startChat(provider, cwd, resumeSession);
  });
  program2.command("history").description("List past conversations").action(() => {
    const sessions = listSessions(20);
    if (sessions.length === 0) {
      log.info("No past conversations yet.");
      return;
    }
    log.brand("ZeroDroid \u2014 Conversation History");
    log.blank();
    for (const s of sessions) {
      const time = new Date(s.updatedAt).toLocaleString();
      const turns = `${s.turns} turn${s.turns !== 1 ? "s" : ""}`;
      log.dim(`  ${s.id}`);
      console.log(`  ${s.title}`);
      log.dim(`  ${turns} \xB7 ${s.provider} \xB7 ${time}`);
      log.blank();
    }
    log.dim("Resume with: zerodroid --continue  or  zerodroid --resume <id>");
  });
  program2.command("uninstall").description("Completely remove ZeroDroid, config, memory, and models").action(async () => {
    log.brand("ZeroDroid Uninstaller");
    log.blank();
    const inquirer = await import("inquirer");
    log.info("Ollama models are typically stored in:");
    log.dim("  Termux: ~/.ollama/models  or  $PREFIX/var/lib/ollama/models");
    log.dim("  Linux/Mac: ~/.ollama/models");
    log.blank();
    const { removeModels } = await inquirer.default.prompt([
      {
        type: "confirm",
        name: "removeModels",
        message: "Do you want to delete ALL downloaded Ollama models to free up space?",
        default: false
      }
    ]);
    if (removeModels) {
      log.step("Removing", "Ollama models...");
      try {
        const { execSync: execSync4 } = await import("child_process");
        const modelsOutput = execSync4("ollama list", { encoding: "utf-8" });
        const lines = modelsOutput.split("\n").slice(1);
        let deleted = 0;
        for (const line of lines) {
          const name = line.split(/\s+/)[0];
          if (name) {
            log.dim(`  Deleting ${name}...`);
            execSync4(`ollama rm ${name}`, { stdio: "ignore" });
            deleted++;
          }
        }
        log.success(`Deleted ${deleted} models.`);
      } catch (e) {
        log.warn("Could not list/remove models automatically. Make sure Ollama is running.");
        log.dim("You can delete them manually with: ollama rm <model-name>");
      }
    }
    log.blank();
    const { confirmUninstall } = await inquirer.default.prompt([
      {
        type: "confirm",
        name: "confirmUninstall",
        message: "Are you sure you want to completely uninstall ZeroDroid and delete all your chat history/memory?",
        default: false
      }
    ]);
    if (!confirmUninstall) {
      log.info("Uninstall cancelled.");
      return;
    }
    log.blank();
    log.step("Removing", "ZeroDroid memory and configuration...");
    try {
      const { rmSync } = await import("fs");
      const { homedir: homedir2 } = await import("os");
      const { join: join5 } = await import("path");
      const configDir = join5(homedir2(), ".zerodroid");
      rmSync(configDir, { recursive: true, force: true });
      log.success("Deleted ~/.zerodroid");
    } catch (e) {
      log.warn("Could not delete ~/.zerodroid automatically.");
    }
    log.step("Removing", "Wrapper script (if exists)...");
    try {
      const { rmSync } = await import("fs");
      const { homedir: homedir2 } = await import("os");
      const { join: join5 } = await import("path");
      rmSync(join5(homedir2(), ".local", "bin", "zerodroid"), { force: true });
    } catch (e) {
    }
    log.step("Removing", "ZeroDroid CLI from npm...");
    console.log("");
    console.log("\x1B[33mTo finish uninstalling, please run this exact command:\x1B[0m");
    console.log("\x1B[1m  npm uninstall -g zerodroid\x1B[0m");
    console.log("");
    log.brand("Goodbye! \u{1F44B}");
    process.exit(0);
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
          type: "list",
          name: "model",
          message: "Select Ollama model:",
          choices: [
            { name: "\u26A1 gemma4:e2b   \u2014 Fast, lightweight (3-4 GB RAM)", value: "gemma4:e2b" },
            { name: "\u{1F9E0} gemma4:e4b   \u2014 Smarter, heavier (5-6 GB RAM)", value: "gemma4:e4b" },
            { name: "\u{1F999} llama3.2:3b  \u2014 Meta Llama 3B, tool-calling (3 GB RAM)", value: "llama3.2:3b" },
            { name: "\u{1FAB6} llama3.2:1b  \u2014 Ultra-light 1B, won't crash (1.5 GB RAM)", value: "llama3.2:1b" },
            { name: "\u{1F4DD} qwen2.5:3b   \u2014 Qwen 3B coding (3-4 GB RAM)", value: "qwen2.5:3b" },
            { name: "\u{1F527} Custom model (enter name)", value: "__custom__" }
          ],
          default: config.ollama.model
        }
      ]);
      if (ollamaAnswer.model === "__custom__") {
        const customModel = await inquirer.default.prompt([
          {
            type: "input",
            name: "name",
            message: "Enter model name (e.g., gemma3:1b):",
            default: config.ollama.model
          }
        ]);
        config.ollama.model = customModel.name;
      } else {
        config.ollama.model = ollamaAnswer.model;
      }
      log.blank();
      log.dim("\u{1F4A1} Switch models anytime:");
      log.dim("   zerodroid config --set ollama.model=gemma4:e2b");
      log.dim("   zerodroid config --set ollama.model=gemma4:e4b");
      log.dim("   Or in chat: /model gemma4:e4b");
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
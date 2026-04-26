/**
 * Agent Core
 * The main agent loop: prompt → AI plans → execute tools → check results → iterate
 */

import type { AIProvider, Message, ToolCall } from './providers/base.js';
import { TOOL_DEFINITIONS, getSystemPrompt } from './prompts/system.js';
import { fileRead } from './tools/file-read.js';
import { fileWrite } from './tools/file-write.js';
import { fileList } from './tools/file-list.js';
import { shellExec } from './tools/shell-exec.js';
import { log } from '../utils/logger.js';
import {
  getProjectContext,
  appendToHistory,
  loadProjectMemory,
  createProjectMemory,
  saveProjectMemory,
} from '../memory/store.js';
import { loadConfig } from '../utils/config.js';

const MAX_TOOL_ITERATIONS = 15;

export interface AgentOptions {
  provider: AIProvider;
  cwd: string;
  stream?: boolean;
  /** Existing session messages to prepend (for conversation resuming) */
  sessionMessages?: Message[];
}

function executeTool(name: string, args: Record<string, unknown>, cwd: string): string {
  switch (name) {
    case 'file_write': {
      const path = args.path as string;
      const content = args.content as string;
      log.file('create', path);
      return fileWrite(path, content, cwd);
    }
    case 'file_read': {
      const path = args.path as string;
      log.file('read', path);
      return fileRead(path, cwd);
    }
    case 'file_list': {
      const path = (args.path as string) || '.';
      const depth = (args.depth as number) || 3;
      return fileList(path, cwd, depth);
    }
    case 'shell_exec': {
      const command = args.command as string;
      const timeout = (args.timeout as number) || 60_000;
      log.shell(command);
      const result = shellExec(command, cwd, timeout);
      const output = result.stdout + (result.stderr ? `\nSTDERR: ${result.stderr}` : '');
      if (!result.success) {
        log.error(`Command failed (exit ${result.exitCode})`);
      }
      return output || '(no output)';
    }
    default:
      return `Unknown tool: ${name}`;
  }
}

function processToolCalls(
  toolCalls: ToolCall[],
  cwd: string
): Message[] {
  const toolMessages: Message[] = [];

  for (const tc of toolCalls) {
    let args: Record<string, unknown>;
    try {
      args = JSON.parse(tc.function.arguments);
    } catch {
      args = {};
    }

    const result = executeTool(tc.function.name, args, cwd);

    toolMessages.push({
      role: 'tool',
      content: result,
      tool_call_id: tc.id,
    });
  }

  return toolMessages;
}

/**
 * Run the agent with a user prompt
 * This is the main entry point — handles the full tool-use loop
 */
export async function runAgent(
  userPrompt: string,
  options: AgentOptions
): Promise<string> {
  const { provider, cwd, sessionMessages } = options;
  const config = loadConfig();

  // Load project context from memory
  const projectContext = getProjectContext(cwd);

  // Build messages — include prior session history for context continuity
  const systemPrompt = getSystemPrompt(cwd, projectContext, config.userName);
  const messages: Message[] = [
    { role: 'system', content: systemPrompt },
  ];

  // Add prior conversation history (for session resuming)
  if (sessionMessages && sessionMessages.length > 0) {
    // Only include user/assistant messages from history (skip old system/tool msgs)
    const historyMsgs = sessionMessages.filter(
      (m) => m.role === 'user' || m.role === 'assistant'
    );
    // Keep last 20 messages for context (to stay within token limits)
    const recentHistory = historyMsgs.slice(-20);
    messages.push(...recentHistory);
  }

  // Add the new user prompt
  messages.push({ role: 'user', content: userPrompt });

  let iterations = 0;
  let finalResponse = '';

  // Auto-detect: does this provider/model support native tool calling?
  // If not, use text-based tool parsing (works with ANY model)
  let useTextTools = false;

  if (provider.name === 'ollama') {
    try {
      // Quick test: send with tools and see if it errors
      await provider.complete({
        messages: [{ role: 'user', content: 'hi' }],
        tools: TOOL_DEFINITIONS,
        temperature: 0,
        maxTokens: 10,
      });
    } catch {
      // Model doesn't support native tool calling — switch to text mode
      useTextTools = true;
      log.dim('Using text-based tool mode (model doesn\'t support native tools)');

      // Rebuild system prompt with text-tool instructions
      messages[0] = {
        role: 'system',
        content: getSystemPrompt(cwd, projectContext, config.userName, true),
      };
    }
  }

  while (iterations < MAX_TOOL_ITERATIONS) {
    iterations++;

    try {
      // Call the AI — with or without native tools
      const result = await provider.complete({
        messages,
        tools: useTextTools ? undefined : TOOL_DEFINITIONS,
        temperature: 0.4,
        maxTokens: 8192,
      });

      const content = result.content || '';

      // ─── TEXT-BASED TOOL MODE ──────────────────────────
      if (useTextTools && content) {
        // Parse <tool_call> blocks from the response text
        const textToolCalls = parseTextToolCalls(content);

        if (textToolCalls.length > 0) {
          // Show the text content (minus tool call blocks) as AI thinking
          const cleanText = content
            .replace(/<tool_call>[\s\S]*?<\/tool_call>/g, '')
            .trim();
          if (cleanText) {
            log.blank();
            log.ai(cleanText);
          }

          // Execute parsed tool calls
          log.blank();
          const toolResultTexts: string[] = [];
          for (const tc of textToolCalls) {
            const toolResult = executeTool(tc.name, tc.arguments, cwd);
            toolResultTexts.push(`[Tool: ${tc.name}] Result:\n${toolResult}`);
          }

          // Add to conversation for the next iteration
          messages.push({ role: 'assistant', content });
          messages.push({
            role: 'user',
            content: `Tool results:\n${toolResultTexts.join('\n\n')}\n\nContinue your work based on these results. Use more tool calls if needed, or respond to the user if done.`,
          });

          finalResponse = cleanText || content;
          continue;
        }

        // No tool calls in text — just a normal response
        log.blank();
        log.ai(content);
        finalResponse = content;
        break;
      }

      // ─── NATIVE TOOL MODE ─────────────────────────────
      if (content) {
        log.blank();
        log.ai(content);
        finalResponse = content;
      }

      if (result.toolCalls && result.toolCalls.length > 0) {
        messages.push({
          role: 'assistant',
          content: content,
          tool_calls: result.toolCalls,
        });

        log.blank();
        const toolResults = processToolCalls(result.toolCalls, cwd);
        messages.push(...toolResults);
        continue;
      }

      // No tool calls — AI is done
      break;
    } catch (err) {
      log.error(`Agent error: ${(err as Error).message}`);
      finalResponse = `Error: ${(err as Error).message}`;
      break;
    }
  }

  if (iterations >= MAX_TOOL_ITERATIONS) {
    log.warn('Reached maximum tool iterations. Stopping.');
  }

  // Save to project memory
  try {
    let memory = loadProjectMemory(cwd);
    if (!memory) {
      memory = createProjectMemory(cwd);
    }

    appendToHistory(cwd, [
      { role: 'user', content: userPrompt },
      { role: 'assistant', content: finalResponse },
    ]);

    // Auto-detect tech stack from commands run
    detectTechStack(messages, memory);
    saveProjectMemory(memory);
  } catch {
    // Non-critical, don't fail on memory errors
  }

  return finalResponse;
}

/**
 * Parse <tool_call> blocks from AI text output
 * This enables tool usage for models that don't support native tool calling API
 *
 * Format:
 *   <tool_call>
 *   {"name": "file_write", "arguments": {"path": "test.js", "content": "..."}}
 *   </tool_call>
 */
interface TextToolCall {
  name: string;
  arguments: Record<string, unknown>;
}

function parseTextToolCalls(text: string): TextToolCall[] {
  const calls: TextToolCall[] = [];
  const regex = /<tool_call>\s*([\s\S]*?)\s*<\/tool_call>/g;
  let match: RegExpExecArray | null;

  while ((match = regex.exec(text)) !== null) {
    const jsonStr = match[1].trim();
    try {
      const parsed = JSON.parse(jsonStr) as { name?: string; arguments?: Record<string, unknown> };
      if (parsed.name && parsed.arguments) {
        calls.push({
          name: parsed.name,
          arguments: parsed.arguments,
        });
      }
    } catch {
      // Try to be lenient — maybe the JSON is slightly malformed
      // Attempt to extract name and arguments manually
      try {
        const nameMatch = jsonStr.match(/"name"\s*:\s*"([^"]+)"/);
        const argsMatch = jsonStr.match(/"arguments"\s*:\s*(\{[\s\S]*\})/);
        if (nameMatch && argsMatch) {
          calls.push({
            name: nameMatch[1],
            arguments: JSON.parse(argsMatch[1]),
          });
        }
      } catch {
        // Skip unparseable tool calls
      }
    }
  }

  return calls;
}

/**
 * Run the agent with streaming output
 */
export async function runAgentStream(
  userPrompt: string,
  options: AgentOptions
): Promise<string> {
  const { provider, cwd } = options;
  const config = loadConfig();
  const projectContext = getProjectContext(cwd);
  const systemPrompt = getSystemPrompt(cwd, projectContext, config.userName);

  const messages: Message[] = [
    { role: 'system', content: systemPrompt },
    { role: 'user', content: userPrompt },
  ];

  let iterations = 0;
  let fullResponse = '';

  while (iterations < MAX_TOOL_ITERATIONS) {
    iterations++;

    try {
      // First, try non-streaming to get tool calls
      const result = await provider.complete({
        messages,
        tools: TOOL_DEFINITIONS,
        temperature: 0.4,
        maxTokens: 8192,
      });

      if (result.toolCalls && result.toolCalls.length > 0) {
        // Show brief AI message if any
        if (result.content) {
          log.ai(result.content);
        }

        messages.push({
          role: 'assistant',
          content: result.content || '',
          tool_calls: result.toolCalls,
        });

        log.blank();
        const toolResults = processToolCalls(result.toolCalls, cwd);
        messages.push(...toolResults);
        continue;
      }

      // No tools — stream the final response
      log.blank();
      process.stdout.write('\x1b[38;2;167;139;250m🤖 \x1b[0m'); // AI color prefix

      for await (const chunk of provider.stream({
        messages,
        temperature: 0.4,
        maxTokens: 8192,
      })) {
        if (chunk.content) {
          process.stdout.write(chunk.content);
          fullResponse += chunk.content;
        }
      }
      console.log(''); // Newline after stream
      break;
    } catch (err) {
      log.error(`Agent error: ${(err as Error).message}`);
      fullResponse = `Error: ${(err as Error).message}`;
      break;
    }
  }

  // Save to memory
  try {
    appendToHistory(cwd, [
      { role: 'user', content: userPrompt },
      { role: 'assistant', content: fullResponse },
    ]);
  } catch { /* non-critical */ }

  return fullResponse;
}

/**
 * Auto-detect tech stack from the conversation (commands and file operations)
 */
function detectTechStack(messages: Message[], memory: ReturnType<typeof loadProjectMemory>): void {
  if (!memory) return;

  const allContent = messages.map((m) => m.content).join(' ').toLowerCase();
  const techMap: Record<string, string> = {
    'create-react-app': 'React',
    'create-vite': 'Vite',
    'create-next-app': 'Next.js',
    'npx expo': 'React Native (Expo)',
    'flask': 'Flask',
    'fastapi': 'FastAPI',
    'express': 'Express.js',
    'django': 'Django',
    'tailwindcss': 'TailwindCSS',
    'typescript': 'TypeScript',
    'prisma': 'Prisma',
    'mongodb': 'MongoDB',
    'postgresql': 'PostgreSQL',
  };

  for (const [keyword, tech] of Object.entries(techMap)) {
    if (allContent.includes(keyword) && !memory.techStack.includes(tech)) {
      memory.techStack.push(tech);
    }
  }
}

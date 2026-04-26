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
  const { provider, cwd } = options;
  const config = loadConfig();

  // Load project context from memory
  const projectContext = getProjectContext(cwd);

  // Build initial messages
  const systemPrompt = getSystemPrompt(cwd, projectContext, config.userName);
  const messages: Message[] = [
    { role: 'system', content: systemPrompt },
    { role: 'user', content: userPrompt },
  ];

  let iterations = 0;
  let finalResponse = '';

  while (iterations < MAX_TOOL_ITERATIONS) {
    iterations++;

    try {
      // Call the AI with tools
      const result = await provider.complete({
        messages,
        tools: TOOL_DEFINITIONS,
        temperature: 0.4,
        maxTokens: 8192,
      });

      // If the AI responded with text content, display it
      if (result.content) {
        log.blank();
        log.ai(result.content);
        finalResponse = result.content;
      }

      // If the AI wants to call tools
      if (result.toolCalls && result.toolCalls.length > 0) {
        // Add assistant message with tool calls to conversation
        messages.push({
          role: 'assistant',
          content: result.content || '',
          tool_calls: result.toolCalls,
        });

        // Execute all tool calls
        log.blank();
        const toolResults = processToolCalls(result.toolCalls, cwd);

        // Add tool results to conversation
        messages.push(...toolResults);

        // Continue the loop — let AI process the results
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

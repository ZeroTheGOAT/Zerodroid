/**
 * ZeroDroid CLI — Interactive Chat (Claude Code Style)
 *
 * Slash commands:
 *   /help           — Show all commands
 *   /model <name>   — Switch model mid-conversation
 *   /provider <p>   — Switch provider (ollama, gemini, etc.)
 *   /new            — Start a new conversation
 *   /history        — List past conversations
 *   /resume <id>    — Resume a past conversation
 *   /continue       — Resume the most recent conversation
 *   /status         — Show current provider, model, session info
 *   /compact        — Summarize conversation to save context
 *   /clear          — Clear the screen
 *   /exit           — Quit
 */

import { createInterface, type Interface } from 'readline';
import { runAgent } from '../agent/core.js';
import { log } from '../utils/logger.js';
import { loadConfig, saveConfig } from '../utils/config.js';
import type { AIProvider } from '../agent/providers/base.js';
import { createProvider } from './provider-factory.js';
import {
  createSession,
  saveSession,
  loadSession,
  listSessions,
  getLastSession,
  addTurn,
  timeAgo,
  type Session,
} from '../sessions/manager.js';

interface ChatState {
  provider: AIProvider;
  session: Session;
  cwd: string;
  rl: Interface;
}

function showBanner(state: ChatState): void {
  log.blank();
  console.log('\x1b[38;2;0;229;255m\x1b[1m');
  console.log('  ╔═══════════════════════════════════════╗');
  console.log('  ║         ⚡ Z E R O D R O I D          ║');
  console.log('  ║       AI Coding Agent v0.1.0          ║');
  console.log('  ╚═══════════════════════════════════════╝');
  console.log('\x1b[0m');

  log.dim(`  Provider: ${state.session.provider} (${state.session.model})`);
  log.dim(`  Session:  ${state.session.title}`);
  log.dim(`  Working:  ${state.cwd}`);
  log.blank();
  log.dim('  Type what you want to build, or /help for commands');
  log.divider();
  log.blank();
}

function showHelp(): void {
  log.blank();
  console.log('\x1b[1m  Commands:\x1b[0m');
  log.dim('  /new                Start a new conversation');
  log.dim('  /history            List past conversations');
  log.dim('  /continue           Resume most recent conversation');
  log.dim('  /resume <id>        Resume a specific conversation');
  log.dim('  /model <name>       Switch model (e.g., /model gemma4:e2b)');
  log.dim('  /provider <name>    Switch provider (ollama, gemini, etc.)');
  log.dim('  /status             Show current session info');
  log.dim('  /compact            Summarize conversation to save context');
  log.dim('  /clear              Clear screen');
  log.dim('  /exit               Quit ZeroDroid');
  log.blank();
  console.log('\x1b[1m  Usage:\x1b[0m');
  log.dim('  Just type what you want in plain English!');
  log.dim('  "Create a React portfolio with dark mode"');
  log.dim('  "Fix the bug in server.js"');
  log.dim('  "Add authentication to this Express app"');
  log.blank();
}

function showHistory(): void {
  const sessions = listSessions(15);
  if (sessions.length === 0) {
    log.info('No past conversations found.');
    return;
  }

  log.blank();
  console.log('\x1b[1m  Past Conversations:\x1b[0m');
  log.blank();

  for (const s of sessions) {
    const time = timeAgo(s.updatedAt);
    const turns = `${s.turns} turn${s.turns !== 1 ? 's' : ''}`;
    const id = `\x1b[2m${s.id}\x1b[0m`;
    const title = s.title;
    const provider = `\x1b[2m[${s.provider}]\x1b[0m`;

    console.log(`  ${id}  ${title}`);
    log.dim(`  ${''.padEnd(s.id.length)}  ${turns} · ${time} · ${s.provider}`);
    log.blank();
  }

  log.dim('  Resume with: /resume <id>  or  /continue (most recent)');
  log.blank();
}

function showStatus(state: ChatState): void {
  log.blank();
  console.log('\x1b[1m  Session Status:\x1b[0m');
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

async function handleSlashCommand(input: string, state: ChatState): Promise<boolean> {
  const parts = input.slice(1).split(/\s+/);
  const cmd = parts[0].toLowerCase();
  const arg = parts.slice(1).join(' ');

  switch (cmd) {
    case 'help':
    case 'h':
    case '?': {
      showHelp();
      return true;
    }

    case 'exit':
    case 'quit':
    case 'q': {
      log.blank();
      log.brand('See you later! 👋');
      log.dim(`Session saved: ${state.session.id}`);
      process.exit(0);
    }

    case 'clear':
    case 'cls': {
      console.clear();
      showBanner(state);
      return true;
    }

    case 'new':
    case 'n': {
      // Save current session before starting a new one
      saveSession(state.session);

      const config = loadConfig();
      state.session = createSession(
        config.provider,
        config[config.provider as keyof typeof config] && typeof config[config.provider as keyof typeof config] === 'object'
          ? (config[config.provider as keyof typeof config] as { model?: string }).model || state.session.model
          : state.session.model,
        state.cwd
      );
      log.success('Started new conversation');
      log.dim(`Session: ${state.session.id}`);
      log.blank();
      return true;
    }

    case 'history':
    case 'ls': {
      showHistory();
      return true;
    }

    case 'continue':
    case 'c': {
      const last = getLastSession();
      if (!last) {
        log.info('No previous sessions found.');
        return true;
      }
      state.session = last;
      // Reload the correct provider for this session
      const config = loadConfig();
      config.provider = last.provider as typeof config.provider;
      state.provider = createProvider(config);
      state.cwd = last.cwd;

      log.success(`Resumed: ${last.title}`);
      log.dim(`${last.turns} turns · ${last.provider} (${last.model})`);
      log.blank();
      return true;
    }

    case 'resume':
    case 'r': {
      if (!arg) {
        log.error('Usage: /resume <session-id>');
        log.dim('Use /history to see available sessions');
        return true;
      }

      // Allow partial ID matching
      const sessions = listSessions(50);
      const match = sessions.find((s) => s.id === arg || s.id.startsWith(arg));

      if (!match) {
        log.error(`Session not found: ${arg}`);
        log.dim('Use /history to see available sessions');
        return true;
      }

      state.session = match;
      const config = loadConfig();
      config.provider = match.provider as typeof config.provider;
      state.provider = createProvider(config);
      state.cwd = match.cwd;

      log.success(`Resumed: ${match.title}`);
      log.dim(`${match.turns} turns · ${match.provider} (${match.model})`);
      log.blank();
      return true;
    }

    case 'model':
    case 'm': {
      if (!arg) {
        log.info(`Current model: ${state.session.model}`);
        log.dim('Usage: /model <model-name>');
        log.dim('Examples: /model gemma4:e2b, /model gemini-2.5-flash');
        return true;
      }

      state.session.model = arg;
      const config = loadConfig();
      const providerConfig = config[config.provider as keyof typeof config];
      if (providerConfig && typeof providerConfig === 'object' && 'model' in providerConfig) {
        (providerConfig as { model: string }).model = arg;
      }
      saveConfig(config);
      state.provider = createProvider(config);

      log.success(`Model switched to: ${arg}`);
      saveSession(state.session);
      return true;
    }

    case 'provider':
    case 'p': {
      if (!arg) {
        log.info(`Current provider: ${state.session.provider}`);
        log.dim('Available: ollama, gemini, claude, openai, openrouter');
        return true;
      }

      const validProviders = ['ollama', 'gemini', 'claude', 'openai', 'openrouter'];
      if (!validProviders.includes(arg)) {
        log.error(`Unknown provider: ${arg}`);
        log.dim(`Available: ${validProviders.join(', ')}`);
        return true;
      }

      const config = loadConfig();
      config.provider = arg as typeof config.provider;
      saveConfig(config);

      try {
        state.provider = createProvider(config);
        state.session.provider = arg;
        const providerConfig = config[arg as keyof typeof config];
        if (providerConfig && typeof providerConfig === 'object' && 'model' in providerConfig) {
          state.session.model = (providerConfig as { model: string }).model;
        }

        if (state.provider.name === 'ollama') {
          log.info('Starting Ollama...');
        }

        const available = await state.provider.isAvailable();
        if (!available) {
          log.error(`Provider "${arg}" is not available.`);
          if (arg === 'ollama') {
            log.dim('Install Ollama: pkg install tur-repo && pkg install ollama');
            log.dim('Then pull a model: ollama pull gemma4:e2b');
          } else {
            log.dim(`Set API key: zerodroid config --set ${arg}.apiKey=YOUR_KEY`);
          }
          return true;
        }

        log.success(`Switched to ${arg} (${state.session.model})`);
        saveSession(state.session);
      } catch (err) {
        log.error((err as Error).message);
      }
      return true;
    }

    case 'status':
    case 's': {
      showStatus(state);
      return true;
    }

    case 'compact': {
      if (state.session.messages.length < 10) {
        log.info('Conversation is already short, no need to compact.');
        return true;
      }

      // Keep system messages + last 10 messages
      const systemMsgs = state.session.messages.filter((m) => m.role === 'system');
      const recentMsgs = state.session.messages.slice(-10);
      state.session.messages = [...systemMsgs, ...recentMsgs];
      saveSession(state.session);

      log.success(`Compacted to ${state.session.messages.length} messages`);
      return true;
    }

    default: {
      log.error(`Unknown command: /${cmd}`);
      log.dim('Type /help for available commands');
      return true;
    }
  }
}

function getPrompt(state: ChatState): string {
  const provider = state.session.provider;
  const model = state.session.model;
  // Short model name for prompt
  const shortModel = model.length > 20 ? model.slice(0, 20) + '…' : model;
  return `\x1b[2m${provider}:${shortModel}\x1b[0m \x1b[38;2;0;229;255m❯\x1b[0m `;
}

export async function startChat(
  provider: AIProvider,
  cwd: string,
  resumeSession?: Session
): Promise<void> {
  const config = loadConfig();

  // Create or resume session
  let session: Session;
  if (resumeSession) {
    session = resumeSession;
  } else {
    session = createSession(
      config.provider,
      config[config.provider as keyof typeof config] && typeof config[config.provider as keyof typeof config] === 'object'
        ? (config[config.provider as keyof typeof config] as { model?: string }).model || 'unknown'
        : 'unknown',
      cwd
    );
  }

  const rl = createInterface({
    input: process.stdin,
    output: process.stdout,
    prompt: '',
  });

  const state: ChatState = { provider, session, cwd, rl };

  // Show the banner
  showBanner(state);

  // If resuming, show context
  if (resumeSession && resumeSession.turns > 0) {
    log.info(`Resumed conversation: ${resumeSession.title}`);
    log.dim(`${resumeSession.turns} turns · started ${timeAgo(resumeSession.createdAt)}`);
    log.blank();
  }

  rl.setPrompt(getPrompt(state));
  rl.prompt();

  rl.on('line', async (line) => {
    const input = line.trim();

    if (!input) {
      rl.setPrompt(getPrompt(state));
      rl.prompt();
      return;
    }

    // Handle slash commands
    if (input.startsWith('/')) {
      await handleSlashCommand(input, state);
      rl.setPrompt(getPrompt(state));
      rl.prompt();
      return;
    }

    // Send to AI agent
    try {
      const response = await runAgent(input, {
        provider: state.provider,
        cwd: state.cwd,
        sessionMessages: state.session.messages,
      });

      // Save turn to session
      addTurn(state.session, input, response);
    } catch (err) {
      log.error(`Error: ${(err as Error).message}`);
    }

    log.blank();
    rl.setPrompt(getPrompt(state));
    rl.prompt();
  });

  rl.on('close', () => {
    saveSession(state.session);
    log.blank();
    log.brand('See you later! 👋');
    log.dim(`Session saved: ${state.session.id}`);
    process.exit(0);
  });
}

/**
 * ZeroDroid CLI — Interactive Chat Mode
 * The main user-facing chat loop
 */

import { createInterface } from 'readline';
import { runAgent } from '../agent/core.js';
import { log } from '../utils/logger.js';
import type { AIProvider } from '../agent/providers/base.js';

export async function startChat(provider: AIProvider, cwd: string): Promise<void> {
  log.blank();
  log.brand('ZeroDroid — AI Coding Agent');
  log.dim(`Provider: ${provider.name} | Working in: ${cwd}`);
  log.dim('Type your request, or "exit" to quit. Use "clear" to reset context.');
  log.divider();
  log.blank();

  const rl = createInterface({
    input: process.stdin,
    output: process.stdout,
    prompt: '\x1b[38;2;0;229;255m❯ \x1b[0m',
  });

  rl.prompt();

  rl.on('line', async (line) => {
    const input = line.trim();

    if (!input) {
      rl.prompt();
      return;
    }

    if (input.toLowerCase() === 'exit' || input.toLowerCase() === 'quit') {
      log.blank();
      log.brand('See you later! 👋');
      process.exit(0);
    }

    if (input.toLowerCase() === 'clear') {
      console.clear();
      log.brand('Context cleared.');
      log.blank();
      rl.prompt();
      return;
    }

    if (input.toLowerCase() === 'help') {
      log.blank();
      log.info('Commands:');
      log.dim('  exit / quit    — Exit ZeroDroid');
      log.dim('  clear          — Clear the screen');
      log.dim('  help           — Show this help');
      log.dim('');
      log.info('Usage:');
      log.dim('  Just type what you want to build or do!');
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
      log.error(`Error: ${(err as Error).message}`);
    }

    log.blank();
    rl.prompt();
  });

  rl.on('close', () => {
    log.blank();
    log.brand('See you later! 👋');
    process.exit(0);
  });
}

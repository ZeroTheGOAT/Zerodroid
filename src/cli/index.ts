/**
 * ZeroDroid CLI — Command Registration
 * Handles all CLI commands: chat, setup, config
 */

import { Command } from 'commander';
import { log } from '../utils/logger.js';
import { loadConfig, saveConfig, type ZeroDroidConfig } from '../utils/config.js';
import { detectEnvironment } from '../utils/detect-env.js';
import { startChat } from './chat.js';
import { runAgent } from '../agent/core.js';
import { createProvider } from './provider-factory.js';
import { shellExec } from '../agent/tools/shell-exec.js';
import { getLastSession, listSessions, loadSession } from '../sessions/manager.js';

export function createCLI(): Command {
  const program = new Command();

  program
    .name('zerodroid')
    .description('⚡ ZeroDroid — Open-source AI coding agent. Vibe code anywhere.')
    .version('0.1.0')
    .option('-p, --provider <provider>', 'AI provider (ollama, gemini, claude, openai, openrouter)')
    .option('-m, --model <model>', 'Model name to use')
    .option('-c, --continue', 'Resume the most recent conversation')
    .option('--resume <id>', 'Resume a specific conversation by ID')
    .argument('[prompt...]', 'Direct prompt to execute')
    .action(async (promptParts: string[], opts: { provider?: string; model?: string; continue?: boolean; resume?: string }) => {
      const config = loadConfig();
      const cwd = process.cwd();

      // Override provider from CLI flag
      if (opts.provider) {
        config.provider = opts.provider as ZeroDroidConfig['provider'];
      }

      const provider = createProvider(config, opts.model);

      // ─── Full Ollama lifecycle management ──────────────
      if (provider.name === 'ollama') {
        const ollama = provider as import('../agent/providers/ollama.js').OllamaProvider;

        // Step 1: Start ollama serve (auto, in background)
        log.info('Checking Ollama...');
        const available = await ollama.isAvailable();
        if (!available) {
          log.error('Ollama is not installed.');
          log.info('Install it with one of these commands:');
          log.dim('  Termux:  pkg install tur-repo && pkg install ollama');
          log.dim('  Linux:   curl -fsSL https://ollama.com/install.sh | sh');
          log.dim('  macOS:   brew install ollama');
          log.blank();
          log.info('Then run "zerodroid" again — everything else is automatic.');
          process.exit(1);
        }
        log.success('Ollama server running');

        // Step 2: Check if model is downloaded, auto-pull if not
        const hasModel = await ollama.hasModel();
        if (!hasModel) {
          const modelName = config.ollama.model;
          log.warn(`Model "${modelName}" not found locally.`);
          log.info(`Downloading ${modelName}... (this only happens once)`);
          log.blank();

          const pulled = await ollama.pullModel(undefined, (status, percent) => {
            if (percent !== undefined) {
              process.stdout.write(`\r  ⬇️  ${status} ${percent}%   `);
            } else {
              console.log(`  ⬇️  ${status}`);
            }
          });

          if (pulled) {
            console.log(''); // newline after progress
            log.success(`Model "${modelName}" ready!`);
          } else {
            console.log('');
            log.error(`Failed to download "${modelName}".`);
            log.info('Try a smaller model:');
            log.dim('  zerodroid config --set ollama.model=gemma3:1b');
            process.exit(1);
          }
        } else {
          log.success(`Model "${config.ollama.model}" ready`);
        }
      } else {
        // Cloud providers — just check availability
        const available = await provider.isAvailable();
        if (!available) {
          log.error(`Provider "${provider.name}" is not available.`);
          log.info(`Make sure your API key is configured: zerodroid config`);
          process.exit(1);
        }
      }

      // Check for resume flags
      let resumeSession = undefined;
      if (opts.continue) {
        resumeSession = getLastSession() || undefined;
        if (resumeSession) {
          log.info(`Resuming: ${resumeSession.title}`);
        }
      } else if (opts.resume) {
        const sessions = listSessions(50);
        const match = sessions.find((s) => s.id === opts.resume || s.id.startsWith(opts.resume!));
        if (match) {
          resumeSession = match;
          log.info(`Resuming: ${match.title}`);
        } else {
          log.warn(`Session not found: ${opts.resume}`);
        }
      }

      const prompt = promptParts.join(' ');

      if (prompt) {
        // One-shot mode: execute the prompt and exit
        log.brand('ZeroDroid');
        log.dim(`Provider: ${provider.name} | Working in: ${cwd}`);
        log.divider();
        await runAgent(prompt, {
          provider,
          cwd,
          sessionMessages: resumeSession?.messages,
        });
        log.blank();
      } else {
        // Interactive chat mode
        await startChat(provider, cwd, resumeSession);
      }
    });

  // ─── zerodroid chat ──────────────────────────────────
  program
    .command('chat')
    .description('Start interactive chat mode')
    .option('-c, --continue', 'Resume the most recent conversation')
    .action(async (opts: { continue?: boolean }) => {
      const config = loadConfig();
      const provider = createProvider(config);
      const cwd = process.cwd();

      const available = await provider.isAvailable();
      if (!available) {
        log.error(`Provider "${provider.name}" is not available.`);
        process.exit(1);
      }

      let resumeSession = undefined;
      if (opts.continue) {
        resumeSession = getLastSession() || undefined;
      }

      await startChat(provider, cwd, resumeSession);
    });

  // ─── zerodroid history ───────────────────────────────
  program
    .command('history')
    .description('List past conversations')
    .action(() => {
      const sessions = listSessions(20);
      if (sessions.length === 0) {
        log.info('No past conversations yet.');
        return;
      }

      log.brand('ZeroDroid — Conversation History');
      log.blank();

      for (const s of sessions) {
        const time = new Date(s.updatedAt).toLocaleString();
        const turns = `${s.turns} turn${s.turns !== 1 ? 's' : ''}`;
        log.dim(`  ${s.id}`);
        console.log(`  ${s.title}`);
        log.dim(`  ${turns} · ${s.provider} · ${time}`);
        log.blank();
      }

      log.dim('Resume with: zerodroid --continue  or  zerodroid --resume <id>');
    });

  // ─── zerodroid uninstall ─────────────────────────────
  program
    .command('uninstall')
    .description('Completely remove ZeroDroid, config, memory, and models')
    .action(async () => {
      log.brand('ZeroDroid Uninstaller');
      log.blank();

      const inquirer = await import('inquirer');

      log.info('Ollama models are typically stored in:');
      log.dim('  Termux: ~/.ollama/models  or  $PREFIX/var/lib/ollama/models');
      log.dim('  Linux/Mac: ~/.ollama/models');
      log.blank();

      const { removeModels } = await inquirer.default.prompt([
        {
          type: 'confirm',
          name: 'removeModels',
          message: 'Do you want to delete ALL downloaded Ollama models to free up space?',
          default: false,
        },
      ]);

      if (removeModels) {
        log.step('Removing', 'Ollama models...');
        try {
          const { execSync } = await import('child_process');
          const modelsOutput = execSync('ollama list', { encoding: 'utf-8' });
          const lines = modelsOutput.split('\n').slice(1); // skip header
          
          let deleted = 0;
          for (const line of lines) {
            const name = line.split(/\s+/)[0];
            if (name) {
              log.dim(`  Deleting ${name}...`);
              execSync(`ollama rm ${name}`, { stdio: 'ignore' });
              deleted++;
            }
          }
          log.success(`Deleted ${deleted} models.`);
        } catch (e) {
          log.warn('Could not list/remove models automatically. Make sure Ollama is running.');
          log.dim('You can delete them manually with: ollama rm <model-name>');
        }
      }

      log.blank();
      const { confirmUninstall } = await inquirer.default.prompt([
        {
          type: 'confirm',
          name: 'confirmUninstall',
          message: 'Are you sure you want to completely uninstall ZeroDroid and delete all your chat history/memory?',
          default: false,
        },
      ]);

      if (!confirmUninstall) {
        log.info('Uninstall cancelled.');
        return;
      }

      log.blank();
      log.step('Removing', 'ZeroDroid memory and configuration...');
      try {
        const { rmSync } = await import('fs');
        const { homedir } = await import('os');
        const { join } = await import('path');
        const configDir = join(homedir(), '.zerodroid');
        rmSync(configDir, { recursive: true, force: true });
        log.success('Deleted ~/.zerodroid');
      } catch (e) {
        log.warn('Could not delete ~/.zerodroid automatically.');
      }

      log.step('Removing', 'Wrapper script (if exists)...');
      try {
        const { rmSync } = await import('fs');
        const { homedir } = await import('os');
        const { join } = await import('path');
        rmSync(join(homedir(), '.local', 'bin', 'zerodroid'), { force: true });
      } catch (e) {}

      log.step('Removing', 'ZeroDroid CLI from npm...');
      console.log('');
      console.log('\x1b[33mTo finish uninstalling, please run this exact command:\x1b[0m');
      console.log('\x1b[1m  npm uninstall -g zerodroid\x1b[0m');
      console.log('');
      log.brand('Goodbye! 👋');
      process.exit(0);
    });

  // ─── zerodroid setup ─────────────────────────────────
  program
    .command('setup')
    .description('Auto-install development tools (Node.js, Python, Git)')
    .action(async () => {
      log.brand('ZeroDroid Setup');
      log.blank();

      const env = detectEnvironment();
      log.info(`Environment: ${env.env}`);
      log.info(`Architecture: ${env.arch} (${env.is64bit ? '64-bit' : '32-bit'})`);
      log.info(`RAM: ${env.totalRAM} GB`);
      log.info(`Shell: ${env.shell}`);
      log.blank();

      log.info('Checking tools...');
      log.dim(`  Node.js: ${env.hasNode ? '✅ installed' : '❌ missing'}`);
      log.dim(`  Python:  ${env.hasPython ? '✅ installed' : '❌ missing'}`);
      log.dim(`  Git:     ${env.hasGit ? '✅ installed' : '❌ missing'}`);
      log.dim(`  Ollama:  ${env.hasOllama ? '✅ installed' : '❌ not installed (optional for offline AI)'}`);
      log.blank();

      if (env.env === 'termux') {
        if (!env.hasNode) {
          log.step('Installing', 'Node.js...');
          shellExec('pkg install nodejs -y', process.cwd(), 120_000);
        }
        if (!env.hasPython) {
          log.step('Installing', 'Python...');
          shellExec('pkg install python -y', process.cwd(), 120_000);
        }
        if (!env.hasGit) {
          log.step('Installing', 'Git...');
          shellExec('pkg install git -y', process.cwd(), 120_000);
        }
        if (!env.termuxStorage) {
          log.step('Setting up', 'storage access...');
          log.info('Please grant storage permission when prompted.');
          shellExec('termux-setup-storage', process.cwd(), 30_000);
        }
      } else if (env.env === 'linux' || env.env === 'wsl') {
        if (!env.hasNode) {
          log.step('Installing', 'Node.js...');
          shellExec('sudo apt install -y nodejs npm', process.cwd(), 120_000);
        }
        if (!env.hasPython) {
          log.step('Installing', 'Python...');
          shellExec('sudo apt install -y python3 python3-pip', process.cwd(), 120_000);
        }
        if (!env.hasGit) {
          log.step('Installing', 'Git...');
          shellExec('sudo apt install -y git', process.cwd(), 120_000);
        }
      } else if (env.env === 'macos') {
        log.info('On macOS, use Homebrew to install missing tools:');
        if (!env.hasNode) log.dim('  brew install node');
        if (!env.hasPython) log.dim('  brew install python3');
        if (!env.hasGit) log.dim('  brew install git');
      } else {
        log.info('On Windows, please install tools manually or use winget/scoop.');
      }

      log.blank();
      log.success('Setup complete!');
      log.info('Run "zerodroid config" to set up your AI provider.');
    });

  // ─── zerodroid config ────────────────────────────────
  program
    .command('config')
    .description('Configure AI provider and API keys')
    .option('--set <key=value>', 'Set a config value')
    .option('--show', 'Show current config')
    .action(async (opts: { set?: string; show?: boolean }) => {
      const config = loadConfig();

      if (opts.show) {
        log.brand('ZeroDroid Config');
        log.blank();
        // Redact API keys for display
        const display = { ...config };
        if (display.gemini.apiKey) display.gemini.apiKey = '***' + display.gemini.apiKey.slice(-4);
        if (display.claude.apiKey) display.claude.apiKey = '***' + display.claude.apiKey.slice(-4);
        if (display.openai.apiKey) display.openai.apiKey = '***' + display.openai.apiKey.slice(-4);
        if (display.openrouter.apiKey) display.openrouter.apiKey = '***' + display.openrouter.apiKey.slice(-4);
        console.log(JSON.stringify(display, null, 2));
        return;
      }

      if (opts.set) {
        const [key, ...valueParts] = opts.set.split('=');
        const value = valueParts.join('=');

        // Support dotted keys like gemini.apiKey
        const keys = key.split('.');
        let target: Record<string, unknown> = config as unknown as Record<string, unknown>;

        for (let i = 0; i < keys.length - 1; i++) {
          target = target[keys[i]] as Record<string, unknown>;
        }

        target[keys[keys.length - 1]] = value;
        saveConfig(config);
        log.success(`Set ${key} = ${key.includes('apiKey') ? '***' : value}`);
        return;
      }

      // Interactive config using inquirer
      const inquirer = await import('inquirer');

      const answers = await inquirer.default.prompt([
        {
          type: 'list',
          name: 'provider',
          message: 'Select AI provider:',
          choices: [
            { name: '🔒 Ollama (Local/Offline — free)', value: 'ollama' },
            { name: '☁️  Gemini (Google — free tier)', value: 'gemini' },
            { name: '☁️  Claude (Anthropic — paid)', value: 'claude' },
            { name: '☁️  OpenAI (GPT — paid)', value: 'openai' },
            { name: '☁️  OpenRouter (Any model)', value: 'openrouter' },
          ],
          default: config.provider,
        },
      ]);

      config.provider = answers.provider;

      if (answers.provider !== 'ollama') {
        const keyAnswer = await inquirer.default.prompt([
          {
            type: 'password',
            name: 'apiKey',
            message: `Enter your ${answers.provider} API key:`,
            mask: '*',
          },
        ]);

        const providerConfig = config[answers.provider as keyof typeof config] as { apiKey: string };
        if (providerConfig && typeof providerConfig === 'object') {
          providerConfig.apiKey = keyAnswer.apiKey;
        }
      } else {
        const ollamaAnswer = await inquirer.default.prompt([
          {
            type: 'list',
            name: 'model',
            message: 'Select Ollama model:',
            choices: [
              { name: '⚡ gemma4:e2b   — Fast, lightweight (3-4 GB RAM)', value: 'gemma4:e2b' },
              { name: '🧠 gemma4:e4b   — Smarter, heavier (5-6 GB RAM)', value: 'gemma4:e4b' },
              { name: '🦙 llama3.2:3b  — Meta Llama 3B, tool-calling (3 GB RAM)', value: 'llama3.2:3b' },
              { name: '🪶 llama3.2:1b  — Ultra-light 1B, won\'t crash (1.5 GB RAM)', value: 'llama3.2:1b' },
              { name: '📝 qwen2.5:3b   — Qwen 3B coding (3-4 GB RAM)', value: 'qwen2.5:3b' },
              { name: '🔧 Custom model (enter name)', value: '__custom__' },
            ],
            default: config.ollama.model,
          },
        ]);

        if (ollamaAnswer.model === '__custom__') {
          const customModel = await inquirer.default.prompt([
            {
              type: 'input',
              name: 'name',
              message: 'Enter model name (e.g., gemma3:1b):',
              default: config.ollama.model,
            },
          ]);
          config.ollama.model = customModel.name;
        } else {
          config.ollama.model = ollamaAnswer.model;
        }

        log.blank();
        log.dim('💡 Switch models anytime:');
        log.dim('   zerodroid config --set ollama.model=gemma4:e2b');
        log.dim('   zerodroid config --set ollama.model=gemma4:e4b');
        log.dim('   Or in chat: /model gemma4:e4b');
      }

      // Ask for user name
      const nameAnswer = await inquirer.default.prompt([
        {
          type: 'input',
          name: 'userName',
          message: 'Your name (so ZeroDroid can address you):',
          default: config.userName || '',
        },
      ]);
      config.userName = nameAnswer.userName;

      saveConfig(config);
      log.blank();
      log.success('Config saved!');
      log.info('Run "zerodroid" to start coding.');
    });

  return program;
}

/**
 * ZeroDroid Configuration Manager
 * Handles ~/.zerodroid/config.json
 */

import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'fs';
import { join } from 'path';
import { getConfigDir } from './detect-env.js';

export interface ZeroDroidConfig {
  /** Active AI provider */
  provider: 'ollama' | 'gemini' | 'claude' | 'openai' | 'openrouter';

  /** Ollama settings */
  ollama: {
    host: string;
    model: string;
  };

  /** Gemini API settings */
  gemini: {
    apiKey: string;
    model: string;
  };

  /** Claude API settings */
  claude: {
    apiKey: string;
    model: string;
  };

  /** OpenAI API settings */
  openai: {
    apiKey: string;
    model: string;
  };

  /** OpenRouter API settings */
  openrouter: {
    apiKey: string;
    model: string;
  };

  /** Default project directory */
  projectsDir: string;

  /** User display name */
  userName: string;

  /** Whether to auto-summarize long conversations */
  autoSummarize: boolean;

  /** Max conversation turns before auto-summarizing */
  maxTurnsBeforeSummary: number;
}

const DEFAULT_CONFIG: ZeroDroidConfig = {
  provider: 'gemini',
  ollama: {
    host: 'http://localhost:11434',
    model: 'qwen3.5:0.8b',
  },
  gemini: {
    apiKey: '',
    model: 'gemini-2.5-flash',
  },
  claude: {
    apiKey: '',
    model: 'claude-sonnet-4-20250514',
  },
  openai: {
    apiKey: '',
    model: 'gpt-4.1',
  },
  openrouter: {
    apiKey: '',
    model: 'google/gemini-2.5-flash',
  },
  projectsDir: '~/zerodroid-projects',
  userName: '',
  autoSummarize: true,
  maxTurnsBeforeSummary: 50,
};

function getConfigPath(): string {
  return join(getConfigDir(), 'config.json');
}

export function ensureConfigDir(): void {
  const dir = getConfigDir();
  if (!existsSync(dir)) {
    mkdirSync(dir, { recursive: true });
  }

  const memoryDir = join(dir, 'memory');
  if (!existsSync(memoryDir)) {
    mkdirSync(memoryDir, { recursive: true });
  }

  const projectsMemoryDir = join(memoryDir, 'projects');
  if (!existsSync(projectsMemoryDir)) {
    mkdirSync(projectsMemoryDir, { recursive: true });
  }
}

export function loadConfig(): ZeroDroidConfig {
  ensureConfigDir();
  const configPath = getConfigPath();

  if (!existsSync(configPath)) {
    saveConfig(DEFAULT_CONFIG);
    return { ...DEFAULT_CONFIG };
  }

  try {
    const raw = readFileSync(configPath, 'utf-8');
    const parsed = JSON.parse(raw);
    // Merge with defaults to handle new fields in updates
    return { ...DEFAULT_CONFIG, ...parsed };
  } catch {
    return { ...DEFAULT_CONFIG };
  }
}

export function saveConfig(config: ZeroDroidConfig): void {
  ensureConfigDir();
  const configPath = getConfigPath();
  writeFileSync(configPath, JSON.stringify(config, null, 2), 'utf-8');
}

export function updateConfig(updates: Partial<ZeroDroidConfig>): ZeroDroidConfig {
  const current = loadConfig();
  const updated = { ...current, ...updates };
  saveConfig(updated);
  return updated;
}

export function getProviderConfig(config: ZeroDroidConfig) {
  switch (config.provider) {
    case 'ollama':
      return config.ollama;
    case 'gemini':
      return config.gemini;
    case 'claude':
      return config.claude;
    case 'openai':
      return config.openai;
    case 'openrouter':
      return config.openrouter;
  }
}

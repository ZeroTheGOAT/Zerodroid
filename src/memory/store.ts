/**
 * Memory Store
 * Persistent memory system — saves conversation history, project context, and user preferences
 * Everything is stored as JSON files in ~/.zerodroid/memory/
 */

import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'fs';
import { join, basename } from 'path';
import { getConfigDir } from '../utils/detect-env.js';
import type { Message } from '../agent/providers/base.js';

export interface ProjectMemory {
  /** Project name */
  name: string;

  /** Project root directory */
  rootDir: string;

  /** What this project is about */
  description: string;

  /** Tech stack detected or declared */
  techStack: string[];

  /** Key decisions made during development */
  decisions: string[];

  /** Last accessed timestamp */
  lastAccessed: string;

  /** Conversation history */
  history: Message[];
}

export interface GlobalMemory {
  /** User's name */
  userName: string;

  /** User preferences and coding style notes */
  preferences: string[];

  /** Facts the AI has learned about the user */
  facts: string[];

  /** Last updated */
  lastUpdated: string;
}

const DEFAULT_GLOBAL: GlobalMemory = {
  userName: '',
  preferences: [],
  facts: [],
  lastUpdated: new Date().toISOString(),
};

function getGlobalMemoryPath(): string {
  return join(getConfigDir(), 'memory', 'global.json');
}

function getProjectMemoryDir(): string {
  return join(getConfigDir(), 'memory', 'projects');
}

function getProjectMemoryPath(projectDir: string): string {
  const projectName = basename(projectDir);
  return join(getProjectMemoryDir(), `${projectName}.json`);
}

// ─── Global Memory ───────────────────────────────────

export function loadGlobalMemory(): GlobalMemory {
  const path = getGlobalMemoryPath();
  if (!existsSync(path)) return { ...DEFAULT_GLOBAL };

  try {
    return JSON.parse(readFileSync(path, 'utf-8'));
  } catch {
    return { ...DEFAULT_GLOBAL };
  }
}

export function saveGlobalMemory(memory: GlobalMemory): void {
  const path = getGlobalMemoryPath();
  const dir = join(getConfigDir(), 'memory');
  if (!existsSync(dir)) mkdirSync(dir, { recursive: true });

  memory.lastUpdated = new Date().toISOString();
  writeFileSync(path, JSON.stringify(memory, null, 2), 'utf-8');
}

export function updateGlobalMemory(updates: Partial<GlobalMemory>): GlobalMemory {
  const current = loadGlobalMemory();
  const updated = { ...current, ...updates };
  saveGlobalMemory(updated);
  return updated;
}

// ─── Project Memory ──────────────────────────────────

export function loadProjectMemory(projectDir: string): ProjectMemory | null {
  const path = getProjectMemoryPath(projectDir);
  if (!existsSync(path)) return null;

  try {
    return JSON.parse(readFileSync(path, 'utf-8'));
  } catch {
    return null;
  }
}

export function saveProjectMemory(memory: ProjectMemory): void {
  const dir = getProjectMemoryDir();
  if (!existsSync(dir)) mkdirSync(dir, { recursive: true });

  const path = getProjectMemoryPath(memory.rootDir);
  memory.lastAccessed = new Date().toISOString();
  writeFileSync(path, JSON.stringify(memory, null, 2), 'utf-8');
}

export function createProjectMemory(projectDir: string, description = ''): ProjectMemory {
  const memory: ProjectMemory = {
    name: basename(projectDir),
    rootDir: projectDir,
    description,
    techStack: [],
    decisions: [],
    lastAccessed: new Date().toISOString(),
    history: [],
  };
  saveProjectMemory(memory);
  return memory;
}

export function appendToHistory(projectDir: string, messages: Message[]): void {
  let memory = loadProjectMemory(projectDir);
  if (!memory) {
    memory = createProjectMemory(projectDir);
  }

  memory.history.push(...messages);

  // Keep history manageable — keep last 100 messages
  if (memory.history.length > 100) {
    memory.history = memory.history.slice(-100);
  }

  saveProjectMemory(memory);
}

export function getProjectContext(projectDir: string): string | undefined {
  const memory = loadProjectMemory(projectDir);
  if (!memory) return undefined;

  const parts: string[] = [];

  if (memory.description) {
    parts.push(`Project: ${memory.name} — ${memory.description}`);
  }

  if (memory.techStack.length > 0) {
    parts.push(`Tech Stack: ${memory.techStack.join(', ')}`);
  }

  if (memory.decisions.length > 0) {
    parts.push(`Key Decisions:\n${memory.decisions.map((d) => `  - ${d}`).join('\n')}`);
  }

  // Include last few conversation messages for context
  if (memory.history.length > 0) {
    const recent = memory.history.slice(-6);
    const summary = recent
      .filter((m) => m.role === 'user' || m.role === 'assistant')
      .map((m) => `  ${m.role}: ${m.content.slice(0, 200)}`)
      .join('\n');
    parts.push(`Recent conversation:\n${summary}`);
  }

  return parts.length > 0 ? parts.join('\n\n') : undefined;
}

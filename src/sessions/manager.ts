/**
 * Session Manager
 * Persistent conversation sessions — survive phone restarts, app closes, everything.
 * Each session is saved as a JSON file in ~/.zerodroid/sessions/
 *
 * Sessions track:
 *  - Full conversation history (messages)
 *  - Which provider/model was used
 *  - Working directory
 *  - Timestamps
 *  - A human-readable title (auto-generated from first prompt)
 */

import { existsSync, mkdirSync, readFileSync, writeFileSync, readdirSync, unlinkSync } from 'fs';
import { join } from 'path';
import { getConfigDir } from '../utils/detect-env.js';
import type { Message } from '../agent/providers/base.js';

export interface Session {
  /** Unique session ID */
  id: string;

  /** Human-readable title (first ~60 chars of first user message) */
  title: string;

  /** Provider used (ollama, gemini, etc.) */
  provider: string;

  /** Model used */
  model: string;

  /** Working directory */
  cwd: string;

  /** Full message history */
  messages: Message[];

  /** Creation timestamp */
  createdAt: string;

  /** Last activity timestamp */
  updatedAt: string;

  /** Number of user turns */
  turns: number;
}

function getSessionsDir(): string {
  return join(getConfigDir(), 'sessions');
}

function ensureSessionsDir(): void {
  const dir = getSessionsDir();
  if (!existsSync(dir)) {
    mkdirSync(dir, { recursive: true });
  }
}

function sessionPath(id: string): string {
  return join(getSessionsDir(), `${id}.json`);
}

function generateId(): string {
  const now = new Date();
  const date = now.toISOString().slice(0, 10).replace(/-/g, '');
  const rand = Math.random().toString(36).slice(2, 8);
  return `${date}_${rand}`;
}

function generateTitle(firstMessage: string): string {
  // Take first 60 chars, trim to last word boundary
  let title = firstMessage.replace(/\n/g, ' ').trim();
  if (title.length > 60) {
    title = title.slice(0, 60);
    const lastSpace = title.lastIndexOf(' ');
    if (lastSpace > 30) {
      title = title.slice(0, lastSpace);
    }
    title += '...';
  }
  return title;
}

// ─── CRUD ────────────────────────────────────────────

export function createSession(provider: string, model: string, cwd: string): Session {
  ensureSessionsDir();

  const session: Session = {
    id: generateId(),
    title: 'New conversation',
    provider,
    model,
    cwd,
    messages: [],
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    turns: 0,
  };

  saveSession(session);
  return session;
}

export function saveSession(session: Session): void {
  ensureSessionsDir();
  session.updatedAt = new Date().toISOString();
  writeFileSync(sessionPath(session.id), JSON.stringify(session, null, 2), 'utf-8');
}

export function loadSession(id: string): Session | null {
  const path = sessionPath(id);
  if (!existsSync(path)) return null;

  try {
    return JSON.parse(readFileSync(path, 'utf-8'));
  } catch {
    return null;
  }
}

export function deleteSession(id: string): boolean {
  const path = sessionPath(id);
  if (!existsSync(path)) return false;
  unlinkSync(path);
  return true;
}

/**
 * List all sessions, sorted by most recently updated first
 */
export function listSessions(limit = 20): Session[] {
  ensureSessionsDir();
  const dir = getSessionsDir();

  const files = readdirSync(dir).filter((f) => f.endsWith('.json'));
  const sessions: Session[] = [];

  for (const file of files) {
    try {
      const data = JSON.parse(readFileSync(join(dir, file), 'utf-8'));
      sessions.push(data);
    } catch {
      // Skip corrupt session files
    }
  }

  // Sort by updatedAt descending (most recent first)
  sessions.sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime());

  return sessions.slice(0, limit);
}

/**
 * Get the most recent session (for --continue flag)
 */
export function getLastSession(): Session | null {
  const sessions = listSessions(1);
  return sessions.length > 0 ? sessions[0] : null;
}

/**
 * Add a user message + assistant response to the session
 */
export function addTurn(session: Session, userMessage: string, assistantMessage: string): void {
  // Auto-title from first user message
  if (session.turns === 0) {
    session.title = generateTitle(userMessage);
  }

  session.messages.push({ role: 'user', content: userMessage });
  session.messages.push({ role: 'assistant', content: assistantMessage });
  session.turns++;

  // Keep sessions manageable — summarize if too long
  if (session.messages.length > 200) {
    // Keep system prompt + last 100 messages
    const systemMsgs = session.messages.filter((m) => m.role === 'system');
    const recentMsgs = session.messages.slice(-100);
    session.messages = [...systemMsgs, ...recentMsgs];
  }

  saveSession(session);
}

/**
 * Format a relative time string (e.g., "2 hours ago", "3 days ago")
 */
export function timeAgo(dateStr: string): string {
  const date = new Date(dateStr);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffMins = Math.floor(diffMs / 60000);
  const diffHours = Math.floor(diffMs / 3600000);
  const diffDays = Math.floor(diffMs / 86400000);

  if (diffMins < 1) return 'just now';
  if (diffMins < 60) return `${diffMins}m ago`;
  if (diffHours < 24) return `${diffHours}h ago`;
  if (diffDays < 7) return `${diffDays}d ago`;
  return date.toLocaleDateString();
}

/**
 * Shell Executor Tool
 * Runs shell commands and captures output
 */

import { execSync, spawn, type ChildProcess } from 'child_process';
import treeKill from 'tree-kill';

const MAX_OUTPUT_LENGTH = 8000;

export interface ShellResult {
  stdout: string;
  stderr: string;
  exitCode: number;
  success: boolean;
}

/**
 * Execute a shell command synchronously with timeout
 */
export function shellExec(command: string, cwd: string, timeoutMs = 60_000): ShellResult {
  try {
    const stdout = execSync(command, {
      cwd,
      encoding: 'utf-8',
      timeout: timeoutMs,
      maxBuffer: 1024 * 1024 * 10, // 10MB
      stdio: ['pipe', 'pipe', 'pipe'],
      shell: true,
    });

    const trimmed = stdout.length > MAX_OUTPUT_LENGTH
      ? stdout.slice(0, MAX_OUTPUT_LENGTH) + '\n... (output truncated)'
      : stdout;

    return {
      stdout: trimmed,
      stderr: '',
      exitCode: 0,
      success: true,
    };
  } catch (err: unknown) {
    const error = err as { stdout?: string; stderr?: string; status?: number; message?: string };
    const stdout = (error.stdout || '').toString();
    const stderr = (error.stderr || error.message || '').toString();

    const trimmedStdout = stdout.length > MAX_OUTPUT_LENGTH
      ? stdout.slice(0, MAX_OUTPUT_LENGTH) + '\n... (output truncated)'
      : stdout;
    const trimmedStderr = stderr.length > MAX_OUTPUT_LENGTH
      ? stderr.slice(0, MAX_OUTPUT_LENGTH) + '\n... (output truncated)'
      : stderr;

    return {
      stdout: trimmedStdout,
      stderr: trimmedStderr,
      exitCode: error.status ?? 1,
      success: false,
    };
  }
}

/**
 * Start a long-running process (like a dev server) in the background
 * Returns the child process for later management
 */
export function shellSpawn(command: string, cwd: string): ChildProcess {
  const child = spawn(command, {
    cwd,
    shell: true,
    stdio: ['ignore', 'pipe', 'pipe'],
    detached: false,
  });

  return child;
}

/**
 * Kill a background process and all its children
 */
export function shellKill(pid: number): Promise<void> {
  return new Promise((resolve, reject) => {
    treeKill(pid, 'SIGTERM', (err) => {
      if (err) reject(err);
      else resolve();
    });
  });
}

/**
 * File Read Tool
 * Reads file contents for the AI agent
 */

import { readFileSync, existsSync } from 'fs';
import { resolve } from 'path';

export function fileRead(filePath: string, cwd: string): string {
  const fullPath = resolve(cwd, filePath);

  if (!existsSync(fullPath)) {
    return `Error: File not found: ${fullPath}`;
  }

  try {
    const content = readFileSync(fullPath, 'utf-8');
    return content;
  } catch (err) {
    return `Error reading file: ${(err as Error).message}`;
  }
}

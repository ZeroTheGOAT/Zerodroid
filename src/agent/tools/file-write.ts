/**
 * File Write Tool
 * Creates or overwrites files, auto-creating parent directories
 */

import { writeFileSync, mkdirSync, existsSync } from 'fs';
import { resolve, dirname } from 'path';

export function fileWrite(filePath: string, content: string, cwd: string): string {
  const fullPath = resolve(cwd, filePath);

  try {
    const dir = dirname(fullPath);
    if (!existsSync(dir)) {
      mkdirSync(dir, { recursive: true });
    }

    writeFileSync(fullPath, content, 'utf-8');
    return `File written: ${fullPath}`;
  } catch (err) {
    return `Error writing file: ${(err as Error).message}`;
  }
}

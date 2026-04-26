/**
 * File List Tool
 * Lists directory contents as a tree
 */

import { readdirSync, statSync, existsSync } from 'fs';
import { join, relative } from 'path';

interface FileEntry {
  name: string;
  type: 'file' | 'directory';
  size?: number;
  children?: FileEntry[];
}

const IGNORED_DIRS = new Set([
  'node_modules', '.git', '__pycache__', '.next', '.cache',
  'dist', 'build', '.expo', '.vscode', '.idea', 'venv',
  'env', '.env', 'coverage',
]);

function listRecursive(dirPath: string, depth: number, maxDepth: number): FileEntry[] {
  if (depth >= maxDepth) return [];

  try {
    const entries = readdirSync(dirPath, { withFileTypes: true });
    const result: FileEntry[] = [];

    for (const entry of entries) {
      if (entry.name.startsWith('.') && depth > 0) continue;
      if (IGNORED_DIRS.has(entry.name)) continue;

      const fullPath = join(dirPath, entry.name);

      if (entry.isDirectory()) {
        result.push({
          name: entry.name,
          type: 'directory',
          children: listRecursive(fullPath, depth + 1, maxDepth),
        });
      } else {
        try {
          const stats = statSync(fullPath);
          result.push({
            name: entry.name,
            type: 'file',
            size: stats.size,
          });
        } catch {
          result.push({ name: entry.name, type: 'file' });
        }
      }
    }

    return result.sort((a, b) => {
      // Directories first, then files
      if (a.type !== b.type) return a.type === 'directory' ? -1 : 1;
      return a.name.localeCompare(b.name);
    });
  } catch {
    return [];
  }
}

function formatTree(entries: FileEntry[], prefix = ''): string {
  const lines: string[] = [];

  for (let i = 0; i < entries.length; i++) {
    const entry = entries[i];
    const isLast = i === entries.length - 1;
    const connector = isLast ? '└── ' : '├── ';
    const childPrefix = isLast ? '    ' : '│   ';

    if (entry.type === 'directory') {
      lines.push(`${prefix}${connector}📁 ${entry.name}/`);
      if (entry.children && entry.children.length > 0) {
        lines.push(formatTree(entry.children, prefix + childPrefix));
      }
    } else {
      lines.push(`${prefix}${connector}${entry.name}`);
    }
  }

  return lines.join('\n');
}

export function fileList(dirPath: string, cwd: string, maxDepth = 3): string {
  const fullPath = join(cwd, dirPath);

  if (!existsSync(fullPath)) {
    return `Error: Directory not found: ${fullPath}`;
  }

  const entries = listRecursive(fullPath, 0, maxDepth);

  if (entries.length === 0) {
    return `Directory is empty: ${fullPath}`;
  }

  const header = `📂 ${relative(cwd, fullPath) || '.'}`;
  return `${header}\n${formatTree(entries)}`;
}

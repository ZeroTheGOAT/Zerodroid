/**
 * ZeroDroid Logger
 * Colored, formatted terminal output with consistent styling
 */

import chalk from 'chalk';

const BRAND = chalk.bold.hex('#00E5FF');
const DIM = chalk.dim;
const SUCCESS = chalk.green;
const ERROR = chalk.red;
const WARN = chalk.yellow;
const INFO = chalk.cyan;
const AI = chalk.hex('#A78BFA');

export const log = {
  brand(msg: string) {
    console.log(BRAND(`⚡ ${msg}`));
  },

  info(msg: string) {
    console.log(INFO(`ℹ ${msg}`));
  },

  success(msg: string) {
    console.log(SUCCESS(`✅ ${msg}`));
  },

  error(msg: string) {
    console.log(ERROR(`❌ ${msg}`));
  },

  warn(msg: string) {
    console.log(WARN(`⚠️  ${msg}`));
  },

  dim(msg: string) {
    console.log(DIM(msg));
  },

  ai(msg: string) {
    console.log(AI(`🤖 ${msg}`));
  },

  /** Print a step in the agent's execution */
  step(action: string, detail: string) {
    console.log(`  ${chalk.hex('#FFD600')('▶')} ${chalk.bold(action)} ${DIM(detail)}`);
  },

  /** Print file operation */
  file(action: 'create' | 'edit' | 'delete' | 'read', path: string) {
    const icons: Record<string, string> = {
      create: '📄',
      edit: '✏️',
      delete: '🗑️',
      read: '👁️',
    };
    console.log(`  ${icons[action]} ${chalk.bold(action)} ${DIM(path)}`);
  },

  /** Print shell command */
  shell(cmd: string) {
    console.log(`  ${chalk.hex('#FF6B6B')('$')} ${chalk.italic(cmd)}`);
  },

  /** Print a divider line */
  divider() {
    console.log(DIM('─'.repeat(50)));
  },

  /** Blank line */
  blank() {
    console.log('');
  },

  /** Stream a chunk of AI response text (no newline) */
  stream(chunk: string) {
    process.stdout.write(AI(chunk));
  },
};

/**
 * ZeroDroid — Main Entry Point
 * Open-source AI coding agent for Android, Linux, Mac, and everywhere
 */

import { createCLI } from './cli/index.js';
import { ensureConfigDir } from './utils/config.js';

// Ensure config directory exists
ensureConfigDir();

// Create and run the CLI
const program = createCLI();
program.parse(process.argv);

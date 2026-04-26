/**
 * Environment Detection
 * Detects whether ZeroDroid is running on Termux, Linux, macOS, or WSL
 */

import { execSync } from 'child_process';
import { existsSync } from 'fs';
import { platform, arch, homedir, totalmem } from 'os';

export type Environment = 'termux' | 'linux' | 'macos' | 'wsl' | 'windows';

export interface EnvironmentInfo {
  env: Environment;
  arch: string;
  platform: string;
  home: string;
  shell: string;
  totalRAM: number; // in GB
  is64bit: boolean;
  hasNode: boolean;
  hasPython: boolean;
  hasGit: boolean;
  hasOllama: boolean;
  termuxStorage: boolean;
}

function commandExists(cmd: string): boolean {
  try {
    execSync(`which ${cmd}`, { stdio: 'ignore' });
    return true;
  } catch {
    // On Windows, 'which' doesn't exist, try 'where'
    try {
      execSync(`where ${cmd}`, { stdio: 'ignore' });
      return true;
    } catch {
      return false;
    }
  }
}

function isTermux(): boolean {
  return (
    existsSync('/data/data/com.termux') ||
    !!process.env.TERMUX_VERSION ||
    !!process.env.PREFIX?.includes('com.termux')
  );
}

function isWSL(): boolean {
  if (platform() !== 'linux') return false;
  try {
    const release = execSync('uname -r', { encoding: 'utf-8' }).toLowerCase();
    return release.includes('microsoft') || release.includes('wsl');
  } catch {
    return false;
  }
}

function getShell(): string {
  return process.env.SHELL || process.env.COMSPEC || '/bin/sh';
}

export function detectEnvironment(): EnvironmentInfo {
  let env: Environment;

  if (isTermux()) {
    env = 'termux';
  } else if (platform() === 'darwin') {
    env = 'macos';
  } else if (platform() === 'win32') {
    env = 'windows';
  } else if (isWSL()) {
    env = 'wsl';
  } else {
    env = 'linux';
  }

  const totalRAM = Math.round((totalmem() / 1024 / 1024 / 1024) * 10) / 10;

  return {
    env,
    arch: arch(),
    platform: platform(),
    home: homedir(),
    shell: getShell(),
    totalRAM,
    is64bit: arch() === 'arm64' || arch() === 'x64',
    hasNode: commandExists('node'),
    hasPython: commandExists('python3') || commandExists('python'),
    hasGit: commandExists('git'),
    hasOllama: commandExists('ollama'),
    termuxStorage: env === 'termux' && existsSync(`${homedir()}/storage`),
  };
}

export function getProjectsDir(envInfo: EnvironmentInfo): string {
  return `${envInfo.home}/zerodroid-projects`;
}

export function getConfigDir(): string {
  return `${homedir()}/.zerodroid`;
}

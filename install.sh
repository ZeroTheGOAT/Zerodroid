#!/bin/bash
# ⚡ ZeroDroid — One-Line Installer
# Works on: Termux (Android), Linux, macOS, WSL
#
# Usage:
#   curl -fsSL https://raw.githubusercontent.com/ZeroTheGOAT/Zerodroid/main/install.sh | bash

set -e

CYAN='\033[0;36m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BOLD='\033[1m'
NC='\033[0m'

echo ""
echo -e "${CYAN}${BOLD}⚡ ZeroDroid Installer${NC}"
echo -e "${CYAN}   AI Coding Agent — Vibe code anywhere${NC}"
echo ""

# ─── Detect environment ──────────────────────────────
IS_TERMUX=false
if [ -d "/data/data/com.termux" ] || [ -n "$TERMUX_VERSION" ]; then
  IS_TERMUX=true
fi

# ─── Install Node.js if missing ──────────────────────
if ! command -v node &> /dev/null; then
  echo -e "${YELLOW}Node.js not found. Installing...${NC}"

  if [ "$IS_TERMUX" = true ]; then
    pkg update -y && pkg install nodejs git -y
  elif [ "$(uname)" = "Darwin" ]; then
    if command -v brew &> /dev/null; then
      brew install node
    else
      echo -e "${RED}Please install Homebrew first: https://brew.sh${NC}"
      exit 1
    fi
  elif command -v apt-get &> /dev/null; then
    sudo apt-get update && sudo apt-get install -y nodejs npm
  elif command -v dnf &> /dev/null; then
    sudo dnf install -y nodejs
  elif command -v pacman &> /dev/null; then
    sudo pacman -S --noconfirm nodejs npm
  else
    echo -e "${RED}Could not auto-install Node.js. Please install it manually.${NC}"
    exit 1
  fi

  echo -e "${GREEN}✅ Node.js installed${NC}"
else
  echo -e "${GREEN}✅ Node.js $(node -v) found${NC}"
fi

# ─── Install Git if missing ──────────────────────────
if ! command -v git &> /dev/null; then
  echo -e "${YELLOW}Git not found. Installing...${NC}"

  if [ "$IS_TERMUX" = true ]; then
    pkg install git -y
  elif command -v apt-get &> /dev/null; then
    sudo apt-get install -y git
  elif [ "$(uname)" = "Darwin" ]; then
    xcode-select --install 2>/dev/null || true
  fi
fi

# ─── Install ZeroDroid ───────────────────────────────
echo ""
echo -e "${CYAN}Installing ZeroDroid...${NC}"

npm install -g github:ZeroTheGOAT/Zerodroid 2>/dev/null || {
  # Fallback: clone and install manually
  echo -e "${YELLOW}npm global install from GitHub failed, trying clone method...${NC}"
  TMPDIR="${TMPDIR:-/tmp}"
  INSTALL_DIR="$TMPDIR/zerodroid-install"
  rm -rf "$INSTALL_DIR"
  git clone --depth 1 https://github.com/ZeroTheGOAT/Zerodroid.git "$INSTALL_DIR"
  cd "$INSTALL_DIR"
  npm install
  npm run build
  npm pack > /dev/null
  npm install -g *.tgz
  cd - > /dev/null
  rm -rf "$INSTALL_DIR"
}

# ─── Setup storage on Termux ─────────────────────────
if [ "$IS_TERMUX" = true ]; then
  if [ ! -d "$HOME/storage" ]; then
    echo ""
    echo -e "${YELLOW}Setting up storage access (tap Allow when prompted)...${NC}"
    termux-setup-storage || true
  fi
fi

# ─── Done! ────────────────────────────────────────────
echo ""
echo -e "${GREEN}${BOLD}⚡ ZeroDroid installed successfully!${NC}"
echo ""
echo -e "  ${BOLD}Get started:${NC}"
echo -e "    ${CYAN}zerodroid config${NC}   — Set up your AI provider (one time)"
echo -e "    ${CYAN}zerodroid${NC}          — Start coding!"
echo ""
echo -e "  ${BOLD}For offline AI (optional):${NC}"
if [ "$IS_TERMUX" = true ]; then
echo -e "    ${CYAN}pkg install tur-repo && pkg install ollama${NC}"
else
echo -e "    ${CYAN}curl -fsSL https://ollama.com/install.sh | sh${NC}"
fi
echo -e "    Then run ${CYAN}zerodroid config --set provider=ollama${NC}"
echo -e "    ZeroDroid auto-starts Ollama & downloads models for you!"
echo ""

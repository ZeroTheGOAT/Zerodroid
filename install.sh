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

# ─── Remove old installation if exists ───────────────
if command -v zerodroid &> /dev/null; then
  echo -e "${YELLOW}Removing old ZeroDroid installation...${NC}"
  npm uninstall -g zerodroid 2>/dev/null || true
fi

# ─── Install ZeroDroid ───────────────────────────────
echo ""
echo -e "${CYAN}Installing ZeroDroid...${NC}"

# Clone to a temp directory, build, pack, and install the tarball globally
# This is the most reliable method across all platforms including Termux
TMPDIR_BASE="${TMPDIR:-/tmp}"
INSTALL_DIR="$TMPDIR_BASE/zerodroid-install-$$"
rm -rf "$INSTALL_DIR"

git clone --depth 1 https://github.com/ZeroTheGOAT/Zerodroid.git "$INSTALL_DIR" 2>/dev/null

cd "$INSTALL_DIR"
npm install --ignore-scripts 2>/dev/null
npm run build 2>/dev/null

# Pack into a tarball and install globally from that
# This copies files instead of symlinking, so it survives cleanup
TARBALL=$(npm pack 2>/dev/null | tail -1)
npm install -g "$TARBALL" 2>/dev/null

cd - > /dev/null
rm -rf "$INSTALL_DIR"

# ─── Verify installation ─────────────────────────────
if ! command -v zerodroid &> /dev/null; then
  echo -e "${YELLOW}Binary not found in PATH, creating link manually...${NC}"

  # Find where npm puts global packages
  NPM_BIN="$(npm config get prefix)/bin"
  NPM_GLOBAL_DIR="$(npm root -g)"

  # Find the actual zerodroid.js entry point
  ZERODROID_BIN="$NPM_GLOBAL_DIR/zerodroid/bin/zerodroid.js"

  if [ -f "$ZERODROID_BIN" ]; then
    chmod +x "$ZERODROID_BIN"
    ln -sf "$ZERODROID_BIN" "$NPM_BIN/zerodroid" 2>/dev/null || true

    # On Termux, also try linking to $PREFIX/bin
    if [ "$IS_TERMUX" = true ] && [ -d "$PREFIX/bin" ]; then
      ln -sf "$ZERODROID_BIN" "$PREFIX/bin/zerodroid" 2>/dev/null || true
    fi
  fi

  # Final fallback: create a wrapper script
  if ! command -v zerodroid &> /dev/null; then
    WRAPPER_DIR="$HOME/.local/bin"
    mkdir -p "$WRAPPER_DIR"
    cat > "$WRAPPER_DIR/zerodroid" << 'WRAPPER'
#!/usr/bin/env node
import("$(npm root -g)/zerodroid/dist/index.js");
WRAPPER
    chmod +x "$WRAPPER_DIR/zerodroid"

    # Add to PATH if not already there
    if [[ ":$PATH:" != *":$WRAPPER_DIR:"* ]]; then
      echo 'export PATH="$HOME/.local/bin:$PATH"' >> "$HOME/.bashrc"
      export PATH="$HOME/.local/bin:$PATH"
      echo -e "${YELLOW}Added ~/.local/bin to PATH (restart terminal or run: source ~/.bashrc)${NC}"
    fi
  fi
fi

# ─── Setup storage on Termux ─────────────────────────
if [ "$IS_TERMUX" = true ]; then
  if [ ! -d "$HOME/storage" ]; then
    echo ""
    echo -e "${YELLOW}Setting up storage access (tap Allow when prompted)...${NC}"
    termux-setup-storage || true
  fi
fi

# ─── Verify it works ─────────────────────────────────
echo ""
if command -v zerodroid &> /dev/null; then
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
else
  echo -e "${RED}${BOLD}Installation completed but 'zerodroid' command not found.${NC}"
  echo -e "${YELLOW}Try running manually:${NC}"
  echo -e "  ${CYAN}node $(npm root -g)/zerodroid/dist/index.js${NC}"
  echo ""
  echo -e "Or restart your terminal and try: ${CYAN}zerodroid${NC}"
  echo ""
fi

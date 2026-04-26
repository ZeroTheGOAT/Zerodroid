# ⚡ ZeroDroid

**Open-source AI coding agent — vibe code on Android, Linux, Mac, anywhere with a terminal.**

Build complete projects (websites, APIs, mobile apps, scripts) just by describing what you want. ZeroDroid writes the code, installs dependencies, runs commands, fixes errors, and manages your project — all from the terminal.

## ✨ Features

- 🤖 **AI-powered coding agent** — describe what you want, ZeroDroid builds it
- 📴 **Offline mode** — runs locally with Ollama + Gemma 4 (no internet needed)
- ☁️ **Cloud mode** — Gemini (free tier), Claude, OpenAI, OpenRouter
- 📁 **File management** — creates, reads, edits files automatically
- 🖥️ **Shell execution** — runs npm, pip, git, and any terminal command
- 🧠 **Memory** — remembers your projects, preferences, and conversation history
- 📱 **Works on Android** — runs in Termux, just like on a PC
- 🌐 **Cross-platform** — Android (Termux), Linux, macOS, WSL, Windows

## 🚀 Quick Start

### Install

```bash
npm install -g zerodroid
```

### On Android (Termux)

```bash
# Install Termux from F-Droid (NOT Play Store)
pkg install nodejs git -y
npm install -g zerodroid
zerodroid setup
```

### Configure

```bash
# Interactive setup (choose provider, set API key)
zerodroid config

# Or set directly
zerodroid config --set provider=gemini
zerodroid config --set gemini.apiKey=YOUR_KEY
```

### Use

```bash
# One-shot: describe what you want
zerodroid "Create a React portfolio with dark mode and contact form"

# Interactive chat mode
zerodroid chat

# Scaffold a project
zerodroid init react my-app

# Auto-install development tools
zerodroid setup
```

## 🤖 AI Providers

| Provider | Internet | Cost | Setup |
|----------|----------|------|-------|
| **Ollama** (Gemma 4) | ❌ Offline | Free | `pkg install ollama` → `ollama pull gemma4:e2b` |
| **Gemini** | ✅ Online | Free (1000 req/day) | Get key from [aistudio.google.com](https://aistudio.google.com) |
| **Claude** | ✅ Online | Paid | Coming in v0.2 |
| **OpenAI** | ✅ Online | Paid | Coming in v0.2 |
| **OpenRouter** | ✅ Online | Varies | Coming in v0.2 |

## 📱 Android Setup (Termux)

1. Install **Termux** from [F-Droid](https://f-droid.org) (NOT the Play Store)
2. Run:
   ```bash
   pkg update && pkg upgrade -y
   pkg install nodejs git -y
   npm install -g zerodroid
   zerodroid setup
   ```
3. For offline AI:
   ```bash
   pkg install tur-repo
   pkg install ollama
   ollama serve &
   ollama pull gemma4:e2b
   zerodroid config --set provider=ollama
   ```

## 🧠 Memory System

ZeroDroid remembers everything:
- **Project context** — tech stack, decisions, what was built
- **Conversation history** — pick up where you left off
- **User preferences** — your name, coding style, preferences

All stored locally in `~/.zerodroid/memory/`.

## 📂 What Can ZeroDroid Build?

Everything a developer can build from a terminal:
- ✅ React / Next.js / Vue / Vite websites
- ✅ Express / Flask / FastAPI / Django APIs
- ✅ React Native + Expo mobile apps (APK via EAS Build)
- ✅ Python scripts and ML projects
- ✅ CLI tools and npm packages
- ✅ Full-stack applications
- ✅ Static sites
- ✅ Anything else

## 🛠️ Development

```bash
git clone https://github.com/user/zerodroid
cd zerodroid
npm install
npm run build
node dist/index.js
```

## 📜 License

MIT — free and open source forever.

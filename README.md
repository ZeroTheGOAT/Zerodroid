# ⚡ ZeroDroid

**Open-source AI coding agent — vibe code on Android, Linux, Mac, anywhere with a terminal.**

Build complete projects (websites, APIs, mobile apps, scripts) just by describing what you want. ZeroDroid writes the code, installs dependencies, runs commands, fixes errors, and manages your project — all from the terminal.

---

## 🚀 Install (One Command)

### On Android (Termux)
> Install [Termux from F-Droid](https://f-droid.org) first (NOT the Play Store), then run:
```bash
curl -fsSL https://raw.githubusercontent.com/ZeroTheGOAT/Zerodroid/main/install.sh | bash
```
That's it. This single command installs Node.js, Git, and ZeroDroid automatically.

### On Linux / macOS / WSL
```bash
npm install -g github:ZeroTheGOAT/Zerodroid
```

### Already have npm?
```bash
npm install -g github:ZeroTheGOAT/Zerodroid
```

---

## ⚡ Usage

```bash
# Set up your AI provider (one time)
zerodroid config

# Then just tell it what to build
zerodroid "Create a React portfolio with dark mode"

# Or start an interactive chat session
zerodroid chat

# Check your environment
zerodroid setup
```

---

## ✨ Features

- 🤖 **AI-powered coding agent** — describe what you want, ZeroDroid builds it
- 📴 **Offline mode** — runs locally with Ollama + Gemma 4 (no internet needed)
- ☁️ **Cloud mode** — Gemini (free tier), Claude, OpenAI, OpenRouter
- 📁 **File management** — creates, reads, edits files automatically
- 🖥️ **Shell execution** — runs npm, pip, git, and any terminal command
- 🧠 **Memory** — remembers your projects, preferences, and conversation history
- 📱 **Works on Android** — runs in Termux, just like on a PC
- 🌐 **Cross-platform** — Android (Termux), Linux, macOS, WSL, Windows

## 🤖 AI Providers

| Provider | Internet | Cost | Setup |
|----------|----------|------|-------|
| **Ollama** (Gemma 4) | ❌ Offline | Free | `pkg install ollama` → `ollama pull gemma4:e2b` |
| **Gemini** | ✅ Online | Free (1000 req/day) | Get key from [aistudio.google.com](https://aistudio.google.com) |
| **Claude** | ✅ Online | Paid | Coming in v0.2 |
| **OpenAI** | ✅ Online | Paid | Coming in v0.2 |
| **OpenRouter** | ✅ Online | Varies | Coming in v0.2 |

## 📴 Offline AI (No Internet Needed)

ZeroDroid can run completely offline on your Android phone using Ollama + Gemma 4:

```bash
# Install Ollama in Termux
pkg install tur-repo
pkg install ollama

# Start it and download the model
ollama serve &
ollama pull gemma4:e2b

# Tell ZeroDroid to use it
zerodroid config --set provider=ollama
```

## 🧠 Memory System

ZeroDroid remembers everything locally in `~/.zerodroid/memory/`:
- **Project context** — tech stack, decisions, what was built
- **Conversation history** — pick up where you left off
- **User preferences** — your name, coding style

## 📂 What Can ZeroDroid Build?

Everything a developer can build from a terminal:
- ✅ React / Next.js / Vue / Vite websites
- ✅ Express / Flask / FastAPI / Django APIs
- ✅ React Native + Expo mobile apps
- ✅ Python scripts and ML projects
- ✅ CLI tools and npm packages
- ✅ Full-stack applications
- ✅ Anything else

## 🛠️ Contributing

```bash
git clone https://github.com/ZeroTheGOAT/Zerodroid.git
cd Zerodroid
npm install
npm run build
node dist/index.js
```

## 📜 License

MIT — free and open source forever.

/**
 * Ollama Provider — Local AI (Gemma 4 E2B, Llama, etc.)
 * Talks to Ollama at localhost:11434
 * Works offline on Android (Termux) and any machine with Ollama installed
 *
 * AUTO-START: If Ollama isn't running, ZeroDroid starts it automatically
 * in the background — no second terminal needed.
 */

import { spawn, execSync } from 'child_process';
import { totalmem } from 'os';
import type {
  AIProvider,
  CompletionOptions,
  CompletionResult,
  StreamChunk,
  Message,
  ToolCall,
  ToolDefinition,
} from './base.js';

export class OllamaProvider implements AIProvider {
  name = 'ollama';
  private host: string;
  private model: string;

  /**
   * Context window size — THIS IS THE KEY TO MOBILE PERFORMANCE
   * Default Gemma4 context is 128K which allocates GIGABYTES of KV cache RAM.
   * On mobile (12GB), that alone causes OOM crashes.
   * We limit to 4096 by default which uses only ~50-100MB of KV cache.
   */
  private numCtx: number;

  /**
   * Thread count — limits CPU cores to prevent thermal throttling on mobile.
   * Mobile chips throttle aggressively under sustained load.
   * Using 4 threads instead of all cores keeps the device cool and actually faster.
   */
  private numThread: number;

  constructor(host = 'http://localhost:11434', model = 'gemma4:e2b') {
    this.host = host.replace(/\/$/, '');
    this.model = model;

    // Auto-detect mobile environment
    const isTermux = !!(process.env.TERMUX_VERSION || process.env.PREFIX?.includes('com.termux'));
    const totalRAM = Math.round(totalmem() / 1024 / 1024 / 1024);

    if (isTermux || totalRAM <= 16) {
      // Mobile / low-RAM: limit context to prevent OOM crash
      // Context window is the key — 128K default allocates GB of KV cache
      this.numCtx = 4096;
    } else {
      // Desktop: larger context
      this.numCtx = 8192;
    }

    // Use all CPU cores for max speed — no thread limit
    this.numThread = 0; // 0 = Ollama auto-detects and uses all cores
  }

  /**
   * Check if Ollama server is reachable
   */
  private async ping(): Promise<boolean> {
    try {
      const res = await fetch(`${this.host}/api/version`);
      return res.ok;
    } catch {
      return false;
    }
  }

  /**
   * Check if the `ollama` binary exists on this system
   */
  private ollamaInstalled(): boolean {
    try {
      execSync('which ollama', { stdio: 'ignore' });
      return true;
    } catch {
      try {
        execSync('where ollama', { stdio: 'ignore' });
        return true;
      } catch {
        return false;
      }
    }
  }

  /**
   * Auto-start Ollama server in the background if it's not running.
   * Waits up to 10 seconds for it to become ready.
   */
  async autoStart(): Promise<boolean> {
    if (!this.ollamaInstalled()) {
      return false;
    }

    // Spawn ollama serve detached so it persists even if ZeroDroid exits
    const child = spawn('ollama', ['serve'], {
      stdio: 'ignore',
      detached: true,
      shell: true,
    });
    child.unref();

    // Wait for server to come online (up to 10 seconds)
    for (let i = 0; i < 20; i++) {
      await new Promise((r) => setTimeout(r, 500));
      if (await this.ping()) {
        return true;
      }
    }

    return false;
  }

  /**
   * Check if a model is already downloaded locally
   */
  async hasModel(modelName?: string): Promise<boolean> {
    const name = modelName || this.model;
    try {
      const res = await fetch(`${this.host}/api/tags`);
      if (!res.ok) return false;
      const data = await res.json() as { models?: Array<{ name: string }> };
      return data.models?.some((m) => m.name === name || m.name.startsWith(name.split(':')[0])) || false;
    } catch {
      return false;
    }
  }

  /**
   * Pull (download) a model via the Ollama API
   * No need for a second terminal — this uses the HTTP API directly!
   * @param onProgress - Callback for download progress
   */
  async pullModel(
    modelName?: string,
    onProgress?: (status: string, percent?: number) => void
  ): Promise<boolean> {
    const name = modelName || this.model;

    try {
      const res = await fetch(`${this.host}/api/pull`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, stream: true }),
      });

      if (!res.ok) {
        const errText = await res.text();
        throw new Error(`Failed to pull model: ${errText}`);
      }

      if (!res.body) {
        throw new Error('No response body for pull stream');
      }

      const decoder = new TextDecoder();
      const reader = res.body.getReader();
      let lastPercent = -1;

      try {
        let buffer = '';
        while (true) {
          const { done, value } = await reader.read();
          if (done) break;

          buffer += decoder.decode(value, { stream: true });
          const lines = buffer.split('\n');
          buffer = lines.pop() || '';

          for (const line of lines) {
            if (!line.trim()) continue;
            try {
              const parsed = JSON.parse(line) as {
                status?: string;
                completed?: number;
                total?: number;
              };

              if (parsed.status && onProgress) {
                let percent: number | undefined;
                if (parsed.total && parsed.completed) {
                  percent = Math.round((parsed.completed / parsed.total) * 100);
                  // Only report at 10% intervals to avoid spam
                  if (percent !== lastPercent && percent % 10 === 0) {
                    lastPercent = percent;
                    onProgress(parsed.status, percent);
                  }
                } else if (parsed.status !== 'pulling manifest') {
                  onProgress(parsed.status);
                }
              }
            } catch {
              // Skip malformed JSON
            }
          }
        }
      } finally {
        reader.releaseLock();
      }

      return true;
    } catch {
      return false;
    }
  }

  /**
   * Ensure a model is available — auto-pull if missing
   */
  async ensureModel(onProgress?: (status: string, percent?: number) => void): Promise<boolean> {
    if (await this.hasModel()) return true;
    return this.pullModel(undefined, onProgress);
  }

  /**
   * List all locally available models
   */
  async listModels(): Promise<string[]> {
    try {
      const res = await fetch(`${this.host}/api/tags`);
      if (!res.ok) return [];
      const data = await res.json() as { models?: Array<{ name: string }> };
      return data.models?.map((m) => m.name) || [];
    } catch {
      return [];
    }
  }

  /**
   * Check if Ollama is available — auto-starts it if not running
   */
  async isAvailable(): Promise<boolean> {
    // Already running? Great.
    if (await this.ping()) return true;

    // Not running — try to start it automatically
    return this.autoStart();
  }

  private formatMessages(messages: Message[]): Array<{ role: string; content: string }> {
    return messages.map((m) => ({
      role: m.role === 'tool' ? 'user' : m.role,
      content: m.content,
    }));
  }

  private formatTools(tools?: ToolDefinition[]) {
    if (!tools || tools.length === 0) return undefined;
    return tools.map((t) => ({
      type: 'function' as const,
      function: {
        name: t.function.name,
        description: t.function.description,
        parameters: t.function.parameters,
      },
    }));
  }

  async complete(options: CompletionOptions): Promise<CompletionResult> {
    const body: Record<string, unknown> = {
      model: this.model,
      messages: this.formatMessages(options.messages),
      stream: false,
      options: {
        temperature: options.temperature ?? 0.7,
        num_predict: options.maxTokens ?? 4096,
        num_ctx: this.numCtx,
        num_thread: this.numThread,
      },
    };

    const formattedTools = this.formatTools(options.tools);
    if (formattedTools) {
      body.tools = formattedTools;
    }

    const res = await fetch(`${this.host}/api/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });

    if (!res.ok) {
      const errText = await res.text();
      throw new Error(`Ollama error (${res.status}): ${errText}`);
    }

    const data = await res.json() as {
      message?: { content?: string; tool_calls?: Array<{ function: { name: string; arguments: Record<string, unknown> } }> };
      prompt_eval_count?: number;
      eval_count?: number;
    };

    const toolCalls: ToolCall[] = [];
    if (data.message?.tool_calls) {
      for (const tc of data.message.tool_calls) {
        toolCalls.push({
          id: `call_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
          type: 'function',
          function: {
            name: tc.function.name,
            arguments: JSON.stringify(tc.function.arguments),
          },
        });
      }
    }

    return {
      content: data.message?.content || '',
      toolCalls: toolCalls.length > 0 ? toolCalls : undefined,
      finishReason: toolCalls.length > 0 ? 'tool_calls' : 'stop',
      usage: {
        promptTokens: data.prompt_eval_count || 0,
        completionTokens: data.eval_count || 0,
        totalTokens: (data.prompt_eval_count || 0) + (data.eval_count || 0),
      },
    };
  }

  async *stream(options: CompletionOptions): AsyncIterable<StreamChunk> {
    const body: Record<string, unknown> = {
      model: this.model,
      messages: this.formatMessages(options.messages),
      stream: true,
      options: {
        temperature: options.temperature ?? 0.7,
        num_predict: options.maxTokens ?? 4096,
        num_ctx: this.numCtx,
        num_thread: this.numThread,
      },
    };

    const formattedTools = this.formatTools(options.tools);
    if (formattedTools) {
      body.tools = formattedTools;
    }

    const res = await fetch(`${this.host}/api/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });

    if (!res.ok) {
      const errText = await res.text();
      throw new Error(`Ollama stream error (${res.status}): ${errText}`);
    }

    if (!res.body) {
      throw new Error('No response body for streaming');
    }

    const decoder = new TextDecoder();
    const reader = res.body.getReader();

    try {
      let buffer = '';
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';

        for (const line of lines) {
          if (!line.trim()) continue;
          try {
            const parsed = JSON.parse(line) as {
              message?: { content?: string };
              done?: boolean;
            };
            yield {
              content: parsed.message?.content || '',
              done: parsed.done || false,
            };
          } catch {
            // Skip malformed JSON lines
          }
        }
      }
    } finally {
      reader.releaseLock();
    }
  }
}

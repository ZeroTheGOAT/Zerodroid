/**
 * Gemini Provider — Google's Gemini API (Free Tier: 1000 req/day)
 * Uses the REST API directly — no SDK dependency needed
 */

import type {
  AIProvider,
  CompletionOptions,
  CompletionResult,
  StreamChunk,
  Message,
  ToolCall,
  ToolDefinition,
} from './base.js';

const GEMINI_BASE = 'https://generativelanguage.googleapis.com/v1beta/models';

export class GeminiProvider implements AIProvider {
  name = 'gemini';
  private apiKey: string;
  private model: string;

  constructor(apiKey: string, model = 'gemini-2.5-flash') {
    this.apiKey = apiKey;
    this.model = model;
  }

  async isAvailable(): Promise<boolean> {
    if (!this.apiKey) return false;
    try {
      const res = await fetch(
        `${GEMINI_BASE}/${this.model}?key=${this.apiKey}`
      );
      return res.ok;
    } catch {
      return false;
    }
  }

  private convertMessages(messages: Message[]) {
    const systemParts: string[] = [];
    const contents: Array<{ role: string; parts: Array<{ text: string }> }> = [];

    for (const msg of messages) {
      if (msg.role === 'system') {
        systemParts.push(msg.content);
        continue;
      }

      const role = msg.role === 'assistant' ? 'model' : 'user';
      contents.push({
        role,
        parts: [{ text: msg.content }],
      });
    }

    return {
      systemInstruction: systemParts.length > 0
        ? { parts: [{ text: systemParts.join('\n\n') }] }
        : undefined,
      contents,
    };
  }

  private convertTools(tools?: ToolDefinition[]) {
    if (!tools || tools.length === 0) return undefined;
    return [
      {
        functionDeclarations: tools.map((t) => ({
          name: t.function.name,
          description: t.function.description,
          parameters: t.function.parameters,
        })),
      },
    ];
  }

  async complete(options: CompletionOptions): Promise<CompletionResult> {
    const { systemInstruction, contents } = this.convertMessages(options.messages);

    const body: Record<string, unknown> = {
      contents,
      generationConfig: {
        temperature: options.temperature ?? 0.7,
        maxOutputTokens: options.maxTokens ?? 8192,
      },
    };

    if (systemInstruction) {
      body.systemInstruction = systemInstruction;
    }

    const geminiTools = this.convertTools(options.tools);
    if (geminiTools) {
      body.tools = geminiTools;
    }

    const url = `${GEMINI_BASE}/${this.model}:generateContent?key=${this.apiKey}`;
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });

    if (!res.ok) {
      const errText = await res.text();
      throw new Error(`Gemini error (${res.status}): ${errText}`);
    }

    const data = await res.json() as {
      candidates?: Array<{
        content?: {
          parts?: Array<{
            text?: string;
            functionCall?: { name: string; args: Record<string, unknown> };
          }>;
        };
        finishReason?: string;
      }>;
      usageMetadata?: {
        promptTokenCount?: number;
        candidatesTokenCount?: number;
        totalTokenCount?: number;
      };
    };

    const candidate = data.candidates?.[0];
    const parts = candidate?.content?.parts || [];

    let content = '';
    const toolCalls: ToolCall[] = [];

    for (const part of parts) {
      if (part.text) {
        content += part.text;
      }
      if (part.functionCall) {
        toolCalls.push({
          id: `call_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
          type: 'function',
          function: {
            name: part.functionCall.name,
            arguments: JSON.stringify(part.functionCall.args),
          },
        });
      }
    }

    return {
      content,
      toolCalls: toolCalls.length > 0 ? toolCalls : undefined,
      finishReason: toolCalls.length > 0 ? 'tool_calls' : 'stop',
      usage: {
        promptTokens: data.usageMetadata?.promptTokenCount || 0,
        completionTokens: data.usageMetadata?.candidatesTokenCount || 0,
        totalTokens: data.usageMetadata?.totalTokenCount || 0,
      },
    };
  }

  async *stream(options: CompletionOptions): AsyncIterable<StreamChunk> {
    const { systemInstruction, contents } = this.convertMessages(options.messages);

    const body: Record<string, unknown> = {
      contents,
      generationConfig: {
        temperature: options.temperature ?? 0.7,
        maxOutputTokens: options.maxTokens ?? 8192,
      },
    };

    if (systemInstruction) {
      body.systemInstruction = systemInstruction;
    }

    const geminiTools = this.convertTools(options.tools);
    if (geminiTools) {
      body.tools = geminiTools;
    }

    const url = `${GEMINI_BASE}/${this.model}:streamGenerateContent?alt=sse&key=${this.apiKey}`;
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });

    if (!res.ok) {
      const errText = await res.text();
      throw new Error(`Gemini stream error (${res.status}): ${errText}`);
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
        if (done) {
          yield { done: true };
          break;
        }

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';

        for (const line of lines) {
          if (!line.startsWith('data: ')) continue;
          const jsonStr = line.slice(6).trim();
          if (!jsonStr || jsonStr === '[DONE]') continue;

          try {
            const parsed = JSON.parse(jsonStr) as {
              candidates?: Array<{
                content?: {
                  parts?: Array<{ text?: string }>;
                };
              }>;
            };
            const text = parsed.candidates?.[0]?.content?.parts?.[0]?.text || '';
            if (text) {
              yield { content: text, done: false };
            }
          } catch {
            // Skip malformed SSE data
          }
        }
      }
    } finally {
      reader.releaseLock();
    }
  }
}

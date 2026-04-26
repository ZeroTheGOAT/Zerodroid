/**
 * Provider Factory
 * Creates the appropriate AI provider based on config
 */

import type { AIProvider } from '../agent/providers/base.js';
import type { ZeroDroidConfig } from '../utils/config.js';
import { OllamaProvider } from '../agent/providers/ollama.js';
import { GeminiProvider } from '../agent/providers/gemini.js';

export function createProvider(config: ZeroDroidConfig, modelOverride?: string): AIProvider {
  switch (config.provider) {
    case 'ollama':
      return new OllamaProvider(
        config.ollama.host,
        modelOverride || config.ollama.model
      );

    case 'gemini':
      return new GeminiProvider(
        config.gemini.apiKey,
        modelOverride || config.gemini.model
      );

    case 'claude':
      // For now, use Gemini as fallback — Claude provider will be added in Phase 2
      if (!config.claude.apiKey) {
        throw new Error('Claude API key not configured. Run: zerodroid config');
      }
      // TODO: Add Claude provider in Phase 2
      throw new Error('Claude provider coming in v0.2. Use "gemini" or "ollama" for now.');

    case 'openai':
      if (!config.openai.apiKey) {
        throw new Error('OpenAI API key not configured. Run: zerodroid config');
      }
      // TODO: Add OpenAI provider in Phase 2
      throw new Error('OpenAI provider coming in v0.2. Use "gemini" or "ollama" for now.');

    case 'openrouter':
      if (!config.openrouter.apiKey) {
        throw new Error('OpenRouter API key not configured. Run: zerodroid config');
      }
      // TODO: Add OpenRouter provider in Phase 2
      throw new Error('OpenRouter provider coming in v0.2. Use "gemini" or "ollama" for now.');

    default:
      throw new Error(`Unknown provider: ${config.provider}`);
  }
}

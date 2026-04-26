/**
 * AI Provider Interface
 * All AI providers must implement this interface
 */

export interface Message {
  role: 'system' | 'user' | 'assistant' | 'tool';
  content: string;
  tool_call_id?: string;
  tool_calls?: ToolCall[];
}

export interface ToolCall {
  id: string;
  type: 'function';
  function: {
    name: string;
    arguments: string;
  };
}

export interface ToolDefinition {
  type: 'function';
  function: {
    name: string;
    description: string;
    parameters: {
      type: 'object';
      properties: Record<string, unknown>;
      required?: string[];
    };
  };
}

export interface CompletionOptions {
  messages: Message[];
  tools?: ToolDefinition[];
  temperature?: number;
  maxTokens?: number;
  stream?: boolean;
}

export interface CompletionResult {
  content: string;
  toolCalls?: ToolCall[];
  finishReason: 'stop' | 'tool_calls' | 'length' | 'error';
  usage?: {
    promptTokens: number;
    completionTokens: number;
    totalTokens: number;
  };
}

export interface StreamChunk {
  content?: string;
  toolCalls?: ToolCall[];
  done: boolean;
}

export interface AIProvider {
  name: string;
  isAvailable(): Promise<boolean>;
  complete(options: CompletionOptions): Promise<CompletionResult>;
  stream(options: CompletionOptions): AsyncIterable<StreamChunk>;
}

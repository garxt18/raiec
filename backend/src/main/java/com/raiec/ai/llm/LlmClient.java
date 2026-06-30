package com.raiec.ai.llm;

/**
 * Minimal LLM abstraction. Implementations call an external chat model; if no API key is
 * configured the client reports {@link #isEnabled()} == false and callers fall back to the
 * built-in rule-based narrative, so the app works with or without an LLM provider.
 */
public interface LlmClient {

    boolean isEnabled();

    /** Returns the model's text completion, or throws on any transport/parse error. */
    String complete(String systemPrompt, String userPrompt);
}

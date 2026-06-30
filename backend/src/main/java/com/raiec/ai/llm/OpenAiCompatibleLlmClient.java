package com.raiec.ai.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Calls an OpenAI-compatible {@code /chat/completions} endpoint. This works with OpenAI,
 * Groq, OpenRouter, Together and similar providers — just point {@code raiec.llm.base-url},
 * {@code raiec.llm.model} and {@code raiec.llm.api-key} at the provider of your choice.
 *
 * If no API key is set, {@link #isEnabled()} returns false and the assessment service uses
 * its rule-based fallback instead.
 */
@Component
public class OpenAiCompatibleLlmClient implements LlmClient {

    private final boolean enabledProp;
    private final String apiKey;
    private final String model;
    private final double temperature;
    private final int maxTokens;
    private final RestClient http;

    public OpenAiCompatibleLlmClient(
            @Value("${raiec.llm.enabled:true}") boolean enabledProp,
            @Value("${raiec.llm.api-key:}") String apiKey,
            @Value("${raiec.llm.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${raiec.llm.model:gpt-4o-mini}") String model,
            @Value("${raiec.llm.temperature:0.3}") double temperature,
            @Value("${raiec.llm.max-tokens:400}") int maxTokens,
            @Value("${raiec.llm.timeout-ms:20000}") int timeoutMs) {
        this.enabledProp = enabledProp;
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        this.http = RestClient.builder().requestFactory(factory).baseUrl(baseUrl).build();
    }

    @Override
    public boolean isEnabled() {
        return enabledProp && apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", temperature,
                "max_tokens", maxTokens,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)));

        Map<?, ?> response = http.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        if (response == null) {
            throw new IllegalStateException("Empty LLM response");
        }
        List<?> choices = (List<?>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("LLM response had no choices");
        }
        Map<?, ?> first = (Map<?, ?>) choices.get(0);
        Map<?, ?> message = (Map<?, ?>) first.get("message");
        Object content = message == null ? null : message.get("content");
        return content == null ? "" : content.toString();
    }
}

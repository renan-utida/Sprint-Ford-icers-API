package com.icers.ford.client;

import java.util.List;

/**
 * Representa a requisição enviada para a API do LLM.
 * Mapeado para o formato da API da Anthropic (Claude).
 */
public record LlmRequest(
        String model,
        int max_tokens,
        List<Message> messages
) {
    public record Message(
            String role,
            String content
    ) {}

    /**
     * Factory method — monta a requisição com o prompt pronto
     */
    public static LlmRequest of(String model, String prompt) {
        return new LlmRequest(
                model,
                2000,
                List.of(new Message("user", prompt))
        );
    }
}
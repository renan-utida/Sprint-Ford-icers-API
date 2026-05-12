package com.icers.ford.client;

import java.util.List;

/**
 * Representa a requisição enviada para a API do Google Gemini.
 */
public record LlmRequest(
        List<Content> contents,
        GenerationConfig generationConfig
) {
    public record Content(
            List<Part> parts
    ) {}

    public record Part(
            String text
    ) {}

    public record GenerationConfig(
            int maxOutputTokens,
            double temperature
    ) {}

    /**
     * Factory method — monta a requisição com o prompt pronto
     */
    public static LlmRequest of(String prompt) {
        return new LlmRequest(
                List.of(new Content(List.of(new Part(prompt)))),
                new GenerationConfig(2000, 0.1)
        );
    }
}
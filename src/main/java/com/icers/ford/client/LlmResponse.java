package com.icers.ford.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Representa a resposta da API do Google Gemini.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmResponse(
        List<Candidate> candidates,
        UsageMetadata usageMetadata
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Candidate(
            Content content,
            String finishReason
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Content(
            List<Part> parts,
            String role
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Part(
            String text
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UsageMetadata(
            Integer promptTokenCount,
            Integer candidatesTokenCount,
            Integer totalTokenCount
    ) {}

    /**
     * Extrai o texto da primeira resposta do modelo.
     * A API do Gemini retorna em candidates[0].content.parts[0].text
     */
    public String extractText() {
        if (candidates == null || candidates.isEmpty()) return null;
        Content content = candidates.getFirst().content();
        if (content == null || content.parts() == null
                || content.parts().isEmpty()) return null;
        return content.parts().getFirst().text();
    }
}
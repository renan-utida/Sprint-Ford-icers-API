package com.icers.ford.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Representa a resposta da API da Anthropic (Claude).
 * @JsonIgnoreProperties ignora campos extras que não nos interessam.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmResponse(
        String id,
        List<ContentBlock> content,
        Usage usage
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContentBlock(
            String type,
            String text
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            Integer input_tokens,
            Integer output_tokens
    ) {}

    /**
     * Extrai o texto da primeira resposta do modelo.
     * A API da Anthropic retorna o texto dentro de content[0].text
     */
    public String extractText() {
        if (content == null || content.isEmpty()) {
            return null;
        }
        return content.stream()
                .filter(block -> "text".equals(block.type()))
                .findFirst()
                .map(ContentBlock::text)
                .orElse(null);
    }
}
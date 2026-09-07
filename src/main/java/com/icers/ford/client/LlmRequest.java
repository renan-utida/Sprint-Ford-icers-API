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
            double temperature,
            ThinkingConfig thinkingConfig
    ) {}

    /**
     * Desabilita o "thinking" (raciocínio interno) dos modelos Gemini
     * 2.5+ / 3.x. Sem isso, o modelo gasta parte do maxOutputTokens
     * só "pensando" antes de escrever a resposta — em orçamentos
     * apertados isso corta o JSON de especificações no meio (ver
     * thoughtsTokenCount no response). Extração estruturada não se
     * beneficia de raciocínio em cadeia; desabilitar deixa a resposta
     * mais rápida, mais previsível e não desperdiça tokens da cota.
     */
    public record ThinkingConfig(
            int thinkingBudget
    ) {}

    /**
     * Factory method — monta a requisição com o prompt pronto
     */
    public static LlmRequest of(String prompt) {
        return new LlmRequest(
                List.of(new Content(List.of(new Part(prompt)))),
                new GenerationConfig(8192, 0.1, new ThinkingConfig(0))
        );
    }
}
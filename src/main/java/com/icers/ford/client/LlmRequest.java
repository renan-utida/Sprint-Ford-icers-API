package com.icers.ford.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

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

    /**
     * Um "part" é texto OU dado inline (nunca os dois) — os campos nulos
     * são omitidos da serialização (@JsonInclude) pra bater exatamente
     * com o formato que a API espera em cada caso.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Part(
            String text,
            @JsonProperty("inline_data") InlineData inlineData
    ) {
        public static Part deTexto(String text) {
            return new Part(text, null);
        }

        public static Part deArquivo(String mimeType, String base64) {
            return new Part(null, new InlineData(mimeType, base64));
        }
    }

    public record InlineData(
            @JsonProperty("mime_type") String mimeType,
            String data
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
                List.of(new Content(List.of(Part.deTexto(prompt)))),
                new GenerationConfig(8192, 0.1, new ThinkingConfig(0))
        );
    }

    /**
     * Factory method — monta a requisição com o prompt e um arquivo
     * (PDF) anexado via inlineData, no mesmo Content.
     */
    public static LlmRequest comPdf(String prompt, String pdfBase64) {
        return new LlmRequest(
                List.of(new Content(List.of(
                        Part.deTexto(prompt),
                        Part.deArquivo("application/pdf", pdfBase64)
                ))),
                new GenerationConfig(8192, 0.1, new ThinkingConfig(0))
        );
    }
}
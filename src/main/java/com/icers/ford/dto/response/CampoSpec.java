package com.icers.ford.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Representa um campo individual da ficha técnica com seu nível de confiança")
public record CampoSpec(

        @Schema(
                description = "Nome do atributo técnico",
                example = "motor"
        )
        String campo,

        @Schema(
                description = "Valor encontrado. Null quando não disponível.",
                example = "V6 3.0L Nano bi turbo",
                nullable = true
        )
        @JsonInclude(JsonInclude.Include.ALWAYS)
        String valor,

        @Schema(
                description = "Nível de confiança da informação",
                example = "ALTA",
                allowableValues = {"ALTA", "MEDIA", "INFERIDA", "NAO_ENCONTRADO"}
        )
        String confianca,

        @Schema(
                description = "URL da fonte onde o dado foi encontrado. Null quando não encontrado.",
                example = "https://www.ford.com.br/caminhonetes/ranger",
                nullable = true
        )
        @JsonInclude(JsonInclude.Include.ALWAYS)
        String fonte,

        @Schema(
                description = "Data de verificação do dado no formato ISO-8601",
                example = "2026-05-10",
                nullable = true
        )
        @JsonProperty("verificado_em")
        @JsonInclude(JsonInclude.Include.ALWAYS)
        String verificadoEm
) {
    /**
     * Factory method para campo encontrado com alta confiança
     */
    public static CampoSpec encontrado(String campo, String valor,
                                       String confianca, String fonte,
                                       String verificadoEm) {
        return new CampoSpec(campo, valor, confianca, fonte, verificadoEm);
    }

    /**
     * Factory method para campo não encontrado.
     * O campo aparece no response com valor null — nunca omitido.
     * Isso é o que a Ford pediu: formato fixo independente do veículo.
     */
    public static CampoSpec naoEncontrado(String campo) {
        return new CampoSpec(campo, null, "NAO_ENCONTRADO", null, null);
    }
}
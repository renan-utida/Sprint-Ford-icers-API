package com.icers.ford.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Ficha técnica padronizada de um veículo. " +
        "Todos os campos solicitados sempre presentes no response, " +
        "mesmo quando não encontrados.")
public record SpecResponse(

        @Schema(description = "ID da ficha técnica no banco — use em DELETE /specs/{id}", example = "1")
        Long id,

        @Schema(description = "Marca do veículo", example = "Ford")
        String marca,

        @Schema(description = "Modelo do veículo", example = "Ranger")
        String modelo,

        @Schema(description = "Versão do veículo", example = "Raptor")
        String versao,

        @Schema(description = "Lista de campos técnicos solicitados com seus valores e confiança")
        List<CampoSpec> campos,

        @Schema(
                description = "Nível de confiança geral da ficha",
                example = "ALTA",
                allowableValues = {"ALTA", "MEDIA", "PARCIAL", "BAIXA"}
        )
        @JsonProperty("confidence_geral")
        String confidenceGeral,

        @Schema(
                description = "Data e hora da consulta",
                example = "2026-05-10T14:30:00"
        )
        @JsonProperty("consultado_em")
        LocalDateTime consultadoEm,

        @Schema(
                description = "Indica se o resultado veio do banco (true) ou do LLM (false)",
                example = "false"
        )
        @JsonProperty("cache_hit")
        boolean cacheHit
) {
    /**
     * Factory method para resultado novo — veio do LLM
     */
    public static SpecResponse fromLlm(Long id, String marca, String modelo,
                                       String versao, List<CampoSpec> campos,
                                       String confidenceGeral) {
        return new SpecResponse(
                id, marca, modelo, versao,
                campos, confidenceGeral,
                LocalDateTime.now(),
                false
        );
    }

    /**
     * Factory method para resultado do banco — cache hit
     */
    public static SpecResponse fromCache(Long id, String marca, String modelo,
                                         String versao, List<CampoSpec> campos,
                                         String confidenceGeral,
                                         LocalDateTime consultadoEm) {
        return new SpecResponse(
                id, marca, modelo, versao,
                campos, confidenceGeral,
                consultadoEm,
                true
        );
    }
}
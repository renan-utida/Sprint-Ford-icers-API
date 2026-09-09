package com.icers.ford.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Schema(description = "Resposta padronizada de erro. " +
        "Nunca contém stack trace, nome de classe ou tecnologia interna.")
public record ErrorResponse(

        @Schema(
                description = "Código do erro",
                example = "VALIDATION_ERROR"
        )
        @JsonProperty("codigo_erro")
        String codigoErro,

        @Schema(
                description = "Mensagem amigável do erro",
                example = "Um ou mais campos estão inválidos"
        )
        String mensagem,

        @Schema(
                description = "Data e hora do erro",
                example = "2026-05-10T14:30:00"
        )
        LocalDateTime timestamp,

        @Schema(
                description = "Endpoint que gerou o erro",
                example = "/api/v1/specs/query"
        )
        String endpoint,

        @Schema(
                description = "Detalhes dos campos inválidos. " +
                        "Presente apenas em erros de validação (400).",
                nullable = true,
                example = "{\"marca\": \"Marca deve conter apenas letras, espaços e hífens\"}"
        )
        @JsonProperty("campos_invalidos")
        Map<String, String> camposInvalidos
) {
    /**
     * Erro genérico — sem detalhes de campos
     */
    public static ErrorResponse of(String codigoErro,
                                   String mensagem,
                                   String endpoint) {
        return new ErrorResponse(
                codigoErro,
                mensagem,
                LocalDateTime.now(),
                endpoint,
                null
        );
    }

    /**
     * Erro de validação — com lista de campos inválidos
     */
    public static ErrorResponse ofValidation(String endpoint,
                                             Map<String, String> camposInvalidos) {
        return new ErrorResponse(
                "VALIDATION_ERROR",
                "Um ou mais campos estão inválidos",
                LocalDateTime.now(),
                endpoint,
                camposInvalidos
        );
    }

    /**
     * Erro 404 — recurso não encontrado
     */
    public static ErrorResponse notFound(String mensagem, String endpoint) {
        return of("NOT_FOUND", mensagem, endpoint);
    }

    /**
     * Erro 503 — serviço externo indisponível, sem revelar tecnologia
     */
    public static ErrorResponse serviceUnavailable(String endpoint) {
        return of(
                "SERVICE_UNAVAILABLE",
                "O serviço está temporariamente indisponível. Tente novamente em instantes.",
                endpoint
        );
    }

    /**
     * Erro 429 — rate limit excedido
     */
    public static ErrorResponse rateLimitExceeded(String endpoint, long retryAfterSeconds) {
        return of(
                "RATE_LIMIT_EXCEEDED",
                "Limite de requisições excedido. Aguarde " + retryAfterSeconds + " segundos.",
                endpoint
        );
    }

    /**
     * Erro 429 — conta temporariamente bloqueada por excesso de
     * tentativas de login falhas
     */
    public static ErrorResponse accountLocked(String endpoint, long retryAfterSeconds) {
        return of(
                "ACCOUNT_LOCKED",
                "Muitas tentativas de login falhas. Aguarde " + retryAfterSeconds
                        + " segundos antes de tentar novamente.",
                endpoint
        );
    }
}
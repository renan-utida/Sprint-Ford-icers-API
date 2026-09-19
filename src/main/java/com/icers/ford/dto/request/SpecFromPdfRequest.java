package com.icers.ford.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.util.List;

/**
 * Campos de texto do multipart/form-data de POST /specs/from-pdf —
 * mesma validação do SpecQueryRequest. O arquivo PDF chega separado,
 * como MultipartFile, no controller.
 */
public record SpecFromPdfRequest(

        @NotBlank(message = "Marca é obrigatória")
        @Size(min = 2, max = 50,
                message = "Marca deve ter entre 2 e 50 caracteres")
        @Pattern(
                regexp = "^[a-zA-ZÀ-ÿ\\s\\-]+$",
                message = "Marca deve conter apenas letras, espaços e hífens"
        )
        @Schema(example = "Ford")
        String marca,

        @NotBlank(message = "Modelo é obrigatório")
        @Size(min = 2, max = 80,
                message = "Modelo deve ter entre 2 e 80 caracteres")
        @Pattern(
                regexp = "^[a-zA-ZÀ-ÿ\\s\\-]+$",
                message = "Modelo deve conter apenas letras, espaços e hífens"
        )
        @Schema(example = "Ranger")
        String modelo,

        @NotBlank(message = "Versão é obrigatória")
        @Size(min = 2, max = 80,
                message = "Versão deve ter entre 2 e 80 caracteres")
        @Pattern(
                regexp = "^[a-zA-ZÀ-ÿ0-9\\s\\-\\.]+$",
                message = "Versão deve conter apenas letras, números, espaços, hífens e pontos"
        )
        @Schema(example = "Raptor")
        String versao,

        @NotEmpty(message = "Lista de atributos é obrigatória")
        @Size(max = 20, message = "Máximo de 20 atributos por consulta")
        @Schema(example = "[\"motor\", \"potencia\", \"preco\"]")
        List<String> atributos
) {}

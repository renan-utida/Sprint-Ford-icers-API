package com.icers.ford.dto.request;

import jakarta.validation.constraints.*;

import java.util.List;

public record SpecQueryRequest(

        @NotBlank(message = "Marca é obrigatória")
        @Size(min = 2, max = 50,
                message = "Marca deve ter entre 2 e 50 caracteres")
        @Pattern(
                regexp = "^[a-zA-ZÀ-ÿ\\s\\-]+$",
                message = "Marca deve conter apenas letras, espaços e hífens"
        )
        String marca,

        @NotBlank(message = "Modelo é obrigatório")
        @Size(min = 2, max = 80,
                message = "Modelo deve ter entre 2 e 80 caracteres")
        @Pattern(
                regexp = "^[a-zA-ZÀ-ÿ\\s\\-]+$",
                message = "Modelo deve conter apenas letras, espaços e hífens"
        )
        String modelo,

        @NotBlank(message = "Versão é obrigatória")
        @Size(min = 2, max = 80,
                message = "Versão deve ter entre 2 e 80 caracteres")
        @Pattern(
                regexp = "^[a-zA-ZÀ-ÿ0-9\\s\\-\\.]+$",
                message = "Versão deve conter apenas letras, números, espaços, hífens e pontos"
        )
        String versao,

        @NotEmpty(message = "Lista de atributos é obrigatória")
        @Size(max = 20, message = "Máximo de 20 atributos por consulta")
        List<String> atributos
) {}
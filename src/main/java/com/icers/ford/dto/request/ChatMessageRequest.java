package com.icers.ford.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatMessageRequest(

        @NotBlank(message = "Mensagem é obrigatória")
        @Size(min = 3, max = 500,
                message = "Mensagem deve ter entre 3 e 500 caracteres")
        String mensagem
) {}
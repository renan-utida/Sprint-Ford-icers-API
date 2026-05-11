package com.icers.ford.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(

        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Email deve ter formato válido")
        @Size(max = 150, message = "Email deve ter no máximo 150 caracteres")
        String email,

        @NotBlank(message = "Senha é obrigatória")
        @Size(min = 6, max = 100, message = "Senha deve ter entre 6 e 100 caracteres")
        String senha
) {}
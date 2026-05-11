package com.icers.ford.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(

        @NotBlank(message = "Refresh token é obrigatório")
        String refreshToken
) {}
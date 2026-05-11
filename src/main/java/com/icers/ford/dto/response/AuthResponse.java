package com.icers.ford.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AuthResponse(

        @JsonProperty("access_token")
        String accessToken,

        @JsonProperty("refresh_token")
        String refreshToken,

        @JsonProperty("token_type")
        String tokenType,

        @JsonProperty("expires_in")
        long expiresIn,

        String role
) {
    /**
     * Factory method para login completo
     */
    public static AuthResponse of(String accessToken,
                                  String refreshToken,
                                  long expiresIn,
                                  String role) {
        return new AuthResponse(
                accessToken,
                refreshToken,
                "Bearer",
                expiresIn,
                role
        );
    }

    /**
     * Factory method para refresh — retorna novo par de tokens
     */
    public static AuthResponse ofRefresh(String accessToken,
                                         String refreshToken,
                                         long expiresIn,
                                         String role) {
        return of(accessToken, refreshToken, expiresIn, role);
    }
}
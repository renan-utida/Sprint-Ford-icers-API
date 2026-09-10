package com.icers.ford.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Dados públicos de um usuário — nunca inclui a senha/hash")
public record UsuarioResponse(
        Long id,
        String nome,
        String email,
        String role,
        @Schema(description = "'S' ativo, 'N' desativado/anonimizado")
        String ativo,
        LocalDateTime criadoEm,
        LocalDateTime ultimoAcesso
) {}
package com.icers.ford.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Atualização de dados do usuário — email e role.
 * <p>
 * Propositalmente SEM campo de senha: alterar senha é uma ação
 * sensível o bastante para merecer um fluxo dedicado (idealmente
 * exigindo confirmação da senha atual, ou reset via email), não algo
 * embutido silenciosamente num PUT genérico de atualização de perfil.
 * Não implementado nesta Sprint.
 */
public record UsuarioUpdateRequest(

        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 150, message = "Nome deve ter no máximo 150 caracteres")
        String nome,

        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Email inválido")
        String email,

        @NotBlank(message = "Role é obrigatória")
        @Pattern(regexp = "ANALYST|ADMIN", message = "Role deve ser ANALYST ou ADMIN")
        String role
) {}
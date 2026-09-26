package com.icers.ford.exception;

import lombok.Getter;

/**
 * Lançada ao tentar anonimizar um usuário que ainda está ativo.
 * A anonimização é irreversível, então exige a etapa deliberada de
 * desativação antes — evita que a conta de alguém em uso seja apagada
 * de forma definitiva num único passo.
 */
@Getter
public class UsuarioAindaAtivoException extends RuntimeException {

    private final Long id;

    public UsuarioAindaAtivoException(Long id) {
        super("Usuário " + id + " precisa estar desativado antes de ser anonimizado.");
        this.id = id;
    }
}

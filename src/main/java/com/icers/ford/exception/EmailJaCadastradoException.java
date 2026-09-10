package com.icers.ford.exception;

/**
 * Lançada ao tentar criar/atualizar um usuário com um email que já
 * pertence a outra conta (uk_sr_usuario_email no banco).
 */
public class EmailJaCadastradoException extends RuntimeException {

    public EmailJaCadastradoException() {
        super("Já existe um usuário cadastrado com este email");
    }
}
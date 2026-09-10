package com.icers.ford.exception;

/**
 * Lançada quando um usuário não existe no banco.
 * O GlobalExceptionHandler converte para 404.
 */
public class UsuarioNaoEncontradoException extends RuntimeException {

    public UsuarioNaoEncontradoException(Long id) {
        super("Usuário não encontrado para o id: " + id);
    }
}
package com.icers.ford.exception;

/**
 * Lançada quando um ADMIN tenta desativar a própria conta.
 * Desativar (ativo='N') bloqueia login imediatamente — mesmo sendo
 * reversível por OUTRO admin, é um jeito fácil de se trancar pra fora
 * sem querer.
 */
public class AutoDesativacaoException extends RuntimeException {

    public AutoDesativacaoException() {
        super("Não é permitido desativar a própria conta.");
    }
}
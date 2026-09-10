package com.icers.ford.exception;

/**
 * Lançada quando um ADMIN tenta anonimizar a própria conta.
 * Bloqueado de propósito: anonimizar a si mesmo removeria o próprio
 * acesso do admin, sem nenhum outro ADMIN necessariamente disponível
 * pra reverter (a anonimização é irreversível via API).
 */
public class AutoAnonimizacaoException extends RuntimeException {

    public AutoAnonimizacaoException() {
        super("Não é permitido anonimizar a própria conta.");
    }
}
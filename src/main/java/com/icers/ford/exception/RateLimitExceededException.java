package com.icers.ford.exception;

/**
 * Lançada quando o usuário excede o limite de requisições.
 * O GlobalExceptionHandler converte para 429 com header Retry-After.
 */
public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super("Limite de requisições excedido. " +
                "Aguarde " + retryAfterSeconds + " segundos.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
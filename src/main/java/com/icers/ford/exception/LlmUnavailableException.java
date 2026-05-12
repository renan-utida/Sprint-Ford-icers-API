package com.icers.ford.exception;

/**
 * Lançada quando o serviço LLM está indisponível ou retorna erro.
 * O GlobalExceptionHandler converte para 503 com mensagem genérica
 * — sem revelar qual tecnologia está sendo usada internamente.
 */
public class LlmUnavailableException extends RuntimeException {

    public LlmUnavailableException(String message) {
        super(message);
    }

    public LlmUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
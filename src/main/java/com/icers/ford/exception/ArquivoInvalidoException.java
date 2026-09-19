package com.icers.ford.exception;

/**
 * Lançada quando o arquivo enviado em POST /specs/from-pdf não é um
 * PDF válido (content-type errado, vazio, ou sem a assinatura %PDF-).
 * O GlobalExceptionHandler converte para 422 — validado ANTES de
 * gastar uma chamada multimodal (mais cara) que já sabemos que falharia.
 */
public class ArquivoInvalidoException extends RuntimeException {

    public ArquivoInvalidoException(String message) {
        super(message);
    }
}

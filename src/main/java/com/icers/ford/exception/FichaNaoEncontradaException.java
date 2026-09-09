package com.icers.ford.exception;

/**
 * Lançada quando uma ficha técnica não existe no banco.
 * O GlobalExceptionHandler converte para 404 com mensagem padronizada.
 */
public class FichaNaoEncontradaException extends RuntimeException {

    public FichaNaoEncontradaException(String marca, String modelo, String versao) {
        super("Ficha técnica não encontrada para: "
                + marca + " " + modelo + " " + versao
                + ". Use POST /api/v1/specs/query para consultar e armazenar a ficha deste veículo.");
    }

    public FichaNaoEncontradaException(Long id) {
        super("Ficha técnica não encontrada para o id: " + id);
    }
}
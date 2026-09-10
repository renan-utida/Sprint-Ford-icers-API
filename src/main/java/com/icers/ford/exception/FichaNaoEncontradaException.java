package com.icers.ford.exception;

import lombok.Getter;

import java.util.List;

/**
 * Lançada quando uma ficha técnica não existe no banco.
 * O GlobalExceptionHandler converte para 404 com mensagem padronizada.
 */
@Getter
public class FichaNaoEncontradaException extends RuntimeException {

    private final List<String> sugestoesSimilares;

    public FichaNaoEncontradaException(String marca, String modelo, String versao) {
        this(marca, modelo, versao, List.of());
    }

    /**
     * Variante com sugestões — outras versões do mesmo marca+modelo
     * já cacheadas, para o cliente saber o que já está disponível
     * sem precisar adivinhar.
     */
    public FichaNaoEncontradaException(String marca, String modelo, String versao,
                                       List<String> sugestoesSimilares) {
        super("Ficha técnica não encontrada para: "
                + marca + " " + modelo + " " + versao
                + ". Use POST /api/v1/specs/query para consultar e armazenar a ficha deste veículo.");
        this.sugestoesSimilares = sugestoesSimilares != null ? sugestoesSimilares : List.of();
    }

    public FichaNaoEncontradaException(Long id) {
        super("Ficha técnica não encontrada para o id: " + id);
        this.sugestoesSimilares = null;
    }
}
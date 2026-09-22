package com.icers.ford.service;

import com.icers.ford.dto.response.SpecResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

// TODO: cobertura de expiração (EXPIRACAO_SEGUNDOS / limparExpirados()) não
// incluída de propósito — sem Clock injetável, a única forma seria reflection
// no record privado Entrada (frágil, não vale o ganho). Reconsiderar se
// IdempotencyService ganhar um Clock injetável como melhoria de design
// separada (discutido na sessão de implementação dos testes unitários,
// Fase C, item 4 da suíte).
@DisplayName("Testes - IdempotencyService")
public class IdempotencyServiceTest {

    private IdempotencyService idempotencyService;

    private SpecResponse respostaPadrao;

    @BeforeEach
    public void setUp() {
        idempotencyService = new IdempotencyService();

        respostaPadrao = new SpecResponse(
                1L, "Ford", "Ranger", "Raptor",
                List.of(), "ALTA", LocalDateTime.now(), false
        );
    }

    // buscar — chave nunca vista

    @Test
    @DisplayName("Deve retornar vazio para chave nunca vista")
    public void testBuscarChaveNuncaVista() {
        assertTrue(idempotencyService.buscar("chave-inexistente").isEmpty());
    }

    @Test
    @DisplayName("Deve retornar vazio (sem lançar) para chave null")
    public void testBuscarChaveNula() {
        assertTrue(idempotencyService.buscar(null).isEmpty());
    }

    @Test
    @DisplayName("Deve retornar vazio (sem lançar) para chave em branco")
    public void testBuscarChaveEmBranco() {
        assertTrue(idempotencyService.buscar("   ").isEmpty());
    }

    @Test
    @DisplayName("Deve retornar vazio (sem lançar) para chave string vazia")
    public void testBuscarChaveVazia() {
        assertTrue(idempotencyService.buscar("").isEmpty());
    }

    // salvar + buscar — caso feliz

    @Test
    @DisplayName("Deve devolver a mesma resposta salva ao buscar pela mesma chave")
    public void testSalvarEBuscarMesmaChave() {
        idempotencyService.salvar("chave-1", respostaPadrao);

        Optional<SpecResponse> resultado = idempotencyService.buscar("chave-1");

        assertTrue(resultado.isPresent());
        assertEquals(respostaPadrao, resultado.get());
    }

    @Test
    @DisplayName("Chaves diferentes não devem interferir entre si")
    public void testChavesDiferentesNaoInterferem() {
        SpecResponse outraResposta = new SpecResponse(
                2L, "Ford", "Bronco", "Wildtrak",
                List.of(), "MEDIA", LocalDateTime.now(), false
        );

        idempotencyService.salvar("chave-a", respostaPadrao);
        idempotencyService.salvar("chave-b", outraResposta);

        assertEquals(respostaPadrao, idempotencyService.buscar("chave-a").get());
        assertEquals(outraResposta, idempotencyService.buscar("chave-b").get());
    }

    @Test
    @DisplayName("Salvar novamente na mesma chave deve sobrescrever a resposta anterior")
    public void testSalvarMesmaChaveSobrescreve() {
        SpecResponse respostaNova = new SpecResponse(
                3L, "Ford", "Ranger", "Raptor",
                List.of(), "ALTA", LocalDateTime.now(), false
        );

        idempotencyService.salvar("chave-1", respostaPadrao);
        idempotencyService.salvar("chave-1", respostaNova);

        Optional<SpecResponse> resultado = idempotencyService.buscar("chave-1");

        assertTrue(resultado.isPresent());
        assertEquals(respostaNova, resultado.get());
        assertNotEquals(respostaPadrao, resultado.get());
    }

    // salvar — guard clauses

    @Test
    @DisplayName("Salvar com chave null não deve lançar nem registrar nada")
    public void testSalvarChaveNulaNaoRegistraNada() {
        assertDoesNotThrow(() -> idempotencyService.salvar(null, respostaPadrao));
        assertTrue(idempotencyService.buscar(null).isEmpty());
    }

    @Test
    @DisplayName("Salvar com chave em branco não deve lançar nem registrar nada")
    public void testSalvarChaveEmBrancoNaoRegistraNada() {
        assertDoesNotThrow(() -> idempotencyService.salvar("   ", respostaPadrao));
        assertTrue(idempotencyService.buscar("   ").isEmpty());
    }
}

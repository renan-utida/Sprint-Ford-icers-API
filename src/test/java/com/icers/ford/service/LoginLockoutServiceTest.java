package com.icers.ford.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

// TODO: cobertura da expiração real do bloqueio (30s) e do envelhecimento da
// janela deslizante de falhas (10min) não incluída de propósito — mesmo gap
// do IdempotencyServiceTest: sem Clock injetável, exigiria esperar o tempo
// real (lento/instável) ou reflection no Instant armazenado internamente.
// Reconsiderar junto com a mesma melhoria de Clock injetável, se decidirmos
// fazer isso (discutido na sessão de implementação dos testes unitários,
// Fase C, item 5 da suíte). Diferente do IdempotencyService, aqui a
// reflection seria mais simples (Map<String, Instant> direto, sem record
// privado aninhado) — vale reconsiderar isolado se o Clock não for adotado.
@DisplayName("Testes - LoginLockoutService")
public class LoginLockoutServiceTest {

    private static final String EMAIL = "admin@specradar.com";
    private static final int LIMITE_FALHAS = 5;

    private LoginLockoutService loginLockoutService;

    @BeforeEach
    public void setUp() {
        loginLockoutService = new LoginLockoutService();
    }

    // Antes de atingir o limite

    @Test
    @DisplayName("Não deve bloquear com menos falhas que o limite")
    public void testNaoBloqueiaAbaixoDoLimite() {
        for (int i = 0; i < LIMITE_FALHAS - 1; i++) {
            loginLockoutService.registrarFalha(EMAIL);
        }

        assertTrue(loginLockoutService.segundosRestantesDeBloqueio(EMAIL).isEmpty());
    }

    @Test
    @DisplayName("Deve retornar vazio para email que nunca teve falha registrada")
    public void testEmailNuncaVistoNaoEstaBloqueado() {
        assertTrue(loginLockoutService.segundosRestantesDeBloqueio("nunca-visto@specradar.com").isEmpty());
    }

    // Ao atingir o limite

    @Test
    @DisplayName("Deve bloquear ao atingir exatamente o limite de falhas")
    public void testBloqueiaAoAtingirLimite() {
        for (int i = 0; i < LIMITE_FALHAS; i++) {
            loginLockoutService.registrarFalha(EMAIL);
        }

        Optional<Long> restante = loginLockoutService.segundosRestantesDeBloqueio(EMAIL);

        assertTrue(restante.isPresent());
        // DURACAO_BLOQUEIO = 30s. O cálculo faz Duration.toSeconds() + 1 —
        // se as duas chamadas de Instant.now() (registrarFalha e esta)
        // caírem no mesmo instante (comum com JIT já aquecida, ex.: rodando
        // dentro da suíte completa), toSeconds() trunca pra 30 em vez de 29,
        // e o resultado vira 31. 31 é o teto matemático correto do método,
        // não flakiness — confirmado ao reproduzir a falha isolando a causa.
        assertTrue(restante.get() >= 28 && restante.get() <= 31,
                "Esperado entre 28 e 31s restantes, veio " + restante.get());
    }

    @Test
    @DisplayName("Deve continuar bloqueado (e renovar o prazo) com falhas além do limite")
    public void testContinuaBloqueadoComFalhasAlemDoLimite() {
        for (int i = 0; i < LIMITE_FALHAS + 2; i++) {
            loginLockoutService.registrarFalha(EMAIL);
        }

        assertTrue(loginLockoutService.segundosRestantesDeBloqueio(EMAIL).isPresent());
    }

    @Test
    @DisplayName("Falhas em contas diferentes não devem interferir entre si")
    public void testContasDiferentesNaoInterferem() {
        String outroEmail = "analyst@specradar.com";

        for (int i = 0; i < LIMITE_FALHAS; i++) {
            loginLockoutService.registrarFalha(EMAIL);
        }
        loginLockoutService.registrarFalha(outroEmail);

        assertTrue(loginLockoutService.segundosRestantesDeBloqueio(EMAIL).isPresent());
        assertTrue(loginLockoutService.segundosRestantesDeBloqueio(outroEmail).isEmpty());
    }

    // registrarSucesso — reset completo

    @Test
    @DisplayName("Login bem-sucedido deve limpar o bloqueio ativo")
    public void testSucessoLimpaBloqueio() {
        for (int i = 0; i < LIMITE_FALHAS; i++) {
            loginLockoutService.registrarFalha(EMAIL);
        }
        assertTrue(loginLockoutService.segundosRestantesDeBloqueio(EMAIL).isPresent());

        loginLockoutService.registrarSucesso(EMAIL);

        assertTrue(loginLockoutService.segundosRestantesDeBloqueio(EMAIL).isEmpty());
    }

    @Test
    @DisplayName("Login bem-sucedido deve zerar o histórico de falhas (não só o bloqueio)")
    public void testSucessoZeraHistoricoDeFalhas() {
        // Chega perto do limite, mas não o atinge
        for (int i = 0; i < LIMITE_FALHAS - 1; i++) {
            loginLockoutService.registrarFalha(EMAIL);
        }
        loginLockoutService.registrarSucesso(EMAIL);

        // Uma única falha nova não deveria bloquear — o histórico anterior foi zerado
        loginLockoutService.registrarFalha(EMAIL);

        assertTrue(loginLockoutService.segundosRestantesDeBloqueio(EMAIL).isEmpty());
    }

    @Test
    @DisplayName("registrarSucesso em conta nunca vista não deve lançar")
    public void testSucessoEmContaNuncaVistaNaoLanca() {
        assertDoesNotThrow(() -> loginLockoutService.registrarSucesso("nunca-visto@specradar.com"));
    }
}

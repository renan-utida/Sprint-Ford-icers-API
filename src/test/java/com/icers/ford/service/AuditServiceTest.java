package com.icers.ford.service;

import com.icers.ford.model.AuditLog;
import com.icers.ford.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

// @Async não tem efeito nenhum aqui — só age quando o Spring cria o proxy
// do bean; chamando o método direto na instância, tudo roda síncrono, o
// que é conveniente pra testar sem esperar nada.
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes - AuditService")
public class AuditServiceTest {

    private static final String SALT = "salt-de-teste-pseudonimizacao";

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditService auditService;

    @BeforeEach
    public void setUp() {
        auditService = new AuditService(auditLogRepository);
        // pseudonymizationSalt vem de @Value — fora do Spring context
        // precisa ser setado via reflection, mesmo padrão do
        // JwtServiceTest do Código 1 (ReflectionTestUtils.setField).
        ReflectionTestUtils.setField(auditService, "pseudonymizationSalt", SALT);
    }

    // hashUserId

    @Test
    @DisplayName("hashUserId(null) deve retornar null")
    public void testHashUserIdNulo() {
        assertNull(auditService.hashUserId(null));
    }

    @Test
    @DisplayName("hashUserId deve retornar hex de 64 caracteres (SHA-256)")
    public void testHashUserIdFormato() {
        String hash = auditService.hashUserId(1L);

        assertNotNull(hash);
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
    }

    @Test
    @DisplayName("hashUserId deve ser determinístico — mesmo id gera sempre o mesmo hash")
    public void testHashUserIdDeterministico() {
        assertEquals(auditService.hashUserId(1L), auditService.hashUserId(1L));
    }

    @Test
    @DisplayName("hashUserId deve gerar hashes diferentes para ids diferentes")
    public void testHashUserIdIdsDiferentesGeramHashesDiferentes() {
        assertNotEquals(auditService.hashUserId(1L), auditService.hashUserId(2L));
    }

    @Test
    @DisplayName("hashUserId deve retornar 'hash-error' (sem lançar) se o salt não estiver configurado")
    public void testHashUserIdSemSaltRetornaHashError() {
        AuditService servicoSemSalt = new AuditService(auditLogRepository);
        // pseudonymizationSalt nunca setado — fica null, getBytes() lançaria NPE

        assertEquals("hash-error", servicoSemSalt.hashUserId(1L));
    }

    // logConsulta

    @Test
    @DisplayName("logConsulta deve salvar AuditLog com os campos corretos")
    public void testLogConsultaSalvaCamposCorretos() {
        auditService.logConsulta(1L, "/api/v1/specs/query", "127.0.0.1",
                "Ford", "Ranger", "Raptor", true, 200);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(captor.capture());

        AuditLog log = captor.getValue();
        assertEquals(auditService.hashUserId(1L), log.getUsuarioHash());
        assertEquals("/api/v1/specs/query", log.getEndpoint());
        assertEquals("POST", log.getMetodoHttp());
        assertEquals(200, log.getStatusResposta());
        assertEquals("127.0.0.1", log.getIpOrigem());
        assertEquals("SPEC_QUERY", log.getAcao());
        assertTrue(log.getDetalhes().contains("Ford"));
        assertTrue(log.getDetalhes().contains("Ranger"));
        assertTrue(log.getDetalhes().contains("Raptor"));
        assertTrue(log.getDetalhes().contains("true"));
    }

    @Test
    @DisplayName("logConsulta não deve lançar mesmo se o repository falhar ao salvar")
    public void testLogConsultaNaoLancaSeRepositoryFalhar() {
        when(auditLogRepository.save(any(AuditLog.class)))
                .thenThrow(new RuntimeException("falha simulada no banco"));

        assertDoesNotThrow(() -> auditService.logConsulta(
                1L, "/api/v1/specs/query", "127.0.0.1",
                "Ford", "Ranger", "Raptor", false, 200));
    }

    // logAuthFailure

    @Test
    @DisplayName("logAuthFailure deve salvar sem usuarioHash (usuário não autenticado) e email mascarado")
    public void testLogAuthFailureSalvaCamposCorretos() {
        when(auditLogRepository.countFalhasAutenticacao(anyString(), any())).thenReturn(1L);

        auditService.logAuthFailure("10.0.0.5", "admin@specradar.com");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(captor.capture());

        AuditLog log = captor.getValue();
        assertNull(log.getUsuarioHash());
        assertEquals("/api/v1/auth/login", log.getEndpoint());
        assertEquals("POST", log.getMetodoHttp());
        assertEquals(401, log.getStatusResposta());
        assertEquals("AUTH_FAILURE", log.getAcao());
        assertTrue(log.getDetalhes().contains("***@specradar.com"));
        assertFalse(log.getDetalhes().contains("admin@"));
    }

    @Test
    @DisplayName("logAuthFailure deve disparar BRUTE_FORCE_ALERT quando o IP atinge o limite de falhas")
    public void testLogAuthFailureDisparaAlertaDeBruteForce() {
        when(auditLogRepository.countFalhasAutenticacao(anyString(), any())).thenReturn(5L);

        auditService.logAuthFailure("10.0.0.5", "admin@specradar.com");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(2)).save(captor.capture());

        AuditLog alerta = captor.getAllValues().get(1);
        assertEquals("BRUTE_FORCE_ALERT", alerta.getAcao());
        assertTrue(alerta.getDetalhes().contains("5"));
    }

    @Test
    @DisplayName("logAuthFailure NÃO deve disparar alerta quando abaixo do limite de falhas")
    public void testLogAuthFailureNaoDisparaAlertaAbaixoDoLimite() {
        when(auditLogRepository.countFalhasAutenticacao(anyString(), any())).thenReturn(4L);

        auditService.logAuthFailure("10.0.0.5", "admin@specradar.com");

        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("logAuthFailure não deve lançar mesmo se a verificação de brute force falhar")
    public void testLogAuthFailureNaoLancaSeVerificacaoBruteForceFalhar() {
        when(auditLogRepository.countFalhasAutenticacao(anyString(), any()))
                .thenThrow(new RuntimeException("falha simulada na query"));

        assertDoesNotThrow(() -> auditService.logAuthFailure("10.0.0.5", "admin@specradar.com"));
        // o log da própria falha (AUTH_FAILURE) já deve ter sido salvo antes da verificação
        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("logAuthFailure com email null deve mascarar como '***'")
    public void testLogAuthFailureComEmailNulo() {
        when(auditLogRepository.countFalhasAutenticacao(anyString(), any())).thenReturn(1L);

        auditService.logAuthFailure("10.0.0.5", null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(captor.capture());
        assertTrue(captor.getValue().getDetalhes().contains("***"));
    }

    // logAuthSuccess

    @Test
    @DisplayName("logAuthSuccess deve salvar com usuarioHash, status 200 e email mascarado")
    public void testLogAuthSuccessSalvaCamposCorretos() {
        auditService.logAuthSuccess(1L, "127.0.0.1", "admin@specradar.com");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(captor.capture());

        AuditLog log = captor.getValue();
        assertEquals(auditService.hashUserId(1L), log.getUsuarioHash());
        assertEquals(200, log.getStatusResposta());
        assertEquals("AUTH_SUCCESS", log.getAcao());
        assertTrue(log.getDetalhes().contains("***@specradar.com"));
    }

    // logAdminAction — o próprio bug real corrigido nesta sessão

    @Test
    @DisplayName("logAdminAction deve salvar com metodoHttp/endpoint/status reais recebidos, não placeholders fixos")
    public void testLogAdminActionSalvaValoresReaisRecebidos() {
        auditService.logAdminAction(1L, "127.0.0.1", "DELETE",
                "/api/v1/specs/42", 204, "DELETE_FICHA", "ficha id=42 removida");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(captor.capture());

        AuditLog log = captor.getValue();
        // REGRESSÃO: antes da correção, metodoHttp era fixo "POST/PUT/DELETE"
        // (15 chars) e endpoint fixo "/api/v1/admin/**" — os dois violavam
        // a coluna real (metodo_http VARCHAR2(10) + CHECK), então a linha
        // nunca era gravada de verdade. Aqui confirmamos que os valores
        // GRAVADOS são exatamente os recebidos como parâmetro.
        assertEquals("DELETE", log.getMetodoHttp());
        assertEquals("/api/v1/specs/42", log.getEndpoint());
        assertEquals(204, log.getStatusResposta());
        assertEquals("ADMIN_DELETE_FICHA", log.getAcao());
        assertEquals("ficha id=42 removida", log.getDetalhes());
    }

    @Test
    @DisplayName("logAdminAction deve prefixar a ação com ADMIN_")
    public void testLogAdminActionPrefixaAcao() {
        auditService.logAdminAction(1L, "127.0.0.1", "POST",
                "/api/v1/usuarios", 201, "CRIAR_USUARIO", "usuario criado");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(captor.capture());

        assertEquals("ADMIN_CRIAR_USUARIO", captor.getValue().getAcao());
    }

    @Test
    @DisplayName("logAdminAction não deve lançar mesmo se o repository falhar ao salvar")
    public void testLogAdminActionNaoLancaSeRepositoryFalhar() {
        when(auditLogRepository.save(any(AuditLog.class)))
                .thenThrow(new RuntimeException("falha simulada no banco"));

        assertDoesNotThrow(() -> auditService.logAdminAction(1L, "127.0.0.1", "POST",
                "/api/v1/usuarios", 201, "CRIAR_USUARIO", "usuario criado"));
    }

    // logRateLimitExceeded

    @Test
    @DisplayName("logRateLimitExceeded deve salvar com status 429")
    public void testLogRateLimitExceededSalvaCamposCorretos() {
        auditService.logRateLimitExceeded(1L, "/api/v1/specs/query", "127.0.0.1");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(captor.capture());

        AuditLog log = captor.getValue();
        assertEquals(429, log.getStatusResposta());
        assertEquals("RATE_LIMIT_EXCEEDED", log.getAcao());
        assertEquals("/api/v1/specs/query", log.getEndpoint());
    }
}

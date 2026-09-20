package com.icers.ford.service;

import com.icers.ford.model.AuditLog;
import com.icers.ford.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Value("${pseudonymization.salt}")
    private String pseudonymizationSalt;

    // Limite de falhas de autenticação antes de logar como ERROR
    private static final int LIMITE_FALHAS_AUTH = 5;
    private static final int JANELA_FALHAS_MINUTOS = 10;

    /**
     * Registra uma consulta de especificações.
     * Nunca lança exceção — falha no audit não deve interromper o fluxo principal.
     */
    @Async
    public void logConsulta(Long userId, String endpoint, String ip,
                            String marca, String modelo, String versao,
                            boolean cacheHit, int status) {
        String detalhe = String.format(
                "Consulta: %s %s %s | cache_hit: %s",
                marca, modelo, versao, cacheHit
        );
        salvar(hashUserId(userId), endpoint, "POST", status, ip,
                "SPEC_QUERY", detalhe);
    }

    /**
     * Registra falha de autenticação.
     * Detecta brute force: 5+ falhas em 10 min do mesmo IP → ERROR.
     */
    @Async
    public void logAuthFailure(String ip, String emailTentado) {
        String detalhe = "Credenciais inválidas para: "
                + mascararEmail(emailTentado);
        salvar(null, "/api/v1/auth/login", "POST", 401, ip,
                "AUTH_FAILURE", detalhe);
        verificarBruteForce(ip);
    }

    /**
     * Registra login bem-sucedido.
     */
    @Async
    public void logAuthSuccess(Long userId, String ip, String email) {
        salvar(hashUserId(userId), "/api/v1/auth/login", "POST", 200, ip,
                "AUTH_SUCCESS", "Login: " + mascararEmail(email));
    }

    /**
     * Registra ação administrativa (alteração de perfil, delete, etc).
     * <p>
     * metodoHttp e endpoint vêm de request.getMethod()/getRequestURI() no
     * controller — antes eram os placeholders fixos "POST/PUT/DELETE" e
     * "/api/v1/admin/**", que nunca bateram com a coluna metodo_http
     * (VARCHAR2(10) + CHECK IN ('GET','POST','PUT','PATCH','DELETE')):
     * toda gravação falhava com ORA-12899/ORA-02290, engolida
     * silenciosamente pelo catch de salvar() — nenhuma ação administrativa
     * teve log de auditoria de verdade até essa correção.
     * <p>
     * status também vem do controller (o código de fato retornado —
     * 201/200/204, conforme a ação) — antes era 200 fixo, incorreto pra
     * criar (201) e pra desativar/reativar/anonimizar/deletar (204).
     */
    @Async
    public void logAdminAction(Long adminId, String ip, String metodoHttp,
                               String endpoint, int status,
                               String acao, String detalhe) {
        salvar(hashUserId(adminId), endpoint, metodoHttp, status, ip,
                "ADMIN_" + acao, detalhe);
        log.info("Ação administrativa — admin: {} | ação: {} | detalhe: {}",
                hashUserId(adminId), acao, detalhe);
    }

    /**
     * Registra Limite de requisições excedido.
     */
    @Async
    public void logRateLimitExceeded(Long userId, String endpoint, String ip) {
        salvar(hashUserId(userId), endpoint, "POST", 429, ip,
                "RATE_LIMIT_EXCEEDED",
                "Limite de requisições excedido");
        log.warn("Rate limit excedido — userId: {} | endpoint: {} | ip: {}",
                userId, endpoint, ip);
    }

    // MÉTODOS PRIVADOS

    private void salvar(String usuarioHash, String endpoint, String metodo,
                        int status, String ip, String acao, String detalhes) {
        try {
            auditLogRepository.save(AuditLog.builder()
                    .usuarioHash(usuarioHash)
                    .endpoint(endpoint)
                    .metodoHttp(metodo)
                    .statusResposta(status)
                    .ipOrigem(ip)
                    .acao(acao)
                    .detalhes(detalhes)
                    .build());
        } catch (Exception e) {
            log.error("Falha ao salvar audit log — acao: {} | erro: {}",
                    acao, e.getMessage());
        }
    }

    /**
     * Verifica alerta de Brute Force
     */
    private void verificarBruteForce(String ip) {
        try {
            LocalDateTime janela = LocalDateTime.now()
                    .minusMinutes(JANELA_FALHAS_MINUTOS);
            long falhas = auditLogRepository
                    .countFalhasAutenticacao(ip, janela);

            if (falhas >= LIMITE_FALHAS_AUTH) {
                log.error("ALERTA BRUTE FORCE — ip: {} | falhas em {}min: {}",
                        ip, JANELA_FALHAS_MINUTOS, falhas);
                salvar(null, "/api/v1/auth/login", "POST", 401, ip,
                        "BRUTE_FORCE_ALERT",
                        "IP com " + falhas + " falhas de auth em "
                                + JANELA_FALHAS_MINUTOS + " minutos");
            }
        } catch (Exception e) {
            log.error("Falha ao verificar brute force: {}", e.getMessage());
        }
    }

    /**
     * Gera pseudônimo do ID do usuário via HMAC-SHA256 com salt secreto.
     * <p>
     * Por que HMAC e não SHA-256 puro (como era antes): IDs de usuário
     * são inteiros sequenciais pequenos (1, 2, 3...) — um SHA-256 sem
     * chave é trivialmente reversível por força bruta (basta hashear
     * 1, 2, 3... até bater), o que não é pseudonimização de verdade,
     * é só ofuscação. HMAC exige conhecer o salt (guardado só no
     * servidor, via .env) pra sequer tentar reverter — reidentificação
     * continua tecnicamente possível (é pseudonimização, não
     * anonimização), mas exige informação adicional protegida
     * separadamente, como a LGPD define.
     */
    public String hashUserId(Long userId) {
        if (userId == null) return null;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    pseudonymizationSalt.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            ));
            byte[] hash = mac.doFinal(userId.toString().getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            log.error("Falha ao gerar hash pseudonimizado: {}", e.getMessage());
            return "hash-error";
        }
    }

    private String mascararEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        return "***@" + email.substring(email.indexOf("@") + 1);
    }
}
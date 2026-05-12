package com.icers.ford.service;

import com.icers.ford.model.AuditLog;
import com.icers.ford.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    // Limite de falhas de autenticação antes de logar como ERROR
    private static final int LIMITE_FALHAS_AUTH = 5;
    private static final int JANELA_FALHAS_MINUTOS = 10;

    /**
     * Registra uma consulta de especificações.
     * Nunca lança exceção — falha no audit não deve interromper o fluxo principal.
     */
    public void logConsulta(Long usuarioId, String endpoint,
                            String metodoHttp, int status,
                            String ip, String marca,
                            String modelo, String versao,
                            boolean cacheHit) {
        try {
            String detalhes = String.format(
                    "Veículo: %s %s %s | cache_hit: %s",
                    marca, modelo, versao, cacheHit ? "S" : "N"
            );

            salvar(
                    hashUsuarioId(usuarioId),
                    endpoint, metodoHttp, status,
                    ip, "SPEC_QUERY", detalhes
            );
        } catch (Exception e) {
            log.error("Falha ao registrar audit log de consulta: {}",
                    e.getMessage());
        }
    }

    /**
     * Registra falha de autenticação.
     * Detecta brute force: 5+ falhas em 10 min do mesmo IP → ERROR.
     */
    public void logAuthFailure(String ip, String emailTentado) {
        try {
            String detalhes = "Email tentado: " + mascararEmail(emailTentado);

            salvar(null, "/api/v1/auth/login", "POST",
                    401, ip, "AUTH_FAILURE", detalhes);

            // Verifica brute force
            LocalDateTime janela = LocalDateTime.now()
                    .minusMinutes(JANELA_FALHAS_MINUTOS);

            long falhas = auditLogRepository
                    .countFalhasAutenticacao(ip, janela);

            if (falhas >= LIMITE_FALHAS_AUTH) {
                log.error("ALERTA BRUTE FORCE — IP: {} tentou {} vezes em {}min",
                        ip, falhas, JANELA_FALHAS_MINUTOS);
            } else {
                log.warn("Falha de autenticação — IP: {} | tentativas: {}",
                        ip, falhas);
            }

        } catch (Exception e) {
            log.error("Falha ao registrar audit log de auth: {}",
                    e.getMessage());
        }
    }

    /**
     * Registra ação administrativa (alteração de perfil, delete, etc).
     */
    public void logAdminAction(Long adminId, String endpoint,
                               String metodoHttp, int status,
                               String ip, String acao,
                               String detalhes) {
        try {
            salvar(
                    hashUsuarioId(adminId),
                    endpoint, metodoHttp, status,
                    ip, "ADMIN_" + acao, detalhes
            );
            log.info("Ação administrativa — ação: {} | admin: {} | ip: {}",
                    acao, hashUsuarioId(adminId), ip);
        } catch (Exception e) {
            log.error("Falha ao registrar audit log admin: {}",
                    e.getMessage());
        }
    }

    /**
     * Registra login bem-sucedido.
     */
    public void logAuthSuccess(Long usuarioId, String ip) {
        try {
            salvar(
                    hashUsuarioId(usuarioId),
                    "/api/v1/auth/login", "POST",
                    200, ip, "AUTH_SUCCESS",
                    "Login realizado com sucesso"
            );
        } catch (Exception e) {
            log.error("Falha ao registrar audit log de login: {}",
                    e.getMessage());
        }
    }

    // MÉTODOS PRIVADOS

    private void salvar(String usuarioHash, String endpoint,
                        String metodoHttp, int status,
                        String ip, String acao, String detalhes) {
        AuditLog audit = AuditLog.builder()
                .usuarioHash(usuarioHash)
                .endpoint(endpoint)
                .metodoHttp(metodoHttp)
                .statusResposta(status)
                .ipOrigem(ip)
                .acao(acao)
                .detalhes(detalhes)
                .build();

        auditLogRepository.save(audit);
    }

    /**
     * Gera hash SHA-256 do ID do usuário.
     * Rastreável para auditoria, mas não expõe email ou dados pessoais.
     */
    public String hashUsuarioId(Long usuarioId) {
        if (usuarioId == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    usuarioId.toString().getBytes(StandardCharsets.UTF_8)
            );
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            // Primeiros 16 caracteres — suficiente para rastreabilidade
            return hex.substring(0, 16);
        } catch (Exception e) {
            return "hash_error";
        }
    }

    private String mascararEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        return "***@" + email.substring(email.indexOf("@") + 1);
    }
}
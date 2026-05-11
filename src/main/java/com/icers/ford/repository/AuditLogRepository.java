package com.icers.ford.repository;

import com.icers.ford.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Busca logs de um usuário em um período — usado na trilha de auditoria
     */
    List<AuditLog> findByUsuarioHashAndCriadoEmBetween(
            String usuarioHash,
            LocalDateTime inicio,
            LocalDateTime fim
    );

    /**
     * Conta falhas de autenticação de um IP desde uma data.
     * Usado para detectar brute force — 5+ falhas em 10 min = alerta.
     */
    @Query("SELECT COUNT(a) FROM AuditLog a " +
            "WHERE a.ipOrigem = :ip " +
            "AND a.acao = 'AUTH_FAILURE' " +
            "AND a.criadoEm >= :desde")
    long countFalhasAutenticacao(
            @Param("ip") String ip,
            @Param("desde") LocalDateTime desde
    );

    /**
     * Busca logs por status HTTP — usado para monitorar erros 5xx
     */
    List<AuditLog> findByStatusRespostaAndCriadoEmAfter(
            Integer statusResposta,
            LocalDateTime desde
    );

    /**
     * Busca logs de um endpoint específico — usado para detectar abuso
     */
    @Query("SELECT COUNT(a) FROM AuditLog a " +
            "WHERE a.ipOrigem = :ip " +
            "AND a.endpoint = :endpoint " +
            "AND a.criadoEm >= :desde")
    long countRequisicoesPorIpEEndpoint(
            @Param("ip") String ip,
            @Param("endpoint") String endpoint,
            @Param("desde") LocalDateTime desde
    );

    /**
     * Ações administrativas recentes — usado na trilha de auditoria do admin
     */
    @Query("SELECT a FROM AuditLog a " +
            "WHERE a.acao LIKE 'ADMIN_%' " +
            "ORDER BY a.criadoEm DESC")
    List<AuditLog> findAcoesAdministrativas();
}
package com.icers.ford.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Trilha de auditoria da plataforma.
 * IMPORTANTE: nunca armazenar dados sensíveis nesta entidade.
 * O usuário é identificado apenas pelo hash do seu ID,
 * nunca pelo email ou token.
 */
@Entity
@Table(name = "sr_audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    // NUMERIC em vez do BIGINT padrão do H2Dialect para Long — ver
    // comentário equivalente em Usuario.java.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "id")
    private Long id;

    /**
     * Hash SHA-256 do user_id — rastreável mas não expõe o email.
     * Null para requisições não autenticadas (ex: tentativas de login).
     */
    @Column(name = "usuario_hash", length = 64)
    private String usuarioHash;

    @Column(name = "endpoint", nullable = false, length = 200)
    private String endpoint;

    @Column(name = "metodo_http", nullable = false, length = 10)
    private String metodoHttp;

    // NUMERIC em vez do INTEGER padrão do H2Dialect — ver comentário do
    // id em Usuario.java.
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "status_resposta", nullable = false)
    private Integer statusResposta;

    @Column(name = "ip_origem", length = 45)
    private String ipOrigem;

    @Column(name = "acao", nullable = false, length = 100)
    private String acao;

    /**
     * Contexto adicional da ação.
     * Nunca contém: token JWT, senha, resposta completa do LLM,
     * stack trace ou dados pessoais.
     */
    @Column(name = "detalhes", length = 1000)
    private String detalhes;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @PrePersist
    protected void prePersist() {
        this.criadoEm = LocalDateTime.now();
    }
}
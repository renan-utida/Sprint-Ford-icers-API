package com.icers.ford.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "sr_historico_consultas")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoricoConsulta {

    // NUMERIC em vez do BIGINT padrão do H2Dialect para Long — ver
    // comentário equivalente em Usuario.java.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "marca", nullable = false, length = 50)
    private String marca;

    @Column(name = "modelo", nullable = false, length = 80)
    private String modelo;

    @Column(name = "versao", nullable = false, length = 80)
    private String versao;

    /**
     * JSON com lista de atributos solicitados.
     * Exemplo: ["motor", "potencia", "torque"]
     */
    @Lob
    @Column(name = "atributos_solicitados")
    private String atributosSolicitados;

    /**
     * true = retornou do banco (cache hit)
     * false = chamou o LLM
     */
    @Column(name = "cache_hit", nullable = false, length = 1)
    private String cacheHit;

    // NUMERIC em vez do BIGINT padrão do H2Dialect — ver comentário do
    // id em Usuario.java.
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "tempo_resposta_ms")
    private Long tempoRespostaMs;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @PrePersist
    protected void prePersist() {
        this.criadoEm = LocalDateTime.now();
        if (this.cacheHit == null) {
            this.cacheHit = "N";
        }
    }

    public boolean isCacheHit() {
        return "S".equals(this.cacheHit);
    }

    public void setCacheHit(boolean cacheHit) {
        this.cacheHit = cacheHit ? "S" : "N";
    }
}
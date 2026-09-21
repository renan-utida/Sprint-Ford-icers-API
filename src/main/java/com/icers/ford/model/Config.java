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
 * Configurações editáveis do sistema, sem precisar recompilar/redeployar.
 * Hoje guarda só a lista de atributos padrão do chat (movida de uma
 * constante hardcoded no ChatService para cá) — desenhada para acomodar
 * outras configurações no futuro, se surgirem.
 */
@Entity
@Table(name = "sr_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Config {

    // NUMERIC em vez do BIGINT padrão do H2Dialect para Long — ver
    // comentário equivalente em Usuario.java.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "id")
    private Long id;

    /**
     * JSON com array de strings — ex: ["motor","potencia","torque"].
     * Usado pelo ChatService quando a mensagem do usuário não menciona
     * nenhum atributo específico.
     */
    @Lob
    @Column(name = "atributos_padrao", nullable = false)
    private String atributosPadraoJson;

    /**
     * Dias até uma ficha ser considerada desatualizada — 2 a 31,
     * validado no ConfigService antes de salvar. Fichas já criadas
     * não são afetadas por uma mudança aqui (ver FichaTecnica).
     */
    // NUMERIC em vez do INTEGER padrão do H2Dialect — ver comentário do
    // id em Usuario.java.
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "intervalo_reverificacao_dias", nullable = false)
    private Integer intervaloReverificacaoDias;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atualizado_por", nullable = false)
    private Usuario atualizadoPor;

    @PrePersist
    @PreUpdate
    protected void prePersistUpdate() {
        this.atualizadoEm = LocalDateTime.now();
    }
}
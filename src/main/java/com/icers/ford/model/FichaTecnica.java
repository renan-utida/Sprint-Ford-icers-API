package com.icers.ford.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.icers.ford.model.enums.ConfidenceLevel;

import java.time.LocalDateTime;

@Entity
@Table(name = "sr_fichas_tecnicas")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FichaTecnica {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_ficha")
    @SequenceGenerator(
            name = "seq_ficha",
            sequenceName = "seq_ficha_id",
            allocationSize = 1
    )
    @Column(name = "id")
    private Long id;

    @Column(name = "marca", nullable = false, length = 50)
    private String marca;

    @Column(name = "modelo", nullable = false, length = 80)
    private String modelo;

    @Column(name = "versao", nullable = false, length = 80)
    private String versao;

    /**
     * JSON completo da ficha técnica.
     * Formato: [{ "campo": "motor", "valor": "V6 3.0L",
     *             "confianca": "ALTA", "fonte": "https://...",
     *             "verificadoEm": "2026-05-10" }]
     */
    @Lob
    @Column(name = "campos_json", nullable = false)
    private String camposJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "confidence_geral", nullable = false, length = 20)
    private ConfidenceLevel confidenceGeral;

    @Column(name = "fonte_url", length = 500)
    private String fonteUrl;

    @Column(name = "verificado_em", nullable = false)
    private LocalDateTime verificadoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criado_por", nullable = false)
    private Usuario criadoPor;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @PrePersist
    protected void prePersist() {
        this.criadoEm = LocalDateTime.now();
        this.atualizadoEm = LocalDateTime.now();
        this.verificadoEm = LocalDateTime.now();
        if (this.confidenceGeral == null) {
            this.confidenceGeral = ConfidenceLevel.PARCIAL;
        }
    }

    @PreUpdate
    protected void preUpdate() {
        this.atualizadoEm = LocalDateTime.now();
    }

}
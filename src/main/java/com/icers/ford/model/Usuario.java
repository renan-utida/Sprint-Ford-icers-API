package com.icers.ford.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.icers.ford.model.enums.Role;

import java.time.LocalDateTime;

@Entity
@Table(name = "sr_usuarios")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Usuario {

    // NUMERIC em vez do BIGINT que H2Dialect esperaria por padrão para Long —
    // NUMBER (Oracle) e NUMBER(19) (H2) reportam via JDBC como NUMERIC nos
    // dois bancos; sem isso, ddl-auto=validate rejeita a coluna sob o
    // perfil dev-h2 (só sob H2Dialect, nunca deu problema no Oracle real).
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "id")
    private Long id;

    @Column(name = "email", nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "nome", nullable = false, length = 150)
    private String nome;

    @Column(name = "senha_hash", nullable = false, length = 255)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Column(name = "ativo", nullable = false, length = 1)
    private String ativo;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "ultimo_acesso")
    private LocalDateTime ultimoAcesso;

    @PrePersist
    protected void prePersist() {
        this.criadoEm = LocalDateTime.now();
        if (this.ativo == null) {
            this.ativo = "S";
        }
    }

    public boolean isAtivo() {
        return "S".equals(this.ativo);
    }
}
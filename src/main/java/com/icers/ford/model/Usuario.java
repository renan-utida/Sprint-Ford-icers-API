package com.icers.ford.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.icers.ford.model.enums.Role;

import java.time.LocalDateTime;

@Entity
@Table(name = "sr_usuarios")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_usuario")
    @SequenceGenerator(
            name = "seq_usuario",
            sequenceName = "seq_usuario_id",
            allocationSize = 1
    )
    @Column(name = "id")
    private Long id;

    @Column(name = "email", nullable = false, unique = true, length = 150)
    private String email;

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
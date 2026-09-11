package com.icers.ford.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Um refresh token já usado (rotacionado) — reapresentar o mesmo jti
 * é rejeitado por AuthController.refresh(), mesmo que o token ainda
 * não tenha expirado naturalmente. Ver V9__revogacao_refresh_token.sql.
 */
@Entity
@Table(name = "sr_refresh_tokens_usados")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshTokenUsado {

    @Id
    @Column(name = "jti", length = 36, nullable = false)
    private String jti;

    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;
}
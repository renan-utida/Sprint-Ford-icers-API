package com.icers.ford.repository;

import com.icers.ford.model.RefreshTokenUsado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface RefreshTokenUsadoRepository extends JpaRepository<RefreshTokenUsado, String> {

    /**
     * Limpeza oportunista — chamada toda vez que um novo jti é
     * marcado como usado (ver AuthController.refresh()), não por um
     * job agendado à parte. Remove entradas cujo token original já
     * teria expirado de qualquer forma, então não precisam mais ser
     * rastreadas.
     */
    @Modifying
    void deleteByExpiraEmBefore(LocalDateTime momento);
}
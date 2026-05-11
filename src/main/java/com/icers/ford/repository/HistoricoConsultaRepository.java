package com.icers.ford.repository;

import com.icers.ford.model.HistoricoConsulta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface HistoricoConsultaRepository extends JpaRepository<HistoricoConsulta, Long> {

    /**
     * Busca histórico de um usuário em um período — usado em métricas
     */
    List<HistoricoConsulta> findByUsuarioIdAndCriadoEmBetween(
            Long usuarioId,
            LocalDateTime inicio,
            LocalDateTime fim
    );

    /**
     * Conta consultas de um usuário na última hora — usado no rate limiting
     */
    @Query("SELECT COUNT(h) FROM HistoricoConsulta h " +
            "WHERE h.usuario.id = :usuarioId " +
            "AND h.criadoEm >= :desde")
    long countConsultasDesde(
            @Param("usuarioId") Long usuarioId,
            @Param("desde") LocalDateTime desde
    );

    /**
     * Taxa de cache hit geral — métrica de performance da plataforma
     */
    @Query("SELECT COUNT(h) FROM HistoricoConsulta h WHERE h.cacheHit = 'S'")
    long countCacheHits();

    /**
     * Veículos mais consultados — usado no painel admin
     */
    @Query("SELECT h.marca, h.modelo, h.versao, COUNT(h) as total " +
            "FROM HistoricoConsulta h " +
            "GROUP BY h.marca, h.modelo, h.versao " +
            "ORDER BY total DESC")
    List<Object[]> findVeiculosMaisConsultados();
}
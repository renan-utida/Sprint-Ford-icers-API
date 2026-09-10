package com.icers.ford.repository;

import com.icers.ford.model.FichaTecnica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FichaTecnicaRepository extends JpaRepository<FichaTecnica, Long> {

    /**
     * Busca ficha pelo trio marca/modelo/versão — usado no cache e no GET direto.
     * Case-insensitive para normalização de entrada.
     */
    Optional<FichaTecnica> findFirstByMarcaIgnoreCaseAndModeloIgnoreCaseAndVersaoIgnoreCase(
            String marca, String modelo, String versao
    );

    /**
     * Verifica se já existe ficha para esse veículo — usado antes de chamar o LLM
     */
    boolean existsByMarcaIgnoreCaseAndModeloIgnoreCaseAndVersaoIgnoreCase(
            String marca, String modelo, String versao
    );

    /**
     * Lista fichas com filtros opcionais de marca e modelo — usado em GET /history
     */
    @Query("SELECT f FROM FichaTecnica f WHERE " +
            "(:marca IS NULL OR LOWER(f.marca) LIKE LOWER(CONCAT('%', :marca, '%'))) AND " +
            "(:modelo IS NULL OR LOWER(f.modelo) LIKE LOWER(CONCAT('%', :modelo, '%'))) " +
            "ORDER BY f.criadoEm DESC")
    List<FichaTecnica> findWithFilters(
            @Param("marca") String marca,
            @Param("modelo") String modelo
    );

    /**
     * Busca todas as fichas de uma marca — usado na tela de histórico do app
     */
    List<FichaTecnica> findByMarcaIgnoreCaseOrderByCriadoEmDesc(String marca);

    /**
     * Busca outras versões já cacheadas do mesmo marca+modelo — usado
     * para sugerir alternativas quando o veículo exato pedido não
     * existe (404 de findByVeiculo).
     */
    List<FichaTecnica> findByMarcaIgnoreCaseAndModeloIgnoreCase(
            String marca, String modelo
    );

    /**
     * Conta quantas fichas existem para um modelo — métrica de cobertura
     */
    long countByModeloIgnoreCase(String modelo);
}
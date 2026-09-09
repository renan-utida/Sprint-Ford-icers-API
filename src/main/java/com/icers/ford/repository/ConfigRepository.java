package com.icers.ford.repository;

import com.icers.ford.model.Config;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConfigRepository extends JpaRepository<Config, Long> {

    /**
     * A tabela sr_config sempre tem uma única linha de configuração
     * (por enquanto) — este metodo busca ela de forma determinística.
     */
    Optional<Config> findFirstByOrderByIdAsc();
}
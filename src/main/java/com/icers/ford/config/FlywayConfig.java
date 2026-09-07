package com.icers.ford.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Estratégia customizada de migration do Flyway.
 *
 * O Oracle não tem DDL transacional: se uma migration falha no meio
 * (ex: um CREATE TABLE que dá erro depois de um CREATE SEQUENCE já ter
 * sido commitado), ela fica marcada como "failed" na tabela de histórico
 * e bloqueia qualquer validação/migração futura até alguém rodar repair().
 *
 * Este bean substitui o comportamento padrão do Spring Boot (que só
 * chama migrate()) por repair() + migrate() a cada boot. repair() remove
 * entradas de migrations falhadas — nunca desfaz objetos já criados no
 * schema — então é seguro chamá-lo sempre: quando não há nada a reparar,
 * é um no-op.
 */
@Configuration
public class FlywayConfig {

    @Bean
    public FlywayMigrationStrategy repairAndMigrateStrategy() {
        return (Flyway flyway) -> {
            flyway.repair();
            flyway.migrate();
        };
    }
}
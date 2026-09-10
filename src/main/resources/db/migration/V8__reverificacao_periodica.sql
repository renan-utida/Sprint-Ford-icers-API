-- V8: Reverificação periódica de fichas técnicas
--
-- Duas colunas novas:
-- 1. sr_config.intervalo_reverificacao_dias — o valor GLOBAL atual,
--    editável pelo ADMIN via PUT /specs/config (2 a 31 dias, sugestão
--    padrão 15).
-- 2. sr_fichas_tecnicas.intervalo_reverificacao_dias — uma CÓPIA do
--    valor global, copiada para cada ficha na criação e RENOVADA para
--    o valor global vigente sempre que a ficha é reverificada (ao
--    expirar). Se o ADMIN mudar o valor global, uma ficha já existente
--    só adota o valor novo na sua próxima reverificação — não
--    imediatamente. É por isso que o intervalo mora nas DUAS tabelas,
--    não só numa referência de uma pra outra.

ALTER TABLE sr_config
    ADD (intervalo_reverificacao_dias NUMBER DEFAULT 15 NOT NULL);

ALTER TABLE sr_fichas_tecnicas
    ADD (intervalo_reverificacao_dias NUMBER);

UPDATE sr_fichas_tecnicas
SET intervalo_reverificacao_dias = 15
WHERE intervalo_reverificacao_dias IS NULL;

COMMIT;

ALTER TABLE sr_fichas_tecnicas
    MODIFY (intervalo_reverificacao_dias NUMBER NOT NULL);

COMMENT ON COLUMN sr_config.intervalo_reverificacao_dias IS
    'Dias até uma ficha ser considerada desatualizada e reverificada na próxima consulta. Editável pelo ADMIN, 2 a 31 dias.';

COMMENT ON COLUMN sr_fichas_tecnicas.intervalo_reverificacao_dias IS
    'Cópia do valor global de sr_config, definida na criação e renovada para o valor vigente a cada reverificação (quando a ficha expira).';
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
--
-- Reescrita (Fase C, portabilidade Oracle/H2): a versão original desta
-- migration adicionava a coluna em sr_fichas_tecnicas como NULLABLE,
-- fazia UPDATE pra preencher com 15, e só depois trocava pra NOT NULL
-- com MODIFY (Oracle) — desenhada pra rodar contra uma tabela JÁ
-- POPULADA em produção. MODIFY (Oracle) não tem equivalente direto em
-- ANSI/H2 (que usa ALTER COLUMN), então essa forma travava a
-- portabilidade. Como sr_fichas_tecnicas está sempre vazia neste ponto
-- da sequência de migrations (V8 roda antes de qualquer ficha real
-- existir), o mesmo estado final é obtido com um único ADD ... DEFAULT
-- ... NOT NULL, sem UPDATE nem MODIFY — igual ao padrão já usado abaixo
-- para sr_config.

ALTER TABLE sr_config
    ADD intervalo_reverificacao_dias NUMBER DEFAULT 15 NOT NULL;

ALTER TABLE sr_fichas_tecnicas
    ADD intervalo_reverificacao_dias NUMBER DEFAULT 15 NOT NULL;

COMMENT ON COLUMN sr_config.intervalo_reverificacao_dias IS
    'Dias até uma ficha ser considerada desatualizada e reverificada na próxima consulta. Editável pelo ADMIN, 2 a 31 dias.';

COMMENT ON COLUMN sr_fichas_tecnicas.intervalo_reverificacao_dias IS
    'Cópia do valor global de sr_config, definida na criação e renovada para o valor vigente a cada reverificação (quando a ficha expira).';

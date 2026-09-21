-- V7: Idempotência - impede ficha técnica duplicada para o mesmo veículo
--
-- Usa índice único baseado em função (UPPER de cada coluna), não uma
-- constraint UNIQUE simples — a aplicação já trata marca/modelo/versao
-- de forma case-insensitive em toda consulta (findFirstByMarcaIgnoreCase...),
-- então o banco precisa impor a mesma regra de igualdade, senão
-- "Ford"/"ford" ainda passariam como veículos "diferentes" pra
-- constraint, deixando a mesma brecha aberta.
--
-- Reescrita (Fase C, portabilidade Oracle/H2): a versão original criava
-- o índice único diretamente sobre UPPER(marca), UPPER(modelo),
-- UPPER(versao) — um índice funcional. Isso foi documentado como "já
-- portável" na análise inicial da Fase C, mas nunca tinha sido testado
-- de verdade contra um H2 real — só inferido por leitura do SQL. Boot
-- real do perfil dev-h2 revelou o erro: o parser de CREATE INDEX do H2
-- não aceita expressões/funções na lista de colunas (só identificadores
-- simples, com ASC/DESC/NULLS opcionais) — sem equivalente direto.
--
-- Fix: 3 colunas computadas (GENERATED ALWAYS AS), uma por atributo, e o
-- índice único sobre essas colunas simples em vez da expressão direta.
-- Testado ao vivo nos dois bancos antes de aplicar: GENERATED ALWAYS AS
-- (...) SEM a palavra-chave VIRTUAL funciona idêntico em Oracle e H2 (a
-- suposição de que Oracle exigiria VIRTUAL era só teórica — confirmada
-- falsa contra o Oracle real da FIAP). ADD de coluna única (sem
-- parênteses) em vez de ADD (...), mesmo motivo já documentado na V8.

ALTER TABLE sr_fichas_tecnicas
    ADD marca_ci VARCHAR2(50) GENERATED ALWAYS AS (UPPER(marca));

ALTER TABLE sr_fichas_tecnicas
    ADD modelo_ci VARCHAR2(80) GENERATED ALWAYS AS (UPPER(modelo));

ALTER TABLE sr_fichas_tecnicas
    ADD versao_ci VARCHAR2(80) GENERATED ALWAYS AS (UPPER(versao));

CREATE UNIQUE INDEX uk_sr_ficha_veiculo_ci
    ON sr_fichas_tecnicas (marca_ci, modelo_ci, versao_ci);

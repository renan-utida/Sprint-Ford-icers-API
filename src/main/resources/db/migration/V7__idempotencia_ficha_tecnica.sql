-- V7: Idempotência - impede ficha técnica duplicada para o mesmo veículo
--
-- Usa índice único baseado em função (UPPER de cada coluna), não uma
-- constraint UNIQUE simples — a aplicação já trata marca/modelo/versao
-- de forma case-insensitive em toda consulta (findFirstByMarcaIgnoreCase...),
-- então o banco precisa impor a mesma regra de igualdade, senão
-- "Ford"/"ford" ainda passariam como veículos "diferentes" pra
-- constraint, deixando a mesma brecha aberta.

CREATE UNIQUE INDEX uk_sr_ficha_veiculo_ci
    ON sr_fichas_tecnicas (UPPER(marca), UPPER(modelo), UPPER(versao));
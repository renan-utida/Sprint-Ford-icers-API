-- V9: Revogação real de refresh token na rotação
--
-- Guarda o jti (identificador único, UUID) de cada refresh token que
-- já foi usado pra pedir um par novo — reapresentar o mesmo jti é
-- rejeitado, mesmo que o token ainda não tenha expirado naturalmente.
-- Isso é o que faz a rotação de token ser real: sem isso, um refresh
-- token continuava válido pelos 7 dias inteiros mesmo depois de já
-- ter sido "trocado" várias vezes.
--
-- Guarda só o jti + a expiração ORIGINAL do token (não o token
-- inteiro) — o suficiente pra rejeitar reuso, e pra permitir limpar
-- entradas depois que o token já teria expirado de qualquer jeito
-- (ver RefreshTokenUsadoRepository.deleteByExpiraEmBefore).

CREATE TABLE sr_refresh_tokens_usados (
    jti         VARCHAR2(36) NOT NULL,
    expira_em   TIMESTAMP    NOT NULL,
    CONSTRAINT pk_sr_refresh_tokens_usados PRIMARY KEY (jti)
);

COMMENT ON TABLE sr_refresh_tokens_usados IS
    'Refresh tokens já usados (rotacionados) — reapresentar o mesmo jti é rejeitado, mesmo antes da expiração natural do token';
COMMENT ON COLUMN sr_refresh_tokens_usados.jti IS
    'Identificador único (UUID) embutido no token JWT no momento da criação';
COMMENT ON COLUMN sr_refresh_tokens_usados.expira_em IS
    'Mesma expiração natural do token original — usada só para limpeza (deletar entradas de tokens que já expirariam de qualquer forma), não afeta a rejeição em si';
-- V5: Seed de Usuários Iniciais
-- Senhas geradas com BCrypt custo 12
-- admin@specradar.com  → senha: Admin@2026
-- analyst@specradar.com → senha: Analyst@2026
-- TROCAR AS SENHAS ANTES DE IR PARA PRODUÇÃO

INSERT INTO sr_usuarios (email, senha_hash, role, ativo)
VALUES (
        'admin@specradar.com',
        '$2a$12$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi',
        'ADMIN',
        'S'
);

INSERT INTO sr_usuarios (email, senha_hash, role, ativo)
VALUES (
        'analyst@specradar.com',
        '$2a$12$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi',
        'ANALYST',
        'S'
);

COMMIT;
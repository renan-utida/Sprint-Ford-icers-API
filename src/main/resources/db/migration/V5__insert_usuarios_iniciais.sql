-- V5: Seed de Usuários Iniciais
-- Senhas geradas com BCrypt custo 12
-- admin@specradar.com  → senha: Admin@2026
-- analyst@specradar.com → senha: Analyst@2026
-- TROCAR AS SENHAS ANTES DE IR PARA PRODUÇÃO

INSERT INTO sr_usuarios (nome, email, senha_hash, role, ativo)
VALUES (
       'Administrador SpecRadar',
       'admin@specradar.com',
       '$2a$12$HCL3spHBg/lbEX/LTAr9COeKZ5M0FDeze1yMe/WhQjUDM8ahu7QRa',
       'ADMIN',
       'S'
);

INSERT INTO sr_usuarios (nome, email, senha_hash, role, ativo)
VALUES (
       'Analista SpecRadar',
       'analyst@specradar.com',
       '$2a$12$UbqGdyJYeTuvfw0PJGpefeF7VF0ygKZDWGF/lcrJq4nwZuElVaHFS',
       'ANALYST',
       'S'
);

COMMIT;
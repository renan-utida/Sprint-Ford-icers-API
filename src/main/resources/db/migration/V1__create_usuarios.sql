-- V1: Tabela de Usuários
-- SpecRadar API | Ford FIAP 2026

BEGIN
EXECUTE IMMEDIATE 'CREATE SEQUENCE seq_usuario_id START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -955 THEN
            RAISE;
        END IF;
END;
/

-- Nomes de constraint/índice prefixados com SR_ (SpecRadar) — o schema
-- Oracle do RM é compartilhado com outros projetos/disciplinas, e nomes
-- de constraint e índice são únicos por SCHEMA no Oracle, não por
-- tabela. Nomes genéricos como "pk_usuario" colidem facilmente com
-- outro projeto que já tenha uma tabela de usuário própria.
CREATE TABLE sr_usuarios (
    id              NUMBER DEFAULT seq_usuario_id.NEXTVAL NOT NULL,
    email           VARCHAR2(150)   NOT NULL,
    senha_hash      VARCHAR2(255)   NOT NULL,
    role            VARCHAR2(20)    NOT NULL,
    ativo           VARCHAR2(1)     DEFAULT 'S' NOT NULL,
    criado_em       TIMESTAMP       DEFAULT CURRENT_TIMESTAMP NOT NULL,
    ultimo_acesso   TIMESTAMP,
    CONSTRAINT pk_sr_usuario PRIMARY KEY (id),
    CONSTRAINT uk_sr_usuario_email UNIQUE (email),
    CONSTRAINT ck_sr_usuario_role CHECK (role IN ('ANALYST', 'ADMIN')),
    CONSTRAINT ck_sr_usuario_ativo CHECK (ativo IN ('S', 'N'))
);

CREATE INDEX idx_sr_usuario_role  ON sr_usuarios(role);

COMMENT ON TABLE  sr_usuarios             IS 'Usuários da plataforma SpecRadar';
COMMENT ON COLUMN sr_usuarios.senha_hash  IS 'Senha criptografada com BCrypt (custo 12)';
COMMENT ON COLUMN sr_usuarios.role        IS 'ANALYST = consulta/exporta | ADMIN = gerencia usuários e logs';
COMMENT ON COLUMN sr_usuarios.ativo       IS 'S = ativo | N = desativado';
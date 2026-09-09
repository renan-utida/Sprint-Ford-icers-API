-- V6: Configuração do sistema
-- Guarda ajustes editáveis pelo ADMIN sem precisar recompilar/redeployar.
-- Hoje só a lista de atributos padrão do chat (antes hardcoded no
-- ChatService); a tabela é desenhada para crescer se surgirem outras
-- configurações no futuro.

-- Criação defensiva da sequence — mesmo racional das migrations
-- anteriores (Oracle sem DDL transacional).
BEGIN
EXECUTE IMMEDIATE 'CREATE SEQUENCE seq_config_id START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -955 THEN
            RAISE;
END IF;
END;
/

CREATE TABLE sr_config (
    id                  NUMBER DEFAULT seq_config_id.NEXTVAL NOT NULL,
    atributos_padrao    CLOB            NOT NULL,
    atualizado_em       TIMESTAMP       DEFAULT CURRENT_TIMESTAMP NOT NULL,
    atualizado_por      NUMBER          NOT NULL,
    CONSTRAINT pk_sr_config PRIMARY KEY (id),
    CONSTRAINT fk_sr_config_usuario FOREIGN KEY (atualizado_por)
       REFERENCES sr_usuarios(id)
);

COMMENT ON TABLE  sr_config                  IS 'Configurações editáveis do sistema (hoje: atributos padrão do chat)';
COMMENT ON COLUMN sr_config.atributos_padrao IS 'JSON com array de strings — atributos usados quando o chat não identifica nenhum na mensagem do usuário';
COMMENT ON COLUMN sr_config.atualizado_por   IS 'FK para sr_usuarios — sempre um ADMIN, único perfil autorizado a alterar';

-- Linha única com os valores padrão atuais (os mesmos que já estavam
-- hardcoded em ChatService.ATRIBUTOS_PADRAO)
INSERT INTO sr_config (atributos_padrao, atualizado_por)
VALUES (
   '["motor","potencia","torque","transmissao","tracao","preco","consumo","dimensoes"]',
   (SELECT id FROM sr_usuarios WHERE email = 'admin@specradar.com')
);

COMMIT;
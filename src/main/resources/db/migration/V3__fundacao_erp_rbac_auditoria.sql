-- ============================================================================
-- V3: fundacao do ERP
--   1. dinheiro em NUMERIC (nunca ponto flutuante)
--   2. RBAC: perfis e permissoes como dados, substituindo users.role
--   3. auditoria append-only
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. Dinheiro
-- ---------------------------------------------------------------------------
ALTER TABLE products
    ALTER COLUMN price TYPE NUMERIC(14, 2) USING ROUND(price::numeric, 2);

-- ---------------------------------------------------------------------------
-- 2. RBAC
-- ---------------------------------------------------------------------------
CREATE TABLE permissions (
    code        VARCHAR(80)  PRIMARY KEY,
    module      VARCHAR(40)  NOT NULL,
    description VARCHAR(255) NOT NULL
);

CREATE TABLE roles (
    id          BIGSERIAL    PRIMARY KEY,
    code        VARCHAR(40)  NOT NULL,
    name        VARCHAR(80)  NOT NULL,
    description VARCHAR(255),
    -- perfis de sistema nao podem ser excluidos
    system_role BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_roles_code UNIQUE (code)
);

CREATE TABLE role_permissions (
    role_id         BIGINT      NOT NULL,
    permission_code VARCHAR(80) NOT NULL,
    PRIMARY KEY (role_id, permission_code),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_code) REFERENCES permissions (code)
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
);

CREATE INDEX idx_user_roles_role ON user_roles (role_id);

ALTER TABLE users ADD COLUMN full_name     VARCHAR(120);
ALTER TABLE users ADD COLUMN active        BOOLEAN     NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN created_at    TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE users ADD COLUMN last_login_at TIMESTAMPTZ;

-- Permissoes existentes nesta versao. Cada modulo novo adiciona as suas na
-- propria migration. ADMINISTRADOR recebe todas automaticamente (ver
-- CustomUserDetailsService), por isso nao aparece em role_permissions.
INSERT INTO permissions (code, module, description) VALUES
    ('produtos.editar',    'Produtos',  'Criar, editar e excluir produtos e alterar preços'),
    ('contatos.ver',       'Comercial', 'Ver as mensagens de contato recebidas pelo site'),
    ('usuarios.gerenciar', 'Sistema',   'Criar usuários, atribuir perfis e editar permissões'),
    ('auditoria.ver',      'Sistema',   'Consultar o registro de auditoria');

INSERT INTO roles (code, name, description, system_role) VALUES
    ('ADMINISTRADOR', 'Administrador',  'Acesso total ao sistema',                              TRUE),
    ('DIRETOR',       'Diretor/CEO',    'Visão estratégica, indicadores e aprovações',          FALSE),
    ('GERENTE',       'Gerente',        'Gestão da operação e aprovações',                      FALSE),
    ('FINANCEIRO',    'Financeiro',     'Contas a pagar e receber, caixa e crédito',            FALSE),
    ('COMPRAS',       'Compras',        'Fornecedores, cotações e pedidos de compra',           FALSE),
    ('VENDEDOR',      'Vendedor',       'Clientes e pedidos da própria carteira',               FALSE),
    ('ESTOQUISTA',    'Estoquista',     'Recebimento, movimentações, lotes e inventário',       FALSE),
    ('EXPEDICAO',     'Expedição',      'Separação e conferência de pedidos',                   FALSE),
    ('LOGISTICA',     'Logística',      'Cargas, rotas e entregas',                             FALSE),
    ('CLIENTE',       'Cliente (site)', 'Cliente cadastrado pela vitrine; sem acesso ao ERP',   TRUE);

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p.code
FROM roles r
JOIN (VALUES
        ('DIRETOR',  'auditoria.ver'),
        ('DIRETOR',  'contatos.ver'),
        ('GERENTE',  'produtos.editar'),
        ('GERENTE',  'contatos.ver'),
        ('VENDEDOR', 'contatos.ver')
     ) AS p(role_code, code) ON p.role_code = r.code;

-- Migra o papel antigo: ADMIN -> ADMINISTRADOR, USER -> CLIENTE
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.code = CASE u.role WHEN 'ADMIN' THEN 'ADMINISTRADOR' ELSE 'CLIENTE' END;

ALTER TABLE users DROP COLUMN role;

-- ---------------------------------------------------------------------------
-- 3. Auditoria
-- ---------------------------------------------------------------------------
CREATE TABLE audit_logs (
    id          BIGSERIAL    PRIMARY KEY,
    occurred_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    user_id     BIGINT,
    -- guardado tambem em texto: o registro continua legivel mesmo que o
    -- usuario seja renomeado
    username    VARCHAR(255),
    action      VARCHAR(80)  NOT NULL,
    entity_type VARCHAR(80),
    entity_id   VARCHAR(80),
    old_value   JSONB,
    new_value   JSONB,
    reason      VARCHAR(500),
    ip          VARCHAR(64),
    user_agent  VARCHAR(500),
    CONSTRAINT fk_audit_logs_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_audit_logs_occurred_at ON audit_logs (occurred_at DESC);
CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX idx_audit_logs_user ON audit_logs (user_id);
CREATE INDEX idx_audit_logs_action ON audit_logs (action);

-- Append-only garantido pelo banco, nao so pela aplicacao.
CREATE FUNCTION audit_logs_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs e somente inclusao: % nao permitido', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_logs_append_only
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION audit_logs_append_only();

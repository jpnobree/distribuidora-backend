-- ============================================================================
-- V5: estoque - filiais, depositos, lotes, saldos por situacao, movimentos
-- (somente inclusao) e inventario
-- ============================================================================

CREATE TABLE branches (
    id     BIGSERIAL    PRIMARY KEY,
    code   VARCHAR(20)  NOT NULL,
    name   VARCHAR(120) NOT NULL,
    active BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_branches_code UNIQUE (code)
);

CREATE TABLE warehouses (
    id        BIGSERIAL    PRIMARY KEY,
    branch_id BIGINT       NOT NULL REFERENCES branches (id),
    code      VARCHAR(20)  NOT NULL,
    name      VARCHAR(120) NOT NULL,
    active    BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_warehouses_branch_code UNIQUE (branch_id, code)
);

INSERT INTO branches (code, name) VALUES ('MATRIZ', 'Matriz');
INSERT INTO warehouses (branch_id, code, name)
SELECT id, 'PRINCIPAL', 'Depósito principal' FROM branches WHERE code = 'MATRIZ';

-- Lote: rastreabilidade fornecedor -> lote -> pedido -> cliente.
CREATE TABLE lots (
    id              BIGSERIAL    PRIMARY KEY,
    product_id      BIGINT       NOT NULL REFERENCES products (id),
    code            VARCHAR(40)  NOT NULL,
    supplier_id     BIGINT       REFERENCES suppliers (id),
    manufactured_on DATE,
    expires_on      DATE,
    received_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    invoice_number  VARCHAR(40),
    CONSTRAINT uq_lots_product_code UNIQUE (product_id, code),
    CONSTRAINT ck_lots_dates CHECK (manufactured_on IS NULL OR expires_on IS NULL OR expires_on >= manufactured_on)
);

CREATE INDEX idx_lots_expires_on ON lots (expires_on);

-- Saldo por deposito + produto + lote. Disponivel = fisico - reservado -
-- bloqueado - avariado (calculado, nunca gravado).
CREATE TABLE stock_balances (
    id            BIGSERIAL      PRIMARY KEY,
    warehouse_id  BIGINT         NOT NULL REFERENCES warehouses (id),
    product_id    BIGINT         NOT NULL REFERENCES products (id),
    lot_id        BIGINT         REFERENCES lots (id),
    qty_physical  NUMERIC(14, 3) NOT NULL DEFAULT 0,
    qty_reserved  NUMERIC(14, 3) NOT NULL DEFAULT 0,
    qty_blocked   NUMERIC(14, 3) NOT NULL DEFAULT 0,
    qty_damaged   NUMERIC(14, 3) NOT NULL DEFAULT 0,
    updated_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version       BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_stock_balances_non_negative CHECK (
        qty_physical >= 0 AND qty_reserved >= 0 AND qty_blocked >= 0 AND qty_damaged >= 0),
    -- nunca ha mais comprometido do que existe fisicamente
    CONSTRAINT ck_stock_balances_consistent CHECK (qty_reserved + qty_blocked + qty_damaged <= qty_physical)
);

CREATE UNIQUE INDEX uq_stock_balances ON stock_balances (warehouse_id, product_id, COALESCE(lot_id, 0));
CREATE INDEX idx_stock_balances_product ON stock_balances (product_id);

-- Todo movimento leva uma quantidade de uma situacao para outra no mesmo
-- saldo: EXTERNO (fora da empresa), DISPONIVEL, BLOQUEADO, AVARIADO.
CREATE TABLE stock_movements (
    id              BIGSERIAL      PRIMARY KEY,
    occurred_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    type            VARCHAR(30)    NOT NULL,
    warehouse_id    BIGINT         NOT NULL REFERENCES warehouses (id),
    product_id      BIGINT         NOT NULL REFERENCES products (id),
    lot_id          BIGINT         REFERENCES lots (id),
    from_bucket     VARCHAR(12)    NOT NULL,
    to_bucket       VARCHAR(12)    NOT NULL,
    quantity        NUMERIC(14, 3) NOT NULL,
    unit_cost       NUMERIC(14, 4),
    loss_reason     VARCHAR(30),
    reason          VARCHAR(500),
    document        VARCHAR(60),
    user_id         BIGINT         REFERENCES users (id),
    username        VARCHAR(255),
    source_type     VARCHAR(40),
    source_id       BIGINT,
    -- liga os dois lados de uma transferencia
    group_id        UUID,
    CONSTRAINT ck_stock_movements_qty CHECK (quantity > 0),
    CONSTRAINT ck_stock_movements_buckets CHECK (from_bucket <> to_bucket)
);

CREATE INDEX idx_stock_movements_product ON stock_movements (product_id, occurred_at DESC);
CREATE INDEX idx_stock_movements_occurred ON stock_movements (occurred_at DESC);
CREATE INDEX idx_stock_movements_lot ON stock_movements (lot_id);
CREATE INDEX idx_stock_movements_type ON stock_movements (type);

CREATE FUNCTION stock_movements_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'stock_movements e somente inclusao: % nao permitido (use um estorno)', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_stock_movements_append_only
    BEFORE UPDATE OR DELETE ON stock_movements
    FOR EACH ROW EXECUTE FUNCTION stock_movements_append_only();

-- Inventario: enquanto aberto, o deposito nao aceita movimentos (os saldos
-- ficam congelados para a contagem bater).
CREATE TABLE inventory_counts (
    id           BIGSERIAL    PRIMARY KEY,
    warehouse_id BIGINT       NOT NULL REFERENCES warehouses (id),
    category     VARCHAR(255),
    status       VARCHAR(12)  NOT NULL DEFAULT 'ABERTO',
    notes        VARCHAR(500),
    created_by   VARCHAR(255) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    closed_by    VARCHAR(255),
    closed_at    TIMESTAMPTZ,
    CONSTRAINT ck_inventory_counts_status CHECK (status IN ('ABERTO', 'FECHADO', 'CANCELADO'))
);

-- no maximo um inventario aberto por deposito
CREATE UNIQUE INDEX uq_inventory_counts_open ON inventory_counts (warehouse_id) WHERE status = 'ABERTO';

CREATE TABLE inventory_count_items (
    id          BIGSERIAL      PRIMARY KEY,
    count_id    BIGINT         NOT NULL REFERENCES inventory_counts (id) ON DELETE CASCADE,
    product_id  BIGINT         NOT NULL REFERENCES products (id),
    lot_id      BIGINT         REFERENCES lots (id),
    system_qty  NUMERIC(14, 3) NOT NULL,
    counted_qty NUMERIC(14, 3),
    CONSTRAINT ck_inventory_items_counted CHECK (counted_qty IS NULL OR counted_qty >= 0)
);

CREATE UNIQUE INDEX uq_inventory_count_items ON inventory_count_items (count_id, product_id, COALESCE(lot_id, 0));

-- ---------------------------------------------------------------------------
-- Permissoes da fase 2
-- ---------------------------------------------------------------------------
INSERT INTO permissions (code, module, description) VALUES
    ('estoque.consultar',  'Estoque', 'Consultar saldos, lotes, validades e movimentações'),
    ('estoque.movimentar', 'Estoque', 'Registrar entradas e transferências entre depósitos'),
    ('estoque.ajustar',    'Estoque', 'Registrar saídas manuais e conduzir inventários'),
    ('estoque.perdas',     'Estoque', 'Registrar perdas e avarias'),
    ('estoque.bloquear',   'Estoque', 'Bloquear e liberar quantidades de estoque');

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p.code
FROM roles r
JOIN (VALUES
        ('DIRETOR',    'estoque.consultar'),
        ('GERENTE',    'estoque.consultar'), ('GERENTE', 'estoque.movimentar'), ('GERENTE', 'estoque.ajustar'),
        ('GERENTE',    'estoque.perdas'), ('GERENTE', 'estoque.bloquear'),
        ('COMPRAS',    'estoque.consultar'),
        ('VENDEDOR',   'estoque.consultar'),
        ('ESTOQUISTA', 'estoque.consultar'), ('ESTOQUISTA', 'estoque.movimentar'), ('ESTOQUISTA', 'estoque.perdas'),
        ('ESTOQUISTA', 'estoque.bloquear'),
        ('EXPEDICAO',  'estoque.consultar'),
        ('FINANCEIRO', 'estoque.consultar')
     ) AS p(role_code, code) ON p.role_code = r.code;

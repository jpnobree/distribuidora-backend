-- ============================================================================
-- V8: compras (pedido com aprovacao), recebimento com lote e custo medio,
-- contas a pagar com baixas no mesmo livro-caixa dos recebimentos
-- ============================================================================

INSERT INTO system_parameters (key, value, description) VALUES
    ('compras.aprovacao_acima_de', '0',
     'Valor a partir do qual o pedido de compra precisa de aprovação (0 = todos precisam)'),
    ('compras.cobertura_dias', '15',
     'Dias de venda que a sugestão de compra tenta cobrir, além do prazo de entrega');

-- ---------------------------------------------------------------------------
-- Pedido de compra
-- ---------------------------------------------------------------------------
CREATE TABLE purchase_orders (
    id              BIGSERIAL      PRIMARY KEY,
    supplier_id     BIGINT         NOT NULL REFERENCES suppliers (id),
    status          VARCHAR(30)    NOT NULL,
    payment_term_id BIGINT         REFERENCES payment_terms (id),
    warehouse_id    BIGINT         NOT NULL REFERENCES warehouses (id),
    expected_on     DATE,
    notes           VARCHAR(1000),
    total           NUMERIC(14, 2) NOT NULL DEFAULT 0,
    created_by      VARCHAR(255)   NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    approved_by     VARCHAR(255),
    approved_at     TIMESTAMPTZ,
    cancelled_by    VARCHAR(255),
    cancelled_at    TIMESTAMPTZ,
    cancel_reason   VARCHAR(500),
    version         BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_purchase_orders_status CHECK (status IN
        ('AGUARDANDO_APROVACAO', 'APROVADO', 'RECEBIDO_PARCIAL', 'RECEBIDO', 'CANCELADO'))
);

CREATE INDEX idx_purchase_orders_supplier ON purchase_orders (supplier_id, created_at DESC);
CREATE INDEX idx_purchase_orders_status ON purchase_orders (status);

CREATE TABLE purchase_order_items (
    id                BIGSERIAL      PRIMARY KEY,
    purchase_order_id BIGINT         NOT NULL REFERENCES purchase_orders (id) ON DELETE CASCADE,
    product_id        BIGINT         NOT NULL REFERENCES products (id),
    -- como foi comprado (ex.: 10 CX) e o equivalente na unidade base (200 kg)
    unit_code         VARCHAR(10)    NOT NULL,
    quantity          NUMERIC(14, 3) NOT NULL,
    factor            NUMERIC(14, 4) NOT NULL,
    qty_base          NUMERIC(14, 3) NOT NULL,
    -- custo por unidade base, como o estoque precisa
    unit_cost         NUMERIC(14, 4) NOT NULL,
    line_total        NUMERIC(14, 2) NOT NULL,
    qty_received      NUMERIC(14, 3) NOT NULL DEFAULT 0,
    CONSTRAINT ck_purchase_order_items_qty CHECK (quantity > 0 AND qty_base > 0 AND qty_received >= 0),
    CONSTRAINT ck_purchase_order_items_cost CHECK (unit_cost >= 0)
);

CREATE INDEX idx_purchase_order_items_order ON purchase_order_items (purchase_order_id);
CREATE INDEX idx_purchase_order_items_product ON purchase_order_items (product_id);

-- ---------------------------------------------------------------------------
-- Recebimento: a mercadoria chegando, com lote e validade
-- ---------------------------------------------------------------------------
CREATE TABLE goods_receipts (
    id                BIGSERIAL      PRIMARY KEY,
    purchase_order_id BIGINT         NOT NULL REFERENCES purchase_orders (id),
    supplier_id       BIGINT         NOT NULL REFERENCES suppliers (id),
    warehouse_id      BIGINT         NOT NULL REFERENCES warehouses (id),
    -- nota do fornecedor
    document          VARCHAR(60),
    document_total    NUMERIC(14, 2),
    notes             VARCHAR(1000),
    received_by       VARCHAR(255)   NOT NULL,
    received_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    total             NUMERIC(14, 2) NOT NULL DEFAULT 0
);

CREATE INDEX idx_goods_receipts_order ON goods_receipts (purchase_order_id);
CREATE INDEX idx_goods_receipts_received ON goods_receipts (received_at DESC);

CREATE TABLE goods_receipt_items (
    id                     BIGSERIAL      PRIMARY KEY,
    receipt_id             BIGINT         NOT NULL REFERENCES goods_receipts (id) ON DELETE CASCADE,
    purchase_order_item_id BIGINT         NOT NULL REFERENCES purchase_order_items (id),
    product_id             BIGINT         NOT NULL REFERENCES products (id),
    lot_id                 BIGINT         REFERENCES lots (id),
    qty_base               NUMERIC(14, 3) NOT NULL,
    -- custo realmente cobrado nesta chegada; recalcula o custo medio
    unit_cost              NUMERIC(14, 4) NOT NULL,
    line_total             NUMERIC(14, 2) NOT NULL,
    -- diferenca para o que foi pedido, quando houver
    cost_difference        NUMERIC(14, 4),
    CONSTRAINT ck_goods_receipt_items_qty CHECK (qty_base > 0 AND unit_cost >= 0)
);

CREATE INDEX idx_goods_receipt_items_receipt ON goods_receipt_items (receipt_id);

-- ---------------------------------------------------------------------------
-- Contas a pagar
-- ---------------------------------------------------------------------------
CREATE TABLE payables (
    id                 BIGSERIAL      PRIMARY KEY,
    receipt_id         BIGINT         NOT NULL REFERENCES goods_receipts (id),
    supplier_id        BIGINT         NOT NULL REFERENCES suppliers (id),
    document           VARCHAR(30)    NOT NULL,
    description        VARCHAR(255)   NOT NULL,
    installment        INT            NOT NULL,
    installments_total INT            NOT NULL,
    issue_date         DATE           NOT NULL,
    due_date           DATE           NOT NULL,
    amount             NUMERIC(14, 2) NOT NULL,
    paid_amount        NUMERIC(14, 2) NOT NULL DEFAULT 0,
    status             VARCHAR(20)    NOT NULL DEFAULT 'ABERTO',
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version            BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_payables_amount CHECK (amount > 0 AND paid_amount >= 0),
    CONSTRAINT ck_payables_status CHECK (status IN ('ABERTO', 'PARCIAL', 'PAGO', 'CANCELADO')),
    CONSTRAINT uq_payables_installment UNIQUE (receipt_id, installment)
);

CREATE INDEX idx_payables_supplier ON payables (supplier_id, due_date);
CREATE INDEX idx_payables_due ON payables (due_date) WHERE status IN ('ABERTO', 'PARCIAL');

-- Pagamento e recebimento moram no mesmo livro: e ele que vai sustentar o
-- fluxo de caixa. Cada lancamento aponta para um titulo a receber OU a pagar.
ALTER TABLE financial_transactions ALTER COLUMN receivable_id DROP NOT NULL;
ALTER TABLE financial_transactions ADD COLUMN payable_id BIGINT REFERENCES payables (id);
ALTER TABLE financial_transactions ADD CONSTRAINT ck_financial_transactions_target
    CHECK ((receivable_id IS NOT NULL) <> (payable_id IS NOT NULL));

CREATE INDEX idx_financial_transactions_payable ON financial_transactions (payable_id);

-- ---------------------------------------------------------------------------
-- Permissoes da fase 5
-- ---------------------------------------------------------------------------
INSERT INTO permissions (code, module, description) VALUES
    ('compras.ver',      'Compras',    'Consultar sugestão de compra e pedidos de compra'),
    ('compras.criar',    'Compras',    'Lançar pedidos de compra'),
    ('compras.aprovar',  'Compras',    'Aprovar pedidos de compra e cancelar'),
    ('compras.receber',  'Compras',    'Registrar o recebimento da mercadoria'),
    ('pagar.ver',        'Financeiro', 'Consultar títulos a pagar'),
    ('pagar.baixar',     'Financeiro', 'Registrar pagamentos e estornos'),
    ('pagar.cancelar',   'Financeiro', 'Cancelar títulos a pagar em aberto');

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p.code
FROM roles r
JOIN (VALUES
        ('DIRETOR',    'compras.ver'), ('DIRETOR', 'compras.aprovar'), ('DIRETOR', 'pagar.ver'),
        ('GERENTE',    'compras.ver'), ('GERENTE', 'compras.criar'), ('GERENTE', 'compras.aprovar'),
        ('GERENTE',    'compras.receber'), ('GERENTE', 'pagar.ver'),
        ('COMPRAS',    'compras.ver'), ('COMPRAS', 'compras.criar'), ('COMPRAS', 'compras.receber'),
        ('ESTOQUISTA', 'compras.ver'), ('ESTOQUISTA', 'compras.receber'),
        ('FINANCEIRO', 'compras.ver'), ('FINANCEIRO', 'pagar.ver'), ('FINANCEIRO', 'pagar.baixar'),
        ('FINANCEIRO', 'pagar.cancelar')
     ) AS p(role_code, code) ON p.role_code = r.code;

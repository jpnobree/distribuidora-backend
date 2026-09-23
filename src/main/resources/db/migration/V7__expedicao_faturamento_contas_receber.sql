-- ============================================================================
-- V7: expedicao (separacao com peso real e conferencia), faturamento com
-- porta para emissor fiscal e contas a receber com baixas
-- ============================================================================

-- O pedido passa a acompanhar o ciclo ate o faturamento.
ALTER TABLE sales_orders DROP CONSTRAINT ck_sales_orders_status;
ALTER TABLE sales_orders ADD CONSTRAINT ck_sales_orders_status
    CHECK (status IN ('AGUARDANDO_APROVACAO', 'APROVADO', 'EM_SEPARACAO', 'SEPARADO', 'FATURADO', 'CANCELADO'));

INSERT INTO system_parameters (key, value, description) VALUES
    ('expedicao.tolerancia_peso_percent', '10',
     'Variação (%) aceita entre o peso pedido e o peso separado em produtos de peso variável'),
    ('expedicao.conferente_diferente', 'false',
     'Exigir que o conferente seja uma pessoa diferente de quem separou'),
    ('faturamento.serie', '1',
     'Série usada na numeração dos documentos de saída');

-- ---------------------------------------------------------------------------
-- Separacao
-- ---------------------------------------------------------------------------
CREATE TABLE picking_lists (
    id            BIGSERIAL    PRIMARY KEY,
    order_id      BIGINT       NOT NULL REFERENCES sales_orders (id),
    status        VARCHAR(20)  NOT NULL DEFAULT 'ABERTA',
    notes         VARCHAR(500),
    created_by    VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    separated_by  VARCHAR(255),
    separated_at  TIMESTAMPTZ,
    checked_by    VARCHAR(255),
    checked_at    TIMESTAMPTZ,
    cancelled_by  VARCHAR(255),
    cancelled_at  TIMESTAMPTZ,
    cancel_reason VARCHAR(500),
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_picking_lists_status CHECK (status IN ('ABERTA', 'SEPARADA', 'CONFERIDA', 'CANCELADA'))
);

-- Um pedido so pode ter uma separacao viva por vez; cancelada libera para refazer.
CREATE UNIQUE INDEX uq_picking_lists_open ON picking_lists (order_id) WHERE status <> 'CANCELADA';
CREATE INDEX idx_picking_lists_status ON picking_lists (status, created_at DESC);

-- Uma linha por lote separado. O FEFO sugere (qty_planned); a balanca informa
-- o que saiu de verdade (qty_picked); a conferencia confirma (qty_checked).
CREATE TABLE picking_items (
    id            BIGSERIAL      PRIMARY KEY,
    picking_id    BIGINT         NOT NULL REFERENCES picking_lists (id) ON DELETE CASCADE,
    order_item_id BIGINT         NOT NULL REFERENCES sales_order_items (id),
    product_id    BIGINT         NOT NULL REFERENCES products (id),
    warehouse_id  BIGINT         NOT NULL REFERENCES warehouses (id),
    lot_id        BIGINT         REFERENCES lots (id),
    qty_planned   NUMERIC(14, 3) NOT NULL DEFAULT 0,
    qty_picked    NUMERIC(14, 3) NOT NULL DEFAULT 0,
    qty_checked   NUMERIC(14, 3),
    CONSTRAINT ck_picking_items_qty CHECK (qty_planned >= 0 AND qty_picked >= 0)
);

CREATE INDEX idx_picking_items_picking ON picking_items (picking_id);
CREATE INDEX idx_picking_items_order_item ON picking_items (order_item_id);

-- Toda diferenca entre pedido, separado e conferido fica registrada com quem
-- registrou: e o que permite cobrar explicacao depois.
CREATE TABLE picking_divergences (
    id            BIGSERIAL      PRIMARY KEY,
    picking_id    BIGINT         NOT NULL REFERENCES picking_lists (id) ON DELETE CASCADE,
    order_item_id BIGINT         NOT NULL REFERENCES sales_order_items (id),
    product_id    BIGINT         NOT NULL REFERENCES products (id),
    type          VARCHAR(30)    NOT NULL,
    qty_expected  NUMERIC(14, 3) NOT NULL,
    qty_found     NUMERIC(14, 3) NOT NULL,
    detail        VARCHAR(500),
    created_by    VARCHAR(255)   NOT NULL,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_picking_divergences_type
        CHECK (type IN ('FALTA', 'SOBRA', 'PESO_FORA_TOLERANCIA', 'TROCA_LOTE', 'CONFERENCIA'))
);

CREATE INDEX idx_picking_divergences_picking ON picking_divergences (picking_id);

-- ---------------------------------------------------------------------------
-- Faturamento
-- ---------------------------------------------------------------------------
CREATE SEQUENCE invoice_number_seq START 1;

CREATE TABLE invoices (
    id              BIGSERIAL      PRIMARY KEY,
    number          BIGINT         NOT NULL,
    series          VARCHAR(5)     NOT NULL,
    order_id        BIGINT         NOT NULL REFERENCES sales_orders (id),
    picking_id      BIGINT         NOT NULL REFERENCES picking_lists (id),
    customer_id     BIGINT         NOT NULL REFERENCES customers (id),
    payment_term_id BIGINT         REFERENCES payment_terms (id),
    status          VARCHAR(20)    NOT NULL DEFAULT 'EMITIDA',
    issued_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    issued_by       VARCHAR(255)   NOT NULL,
    subtotal        NUMERIC(14, 2) NOT NULL,
    discount_total  NUMERIC(14, 2) NOT NULL,
    total           NUMERIC(14, 2) NOT NULL,
    -- CMV do que saiu: custo medio no momento da baixa
    cost_total      NUMERIC(14, 2),
    -- Porta fiscal: hoje o documento e interno (sem valor fiscal). Quando o
    -- emissor de NF-e entrar, os campos abaixo recebem chave, numero e retorno.
    fiscal_status   VARCHAR(20)    NOT NULL DEFAULT 'INTERNO',
    fiscal_number   VARCHAR(20),
    fiscal_key      VARCHAR(60),
    fiscal_message  VARCHAR(500),
    fiscal_issued_at TIMESTAMPTZ,
    cancelled_by    VARCHAR(255),
    cancelled_at    TIMESTAMPTZ,
    cancel_reason   VARCHAR(500),
    version         BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uq_invoices_number UNIQUE (series, number),
    CONSTRAINT ck_invoices_status CHECK (status IN ('EMITIDA', 'CANCELADA')),
    CONSTRAINT ck_invoices_fiscal_status
        CHECK (fiscal_status IN ('INTERNO', 'PENDENTE', 'AUTORIZADO', 'REJEITADO'))
);

CREATE INDEX idx_invoices_customer ON invoices (customer_id, issued_at DESC);
CREATE INDEX idx_invoices_order ON invoices (order_id);
CREATE INDEX idx_invoices_issued ON invoices (issued_at DESC);

CREATE TABLE invoice_items (
    id            BIGSERIAL      PRIMARY KEY,
    invoice_id    BIGINT         NOT NULL REFERENCES invoices (id) ON DELETE CASCADE,
    order_item_id BIGINT         NOT NULL REFERENCES sales_order_items (id),
    product_id    BIGINT         NOT NULL REFERENCES products (id),
    description   VARCHAR(255)   NOT NULL,
    base_unit     VARCHAR(10)    NOT NULL,
    -- quantidade faturada = peso real separado, na unidade base
    quantity      NUMERIC(14, 3) NOT NULL,
    qty_ordered   NUMERIC(14, 3) NOT NULL,
    unit_price    NUMERIC(14, 2) NOT NULL,
    discount_percent NUMERIC(5, 2) NOT NULL DEFAULT 0,
    line_total    NUMERIC(14, 2) NOT NULL,
    unit_cost     NUMERIC(14, 4),
    line_cost     NUMERIC(14, 2),
    CONSTRAINT ck_invoice_items_qty CHECK (quantity > 0)
);

CREATE INDEX idx_invoice_items_invoice ON invoice_items (invoice_id);
CREATE INDEX idx_invoice_items_product ON invoice_items (product_id);

-- ---------------------------------------------------------------------------
-- Contas a receber
-- ---------------------------------------------------------------------------
CREATE TABLE receivables (
    id                BIGSERIAL      PRIMARY KEY,
    invoice_id        BIGINT         NOT NULL REFERENCES invoices (id),
    customer_id       BIGINT         NOT NULL REFERENCES customers (id),
    document          VARCHAR(30)    NOT NULL,
    installment       INT            NOT NULL,
    installments_total INT           NOT NULL,
    issue_date        DATE           NOT NULL,
    due_date          DATE           NOT NULL,
    amount            NUMERIC(14, 2) NOT NULL,
    paid_amount       NUMERIC(14, 2) NOT NULL DEFAULT 0,
    status            VARCHAR(20)    NOT NULL DEFAULT 'ABERTO',
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version           BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_receivables_amount CHECK (amount > 0 AND paid_amount >= 0),
    CONSTRAINT ck_receivables_status CHECK (status IN ('ABERTO', 'PARCIAL', 'PAGO', 'CANCELADO')),
    CONSTRAINT uq_receivables_installment UNIQUE (invoice_id, installment)
);

CREATE INDEX idx_receivables_customer ON receivables (customer_id, due_date);
CREATE INDEX idx_receivables_due ON receivables (due_date) WHERE status IN ('ABERTO', 'PARCIAL');

-- Baixas e estornos: somente inclusao, como os movimentos de estoque.
-- Corrigir uma baixa errada = lancar o estorno, nunca apagar.
CREATE TABLE financial_transactions (
    id            BIGSERIAL      PRIMARY KEY,
    receivable_id BIGINT         NOT NULL REFERENCES receivables (id),
    type          VARCHAR(20)    NOT NULL,
    -- quanto foi abatido do titulo
    amount        NUMERIC(14, 2) NOT NULL,
    -- juros/multa cobrados e desconto concedido no recebimento
    interest      NUMERIC(14, 2) NOT NULL DEFAULT 0,
    discount      NUMERIC(14, 2) NOT NULL DEFAULT 0,
    paid_on       DATE           NOT NULL,
    method        VARCHAR(20)    NOT NULL,
    notes         VARCHAR(500),
    user_id       BIGINT         REFERENCES users (id),
    username      VARCHAR(255)   NOT NULL,
    occurred_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    reversal_of   BIGINT         REFERENCES financial_transactions (id),
    CONSTRAINT ck_financial_transactions_type CHECK (type IN ('BAIXA', 'ESTORNO')),
    CONSTRAINT ck_financial_transactions_amount CHECK (amount > 0 AND interest >= 0 AND discount >= 0),
    CONSTRAINT ck_financial_transactions_method
        CHECK (method IN ('DINHEIRO', 'PIX', 'BOLETO', 'TRANSFERENCIA', 'CARTAO', 'CHEQUE', 'OUTRO'))
);

CREATE INDEX idx_financial_transactions_receivable ON financial_transactions (receivable_id);
CREATE INDEX idx_financial_transactions_paid ON financial_transactions (paid_on DESC);

CREATE FUNCTION financial_transactions_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'financial_transactions e somente inclusao: % nao permitido (use um estorno)', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_financial_transactions_append_only
    BEFORE UPDATE OR DELETE ON financial_transactions
    FOR EACH ROW EXECUTE FUNCTION financial_transactions_append_only();

-- ---------------------------------------------------------------------------
-- Permissoes da fase 4
-- ---------------------------------------------------------------------------
INSERT INTO permissions (code, module, description) VALUES
    ('expedicao.ver',       'Expedição',  'Consultar separações'),
    ('expedicao.separar',   'Expedição',  'Gerar separação e registrar o peso real separado'),
    ('expedicao.conferir',  'Expedição',  'Conferir a separação e liberar para faturamento'),
    ('faturamento.emitir',  'Faturamento','Faturar pedidos conferidos e gerar os títulos'),
    ('faturamento.cancelar','Faturamento','Cancelar faturamento e estornar estoque e títulos'),
    ('receber.ver',         'Financeiro', 'Consultar títulos a receber'),
    ('receber.baixar',      'Financeiro', 'Registrar recebimentos e estornos'),
    ('receber.cancelar',    'Financeiro', 'Cancelar títulos a receber em aberto');

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p.code
FROM roles r
JOIN (VALUES
        ('DIRETOR',    'expedicao.ver'), ('DIRETOR', 'receber.ver'),
        ('GERENTE',    'expedicao.ver'), ('GERENTE', 'expedicao.separar'), ('GERENTE', 'expedicao.conferir'),
        ('GERENTE',    'faturamento.emitir'), ('GERENTE', 'faturamento.cancelar'), ('GERENTE', 'receber.ver'),
        ('FINANCEIRO', 'expedicao.ver'), ('FINANCEIRO', 'faturamento.emitir'), ('FINANCEIRO', 'faturamento.cancelar'),
        ('FINANCEIRO', 'receber.ver'), ('FINANCEIRO', 'receber.baixar'), ('FINANCEIRO', 'receber.cancelar'),
        ('EXPEDICAO',  'expedicao.ver'), ('EXPEDICAO', 'expedicao.separar'), ('EXPEDICAO', 'expedicao.conferir'),
        ('ESTOQUISTA', 'expedicao.ver'), ('ESTOQUISTA', 'expedicao.separar'),
        ('LOGISTICA',  'expedicao.ver'),
        ('VENDEDOR',   'receber.ver')
     ) AS p(role_code, code) ON p.role_code = r.code;

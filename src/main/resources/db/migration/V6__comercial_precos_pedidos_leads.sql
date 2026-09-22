-- ============================================================================
-- V6: comercial - tabelas de preco, parametros, pedidos de venda com
-- bloqueios para aprovacao, reserva de estoque por lote e leads
-- ============================================================================

CREATE TABLE system_parameters (
    key         VARCHAR(80)  PRIMARY KEY,
    value       VARCHAR(255) NOT NULL,
    description VARCHAR(255) NOT NULL
);

INSERT INTO system_parameters (key, value, description) VALUES
    ('comercial.desconto_max_percent', '5',
     'Desconto máximo (%) sobre o preço de tabela sem aprovação');

CREATE TABLE price_tables (
    id         BIGSERIAL    PRIMARY KEY,
    name       VARCHAR(80)  NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_price_tables_name UNIQUE (name)
);

-- Preco por unidade base do produto (ex.: R$/kg).
CREATE TABLE price_table_items (
    price_table_id BIGINT         NOT NULL REFERENCES price_tables (id) ON DELETE CASCADE,
    product_id     BIGINT         NOT NULL REFERENCES products (id),
    price          NUMERIC(14, 2) NOT NULL,
    PRIMARY KEY (price_table_id, product_id),
    CONSTRAINT ck_price_table_items_price CHECK (price >= 0)
);

ALTER TABLE customers ADD COLUMN price_table_id BIGINT REFERENCES price_tables (id);

CREATE TABLE sales_orders (
    id                  BIGSERIAL      PRIMARY KEY,
    customer_id         BIGINT         NOT NULL REFERENCES customers (id),
    seller_id           BIGINT         REFERENCES users (id),
    status              VARCHAR(30)    NOT NULL,
    payment_term_id     BIGINT         REFERENCES payment_terms (id),
    price_table_id      BIGINT         REFERENCES price_tables (id),
    expected_delivery_on DATE,
    notes               VARCHAR(1000),
    subtotal            NUMERIC(14, 2) NOT NULL,
    discount_total      NUMERIC(14, 2) NOT NULL,
    total               NUMERIC(14, 2) NOT NULL,
    estimated_cost      NUMERIC(14, 2),
    -- peso variavel: total estimado ate a separacao informar o peso real
    has_estimated_weight BOOLEAN       NOT NULL DEFAULT FALSE,
    created_by          VARCHAR(255)   NOT NULL,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    approved_by         VARCHAR(255),
    approved_at         TIMESTAMPTZ,
    cancelled_by        VARCHAR(255),
    cancelled_at        TIMESTAMPTZ,
    cancel_reason       VARCHAR(500),
    version             BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_sales_orders_status CHECK (status IN ('AGUARDANDO_APROVACAO', 'APROVADO', 'CANCELADO'))
);

CREATE INDEX idx_sales_orders_customer ON sales_orders (customer_id, created_at DESC);
CREATE INDEX idx_sales_orders_seller ON sales_orders (seller_id, created_at DESC);
CREATE INDEX idx_sales_orders_status ON sales_orders (status);

CREATE TABLE sales_order_items (
    id                 BIGSERIAL      PRIMARY KEY,
    order_id           BIGINT         NOT NULL REFERENCES sales_orders (id) ON DELETE CASCADE,
    product_id         BIGINT         NOT NULL REFERENCES products (id),
    -- como o cliente pediu (ex.: 3 CX)...
    unit_code          VARCHAR(10)    NOT NULL,
    quantity           NUMERIC(14, 3) NOT NULL,
    factor             NUMERIC(14, 4) NOT NULL,
    nominal            BOOLEAN        NOT NULL,
    -- ...e o equivalente na unidade base (ex.: 60 kg), usado em estoque e preco
    qty_base           NUMERIC(14, 3) NOT NULL,
    list_price         NUMERIC(14, 2) NOT NULL,
    unit_price         NUMERIC(14, 2) NOT NULL,
    discount_percent   NUMERIC(5, 2)  NOT NULL,
    line_total         NUMERIC(14, 2) NOT NULL,
    unit_cost_snapshot NUMERIC(14, 4),
    price_source       VARCHAR(20)    NOT NULL,
    CONSTRAINT ck_sales_order_items_qty CHECK (quantity > 0 AND qty_base > 0),
    CONSTRAINT ck_sales_order_items_price CHECK (unit_price >= 0 AND list_price >= 0)
);

CREATE INDEX idx_sales_order_items_order ON sales_order_items (order_id);

-- Motivos que impedem a aprovacao automatica do pedido.
CREATE TABLE sales_order_blocks (
    id          BIGSERIAL    PRIMARY KEY,
    order_id    BIGINT       NOT NULL REFERENCES sales_orders (id) ON DELETE CASCADE,
    type        VARCHAR(30)  NOT NULL,
    detail      VARCHAR(500) NOT NULL,
    resolved_by VARCHAR(255),
    resolved_at TIMESTAMPTZ
);

CREATE INDEX idx_sales_order_blocks_order ON sales_order_blocks (order_id);

-- Reserva de estoque por lote (FEFO) para um item de pedido aprovado.
CREATE TABLE stock_reservations (
    id            BIGSERIAL      PRIMARY KEY,
    order_item_id BIGINT         NOT NULL REFERENCES sales_order_items (id),
    warehouse_id  BIGINT         NOT NULL REFERENCES warehouses (id),
    product_id    BIGINT         NOT NULL REFERENCES products (id),
    lot_id        BIGINT         REFERENCES lots (id),
    quantity      NUMERIC(14, 3) NOT NULL,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    released_at   TIMESTAMPTZ,
    CONSTRAINT ck_stock_reservations_qty CHECK (quantity > 0)
);

CREATE INDEX idx_stock_reservations_item ON stock_reservations (order_item_id);
CREATE INDEX idx_stock_reservations_open ON stock_reservations (product_id) WHERE released_at IS NULL;

-- ---------------------------------------------------------------------------
-- Leads (prospeccao): pre-cliente vindo do mapa ou cadastrado a mao
-- ---------------------------------------------------------------------------
CREATE TABLE leads (
    id                    BIGSERIAL     PRIMARY KEY,
    name                  VARCHAR(150)  NOT NULL,
    segment_id            BIGINT        REFERENCES customer_segments (id),
    phone                 VARCHAR(30),
    email                 VARCHAR(150),
    website               VARCHAR(255),
    contact_name          VARCHAR(100),
    street                VARCHAR(150),
    number                VARCHAR(20),
    district              VARCHAR(80),
    city                  VARCHAR(80),
    state                 VARCHAR(2),
    latitude              NUMERIC(9, 6),
    longitude             NUMERIC(9, 6),
    source                VARCHAR(20)   NOT NULL,
    -- identificador no OpenStreetMap: impede cadastrar o mesmo lugar duas vezes
    external_id           VARCHAR(40),
    status                VARCHAR(20)   NOT NULL DEFAULT 'NOVO',
    seller_id             BIGINT        REFERENCES users (id),
    notes                 VARCHAR(2000),
    converted_customer_id BIGINT        REFERENCES customers (id),
    created_by            VARCHAR(255)  NOT NULL,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version               BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_leads_source CHECK (source IN ('MAPA', 'MANUAL')),
    CONSTRAINT ck_leads_status CHECK (status IN ('NOVO', 'CONTATADO', 'QUALIFICADO', 'CONVERTIDO', 'DESCARTADO')),
    CONSTRAINT uq_leads_external UNIQUE (external_id)
);

CREATE INDEX idx_leads_seller ON leads (seller_id);
CREATE INDEX idx_leads_status ON leads (status);

-- ---------------------------------------------------------------------------
-- Permissoes da fase 3
-- ---------------------------------------------------------------------------
INSERT INTO permissions (code, module, description) VALUES
    ('pedidos.ver',              'Comercial', 'Consultar pedidos da própria carteira'),
    ('pedidos.ver_todos',        'Comercial', 'Consultar pedidos de todos os vendedores'),
    ('pedidos.criar',            'Comercial', 'Lançar pedidos de venda'),
    ('pedidos.aprovar_desconto', 'Comercial', 'Aprovar desconto acima do limite e venda abaixo do custo'),
    ('pedidos.cancelar',         'Comercial', 'Cancelar pedidos'),
    ('credito.liberar',          'Financeiro', 'Liberar pedido bloqueado por crédito'),
    ('precos.gerenciar',         'Comercial', 'Criar e editar tabelas de preço e o desconto máximo'),
    ('leads.ver',                'Comercial', 'Consultar leads da própria carteira'),
    ('leads.gerenciar',          'Comercial', 'Buscar, cadastrar e trabalhar leads');

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p.code
FROM roles r
JOIN (VALUES
        ('DIRETOR',    'pedidos.ver'), ('DIRETOR', 'pedidos.ver_todos'), ('DIRETOR', 'pedidos.aprovar_desconto'),
        ('DIRETOR',    'credito.liberar'), ('DIRETOR', 'leads.ver'),
        ('GERENTE',    'pedidos.ver'), ('GERENTE', 'pedidos.ver_todos'), ('GERENTE', 'pedidos.criar'),
        ('GERENTE',    'pedidos.aprovar_desconto'), ('GERENTE', 'pedidos.cancelar'), ('GERENTE', 'precos.gerenciar'),
        ('GERENTE',    'leads.ver'), ('GERENTE', 'leads.gerenciar'),
        ('FINANCEIRO', 'pedidos.ver'), ('FINANCEIRO', 'pedidos.ver_todos'), ('FINANCEIRO', 'credito.liberar'),
        ('VENDEDOR',   'pedidos.ver'), ('VENDEDOR', 'pedidos.criar'), ('VENDEDOR', 'leads.ver'),
        ('VENDEDOR',   'leads.gerenciar'),
        ('EXPEDICAO',  'pedidos.ver'), ('EXPEDICAO', 'pedidos.ver_todos'),
        ('LOGISTICA',  'pedidos.ver'), ('LOGISTICA', 'pedidos.ver_todos')
     ) AS p(role_code, code) ON p.role_code = r.code;

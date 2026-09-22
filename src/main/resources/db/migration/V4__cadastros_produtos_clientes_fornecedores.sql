-- ============================================================================
-- V4: cadastros do ERP - unidades, marcas, condicoes de pagamento, segmentos,
-- fornecedores, clientes e campos operacionais de produto
-- ============================================================================

-- ---------------------------------------------------------------------------
-- Tabelas auxiliares
-- ---------------------------------------------------------------------------
CREATE TABLE units (
    code           VARCHAR(10)  PRIMARY KEY,
    name           VARCHAR(40)  NOT NULL,
    -- kg aceita 20,735; unidade/caixa nao
    allows_decimal BOOLEAN      NOT NULL
);

INSERT INTO units (code, name, allows_decimal) VALUES
    ('KG',  'Quilograma', TRUE),
    ('G',   'Grama',      TRUE),
    ('L',   'Litro',      TRUE),
    ('UN',  'Unidade',    FALSE),
    ('CX',  'Caixa',      FALSE),
    ('PCT', 'Pacote',     FALSE),
    ('FD',  'Fardo',      FALSE),
    ('BD',  'Balde',      FALSE),
    ('SC',  'Saco',       FALSE),
    ('DZ',  'Dúzia',      FALSE);

CREATE TABLE brands (
    id     BIGSERIAL    PRIMARY KEY,
    name   VARCHAR(80)  NOT NULL,
    active BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_brands_name UNIQUE (name)
);

CREATE TABLE payment_terms (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(60)  NOT NULL,
    -- prazos em dias de cada parcela, ex.: {28,35,42}; {0} = a vista
    installment_days INTEGER[] NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_payment_terms_name UNIQUE (name),
    CONSTRAINT ck_payment_terms_days CHECK (cardinality(installment_days) BETWEEN 1 AND 12)
);

INSERT INTO payment_terms (name, installment_days) VALUES
    ('À vista',      '{0}'),
    ('7 dias',       '{7}'),
    ('14 dias',      '{14}'),
    ('21 dias',      '{21}'),
    ('28 dias',      '{28}'),
    ('14/28 dias',   '{14,28}'),
    ('28/35/42 dias','{28,35,42}');

CREATE TABLE customer_segments (
    id     BIGSERIAL   PRIMARY KEY,
    name   VARCHAR(60) NOT NULL,
    active BOOLEAN     NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_customer_segments_name UNIQUE (name)
);

INSERT INTO customer_segments (name) VALUES
    ('Restaurante'), ('Churrascaria'), ('Galeteria'), ('Hamburgueria'), ('Pizzaria'),
    ('Mercado'), ('Lanchonete'), ('Hotel'), ('Alimentação coletiva'), ('Outros');

-- subcategorias: categoria com pai
ALTER TABLE categories ADD COLUMN parent_id BIGINT;
ALTER TABLE categories ADD CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories (id);

-- ---------------------------------------------------------------------------
-- Fornecedores
-- ---------------------------------------------------------------------------
CREATE TABLE suppliers (
    id                 BIGSERIAL     PRIMARY KEY,
    legal_name         VARCHAR(150)  NOT NULL,
    trade_name         VARCHAR(150),
    document           VARCHAR(14)   NOT NULL,
    state_registration VARCHAR(20),
    phone              VARCHAR(20),
    whatsapp           VARCHAR(20),
    email              VARCHAR(150),
    contact_name       VARCHAR(100),
    cep                VARCHAR(8),
    street             VARCHAR(150),
    number             VARCHAR(20),
    complement         VARCHAR(80),
    district           VARCHAR(80),
    city               VARCHAR(80),
    state              VARCHAR(2),
    payment_term_id    BIGINT,
    lead_time_days     INTEGER,
    notes              VARCHAR(2000),
    active             BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version            BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_suppliers_document UNIQUE (document),
    CONSTRAINT fk_suppliers_payment_term FOREIGN KEY (payment_term_id) REFERENCES payment_terms (id),
    CONSTRAINT ck_suppliers_lead_time CHECK (lead_time_days IS NULL OR lead_time_days >= 0)
);

-- ---------------------------------------------------------------------------
-- Clientes
-- ---------------------------------------------------------------------------
CREATE TABLE customers (
    id                 BIGSERIAL      PRIMARY KEY,
    person_type        VARCHAR(2)     NOT NULL,
    legal_name         VARCHAR(150)   NOT NULL,
    trade_name         VARCHAR(150),
    document           VARCHAR(14)    NOT NULL,
    state_registration VARCHAR(20),
    phone              VARCHAR(20),
    whatsapp           VARCHAR(20),
    email              VARCHAR(150),
    contact_name       VARCHAR(100),
    cep                VARCHAR(8),
    street             VARCHAR(150),
    number             VARCHAR(20),
    complement         VARCHAR(80),
    district           VARCHAR(80),
    city               VARCHAR(80),
    state              VARCHAR(2),
    latitude           NUMERIC(9, 6),
    longitude          NUMERIC(9, 6),
    segment_id         BIGINT,
    seller_id          BIGINT,
    payment_term_id    BIGINT,
    credit_limit       NUMERIC(14, 2) NOT NULL DEFAULT 0,
    status             VARCHAR(20)    NOT NULL DEFAULT 'ATIVO',
    notes              VARCHAR(2000),
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version            BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uq_customers_document UNIQUE (document),
    CONSTRAINT ck_customers_person_type CHECK (person_type IN ('PJ', 'PF')),
    CONSTRAINT ck_customers_status CHECK (status IN ('ATIVO', 'BLOQUEADO', 'INATIVO')),
    CONSTRAINT ck_customers_credit_limit CHECK (credit_limit >= 0),
    CONSTRAINT fk_customers_segment FOREIGN KEY (segment_id) REFERENCES customer_segments (id),
    CONSTRAINT fk_customers_seller FOREIGN KEY (seller_id) REFERENCES users (id),
    CONSTRAINT fk_customers_payment_term FOREIGN KEY (payment_term_id) REFERENCES payment_terms (id)
);

CREATE INDEX idx_customers_seller ON customers (seller_id);
CREATE INDEX idx_customers_city ON customers (city);

-- ---------------------------------------------------------------------------
-- Produtos: campos operacionais
-- ---------------------------------------------------------------------------
ALTER TABLE products ADD COLUMN barcode          VARCHAR(14);
ALTER TABLE products ADD COLUMN brand_id         BIGINT REFERENCES brands (id);
ALTER TABLE products ADD COLUMN subcategory_id   BIGINT REFERENCES categories (id);
ALTER TABLE products ADD COLUMN base_unit        VARCHAR(10) REFERENCES units (code);
ALTER TABLE products ADD COLUMN variable_weight  BOOLEAN        NOT NULL DEFAULT FALSE;
ALTER TABLE products ADD COLUMN gross_weight_kg  NUMERIC(10, 3);
ALTER TABLE products ADD COLUMN storage_type     VARCHAR(12)    NOT NULL DEFAULT 'SECO';
ALTER TABLE products ADD COLUMN lot_control      BOOLEAN        NOT NULL DEFAULT FALSE;
ALTER TABLE products ADD COLUMN expiry_control   BOOLEAN        NOT NULL DEFAULT FALSE;
ALTER TABLE products ADD COLUMN shelf_life_days  INTEGER;
ALTER TABLE products ADD COLUMN cost_price       NUMERIC(14, 4);
ALTER TABLE products ADD COLUMN average_cost     NUMERIC(14, 4);
ALTER TABLE products ADD COLUMN min_stock        NUMERIC(14, 3);
ALTER TABLE products ADD COLUMN max_stock        NUMERIC(14, 3);
ALTER TABLE products ADD COLUMN lead_time_days   INTEGER;
ALTER TABLE products ADD COLUMN main_supplier_id BIGINT REFERENCES suppliers (id);
ALTER TABLE products ADD COLUMN active           BOOLEAN        NOT NULL DEFAULT TRUE;
ALTER TABLE products ADD COLUMN created_at       TIMESTAMPTZ    NOT NULL DEFAULT now();
ALTER TABLE products ADD COLUMN updated_at       TIMESTAMPTZ    NOT NULL DEFAULT now();
ALTER TABLE products ADD COLUMN version          BIGINT         NOT NULL DEFAULT 0;

ALTER TABLE products ADD CONSTRAINT ck_products_storage CHECK (storage_type IN ('SECO', 'REFRIGERADO', 'CONGELADO'));
ALTER TABLE products ADD CONSTRAINT ck_products_stock CHECK (
    (min_stock IS NULL OR min_stock >= 0) AND (max_stock IS NULL OR max_stock >= 0)
    AND (min_stock IS NULL OR max_stock IS NULL OR max_stock >= min_stock));
ALTER TABLE products ADD CONSTRAINT ck_products_cost CHECK (cost_price IS NULL OR cost_price >= 0);
ALTER TABLE products ADD CONSTRAINT uq_products_sku UNIQUE (sku);

-- Unidade base a partir do texto livre atual ("kg", "cx", "pct", "un").
UPDATE products SET base_unit = CASE lower(trim(unit))
    WHEN 'kg'  THEN 'KG'
    WHEN 'cx'  THEN 'CX'
    WHEN 'pct' THEN 'PCT'
    ELSE 'UN' END;
ALTER TABLE products ALTER COLUMN base_unit SET NOT NULL;

-- Congelados: categoria "congelados" ou "Cong." no nome do fornecedor.
UPDATE products SET storage_type = 'CONGELADO'
WHERE category = 'congelados' OR name ~* '\mcong\M';

-- Conversoes de unidade: 1 caixa = N unidades base.
CREATE TABLE product_units (
    product_id  BIGINT        NOT NULL,
    unit_code   VARCHAR(10)   NOT NULL,
    factor      NUMERIC(14, 4) NOT NULL,
    -- fator aproximado (peso variavel): o peso real e apurado na separacao
    nominal     BOOLEAN       NOT NULL DEFAULT FALSE,
    barcode     VARCHAR(14),
    PRIMARY KEY (product_id, unit_code),
    CONSTRAINT fk_product_units_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT fk_product_units_unit FOREIGN KEY (unit_code) REFERENCES units (code),
    CONSTRAINT ck_product_units_factor CHECK (factor > 0)
);

-- "CX 12 KG" -> 1 CX = 12 kg; "CX +- 20 KG" -> 1 CX ~ 20 kg (peso variavel).
INSERT INTO product_units (product_id, unit_code, factor, nominal)
SELECT id, 'CX', replace(m[2], ',', '.')::numeric, m[1] IS NOT NULL
FROM (
    SELECT id, regexp_match(upper(name), 'CX\s*(?:C/)?\s*(\+-)?\s*(\d+(?:[.,]\d+)?)\s*KG') AS m
    FROM products
    WHERE base_unit = 'KG'
) found
WHERE m IS NOT NULL AND replace(m[2], ',', '.')::numeric > 0;

UPDATE products p SET variable_weight = TRUE
WHERE EXISTS (SELECT 1 FROM product_units pu WHERE pu.product_id = p.id AND pu.nominal);

-- Fornecedores alternativos do produto
CREATE TABLE product_suppliers (
    product_id     BIGINT         NOT NULL,
    supplier_id    BIGINT         NOT NULL,
    supplier_sku   VARCHAR(40),
    last_cost      NUMERIC(14, 4),
    lead_time_days INTEGER,
    PRIMARY KEY (product_id, supplier_id),
    CONSTRAINT fk_product_suppliers_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT fk_product_suppliers_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id)
);

-- ---------------------------------------------------------------------------
-- Permissoes da fase 1
-- ---------------------------------------------------------------------------
INSERT INTO permissions (code, module, description) VALUES
    ('produtos.ver',              'Produtos',     'Consultar o cadastro completo de produtos no ERP'),
    ('produtos.custo.ver',        'Produtos',     'Ver custo e margem dos produtos'),
    ('produtos.custo.alterar',    'Produtos',     'Alterar o custo dos produtos'),
    ('clientes.ver',              'Clientes',     'Consultar clientes da própria carteira'),
    ('clientes.ver_todos',        'Clientes',     'Consultar clientes de todos os vendedores'),
    ('clientes.editar',           'Clientes',     'Cadastrar e editar clientes'),
    ('clientes.credito.alterar',  'Clientes',     'Alterar limite de crédito e bloquear clientes'),
    ('fornecedores.ver',          'Fornecedores', 'Consultar fornecedores'),
    ('fornecedores.editar',       'Fornecedores', 'Cadastrar e editar fornecedores'),
    ('cadastros.configurar',      'Sistema',      'Editar marcas, condições de pagamento e segmentos');

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p.code
FROM roles r
JOIN (VALUES
        ('DIRETOR',    'produtos.ver'), ('DIRETOR', 'produtos.custo.ver'), ('DIRETOR', 'clientes.ver'),
        ('DIRETOR',    'clientes.ver_todos'), ('DIRETOR', 'fornecedores.ver'), ('DIRETOR', 'clientes.credito.alterar'),
        ('GERENTE',    'produtos.ver'), ('GERENTE', 'produtos.custo.ver'), ('GERENTE', 'clientes.ver'),
        ('GERENTE',    'clientes.ver_todos'), ('GERENTE', 'clientes.editar'), ('GERENTE', 'fornecedores.ver'),
        ('GERENTE',    'fornecedores.editar'), ('GERENTE', 'cadastros.configurar'),
        ('FINANCEIRO', 'clientes.ver'), ('FINANCEIRO', 'clientes.ver_todos'), ('FINANCEIRO', 'clientes.credito.alterar'),
        ('FINANCEIRO', 'fornecedores.ver'), ('FINANCEIRO', 'produtos.ver'),
        ('COMPRAS',    'produtos.ver'), ('COMPRAS', 'produtos.custo.ver'), ('COMPRAS', 'produtos.custo.alterar'),
        ('COMPRAS',    'produtos.editar'), ('COMPRAS', 'fornecedores.ver'), ('COMPRAS', 'fornecedores.editar'),
        ('VENDEDOR',   'produtos.ver'), ('VENDEDOR', 'clientes.ver'), ('VENDEDOR', 'clientes.editar'),
        ('ESTOQUISTA', 'produtos.ver'), ('ESTOQUISTA', 'fornecedores.ver'),
        ('EXPEDICAO',  'produtos.ver'),
        ('LOGISTICA',  'clientes.ver'), ('LOGISTICA', 'clientes.ver_todos')
     ) AS p(role_code, code) ON p.role_code = r.code;

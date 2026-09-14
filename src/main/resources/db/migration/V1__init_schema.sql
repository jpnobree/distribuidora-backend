-- ============================================================================
-- V1: schema inicial - usuarios, categorias, produtos (+ tags), mensagens
-- ============================================================================
-- Esta migration e a fonte de verdade do schema (Flyway a aplica sozinho no
-- startup). Substitui o antigo sql/schema.sql, que era rodado manualmente.
-- ============================================================================

CREATE TABLE users (
    id       BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    email    VARCHAR(255),
    role     VARCHAR(20)  NOT NULL,
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'USER'))
);

CREATE TABLE categories (
    id   BIGSERIAL PRIMARY KEY,
    slug VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    icon VARCHAR(255),
    CONSTRAINT uq_categories_slug UNIQUE (slug)
);

CREATE TABLE products (
    id          BIGSERIAL PRIMARY KEY,
    slug        VARCHAR(255)  NOT NULL,
    sku         VARCHAR(255)  NOT NULL,
    name        VARCHAR(255)  NOT NULL,
    category    VARCHAR(255)  NOT NULL,
    unit        VARCHAR(255)  NOT NULL,
    price       DOUBLE PRECISION,
    image       VARCHAR(255),
    description VARCHAR(2000),
    origin      VARCHAR(255),
    available   BOOLEAN       NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_products_slug UNIQUE (slug)
);

CREATE INDEX idx_products_category ON products (category);

-- tags livres de cada produto (ex: "Premium", "Resfriado")
CREATE TABLE product_tags (
    product_id BIGINT       NOT NULL,
    tag        VARCHAR(255),
    CONSTRAINT fk_product_tags_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
);

CREATE TABLE contact_messages (
    id           BIGSERIAL PRIMARY KEY,
    requester_id BIGINT        NOT NULL,
    product_slug VARCHAR(255),
    message      VARCHAR(2000) NOT NULL,
    phone        VARCHAR(255),
    created_at   TIMESTAMP     NOT NULL,
    answered     BOOLEAN       NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_contact_messages_requester FOREIGN KEY (requester_id) REFERENCES users (id)
);

CREATE INDEX idx_contact_messages_requester ON contact_messages (requester_id);

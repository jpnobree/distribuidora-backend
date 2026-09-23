-- ============================================================================
-- V9: despesa avulsa (aluguel, energia, combustivel) como titulo a pagar.
-- Sem ela, o fluxo de caixa e o DRE so enxergam compra de mercadoria.
-- ============================================================================

-- Despesa nao vem de recebimento; compra de mercadoria nao tem categoria.
ALTER TABLE payables ALTER COLUMN receipt_id DROP NOT NULL;
ALTER TABLE payables ADD COLUMN expense_category VARCHAR(30);
ALTER TABLE payables ADD CONSTRAINT ck_payables_origin
    CHECK (receipt_id IS NOT NULL OR expense_category IS NOT NULL);

INSERT INTO permissions (code, module, description) VALUES
    ('pagar.lancar',         'Financeiro', 'Lançar despesas a pagar'),
    ('financeiro.resultado', 'Financeiro', 'Ver fluxo de caixa e DRE gerencial');

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p.code
FROM roles r
JOIN (VALUES
        ('DIRETOR',    'financeiro.resultado'),
        ('GERENTE',    'financeiro.resultado'), ('GERENTE', 'pagar.lancar'),
        ('FINANCEIRO', 'financeiro.resultado'), ('FINANCEIRO', 'pagar.lancar')
     ) AS p(role_code, code) ON p.role_code = r.code;

package com.distribuidora.backend.audit;

public enum AuditAction {
    LOGIN_SUCESSO("Login realizado"),
    LOGIN_FALHA("Tentativa de login recusada"),
    CLIENTE_CADASTRADO_SITE("Cliente cadastrado pelo site"),
    USUARIO_CRIADO("Usuário criado"),
    USUARIO_ALTERADO("Usuário alterado"),
    USUARIO_SENHA_REDEFINIDA("Senha redefinida"),
    PERFIL_CRIADO("Perfil criado"),
    PERFIL_ALTERADO("Perfil alterado"),
    PERFIL_EXCLUIDO("Perfil excluído"),
    PRODUTO_CRIADO("Produto criado"),
    PRODUTO_ALTERADO("Produto alterado"),
    PRODUTO_PRECO_ALTERADO("Preço de produto alterado"),
    PRODUTO_EXCLUIDO("Produto excluído"),
    PRODUTO_DESATIVADO("Produto desativado"),
    PRODUTO_CUSTO_ALTERADO("Custo de produto alterado"),
    CLIENTE_CRIADO("Cliente cadastrado"),
    CLIENTE_ALTERADO("Cliente alterado"),
    CLIENTE_CREDITO_ALTERADO("Crédito do cliente alterado"),
    FORNECEDOR_CRIADO("Fornecedor cadastrado"),
    FORNECEDOR_ALTERADO("Fornecedor alterado"),
    CADASTRO_AUXILIAR_ALTERADO("Tabela auxiliar alterada"),
    ESTOQUE_INVENTARIO_FECHADO("Inventário fechado"),
    PEDIDO_CRIADO("Pedido lançado"),
    PEDIDO_APROVADO("Pedido aprovado"),
    PEDIDO_CANCELADO("Pedido cancelado"),
    SEPARACAO_CONFIRMADA("Separação confirmada"),
    SEPARACAO_CONFERIDA("Separação conferida"),
    SEPARACAO_CANCELADA("Separação cancelada"),
    PEDIDO_FATURADO("Pedido faturado"),
    FATURA_CANCELADA("Faturamento cancelado"),
    TITULO_BAIXADO("Título recebido"),
    TITULO_ESTORNADO("Baixa estornada"),
    TITULO_CANCELADO("Título cancelado"),
    COMPRA_LANCADA("Pedido de compra lançado"),
    COMPRA_APROVADA("Pedido de compra aprovado"),
    COMPRA_CANCELADA("Pedido de compra cancelado"),
    COMPRA_RECEBIDA("Mercadoria recebida"),
    TITULO_PAGO("Título pago"),
    PAGAMENTO_ESTORNADO("Pagamento estornado"),
    TITULO_PAGAR_CANCELADO("Título a pagar cancelado"),
    TABELA_PRECO_ALTERADA("Tabela de preço alterada"),
    PARAMETRO_ALTERADO("Parâmetro alterado"),
    LEAD_CONVERTIDO("Lead convertido em cliente");

    private final String label;

    AuditAction(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

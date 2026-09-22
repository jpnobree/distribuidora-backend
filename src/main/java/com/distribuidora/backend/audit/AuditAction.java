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
    PRODUTO_EXCLUIDO("Produto excluído");

    private final String label;

    AuditAction(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

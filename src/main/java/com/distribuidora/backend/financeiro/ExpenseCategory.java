package com.distribuidora.backend.financeiro;

// Categorias de despesa para o DRE. Lista fixa de propósito: agrupar por texto
// digitado ("Combustivel" x "Combustível") quebraria o relatório, e uma tabela
// com CRUD e tela custaria mais do que o problema pede. "Outros" + descrição
// cobre o que faltar.
// ponytail: enum em vez de tabela; vira cadastro quando ele pedir categoria
// própria ou centro de custo.
public enum ExpenseCategory {
    ALUGUEL("Aluguel"),
    ENERGIA("Energia elétrica"),
    AGUA("Água"),
    TELEFONE_INTERNET("Telefone e internet"),
    COMBUSTIVEL("Combustível"),
    MANUTENCAO("Manutenção"),
    FRETE("Frete"),
    SALARIOS("Salários e encargos"),
    IMPOSTOS("Impostos e taxas"),
    TARIFAS_BANCARIAS("Tarifas bancárias"),
    MATERIAL("Material de consumo"),
    OUTROS("Outros");

    private final String label;

    ExpenseCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

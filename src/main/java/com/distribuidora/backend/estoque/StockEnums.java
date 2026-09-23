package com.distribuidora.backend.estoque;

public final class StockEnums {

    private StockEnums() {
    }

    // Situacao da quantidade dentro de um saldo. EXTERNO = fora da empresa.
    // RESERVADO entra na fase 3 (pedidos).
    public enum Bucket { EXTERNO, DISPONIVEL, BLOQUEADO, AVARIADO }

    public enum MovementType {
        IMPLANTACAO("Implantação de saldo", Bucket.EXTERNO, Bucket.DISPONIVEL),
        ENTRADA_MANUAL("Entrada manual", Bucket.EXTERNO, Bucket.DISPONIVEL),
        ENTRADA_COMPRA("Entrada por compra", Bucket.EXTERNO, Bucket.DISPONIVEL),
        SAIDA_MANUAL("Saída manual", Bucket.DISPONIVEL, Bucket.EXTERNO),
        SAIDA_VENDA("Saída por venda", Bucket.DISPONIVEL, Bucket.EXTERNO),
        ESTORNO_VENDA("Estorno de venda", Bucket.EXTERNO, Bucket.DISPONIVEL),
        TRANSFERENCIA_SAIDA("Transferência (saída)", Bucket.DISPONIVEL, Bucket.EXTERNO),
        TRANSFERENCIA_ENTRADA("Transferência (entrada)", Bucket.EXTERNO, Bucket.DISPONIVEL),
        AJUSTE_INVENTARIO_ENTRADA("Ajuste de inventário (sobra)", Bucket.EXTERNO, Bucket.DISPONIVEL),
        AJUSTE_INVENTARIO_SAIDA("Ajuste de inventário (falta)", Bucket.DISPONIVEL, Bucket.EXTERNO),
        BLOQUEIO("Bloqueio", Bucket.DISPONIVEL, Bucket.BLOQUEADO),
        DESBLOQUEIO("Liberação de bloqueio", Bucket.BLOQUEADO, Bucket.DISPONIVEL),
        AVARIA("Avaria", Bucket.DISPONIVEL, Bucket.AVARIADO),
        PERDA("Perda", Bucket.DISPONIVEL, Bucket.EXTERNO),
        PERDA_AVARIADO("Baixa de avariado", Bucket.AVARIADO, Bucket.EXTERNO),
        PERDA_BLOQUEADO("Baixa de bloqueado", Bucket.BLOQUEADO, Bucket.EXTERNO);

        private final String label;
        private final Bucket from;
        private final Bucket to;

        MovementType(String label, Bucket from, Bucket to) {
            this.label = label;
            this.from = from;
            this.to = to;
        }

        public String getLabel() {
            return label;
        }

        public Bucket from() {
            return from;
        }

        public Bucket to() {
            return to;
        }

        public boolean isLoss() {
            return this == PERDA || this == PERDA_AVARIADO || this == PERDA_BLOQUEADO;
        }
    }

    public enum LossReason {
        VENCIMENTO("Vencimento"),
        AVARIA("Avaria"),
        ARMAZENAMENTO("Armazenamento inadequado"),
        TRANSPORTE("Transporte"),
        ERRO_OPERACIONAL("Erro operacional"),
        DEVOLUCAO("Devolução"),
        OUTROS("Outros");

        private final String label;

        LossReason(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }
}

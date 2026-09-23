package com.distribuidora.backend.expedicao;

import java.math.BigDecimal;
import java.math.RoundingMode;

// Regra de peso da separacao, isolada do resto para poder ser conferida
// sozinha: em carnes e frios a peca nunca sai exata, mas a diferenca tem
// limite e nunca passa calada.
public final class WeightRules {

    private WeightRules() {
    }

    public enum Outcome {
        // saiu exatamente o pedido
        EXATO,
        // peso variavel dentro da tolerancia: normal, nao vira divergencia
        DENTRO_TOLERANCIA,
        // peso variavel alem da tolerancia: registra divergencia
        FORA_TOLERANCIA,
        // saiu menos que o pedido: registra falta
        FALTA,
        // peso fixo nao pode sair a mais: a separacao e recusada
        SOBRA_PROIBIDA
    }

    public static Outcome evaluate(BigDecimal ordered, BigDecimal picked, boolean variableWeight,
                                   BigDecimal tolerancePercent) {
        int cmp = picked.compareTo(ordered);
        if (cmp == 0) {
            return Outcome.EXATO;
        }
        if (!variableWeight) {
            return cmp > 0 ? Outcome.SOBRA_PROIBIDA : Outcome.FALTA;
        }
        return deviationPercent(ordered, picked).compareTo(tolerancePercent) <= 0
                ? Outcome.DENTRO_TOLERANCIA : Outcome.FORA_TOLERANCIA;
    }

    public static BigDecimal deviationPercent(BigDecimal ordered, BigDecimal picked) {
        if (ordered.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return picked.subtract(ordered).abs().multiply(BigDecimal.valueOf(100))
                .divide(ordered, 2, RoundingMode.HALF_UP);
    }
}

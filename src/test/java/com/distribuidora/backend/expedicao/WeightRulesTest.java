package com.distribuidora.backend.expedicao;

import com.distribuidora.backend.expedicao.WeightRules.Outcome;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeightRulesTest {

    private static final BigDecimal TEN_PERCENT = new BigDecimal("10");

    @Test
    void pesoVariavelDentroDaTolerancia_naoEDivergencia() {
        // pedido de 20 kg, saiu uma peca de 20,7 kg: 3,5% de variacao
        assertEquals(Outcome.DENTRO_TOLERANCIA,
                WeightRules.evaluate(new BigDecimal("20"), new BigDecimal("20.7"), true, TEN_PERCENT));
    }

    @Test
    void pesoVariavelAlemDaTolerancia_viraDivergencia() {
        assertEquals(Outcome.FORA_TOLERANCIA,
                WeightRules.evaluate(new BigDecimal("20"), new BigDecimal("23"), true, TEN_PERCENT));
        assertEquals(new BigDecimal("15.00"),
                WeightRules.deviationPercent(new BigDecimal("20"), new BigDecimal("23")));
    }

    @Test
    void separarMenosQueOPedido_registraFalta() {
        assertEquals(Outcome.FALTA,
                WeightRules.evaluate(new BigDecimal("20"), new BigDecimal("12"), false, TEN_PERCENT));
    }

    @Test
    void pesoFixoNaoPodeSairAMais() {
        // 10 caixas pedidas, 11 na doca: a separacao e recusada
        assertEquals(Outcome.SOBRA_PROIBIDA,
                WeightRules.evaluate(new BigDecimal("10"), new BigDecimal("11"), false, TEN_PERCENT));
    }

    @Test
    void quantidadeExata_naoGeraNada() {
        assertEquals(Outcome.EXATO,
                WeightRules.evaluate(new BigDecimal("20.000"), new BigDecimal("20"), true, TEN_PERCENT));
    }

    @Test
    void toleranciaZero_qualquerDiferencaEDivergencia() {
        assertEquals(Outcome.FORA_TOLERANCIA,
                WeightRules.evaluate(new BigDecimal("20"), new BigDecimal("20.1"), true, BigDecimal.ZERO));
    }
}

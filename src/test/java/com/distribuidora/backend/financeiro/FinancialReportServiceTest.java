package com.distribuidora.backend.financeiro;

import com.distribuidora.backend.financeiro.FinanceDtos.CashFlowBucket;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FinancialReportServiceTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 9, 22);

    private static Object[] titulo(int emDias, String valor) {
        return new Object[]{HOJE.plusDays(emDias), new BigDecimal(valor)};
    }

    @Test
    void cadaTituloCaiEmUmaFaixaSo() {
        List<CashFlowBucket> faixas = FinancialReportService.buckets(
                List.of(titulo(0, "100"), titulo(5, "200"), titulo(12, "300"), titulo(25, "400")),
                List.<Object[]>of(), HOJE);

        assertEquals(List.of("100", "200", "300", "400", "0", "0"),
                faixas.stream().map(f -> f.incoming().toPlainString()).toList());
    }

    @Test
    void acumuladoResponde_quantoSobraNoFim() {
        // entra 1000 em 5 dias, sai 400 hoje e 300 em 20 dias
        List<CashFlowBucket> faixas = FinancialReportService.buckets(
                List.<Object[]>of(titulo(5, "1000")),
                List.of(titulo(0, "400"), titulo(20, "300")), HOJE);

        assertEquals("-400", faixas.get(0).accumulated().toPlainString());
        assertEquals("600", faixas.get(1).accumulated().toPlainString());
        assertEquals("300", faixas.get(3).accumulated().toPlainString());
        assertEquals("300", faixas.get(5).accumulated().toPlainString());
    }

    @Test
    void vencidoNaoEntraNasFaixas() {
        List<CashFlowBucket> faixas = FinancialReportService.buckets(
                List.of(titulo(-10, "500"), titulo(3, "100")), List.<Object[]>of(), HOJE);

        assertEquals("0", faixas.get(0).incoming().toPlainString());
        assertEquals("100", faixas.get(1).incoming().toPlainString());
        assertEquals("100", faixas.get(5).accumulated().toPlainString());
    }

    @Test
    void faixasNaoSeSobrepoem() {
        List<CashFlowBucket> faixas = FinancialReportService.buckets(List.<Object[]>of(), List.<Object[]>of(), HOJE);

        assertEquals(HOJE, faixas.get(0).from());
        assertEquals(HOJE, faixas.get(0).to());
        assertEquals(HOJE.plusDays(1), faixas.get(1).from());
        assertEquals(HOJE.plusDays(7), faixas.get(1).to());
        assertEquals(HOJE.plusDays(8), faixas.get(2).from());
        assertEquals(HOJE.plusDays(90), faixas.get(5).to());
    }
}

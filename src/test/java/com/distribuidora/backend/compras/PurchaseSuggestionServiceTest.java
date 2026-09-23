package com.distribuidora.backend.compras;

import com.distribuidora.backend.compras.PurchaseSuggestionService.Need;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PurchaseSuggestionServiceTest {

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    @Test
    void estoqueSuficienteParaOPrazo_naoSugereCompra() {
        // vende 10 kg/dia, tem 300 kg, entrega em 5 dias: nao precisa comprar
        assertNull(PurchaseSuggestionService.evaluate(bd("300"), BigDecimal.ZERO, bd("50"), null, bd("10"), 5, 15));
    }

    @Test
    void coberturaMenorQueOPrazo_sugereAteCobrirPrazoMaisMargem() {
        // vende 10 kg/dia, tem 30 kg, entrega em 5 dias, cobertura de 15:
        // alvo 10 * (5 + 15) = 200, faltam 170
        Need need = PurchaseSuggestionService.evaluate(bd("30"), BigDecimal.ZERO, BigDecimal.ZERO, null,
                bd("10"), 5, 15);
        assertNotNull(need);
        assertEquals(0, need.quantity().compareTo(bd("170")));
        assertEquals("Cobertura menor que o prazo de entrega", need.reason());
    }

    @Test
    void oQueJaEstaComprado_entraNaConta() {
        // mesma situacao com 10 kg a caminho: sugere 160 em vez de 170
        Need need = PurchaseSuggestionService.evaluate(bd("30"), bd("10"), BigDecimal.ZERO, null, bd("10"), 5, 15);
        assertNotNull(need);
        assertEquals(0, need.quantity().compareTo(bd("160")));
    }

    @Test
    void mercadoriaACaminhoSuficiente_naoMandaComprarDeNovo() {
        // 150 kg ja comprados cobrem a necessidade: nao sugere nada
        assertNull(PurchaseSuggestionService.evaluate(bd("30"), bd("150"), BigDecimal.ZERO, null, bd("10"), 5, 15));
    }

    @Test
    void estoqueMaximoLimitaASugestao() {
        Need need = PurchaseSuggestionService.evaluate(bd("30"), BigDecimal.ZERO, BigDecimal.ZERO, bd("100"),
                bd("10"), 5, 15);
        assertNotNull(need);
        assertEquals(0, need.quantity().compareTo(bd("70")));
    }

    @Test
    void semVendaNoPeriodo_soRepoeQuemEstaAbaixoDoMinimo() {
        assertNull(PurchaseSuggestionService.evaluate(bd("5"), BigDecimal.ZERO, BigDecimal.ZERO, null,
                BigDecimal.ZERO, 5, 15));

        Need need = PurchaseSuggestionService.evaluate(bd("5"), BigDecimal.ZERO, bd("20"), null,
                BigDecimal.ZERO, 5, 15);
        assertNotNull(need);
        assertEquals(0, need.quantity().compareTo(bd("15")));
        assertEquals("Abaixo do estoque mínimo", need.reason());
    }

    @Test
    void semEstoqueEComVenda_apontaAUrgencia() {
        Need need = PurchaseSuggestionService.evaluate(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null,
                bd("8"), 3, 10);
        assertNotNull(need);
        assertEquals("Sem estoque disponível e com venda no período", need.reason());
        assertTrue(need.quantity().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void minimoMandaQuandoEMaiorQueAVenda() {
        // vende pouco, mas o cadastro exige 100 de minimo
        Need need = PurchaseSuggestionService.evaluate(bd("10"), BigDecimal.ZERO, bd("100"), null, bd("1"), 2, 5);
        assertNotNull(need);
        assertEquals(0, need.quantity().compareTo(bd("90")));
        assertEquals("Abaixo do estoque mínimo", need.reason());
    }
}

package com.distribuidora.backend.financeiro;

import com.distribuidora.backend.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayableTest {

    private static final LocalDate EMISSAO = LocalDate.of(2026, 3, 10);

    private Payable title(String amount) {
        return new Payable(1L, 1L, "NF 123", "Compra Frigorifico", 1, 1, EMISSAO, EMISSAO.plusDays(30),
                new BigDecimal(amount));
    }

    @Test
    void pagamentoParcial_deixaOTituloEmAbertoPeloSaldo() {
        Payable title = title("2000.00");
        title.pay(new BigDecimal("500.00"));

        assertEquals(Payable.Status.PARCIAL, title.getStatus());
        assertEquals(new BigDecimal("1500.00"), title.openAmount());
    }

    @Test
    void pagamentoTotal_quitaOTitulo() {
        Payable title = title("2000.00");
        title.pay(new BigDecimal("2000.00"));

        assertEquals(Payable.Status.PAGO, title.getStatus());
        assertEquals(0, title.openAmount().signum());
    }

    @Test
    void naoPagaMaisQueOSaldo() {
        Payable title = title("2000.00");
        assertThrows(BusinessRuleException.class, () -> title.pay(new BigDecimal("2000.01")));
    }

    @Test
    void estorno_devolveOTituloParaAberto() {
        Payable title = title("2000.00");
        title.pay(new BigDecimal("2000.00"));
        title.reverse(new BigDecimal("2000.00"));

        assertEquals(Payable.Status.ABERTO, title.getStatus());
        assertEquals(new BigDecimal("2000.00"), title.openAmount());
    }

    @Test
    void tituloComPagamentoNaoPodeSerCancelado() {
        Payable title = title("2000.00");
        title.pay(new BigDecimal("1.00"));
        BusinessRuleException error = assertThrows(BusinessRuleException.class, title::cancel);
        assertTrue(error.getMessage().contains("Estorne a baixa"));
    }

    @Test
    void vencimentoNoPassadoMarcaOTituloComoVencido() {
        Payable title = title("2000.00");
        assertTrue(title.isOverdue(EMISSAO.plusDays(31)));
    }
}

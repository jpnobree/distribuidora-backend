package com.distribuidora.backend.financeiro;

import com.distribuidora.backend.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReceivableTest {

    private static final LocalDate EMISSAO = LocalDate.of(2026, 3, 10);

    private Receivable title(String amount) {
        return new Receivable(1L, 1L, "1001", 1, 1, EMISSAO, EMISSAO.plusDays(28), new BigDecimal(amount));
    }

    @Test
    void recebimentoParcial_deixaOTituloEmAbertoPeloSaldo() {
        Receivable title = title("1000.00");
        title.receive(new BigDecimal("400.00"));

        assertEquals(Receivable.Status.PARCIAL, title.getStatus());
        assertEquals(new BigDecimal("600.00"), title.openAmount());
    }

    @Test
    void recebimentoTotal_quitaOTitulo() {
        Receivable title = title("1000.00");
        title.receive(new BigDecimal("400.00"));
        title.receive(new BigDecimal("600.00"));

        assertEquals(Receivable.Status.PAGO, title.getStatus());
        assertEquals(0, title.openAmount().signum());
    }

    @Test
    void naoRecebeMaisQueOSaldo() {
        Receivable title = title("1000.00");
        BusinessRuleException error = assertThrows(BusinessRuleException.class,
                () -> title.receive(new BigDecimal("1000.01")));
        assertTrue(error.getMessage().contains("maior que o saldo"));
    }

    @Test
    void estorno_devolveOTituloParaAberto() {
        Receivable title = title("1000.00");
        title.receive(new BigDecimal("1000.00"));
        title.reverse(new BigDecimal("1000.00"));

        assertEquals(Receivable.Status.ABERTO, title.getStatus());
        assertEquals(new BigDecimal("1000.00"), title.openAmount());
    }

    @Test
    void tituloComRecebimentoNaoPodeSerCancelado() {
        Receivable title = title("1000.00");
        title.receive(new BigDecimal("10.00"));
        BusinessRuleException error = assertThrows(BusinessRuleException.class, title::cancel);
        assertTrue(error.getMessage().contains("Estorne a baixa"));
    }

    @Test
    void tituloVencidoEIdentificadoPelaData() {
        Receivable title = title("1000.00");
        assertTrue(title.isOverdue(EMISSAO.plusDays(29)));
        assertTrue(!title.isOverdue(EMISSAO.plusDays(28)));
    }

    @Test
    void parcelamento_fechaNoCentavo() {
        List<BigDecimal> parts = ReceivableService.split(new BigDecimal("100.00"), 3);

        assertEquals(List.of(new BigDecimal("33.33"), new BigDecimal("33.33"), new BigDecimal("33.34")), parts);
        assertEquals(new BigDecimal("100.00"), parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void parcelaUnica_recebeOTotalInteiro() {
        assertEquals(List.of(new BigDecimal("1234.56")), ReceivableService.split(new BigDecimal("1234.56"), 1));
    }
}

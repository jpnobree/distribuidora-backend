package com.distribuidora.backend.estoque;

import com.distribuidora.backend.estoque.StockEnums.MovementType;
import com.distribuidora.backend.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StockBalanceTest {

    private static BigDecimal q(String v) {
        return new BigDecimal(v);
    }

    private static void apply(StockBalance b, MovementType type, String qty) {
        b.move(type.from(), type.to(), q(qty));
    }

    @Test
    void cicloCompleto_entradaBloqueioAvariaPerda() {
        StockBalance b = new StockBalance(1L, 10L, 5L);
        apply(b, MovementType.ENTRADA_MANUAL, "100");
        apply(b, MovementType.BLOQUEIO, "20");
        apply(b, MovementType.AVARIA, "5.5");

        assertEquals(0, q("100").compareTo(b.getQtyPhysical()));
        assertEquals(0, q("74.5").compareTo(b.available()));

        apply(b, MovementType.PERDA_AVARIADO, "5.5");
        apply(b, MovementType.DESBLOQUEIO, "20");
        apply(b, MovementType.SAIDA_MANUAL, "30");

        assertEquals(0, q("64.5").compareTo(b.getQtyPhysical()));
        assertEquals(0, q("64.5").compareTo(b.available()));
        assertEquals(0, BigDecimal.ZERO.compareTo(b.getQtyDamaged()));
        assertEquals(0, BigDecimal.ZERO.compareTo(b.getQtyBlocked()));
    }

    @Test
    void naoDeixaSaldoNegativo_nemUsaQuantidadeBloqueada() {
        StockBalance b = new StockBalance(1L, 10L, null);
        apply(b, MovementType.ENTRADA_MANUAL, "10");
        apply(b, MovementType.BLOQUEIO, "8");

        assertThrows(BusinessRuleException.class, () -> apply(b, MovementType.SAIDA_MANUAL, "3"));
        assertThrows(BusinessRuleException.class, () -> apply(b, MovementType.PERDA_AVARIADO, "1"));
        assertEquals(0, q("2").compareTo(b.available()));
    }
}

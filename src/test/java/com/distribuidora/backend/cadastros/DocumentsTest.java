package com.distribuidora.backend.cadastros;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentsTest {

    @Test
    void aceitaCnpjECpfValidos_comOuSemMascara() {
        assertTrue(Documents.isValid(Documents.digits("11.222.333/0001-81")));
        assertTrue(Documents.isValid(Documents.digits("529.982.247-25")));
    }

    @Test
    void recusaDigitoVerificadorErrado_sequenciaRepetida_eTamanhoInvalido() {
        assertFalse(Documents.isValid("11222333000182"));
        assertFalse(Documents.isValid("52998224726"));
        assertFalse(Documents.isValid("11111111111"));
        assertFalse(Documents.isValid("00000000000000"));
        assertFalse(Documents.isValid("123"));
    }

    @Test
    void tipoDePessoaDerivadoDoDocumento() {
        assertEquals("PJ", PartySupport.personType("11222333000181"));
        assertEquals("PF", PartySupport.personType("52998224725"));
    }
}

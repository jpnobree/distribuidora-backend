package com.distribuidora.backend.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditService auditService;

    private static Map<String, Object> produto(String name, String price, boolean available) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", name);
        values.put("price", price == null ? null : new BigDecimal(price));
        values.put("available", available);
        return values;
    }

    @Test
    void recordChange_deveGravarSomenteOsCamposAlterados() {
        auditService.recordChange(AuditAction.PRODUTO_ALTERADO, "Product", "picanha",
                produto("Picanha", "79.90", true), produto("Picanha", "84.50", false), null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog log = captor.getValue();
        assertEquals(Map.of("price", new BigDecimal("79.90"), "available", true), log.getOldValue());
        assertEquals(Map.of("price", new BigDecimal("84.50"), "available", false), log.getNewValue());
        assertEquals("picanha", log.getEntityId());
    }

    @Test
    void recordChange_naoGrava_quandoSoAEscalaDoDecimalMudou() {
        auditService.recordChange(AuditAction.PRODUTO_ALTERADO, "Product", "picanha",
                produto("Picanha", "79.9", true), produto("Picanha", "79.90", true), null);

        verify(auditLogRepository, never()).save(any());
    }

    @Test
    void recordChange_deveRegistrarPrecoRemovido_comoNulo() {
        auditService.recordChange(AuditAction.PRODUTO_PRECO_ALTERADO, "Product", "picanha",
                produto("Picanha", "79.90", true), produto("Picanha", null, true), null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertNull(captor.getValue().getNewValue().get("price"));
    }

    @Test
    void recordChange_deveGravarTudo_quandoCriacao() {
        auditService.recordChange(AuditAction.PRODUTO_CRIADO, "Product", "picanha",
                null, produto("Picanha", "79.90", true), null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertNull(captor.getValue().getOldValue());
        assertEquals(3, captor.getValue().getNewValue().size());
    }
}

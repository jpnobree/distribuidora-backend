package com.distribuidora.backend.faturamento;

// Porta para o emissor de documento fiscal. O ERP nao sabe se por tras existe
// NF-e, NFC-e ou nada: ele pede a emissao e guarda o retorno na nota.
//
// Hoje a unica implementacao e a interna (documento sem valor fiscal). Ligar
// um emissor real e escrever outra implementacao desta interface; nenhuma
// regra de estoque, faturamento ou financeiro muda.
public interface FiscalDocumentProvider {

    record FiscalResult(Invoice.FiscalStatus status, String number, String key, String message) {
    }

    FiscalResult issue(Invoice invoice);

    FiscalResult cancel(Invoice invoice, String reason);
}

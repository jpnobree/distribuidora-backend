package com.distribuidora.backend.faturamento;

import org.springframework.stereotype.Component;

// Emissao interna: numera o documento e registra que ele NAO tem valor fiscal.
// A operacao (separacao, baixa de estoque, titulo a receber) funciona por
// completo; o que falta e so a autorizacao da SEFAZ.
//
// Quando entrar um emissor real, ele vira outra implementacao anotada com
// @Primary (ou esta sai do contexto); nada mais muda.
@Component
public class InternalFiscalDocumentProvider implements FiscalDocumentProvider {

    static final String NOT_FISCAL = "A emissão de NF-e depende de contratar um emissor fiscal e "
            + "configurar o certificado digital. Até lá, este documento organiza a saída, o estoque e o título.";

    @Override
    public FiscalResult issue(Invoice invoice) {
        return new FiscalResult(Invoice.FiscalStatus.INTERNO, invoice.display(), null, NOT_FISCAL);
    }

    @Override
    public FiscalResult cancel(Invoice invoice, String reason) {
        return new FiscalResult(Invoice.FiscalStatus.INTERNO, invoice.getFiscalNumber(), invoice.getFiscalKey(),
                "Cancelamento interno: " + reason);
    }
}

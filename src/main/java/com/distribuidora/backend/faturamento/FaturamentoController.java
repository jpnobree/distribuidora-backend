package com.distribuidora.backend.faturamento;

import com.distribuidora.backend.dto.PageResponse;
import com.distribuidora.backend.faturamento.InvoiceDtos.InvoiceSummary;
import com.distribuidora.backend.faturamento.InvoiceDtos.InvoiceTotals;
import com.distribuidora.backend.faturamento.InvoiceDtos.InvoiceView;
import com.distribuidora.backend.faturamento.InvoiceDtos.ReasonRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Faturamento")
@Validated
@RestController
@RequestMapping("/api/erp/invoices")
public class FaturamentoController {

    private final InvoiceService invoiceService;
    private final InvoiceQueryService queryService;

    public FaturamentoController(InvoiceService invoiceService, InvoiceQueryService queryService) {
        this.invoiceService = invoiceService;
        this.queryService = queryService;
    }

    @PreAuthorize("hasAnyAuthority('faturamento.emitir', 'receber.ver', 'pedidos.ver_todos')")
    @GetMapping
    public PageResponse<InvoiceSummary> list(@RequestParam(required = false) Invoice.Status status,
                                             @RequestParam(required = false) Long customerId,
                                             @RequestParam(required = false) String search,
                                             @RequestParam(defaultValue = "0") @Min(0) int page,
                                             @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return PageResponse.from(queryService.search(status, customerId, search, page, size));
    }

    @PreAuthorize("hasAnyAuthority('faturamento.emitir', 'receber.ver', 'pedidos.ver_todos')")
    @GetMapping("/totals")
    public InvoiceTotals totals() {
        return queryService.totals();
    }

    @PreAuthorize("hasAnyAuthority('faturamento.emitir', 'receber.ver', 'pedidos.ver')")
    @GetMapping("/{id}")
    public InvoiceView view(@PathVariable Long id) {
        return queryService.view(id);
    }

    @PreAuthorize("hasAuthority('faturamento.emitir')")
    @PostMapping
    @Transactional
    public ResponseEntity<InvoiceView> issue(@RequestBody Map<String, Long> body) {
        return ResponseEntity.status(201).body(queryService.toView(invoiceService.issue(body.get("pickingId"))));
    }

    @PreAuthorize("hasAuthority('faturamento.cancelar')")
    @PostMapping("/{id}/cancel")
    @Transactional
    public InvoiceView cancel(@PathVariable Long id, @Valid @RequestBody ReasonRequest request) {
        return queryService.toView(invoiceService.cancel(id, request.reason()));
    }
}

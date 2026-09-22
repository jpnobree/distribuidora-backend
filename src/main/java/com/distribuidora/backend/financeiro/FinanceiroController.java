package com.distribuidora.backend.financeiro;

import com.distribuidora.backend.dto.PageResponse;
import com.distribuidora.backend.financeiro.FinanceDtos.ReasonRequest;
import com.distribuidora.backend.financeiro.FinanceDtos.ReceivableSummary;
import com.distribuidora.backend.financeiro.FinanceDtos.ReceivableView;
import com.distribuidora.backend.financeiro.FinanceDtos.ReceivablesTotals;
import com.distribuidora.backend.financeiro.FinanceDtos.ReceiveRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "Financeiro")
@Validated
@RestController
@RequestMapping("/api/erp/receivables")
public class FinanceiroController {

    private final ReceivableService receivableService;
    private final ReceivableQueryService queryService;

    public FinanceiroController(ReceivableService receivableService, ReceivableQueryService queryService) {
        this.receivableService = receivableService;
        this.queryService = queryService;
    }

    @PreAuthorize("hasAuthority('receber.ver')")
    @GetMapping
    public PageResponse<ReceivableSummary> list(
            @RequestParam(required = false) Receivable.Status status,
            @RequestParam(required = false) Long customerId,
            @RequestParam(defaultValue = "false") boolean openOnly,
            @RequestParam(defaultValue = "false") boolean overdueOnly,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return PageResponse.from(queryService.search(status, customerId, openOnly, overdueOnly, dueFrom, dueTo,
                search, page, size));
    }

    @PreAuthorize("hasAuthority('receber.ver')")
    @GetMapping("/totals")
    public ReceivablesTotals totals(@RequestParam(required = false) Long customerId) {
        return queryService.totals(customerId);
    }

    @PreAuthorize("hasAuthority('receber.ver')")
    @GetMapping("/{id}")
    public ReceivableView view(@PathVariable Long id) {
        return queryService.view(id);
    }

    @PreAuthorize("hasAuthority('receber.baixar')")
    @PostMapping("/{id}/receive")
    @Transactional
    public ReceivableView receive(@PathVariable Long id, @Valid @RequestBody ReceiveRequest request) {
        return queryService.toView(receivableService.receive(id, request));
    }

    @PreAuthorize("hasAuthority('receber.baixar')")
    @PostMapping("/transactions/{transactionId}/reverse")
    @Transactional
    public ReceivableView reverse(@PathVariable Long transactionId, @Valid @RequestBody ReasonRequest request) {
        return queryService.toView(receivableService.reverse(transactionId, request.reason()));
    }

    @PreAuthorize("hasAuthority('receber.cancelar')")
    @PostMapping("/{id}/cancel")
    @Transactional
    public ReceivableView cancel(@PathVariable Long id, @Valid @RequestBody ReasonRequest request) {
        return queryService.toView(receivableService.cancel(id, request.reason()));
    }
}

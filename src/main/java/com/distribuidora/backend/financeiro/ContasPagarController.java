package com.distribuidora.backend.financeiro;

import com.distribuidora.backend.dto.PageResponse;
import com.distribuidora.backend.financeiro.FinanceDtos.PayableSummary;
import com.distribuidora.backend.financeiro.FinanceDtos.PayableView;
import com.distribuidora.backend.financeiro.FinanceDtos.PayablesTotals;
import com.distribuidora.backend.financeiro.FinanceDtos.ReasonRequest;
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
@RequestMapping("/api/erp/payables")
public class ContasPagarController {

    private final PayableService payableService;
    private final PayableQueryService queryService;

    public ContasPagarController(PayableService payableService, PayableQueryService queryService) {
        this.payableService = payableService;
        this.queryService = queryService;
    }

    @PreAuthorize("hasAuthority('pagar.ver')")
    @GetMapping
    public PageResponse<PayableSummary> list(
            @RequestParam(required = false) Payable.Status status,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(defaultValue = "false") boolean openOnly,
            @RequestParam(defaultValue = "false") boolean overdueOnly,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return PageResponse.from(queryService.search(status, supplierId, openOnly, overdueOnly, dueFrom, dueTo,
                search, page, size));
    }

    @PreAuthorize("hasAuthority('pagar.ver')")
    @GetMapping("/totals")
    public PayablesTotals totals(@RequestParam(required = false) Long supplierId) {
        return queryService.totals(supplierId);
    }

    @PreAuthorize("hasAuthority('pagar.ver')")
    @GetMapping("/{id}")
    public PayableView view(@PathVariable Long id) {
        return queryService.view(id);
    }

    @PreAuthorize("hasAuthority('pagar.baixar')")
    @PostMapping("/{id}/pay")
    @Transactional
    public PayableView pay(@PathVariable Long id, @Valid @RequestBody ReceiveRequest request) {
        return queryService.toView(payableService.pay(id, request));
    }

    @PreAuthorize("hasAuthority('pagar.baixar')")
    @PostMapping("/transactions/{transactionId}/reverse")
    @Transactional
    public PayableView reverse(@PathVariable Long transactionId, @Valid @RequestBody ReasonRequest request) {
        return queryService.toView(payableService.reverse(transactionId, request.reason()));
    }

    @PreAuthorize("hasAuthority('pagar.cancelar')")
    @PostMapping("/{id}/cancel")
    @Transactional
    public PayableView cancel(@PathVariable Long id, @Valid @RequestBody ReasonRequest request) {
        return queryService.toView(payableService.cancel(id, request.reason()));
    }
}

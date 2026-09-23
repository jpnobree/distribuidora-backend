package com.distribuidora.backend.compras;

import com.distribuidora.backend.compras.PurchaseDtos.PurchaseOrderRequest;
import com.distribuidora.backend.compras.PurchaseDtos.PurchaseOrderSummary;
import com.distribuidora.backend.compras.PurchaseDtos.PurchaseOrderView;
import com.distribuidora.backend.compras.PurchaseDtos.ReasonRequest;
import com.distribuidora.backend.compras.PurchaseDtos.ReceiptRequest;
import com.distribuidora.backend.compras.PurchaseDtos.SuggestionResponse;
import com.distribuidora.backend.dto.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Compras")
@Validated
@RestController
@RequestMapping("/api/erp/purchases")
public class ComprasController {

    private final PurchaseOrderService orderService;
    private final PurchaseQueryService queryService;
    private final PurchaseSuggestionService suggestionService;
    private final GoodsReceiptService receiptService;

    public ComprasController(PurchaseOrderService orderService, PurchaseQueryService queryService,
                             PurchaseSuggestionService suggestionService, GoodsReceiptService receiptService) {
        this.orderService = orderService;
        this.queryService = queryService;
        this.suggestionService = suggestionService;
        this.receiptService = receiptService;
    }

    @PreAuthorize("hasAuthority('compras.ver')")
    @GetMapping("/suggestion")
    public SuggestionResponse suggestion(@RequestParam(required = false) @Min(7) @Max(365) Integer salesWindowDays,
                                         @RequestParam(required = false) Long supplierId) {
        return suggestionService.suggest(salesWindowDays, supplierId);
    }

    @PreAuthorize("hasAuthority('compras.ver')")
    @GetMapping
    public PageResponse<PurchaseOrderSummary> list(@RequestParam(required = false) PurchaseOrder.Status status,
                                                   @RequestParam(required = false) Long supplierId,
                                                   @RequestParam(required = false) String search,
                                                   @RequestParam(defaultValue = "0") @Min(0) int page,
                                                   @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return PageResponse.from(queryService.search(status, supplierId, search, page, size));
    }

    @PreAuthorize("hasAuthority('compras.ver')")
    @GetMapping("/{id}")
    public PurchaseOrderView view(@PathVariable Long id) {
        return queryService.view(id);
    }

    @PreAuthorize("hasAuthority('compras.criar')")
    @PostMapping
    @Transactional
    public ResponseEntity<PurchaseOrderView> create(@Valid @RequestBody PurchaseOrderRequest request) {
        return ResponseEntity.status(201).body(queryService.toView(orderService.create(request)));
    }

    @PreAuthorize("hasAuthority('compras.aprovar')")
    @PostMapping("/{id}/approve")
    @Transactional
    public PurchaseOrderView approve(@PathVariable Long id) {
        return queryService.toView(orderService.approve(id));
    }

    @PreAuthorize("hasAnyAuthority('compras.criar', 'compras.aprovar')")
    @PostMapping("/{id}/cancel")
    @Transactional
    public PurchaseOrderView cancel(@PathVariable Long id, @Valid @RequestBody ReasonRequest request) {
        return queryService.toView(orderService.cancel(id, request.reason()));
    }

    @PreAuthorize("hasAuthority('compras.receber')")
    @PostMapping("/receipts")
    @Transactional
    public ResponseEntity<PurchaseOrderView> receive(@Valid @RequestBody ReceiptRequest request) {
        receiptService.receive(request);
        return ResponseEntity.status(201).body(queryService.view(request.purchaseOrderId()));
    }
}

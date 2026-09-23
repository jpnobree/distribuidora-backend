package com.distribuidora.backend.comercial;

import com.distribuidora.backend.comercial.LeadService.ImportRequest;
import com.distribuidora.backend.comercial.LeadService.ImportResult;
import com.distribuidora.backend.comercial.LeadService.LeadFields;
import com.distribuidora.backend.comercial.LeadService.LeadUpdate;
import com.distribuidora.backend.comercial.LeadService.LeadView;
import com.distribuidora.backend.comercial.SalesDtos.*;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Tag(name = "Comercial")
@Validated
@RestController
@RequestMapping("/api/erp")
public class SalesController {

    private final SalesOrderService orderService;
    private final SalesQueryService queryService;
    private final PriceTableService priceTableService;
    private final PricingService pricingService;
    private final LeadService leadService;

    public SalesController(SalesOrderService orderService, SalesQueryService queryService,
                           PriceTableService priceTableService, PricingService pricingService,
                           LeadService leadService) {
        this.orderService = orderService;
        this.queryService = queryService;
        this.priceTableService = priceTableService;
        this.pricingService = pricingService;
        this.leadService = leadService;
    }

    // ------------------------------------------------------------ pedidos

    @PreAuthorize("hasAuthority('pedidos.ver')")
    @GetMapping("/orders")
    public PageResponse<OrderSummary> orders(
            @RequestParam(required = false) SalesOrder.Status status,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean pendingOnly,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return PageResponse.from(queryService.search(status, customerId, sellerId, search, pendingOnly, page, size));
    }

    @PreAuthorize("hasAuthority('pedidos.ver')")
    @GetMapping("/orders/{id}")
    public OrderView order(@PathVariable Long id) {
        return queryService.view(id);
    }

    @PreAuthorize("hasAuthority('pedidos.criar')")
    @PostMapping("/orders")
    @Transactional
    public ResponseEntity<OrderView> create(@Valid @RequestBody OrderRequest request) {
        return ResponseEntity.status(201).body(queryService.toView(orderService.create(request)));
    }

    @PreAuthorize("hasAnyAuthority('pedidos.aprovar_desconto', 'credito.liberar')")
    @PostMapping("/orders/{id}/approve")
    @Transactional
    public OrderView approve(@PathVariable Long id) {
        return queryService.toView(orderService.approve(id));
    }

    @PreAuthorize("hasAuthority('pedidos.ver')")
    @PostMapping("/orders/{id}/cancel")
    @Transactional
    public OrderView cancel(@PathVariable Long id, @Valid @RequestBody CancelRequest request) {
        return queryService.toView(orderService.cancel(id, request.reason()));
    }

    @PreAuthorize("hasAuthority('pedidos.criar')")
    @GetMapping("/sales/quote")
    public Quote quote(@RequestParam Long customerId, @RequestParam Long productId) {
        return queryService.quote(customerId, productId);
    }

    @PreAuthorize("hasAuthority('pedidos.criar')")
    @GetMapping("/sales/customers/{customerId}/credit")
    public CreditView credit(@PathVariable Long customerId) {
        return queryService.credit(customerId);
    }

    // ------------------------------------------------------------ precos

    @PreAuthorize("hasAnyAuthority('precos.gerenciar', 'clientes.ver')")
    @GetMapping("/price-tables")
    public List<PriceTableView> priceTables() {
        return priceTableService.list();
    }

    @PreAuthorize("hasAuthority('precos.gerenciar')")
    @PostMapping("/price-tables")
    public PriceTableView createPriceTable(@Valid @RequestBody PriceTableRequest request) {
        return priceTableService.save(null, request.name(), request.active());
    }

    @PreAuthorize("hasAuthority('precos.gerenciar')")
    @PutMapping("/price-tables/{id}")
    public PriceTableView updatePriceTable(@PathVariable Long id, @Valid @RequestBody PriceTableRequest request) {
        return priceTableService.save(id, request.name(), request.active());
    }

    @PreAuthorize("hasAuthority('precos.gerenciar')")
    @GetMapping("/price-tables/{id}/items")
    public List<PriceTableItemView> priceTableItems(@PathVariable Long id) {
        return priceTableService.items(id);
    }

    @PreAuthorize("hasAuthority('precos.gerenciar')")
    @PutMapping("/price-tables/{id}/items")
    public List<PriceTableItemView> updatePriceTableItems(@PathVariable Long id,
                                                          @Valid @RequestBody PriceItemsRequest request) {
        return priceTableService.updateItems(id, request.items(), request.removeProductIds());
    }

    @PreAuthorize("hasAnyAuthority('precos.gerenciar', 'pedidos.criar')")
    @GetMapping("/sales/parameters")
    public Map<String, BigDecimal> parameters() {
        return Map.of("maxDiscountPercent", pricingService.maxDiscountPercent());
    }

    @PreAuthorize("hasAuthority('precos.gerenciar')")
    @PutMapping("/sales/parameters")
    public Map<String, BigDecimal> updateParameters(@Valid @RequestBody DiscountParameterRequest request) {
        return Map.of("maxDiscountPercent", priceTableService.updateMaxDiscount(request.maxDiscountPercent()));
    }

    // ------------------------------------------------------------ leads

    @PreAuthorize("hasAuthority('leads.ver')")
    @GetMapping("/leads")
    public PageResponse<LeadView> leads(@RequestParam(required = false) String search,
                                        @RequestParam(required = false) Lead.Status status,
                                        @RequestParam(defaultValue = "0") @Min(0) int page,
                                        @RequestParam(defaultValue = "50") @Min(1) @Max(500) int size) {
        return PageResponse.from(leadService.search(search, status, page, size));
    }

    @PreAuthorize("hasAuthority('leads.gerenciar')")
    @PostMapping("/leads")
    public ResponseEntity<LeadView> createLead(@Valid @RequestBody LeadFields fields) {
        return ResponseEntity.status(201).body(leadService.create(fields));
    }

    @PreAuthorize("hasAuthority('leads.gerenciar')")
    @PostMapping("/leads/import")
    public ImportResult importLeads(@Valid @RequestBody ImportRequest request) {
        return leadService.importFromMap(request.places());
    }

    @PreAuthorize("hasAuthority('leads.ver')")
    @PostMapping("/leads/known")
    public Set<String> knownPlaces(@RequestBody List<String> externalIds) {
        return leadService.existingExternalIds(externalIds);
    }

    @PreAuthorize("hasAuthority('leads.gerenciar')")
    @PutMapping("/leads/{id}")
    public LeadView updateLead(@PathVariable Long id, @Valid @RequestBody LeadUpdate update) {
        return leadService.update(id, update);
    }

    @PreAuthorize("hasAuthority('leads.gerenciar')")
    @PostMapping("/leads/{id}/converted")
    public LeadView converted(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        return leadService.markConverted(id, body.get("customerId"));
    }
}

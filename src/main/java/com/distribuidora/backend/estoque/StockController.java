package com.distribuidora.backend.estoque;

import com.distribuidora.backend.dto.PageResponse;
import com.distribuidora.backend.estoque.StockDtos.*;
import com.distribuidora.backend.estoque.StockEnums.LossReason;
import com.distribuidora.backend.estoque.StockEnums.MovementType;
import com.distribuidora.backend.model.Product;
import com.distribuidora.backend.repository.ProductRepository;
import com.distribuidora.backend.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Tag(name = "Estoque")
@Validated
@RestController
@RequestMapping("/api/erp/stock")
public class StockController {

    private static final String CONSULTAR = "hasAuthority('estoque.consultar')";

    private final StockService stockService;
    private final StockQueryService queryService;
    private final InventoryService inventoryService;
    private final InventoryCountRepository inventoryRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final LotRepository lotRepository;
    private final CurrentUser currentUser;

    public StockController(StockService stockService, StockQueryService queryService,
                           InventoryService inventoryService, InventoryCountRepository inventoryRepository,
                           WarehouseRepository warehouseRepository, ProductRepository productRepository,
                           LotRepository lotRepository, CurrentUser currentUser) {
        this.stockService = stockService;
        this.queryService = queryService;
        this.inventoryService = inventoryService;
        this.inventoryRepository = inventoryRepository;
        this.warehouseRepository = warehouseRepository;
        this.productRepository = productRepository;
        this.lotRepository = lotRepository;
        this.currentUser = currentUser;
    }

    // ------------------------------------------------------------ consultas

    @PreAuthorize(CONSULTAR)
    @GetMapping("/warehouses")
    public List<WarehouseResponse> warehouses() {
        return warehouseRepository.findAllByOrderByNameAsc().stream()
                .map(w -> new WarehouseResponse(w.getId(), w.getCode(), w.getName(), w.isActive())).toList();
    }

    @PreAuthorize(CONSULTAR)
    @GetMapping("/summary")
    public Summary summary(@RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {
        return queryService.summary(days);
    }

    @PreAuthorize(CONSULTAR)
    @GetMapping("/position")
    public PageResponse<PositionRow> position(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String situation,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return PageResponse.from(queryService.position(search, category, warehouseId, situation, PageRequest.of(page, size)));
    }

    @PreAuthorize(CONSULTAR)
    @GetMapping("/products/{productId}/balances")
    public List<BalanceDetail> balances(@PathVariable Long productId) {
        return queryService.productBalances(productId);
    }

    @PreAuthorize(CONSULTAR)
    @GetMapping("/expiring")
    public List<ExpiringLot> expiring(@RequestParam(defaultValue = "30") @Min(0) @Max(365) int days,
                                      @RequestParam(required = false) Long warehouseId) {
        return queryService.expiringLots(days, warehouseId);
    }

    @PreAuthorize(CONSULTAR)
    @GetMapping("/movements")
    public PageResponse<MovementResponse> movements(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long lotId,
            @RequestParam(required = false) MovementType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return PageResponse.from(queryService.movements(productId, warehouseId, lotId, type, from, to, page, size));
    }

    @PreAuthorize(CONSULTAR)
    @GetMapping("/fefo")
    public FefoResponse fefo(@RequestParam Long productId, @RequestParam BigDecimal quantity,
                             @RequestParam(required = false) Long warehouseId) {
        return stockService.fefo(productId, quantity, warehouseId);
    }

    @PreAuthorize(CONSULTAR)
    @GetMapping("/options")
    public Map<String, Object> options() {
        return Map.of(
                "movementTypes", Arrays.stream(MovementType.values())
                        .map(t -> Map.of("code", t.name(), "label", t.getLabel())).toList(),
                "lossReasons", Arrays.stream(LossReason.values())
                        .map(r -> Map.of("code", r.name(), "label", r.getLabel())).toList());
    }

    // ------------------------------------------------------------ operacoes

    @PreAuthorize("hasAuthority('estoque.movimentar')")
    @PostMapping("/entries")
    public ResponseEntity<Map<String, Long>> entry(@Valid @RequestBody EntryRequest request) {
        return created(stockService.entry(request));
    }

    @PreAuthorize("hasAuthority('estoque.ajustar')")
    @PostMapping("/exits")
    public ResponseEntity<Map<String, Long>> exit(@Valid @RequestBody ExitRequest request) {
        return created(stockService.exit(request));
    }

    @PreAuthorize("hasAuthority('estoque.perdas')")
    @PostMapping("/losses")
    public ResponseEntity<Map<String, Long>> loss(@Valid @RequestBody LossRequest request) {
        return created(stockService.loss(request));
    }

    @PreAuthorize("hasAuthority('estoque.bloquear')")
    @PostMapping("/blocks")
    public ResponseEntity<Map<String, Long>> block(@Valid @RequestBody BlockRequest request) {
        return created(stockService.block(request));
    }

    @PreAuthorize("hasAuthority('estoque.movimentar')")
    @PostMapping("/transfers")
    public ResponseEntity<Map<String, Long>> transfer(@Valid @RequestBody TransferRequest request) {
        return created(stockService.transfer(request).get(0));
    }

    private static ResponseEntity<Map<String, Long>> created(StockMovement movement) {
        return ResponseEntity.status(201).body(Map.of("movementId", movement.getId()));
    }

    // ------------------------------------------------------------ inventario

    @PreAuthorize(CONSULTAR)
    @GetMapping("/inventories")
    @Transactional(readOnly = true)
    public List<InventoryResponse> inventories() {
        return inventoryRepository.findTop50ByOrderByCreatedAtDesc().stream().map(c -> describe(c, false)).toList();
    }

    @PreAuthorize(CONSULTAR)
    @GetMapping("/inventories/{id}")
    @Transactional(readOnly = true)
    public InventoryResponse inventory(@PathVariable Long id) {
        return describe(inventoryRepository.findById(id)
                .orElseThrow(() -> new com.distribuidora.backend.exception.ResourceNotFoundException(
                        "Inventario nao encontrado: " + id)), true);
    }

    @PreAuthorize("hasAuthority('estoque.ajustar')")
    @PostMapping("/inventories")
    @Transactional
    public ResponseEntity<InventoryResponse> openInventory(@Valid @RequestBody InventoryCreateRequest request) {
        return ResponseEntity.status(201)
                .body(describe(inventoryService.open(request.warehouseId(), request.category(), request.notes()), true));
    }

    // Contar pode ser feito pelo estoquista; fechar (gerar ajustes) exige "ajustar".
    @PreAuthorize("hasAnyAuthority('estoque.movimentar', 'estoque.ajustar')")
    @PutMapping("/inventories/{id}/counts")
    @Transactional
    public InventoryResponse count(@PathVariable Long id, @Valid @RequestBody CountUpdateRequest request) {
        return describe(inventoryService.updateCounts(id, request.entries()), true);
    }

    @PreAuthorize("hasAuthority('estoque.ajustar')")
    @PostMapping("/inventories/{id}/close")
    @Transactional
    public InventoryResponse close(@PathVariable Long id) {
        return describe(inventoryService.close(id), true);
    }

    @PreAuthorize("hasAuthority('estoque.ajustar')")
    @PostMapping("/inventories/{id}/cancel")
    @Transactional
    public InventoryResponse cancel(@PathVariable Long id) {
        return describe(inventoryService.cancel(id), true);
    }

    private InventoryResponse describe(InventoryCount count, boolean withItems) {
        String warehouseName = warehouseRepository.findById(count.getWarehouseId()).map(Warehouse::getName).orElse(null);
        List<InventoryCount.Item> items = count.getItems();
        int counted = (int) items.stream().filter(i -> i.getCountedQty() != null).count();
        List<InventoryItemResponse> itemResponses = List.of();
        if (withItems) {
            Map<Long, Product> products = productRepository.findAllById(
                    items.stream().map(InventoryCount.Item::getProductId).distinct().toList()).stream()
                    .collect(Collectors.toMap(Product::getId, Function.identity()));
            Map<Long, String> lots = lotRepository.findAllById(
                    items.stream().map(InventoryCount.Item::getLotId).filter(Objects::nonNull).toList()).stream()
                    .collect(Collectors.toMap(Lot::getId, Lot::getCode));
            boolean seesCost = currentUser.can(StockQueryService.CUSTO_VER);
            itemResponses = items.stream()
                    .sorted((a, b) -> products.get(a.getProductId()).getName()
                            .compareToIgnoreCase(products.get(b.getProductId()).getName()))
                    .map(i -> {
                        Product p = products.get(i.getProductId());
                        BigDecimal diff = i.getCountedQty() == null ? null : i.getCountedQty().subtract(i.getSystemQty());
                        BigDecimal value = diff == null || !seesCost || p.getAverageCost() == null ? null
                                : diff.multiply(p.getAverageCost()).setScale(2, RoundingMode.HALF_UP);
                        return new InventoryItemResponse(i.getId(), p.getId(), p.getName(), p.getSku(),
                                p.getBaseUnit(), i.getLotId(), lots.get(i.getLotId()), i.getSystemQty(),
                                i.getCountedQty(), diff, value);
                    })
                    .toList();
        }
        return new InventoryResponse(count.getId(), count.getWarehouseId(), warehouseName, count.getCategory(),
                count.getStatus(), count.getNotes(), count.getCreatedBy(), count.getCreatedAt(), count.getClosedBy(),
                count.getClosedAt(), items.size(), counted, itemResponses);
    }
}

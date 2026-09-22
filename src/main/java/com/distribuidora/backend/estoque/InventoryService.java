package com.distribuidora.backend.estoque;

import com.distribuidora.backend.audit.AuditAction;
import com.distribuidora.backend.audit.AuditService;
import com.distribuidora.backend.estoque.StockDtos.CountEntry;
import com.distribuidora.backend.estoque.StockEnums.MovementType;
import com.distribuidora.backend.exception.BusinessRuleException;
import com.distribuidora.backend.exception.ResourceNotFoundException;
import com.distribuidora.backend.model.Product;
import com.distribuidora.backend.repository.ProductRepository;
import com.distribuidora.backend.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// Inventario: abre (congela o deposito e fotografa o saldo), recebe a
// contagem e, ao fechar, gera um movimento de ajuste por diferenca.
@Service
public class InventoryService {

    private final InventoryCountRepository inventoryRepository;
    private final StockBalanceRepository balanceRepository;
    private final ProductRepository productRepository;
    private final LotRepository lotRepository;
    private final StockService stockService;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    public InventoryService(InventoryCountRepository inventoryRepository, StockBalanceRepository balanceRepository,
                            ProductRepository productRepository, LotRepository lotRepository,
                            StockService stockService, CurrentUser currentUser, AuditService auditService) {
        this.inventoryRepository = inventoryRepository;
        this.balanceRepository = balanceRepository;
        this.productRepository = productRepository;
        this.lotRepository = lotRepository;
        this.stockService = stockService;
        this.currentUser = currentUser;
        this.auditService = auditService;
    }

    @Transactional
    public InventoryCount open(Long warehouseId, String category, String notes) {
        stockService.activeWarehouse(warehouseId);
        stockService.ensureNoOpenInventory(warehouseId);
        String scope = category == null || category.isBlank() ? null : category;
        Map<Long, Product> products = productRepository.findAll().stream()
                .filter(p -> scope == null || scope.equals(p.getCategory()))
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        InventoryCount count = new InventoryCount(warehouseId, scope, notes, currentUser.username());
        Set<Long> withBalance = new HashSet<>();
        balanceRepository.findByWarehouseId(warehouseId).stream()
                .filter(b -> products.containsKey(b.getProductId()) && b.getQtyPhysical().signum() > 0)
                .forEach(b -> {
                    count.addItem(b.getProductId(), b.getLotId(), b.getQtyPhysical());
                    withBalance.add(b.getProductId());
                });
        // Produto ativo sem lote e sem saldo tambem e contado: pode haver sobra.
        products.values().stream()
                .filter(p -> p.isActive() && !p.isLotControl() && !withBalance.contains(p.getId()))
                .forEach(p -> count.addItem(p.getId(), null, BigDecimal.ZERO));
        if (count.getItems().isEmpty()) {
            throw new BusinessRuleException("Nada para inventariar neste deposito/categoria.");
        }
        return inventoryRepository.save(count);
    }

    @Transactional
    public InventoryCount updateCounts(Long id, List<CountEntry> entries) {
        InventoryCount count = openCount(id);
        Map<Long, InventoryCount.Item> items = count.getItems().stream()
                .collect(Collectors.toMap(InventoryCount.Item::getId, Function.identity()));
        for (CountEntry entry : entries) {
            InventoryCount.Item item = items.get(entry.itemId());
            if (item == null) {
                throw new BusinessRuleException("Item " + entry.itemId() + " nao pertence a este inventario.");
            }
            item.setCountedQty(entry.countedQty());
        }
        return count;
    }

    @Transactional
    public InventoryCount close(Long id) {
        InventoryCount count = openCount(id);
        long missing = count.getItems().stream().filter(i -> i.getCountedQty() == null).count();
        if (missing > 0) {
            throw new BusinessRuleException("Faltam " + missing + " item(ns) sem contagem. Informe 0 se nao houver.");
        }
        Map<Long, Product> products = productRepository.findAllById(
                count.getItems().stream().map(InventoryCount.Item::getProductId).toList()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, Lot> lots = lotRepository.findAllById(
                count.getItems().stream().map(InventoryCount.Item::getLotId).filter(java.util.Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(Lot::getId, Function.identity()));

        int adjusted = 0;
        BigDecimal surplusValue = BigDecimal.ZERO;
        BigDecimal shortageValue = BigDecimal.ZERO;
        for (InventoryCount.Item item : count.getItems()) {
            // deposito congelado: o saldo atual e o da foto
            BigDecimal difference = item.getCountedQty().subtract(item.getSystemQty());
            if (difference.signum() == 0) {
                continue;
            }
            Product product = products.get(item.getProductId());
            Lot lot = item.getLotId() == null ? null : lots.get(item.getLotId());
            MovementType type = difference.signum() > 0
                    ? MovementType.AJUSTE_INVENTARIO_ENTRADA : MovementType.AJUSTE_INVENTARIO_SAIDA;
            stockService.apply(type, count.getWarehouseId(), product, lot, difference.abs(), product.getAverageCost(),
                    null, "Inventario #" + count.getId(), null, "INVENTARIO", count.getId(), null);
            adjusted++;
            BigDecimal value = product.getAverageCost() == null ? BigDecimal.ZERO
                    : difference.abs().multiply(product.getAverageCost());
            if (difference.signum() > 0) {
                surplusValue = surplusValue.add(value);
            } else {
                shortageValue = shortageValue.add(value);
            }
        }
        count.close(currentUser.username());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("items", count.getItems().size());
        summary.put("adjustedItems", adjusted);
        summary.put("surplusValue", surplusValue.setScale(2, java.math.RoundingMode.HALF_UP));
        summary.put("shortageValue", shortageValue.setScale(2, java.math.RoundingMode.HALF_UP));
        auditService.recordChange(AuditAction.ESTOQUE_INVENTARIO_FECHADO, "InventoryCount", count.getId(), null,
                summary, null);
        return count;
    }

    @Transactional
    public InventoryCount cancel(Long id) {
        InventoryCount count = openCount(id);
        count.cancel(currentUser.username());
        return count;
    }

    private InventoryCount openCount(Long id) {
        InventoryCount count = inventoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventario nao encontrado: " + id));
        if (count.getStatus() != InventoryCount.Status.ABERTO) {
            throw new BusinessRuleException("Este inventario ja foi " + count.getStatus().name().toLowerCase() + ".");
        }
        return count;
    }
}

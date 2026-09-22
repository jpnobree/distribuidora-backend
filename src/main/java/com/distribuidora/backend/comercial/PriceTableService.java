package com.distribuidora.backend.comercial;

import com.distribuidora.backend.audit.AuditAction;
import com.distribuidora.backend.audit.AuditService;
import com.distribuidora.backend.comercial.SalesDtos.PriceItemRequest;
import com.distribuidora.backend.comercial.SalesDtos.PriceTableItemView;
import com.distribuidora.backend.comercial.SalesDtos.PriceTableView;
import com.distribuidora.backend.exception.BusinessRuleException;
import com.distribuidora.backend.exception.ConflictException;
import com.distribuidora.backend.exception.ResourceNotFoundException;
import com.distribuidora.backend.model.Product;
import com.distribuidora.backend.repository.ProductRepository;
import com.distribuidora.backend.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PriceTableService {

    private final PriceTableRepository tableRepository;
    private final SystemParameterRepository parameterRepository;
    private final ProductRepository productRepository;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    public PriceTableService(PriceTableRepository tableRepository, SystemParameterRepository parameterRepository,
                             ProductRepository productRepository, CurrentUser currentUser, AuditService auditService) {
        this.tableRepository = tableRepository;
        this.parameterRepository = parameterRepository;
        this.productRepository = productRepository;
        this.currentUser = currentUser;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<PriceTableView> list() {
        return tableRepository.findAllByOrderByNameAsc().stream()
                .map(t -> new PriceTableView(t.getId(), t.getName(), t.isActive(), t.getPrices().size())).toList();
    }

    @Transactional
    public PriceTableView save(Long id, String name, boolean active) {
        String trimmed = name.trim();
        PriceTable table = id == null ? null : get(id);
        if ((table == null || !table.getName().equalsIgnoreCase(trimmed)) && tableRepository.existsByNameIgnoreCase(trimmed)) {
            throw new ConflictException("Ja existe a tabela " + trimmed);
        }
        if (table == null) {
            table = tableRepository.save(new PriceTable(trimmed));
        }
        table.setName(trimmed);
        table.setActive(active);
        auditService.recordChange(AuditAction.TABELA_PRECO_ALTERADA, "PriceTable", table.getId(), null,
                Map.of("name", trimmed, "active", active), null);
        return new PriceTableView(table.getId(), table.getName(), table.isActive(), table.getPrices().size());
    }

    @Transactional(readOnly = true)
    public List<PriceTableItemView> items(Long id) {
        PriceTable table = get(id);
        Map<Long, Product> products = productRepository.findAllById(table.getPrices().keySet()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        boolean seesCost = currentUser.can("produtos.custo.ver");
        return table.getPrices().entrySet().stream()
                .map(e -> {
                    Product p = products.get(e.getKey());
                    return new PriceTableItemView(p.getId(), p.getName(), p.getSku(), p.getBaseUnit(), p.getPrice(),
                            e.getValue(), seesCost ? p.getAverageCost() : null);
                })
                .sorted(Comparator.comparing(PriceTableItemView::productName))
                .toList();
    }

    // Grava so as diferencas na auditoria: preco por produto antes e depois.
    @Transactional
    public List<PriceTableItemView> updateItems(Long id, List<PriceItemRequest> items, List<Long> remove) {
        PriceTable table = get(id);
        Map<Long, BigDecimal> prices = table.getPrices();
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();

        List<Long> ids = items.stream().map(PriceItemRequest::productId).toList();
        Map<Long, Product> products = productRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        for (PriceItemRequest item : items) {
            Product product = products.get(item.productId());
            if (product == null) {
                throw new BusinessRuleException("Produto inexistente: " + item.productId());
            }
            String key = product.getSku();
            before.put(key, prices.get(product.getId()));
            after.put(key, item.price());
            prices.put(product.getId(), item.price());
        }
        if (remove != null) {
            for (Long productId : remove) {
                if (prices.containsKey(productId)) {
                    String key = productRepository.findById(productId).map(Product::getSku).orElse(String.valueOf(productId));
                    before.put(key, prices.remove(productId));
                    after.put(key, null);
                }
            }
        }
        auditService.recordChange(AuditAction.TABELA_PRECO_ALTERADA, "PriceTable", table.getName(), before, after, null);
        return items(id);
    }

    @Transactional
    public BigDecimal updateMaxDiscount(BigDecimal percent) {
        SystemParameter parameter = parameterRepository.findById(SystemParameter.MAX_DISCOUNT).orElseThrow();
        BigDecimal previous = new BigDecimal(parameter.getValue());
        parameter.setValue(percent.stripTrailingZeros().toPlainString());
        auditService.recordChange(AuditAction.PARAMETRO_ALTERADO, "SystemParameter", SystemParameter.MAX_DISCOUNT,
                Map.of("value", previous), Map.of("value", percent), null);
        return percent;
    }

    private PriceTable get(Long id) {
        return tableRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tabela de preco nao encontrada: " + id));
    }
}

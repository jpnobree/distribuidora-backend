package com.distribuidora.backend.compras;

import com.distribuidora.backend.cadastros.PaymentTerm;
import com.distribuidora.backend.cadastros.PaymentTermRepository;
import com.distribuidora.backend.cadastros.Supplier;
import com.distribuidora.backend.cadastros.SupplierRepository;
import com.distribuidora.backend.compras.PurchaseDtos.PurchaseItemView;
import com.distribuidora.backend.compras.PurchaseDtos.PurchaseOrderSummary;
import com.distribuidora.backend.compras.PurchaseDtos.PurchaseOrderView;
import com.distribuidora.backend.compras.PurchaseDtos.ReceiptItemView;
import com.distribuidora.backend.compras.PurchaseDtos.ReceiptView;
import com.distribuidora.backend.estoque.Lot;
import com.distribuidora.backend.estoque.LotRepository;
import com.distribuidora.backend.estoque.Warehouse;
import com.distribuidora.backend.estoque.WarehouseRepository;
import com.distribuidora.backend.financeiro.Payable;
import com.distribuidora.backend.financeiro.PayableRepository;
import com.distribuidora.backend.model.Product;
import com.distribuidora.backend.repository.ProductRepository;
import com.distribuidora.backend.security.CurrentUser;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PurchaseQueryService {

    private final PurchaseOrderRepository orderRepository;
    private final PurchaseOrderService orderService;
    private final GoodsReceiptRepository receiptRepository;
    private final SupplierRepository supplierRepository;
    private final PaymentTermRepository paymentTermRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final LotRepository lotRepository;
    private final PayableRepository payableRepository;
    private final CurrentUser currentUser;

    public PurchaseQueryService(PurchaseOrderRepository orderRepository, PurchaseOrderService orderService,
                                GoodsReceiptRepository receiptRepository, SupplierRepository supplierRepository,
                                PaymentTermRepository paymentTermRepository, ProductRepository productRepository,
                                WarehouseRepository warehouseRepository, LotRepository lotRepository,
                                PayableRepository payableRepository, CurrentUser currentUser) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.receiptRepository = receiptRepository;
        this.supplierRepository = supplierRepository;
        this.paymentTermRepository = paymentTermRepository;
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
        this.lotRepository = lotRepository;
        this.payableRepository = payableRepository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public Page<PurchaseOrderSummary> search(PurchaseOrder.Status status, Long supplierId, String search,
                                             int page, int size) {
        Specification<PurchaseOrder> spec = Specification.where(null);
        if (status != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), status));
        }
        if (supplierId != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("supplierId"), supplierId));
        }
        if (search != null && !search.isBlank()) {
            String like = "%" + search.trim().toLowerCase() + "%";
            spec = spec.and((r, q, cb) -> {
                Subquery<Long> found = q.subquery(Long.class);
                var s = found.from(Supplier.class);
                found.select(s.get("id")).where(cb.or(cb.like(cb.lower(s.get("legalName")), like),
                        cb.like(cb.lower(s.get("tradeName")), like)));
                return cb.in(r.get("supplierId")).value(found);
            });
        }
        Page<PurchaseOrder> orders = orderRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        Map<Long, Supplier> suppliers = suppliers(orders.getContent());
        return orders.map(o -> new PurchaseOrderSummary(o.getId(), o.getStatus(), o.getStatus().getLabel(),
                o.getSupplierId(), name(suppliers.get(o.getSupplierId())), o.getExpectedOn(), o.getTotal(),
                o.getItems().size(), o.getCreatedBy(), o.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    public PurchaseOrderView view(Long id) {
        return toView(orderService.load(id));
    }

    @Transactional(readOnly = true)
    public PurchaseOrderView toView(PurchaseOrder order) {
        Supplier supplier = supplierRepository.findById(order.getSupplierId()).orElse(null);
        Map<Long, Product> products = productRepository.findAllById(order.getItems().stream()
                .map(PurchaseOrderItem::getProductId).distinct().toList()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, String> warehouses = warehouseRepository.findAll().stream()
                .collect(Collectors.toMap(Warehouse::getId, Warehouse::getName));

        List<PurchaseItemView> items = order.getItems().stream().map(i -> {
            Product p = products.get(i.getProductId());
            return new PurchaseItemView(i.getId(), p.getId(), p.getName(), p.getSku(), p.getBaseUnit(),
                    i.getUnitCode(), i.getQuantity(), i.getFactor(), i.getQtyBase(), i.getUnitCost(),
                    i.getLineTotal(), i.getQtyReceived(), i.pending(), p.isLotControl(), p.isExpiryControl(),
                    p.getShelfLifeDays());
        }).toList();

        List<ReceiptView> receipts = receiptRepository.findByPurchaseOrderIdOrderByIdAsc(order.getId()).stream()
                .map(r -> toReceiptView(r, products, warehouses, supplier))
                .toList();

        return new PurchaseOrderView(order.getId(), order.getStatus(), order.getStatus().getLabel(),
                order.getSupplierId(), name(supplier), supplier == null ? null : supplier.getDocument(),
                order.getWarehouseId(), warehouses.get(order.getWarehouseId()),
                order.getPaymentTermId() == null ? null : paymentTermRepository.findById(order.getPaymentTermId())
                        .map(PaymentTerm::getName).orElse(null),
                order.getExpectedOn(), order.getNotes(), order.getTotal(), order.getCreatedBy(), order.getCreatedAt(),
                order.getApprovedBy(), order.getApprovedAt(), order.getCancelledBy(), order.getCancelledAt(),
                order.getCancelReason(), items, receipts, orderService.canApprove(order),
                orderService.canCancel(order),
                order.canReceive() && currentUser.can(PurchaseOrderService.RECEBER),
                orderService.approvalThreshold(), order.getVersion());
    }

    private ReceiptView toReceiptView(GoodsReceipt receipt, Map<Long, Product> products,
                                      Map<Long, String> warehouses, Supplier supplier) {
        Map<Long, Lot> lots = lotRepository.findAllById(receipt.getItems().stream()
                .map(GoodsReceiptItem::getLotId).filter(Objects::nonNull).toList()).stream()
                .collect(Collectors.toMap(Lot::getId, Function.identity()));
        List<ReceiptItemView> items = receipt.getItems().stream().map(i -> {
            Product p = products.get(i.getProductId());
            Lot lot = i.getLotId() == null ? null : lots.get(i.getLotId());
            return new ReceiptItemView(i.getId(), i.getProductId(), p == null ? null : p.getName(),
                    p == null ? null : p.getBaseUnit(), lot == null ? null : lot.getCode(),
                    lot == null ? null : lot.getExpiresOn(), i.getQtyBase(), i.getUnitCost(), i.getLineTotal(),
                    i.getCostDifference());
        }).toList();
        List<String> payables = payableRepository.findByReceiptIdOrderByInstallment(receipt.getId()).stream()
                .map(p -> p.getDocument() + " vence " + p.getDueDate() + " (" + p.getStatus().getLabel() + ")")
                .toList();
        return new ReceiptView(receipt.getId(), receipt.getPurchaseOrderId(), receipt.getSupplierId(),
                name(supplier), receipt.getWarehouseId(), warehouses.get(receipt.getWarehouseId()),
                receipt.getDocument(), receipt.getDocumentTotal(), receipt.getTotal(), receipt.getNotes(),
                receipt.getReceivedBy(), receipt.getReceivedAt(), items, payables);
    }

    private Map<Long, Supplier> suppliers(Collection<PurchaseOrder> orders) {
        return supplierRepository.findAllById(orders.stream().map(PurchaseOrder::getSupplierId).distinct().toList())
                .stream().collect(Collectors.toMap(Supplier::getId, Function.identity()));
    }

    private static String name(Supplier supplier) {
        return supplier == null ? null : supplier.displayName();
    }
}

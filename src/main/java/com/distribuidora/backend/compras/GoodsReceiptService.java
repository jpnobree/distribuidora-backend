package com.distribuidora.backend.compras;

import com.distribuidora.backend.audit.AuditAction;
import com.distribuidora.backend.audit.AuditService;
import com.distribuidora.backend.cadastros.PaymentTerm;
import com.distribuidora.backend.cadastros.PaymentTermRepository;
import com.distribuidora.backend.cadastros.Supplier;
import com.distribuidora.backend.cadastros.SupplierRepository;
import com.distribuidora.backend.compras.PurchaseDtos.ReceiptItemRequest;
import com.distribuidora.backend.compras.PurchaseDtos.ReceiptRequest;
import com.distribuidora.backend.estoque.StockMovement;
import com.distribuidora.backend.estoque.StockService;
import com.distribuidora.backend.exception.BusinessRuleException;
import com.distribuidora.backend.exception.ResourceNotFoundException;
import com.distribuidora.backend.financeiro.Payable;
import com.distribuidora.backend.financeiro.PayableService;
import com.distribuidora.backend.model.Product;
import com.distribuidora.backend.model.ProductSupplier;
import com.distribuidora.backend.repository.ProductRepository;
import com.distribuidora.backend.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

// Recebimento da mercadoria: uma transacao so que da entrada no estoque com
// lote e validade, recalcula o custo medio e abre os titulos a pagar.
@Service
public class GoodsReceiptService {

    private final GoodsReceiptRepository receiptRepository;
    private final PurchaseOrderRepository orderRepository;
    private final PurchaseOrderService orderService;
    private final SupplierRepository supplierRepository;
    private final PaymentTermRepository paymentTermRepository;
    private final ProductRepository productRepository;
    private final StockService stockService;
    private final PayableService payableService;
    private final CurrentUser currentUser;
    private final AuditService auditService;
    private final Clock clock;

    public GoodsReceiptService(GoodsReceiptRepository receiptRepository, PurchaseOrderRepository orderRepository,
                               PurchaseOrderService orderService, SupplierRepository supplierRepository,
                               PaymentTermRepository paymentTermRepository, ProductRepository productRepository,
                               StockService stockService, PayableService payableService, CurrentUser currentUser,
                               AuditService auditService, Clock clock) {
        this.receiptRepository = receiptRepository;
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.supplierRepository = supplierRepository;
        this.paymentTermRepository = paymentTermRepository;
        this.productRepository = productRepository;
        this.stockService = stockService;
        this.payableService = payableService;
        this.currentUser = currentUser;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public GoodsReceipt receive(ReceiptRequest request) {
        PurchaseOrder order = orderService.load(request.purchaseOrderId());
        if (!order.canReceive()) {
            throw new BusinessRuleException("Pedido " + order.getStatus().getLabel().toLowerCase()
                    + " nao recebe mercadoria.");
        }
        Supplier supplier = supplierRepository.findById(order.getSupplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Fornecedor nao encontrado."));
        Map<Long, PurchaseOrderItem> orderItems = order.getItems().stream()
                .collect(Collectors.toMap(PurchaseOrderItem::getId, Function.identity()));
        Map<Long, Product> products = productRepository.findAllById(order.getItems().stream()
                .map(PurchaseOrderItem::getProductId).distinct().toList()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        // salvo antes dos itens para que cada movimento de estoque ja aponte
        // para o recebimento que o originou
        GoodsReceipt receipt = receiptRepository.save(new GoodsReceipt(order.getId(), supplier.getId(),
                order.getWarehouseId(), blank(request.document()), request.documentTotal(), blank(request.notes()),
                currentUser.username()));

        for (ReceiptItemRequest line : request.items()) {
            if (line.qtyBase().signum() == 0) {
                continue;
            }
            if (line.qtyBase().signum() < 0) {
                throw new BusinessRuleException("Quantidade recebida nao pode ser negativa.");
            }
            PurchaseOrderItem orderItem = orderItems.get(line.purchaseOrderItemId());
            if (orderItem == null) {
                throw new BusinessRuleException("Item " + line.purchaseOrderItemId() + " nao e deste pedido.");
            }
            Product product = products.get(orderItem.getProductId());
            BigDecimal quantity = line.qtyBase().setScale(3, RoundingMode.HALF_UP);

            StockMovement movement = stockService.receivePurchase(order.getWarehouseId(), product, line.lotCode(),
                    line.manufacturedOn(), line.expiresOn(), supplier.getId(), quantity, line.unitCost(),
                    blank(request.document()), receipt.getId());
            orderItem.receive(quantity);
            updateSupplierCost(product, supplier.getId(), line.unitCost());

            BigDecimal difference = line.unitCost().subtract(orderItem.getUnitCost());
            receipt.addItem(new GoodsReceiptItem(orderItem.getId(), product.getId(), movement.getLotId(), quantity,
                    line.unitCost(), quantity.multiply(line.unitCost()).setScale(2, RoundingMode.HALF_UP),
                    difference.signum() == 0 ? null : difference));
        }
        if (receipt.getItems().isEmpty()) {
            throw new BusinessRuleException("Informe o que chegou: nenhuma quantidade foi preenchida.");
        }
        receipt.recalculate();
        receiptRepository.save(receipt);
        order.refreshReceiving();
        orderRepository.save(order);

        String document = receipt.getDocument() != null ? receipt.getDocument() : "REC-" + receipt.getId();
        List<Payable> titles = payableService.generate(receipt.getId(), supplier.getId(), document,
                "Compra " + supplier.displayName() + " (pedido " + order.getId() + ")", receipt.getTotal(),
                installmentDays(order.getPaymentTermId()), LocalDate.now(clock));

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("pedido", order.getId());
        values.put("fornecedor", supplier.displayName());
        values.put("nota", receipt.getDocument());
        values.put("total", receipt.getTotal());
        values.put("itens", receipt.getItems().size());
        values.put("titulos", titles.stream().map(t -> t.getDocument() + " vence " + t.getDueDate()).toList());
        values.put("divergencia_custo", receipt.getItems().stream()
                .filter(i -> i.getCostDifference() != null)
                .map(i -> products.get(i.getProductId()).getName() + ": " + i.getCostDifference()).toList());
        auditService.recordChange(AuditAction.COMPRA_RECEBIDA, "GoodsReceipt", receipt.getId(), null, values, null);
        return receipt;
    }

    // O ultimo custo pago a este fornecedor ajuda a proxima compra.
    private void updateSupplierCost(Product product, Long supplierId, BigDecimal unitCost) {
        ProductSupplier current = product.getSuppliers().stream()
                .filter(s -> Objects.equals(s.getSupplierId(), supplierId))
                .findFirst()
                .orElse(null);
        product.getSuppliers().remove(current);
        product.getSuppliers().add(new ProductSupplier(supplierId,
                current == null ? null : current.getSupplierSku(), unitCost,
                current == null ? null : current.getLeadTimeDays()));
    }

    private List<Integer> installmentDays(Long paymentTermId) {
        if (paymentTermId == null) {
            return List.of(0);
        }
        return paymentTermRepository.findById(paymentTermId)
                .map(PaymentTerm::getInstallmentDays)
                .filter(days -> !days.isEmpty())
                .orElse(List.of(0));
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

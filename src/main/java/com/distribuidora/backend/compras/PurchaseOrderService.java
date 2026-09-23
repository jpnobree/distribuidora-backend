package com.distribuidora.backend.compras;

import com.distribuidora.backend.audit.AuditAction;
import com.distribuidora.backend.audit.AuditService;
import com.distribuidora.backend.cadastros.Supplier;
import com.distribuidora.backend.cadastros.SupplierRepository;
import com.distribuidora.backend.comercial.SystemParameter;
import com.distribuidora.backend.comercial.SystemParameterRepository;
import com.distribuidora.backend.compras.PurchaseDtos.PurchaseItemRequest;
import com.distribuidora.backend.compras.PurchaseDtos.PurchaseOrderRequest;
import com.distribuidora.backend.estoque.Warehouse;
import com.distribuidora.backend.estoque.WarehouseRepository;
import com.distribuidora.backend.exception.BusinessRuleException;
import com.distribuidora.backend.exception.ResourceNotFoundException;
import com.distribuidora.backend.model.Product;
import com.distribuidora.backend.model.ProductUnit;
import com.distribuidora.backend.repository.ProductRepository;
import com.distribuidora.backend.security.CurrentUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

// Pedido de compra: o compromisso de dinheiro com o fornecedor.
@Service
public class PurchaseOrderService {

    public static final String VER = "compras.ver";
    public static final String CRIAR = "compras.criar";
    public static final String APROVAR = "compras.aprovar";
    public static final String RECEBER = "compras.receber";

    private final PurchaseOrderRepository orderRepository;
    private final SupplierRepository supplierRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final SystemParameterRepository parameterRepository;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    public PurchaseOrderService(PurchaseOrderRepository orderRepository, SupplierRepository supplierRepository,
                                WarehouseRepository warehouseRepository, ProductRepository productRepository,
                                SystemParameterRepository parameterRepository, CurrentUser currentUser,
                                AuditService auditService) {
        this.orderRepository = orderRepository;
        this.supplierRepository = supplierRepository;
        this.warehouseRepository = warehouseRepository;
        this.productRepository = productRepository;
        this.parameterRepository = parameterRepository;
        this.currentUser = currentUser;
        this.auditService = auditService;
    }

    @Transactional
    public PurchaseOrder create(PurchaseOrderRequest request) {
        Supplier supplier = supplierRepository.findById(request.supplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Fornecedor nao encontrado: " + request.supplierId()));
        if (!supplier.isActive()) {
            throw new BusinessRuleException("Fornecedor inativo nao recebe pedido de compra.");
        }
        Warehouse warehouse = warehouseRepository.findById(request.warehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Deposito nao encontrado."));
        if (!warehouse.isActive()) {
            throw new BusinessRuleException("Deposito inativo.");
        }

        PurchaseOrder order = new PurchaseOrder(supplier.getId(), warehouse.getId(), supplier.getPaymentTermId(),
                request.expectedOn(), blank(request.notes()), currentUser.username());
        for (PurchaseItemRequest line : request.items()) {
            order.addItem(buildItem(line));
        }
        order.recalculate();

        // Compra acima do limite precisa de quem tem alcada; abaixo dele, quem
        // lanca ja aprova (e fica registrado que foi ele).
        if (order.getTotal().compareTo(approvalThreshold()) <= 0 || currentUser.can(APROVAR)) {
            order.approve(currentUser.username());
        }
        orderRepository.save(order);

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("fornecedor", supplier.displayName());
        values.put("total", order.getTotal());
        values.put("itens", order.getItems().size());
        values.put("status", order.getStatus().getLabel());
        auditService.recordChange(AuditAction.COMPRA_LANCADA, "PurchaseOrder", order.getId(), null, values, null);
        return order;
    }

    private PurchaseOrderItem buildItem(PurchaseItemRequest line) {
        Product product = productRepository.findById(line.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Produto nao encontrado: " + line.productId()));
        if (!product.isActive()) {
            throw new BusinessRuleException(product.getName() + " esta inativo e nao pode ser comprado.");
        }
        BigDecimal factor = BigDecimal.ONE;
        if (!line.unitCode().equals(product.getBaseUnit())) {
            ProductUnit conversion = product.getUnits().stream()
                    .filter(u -> u.getUnitCode().equals(line.unitCode()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessRuleException(product.getName() + " nao tem conversao para "
                            + line.unitCode() + "."));
            factor = conversion.getFactor();
        }
        BigDecimal qtyBase = line.quantity().multiply(factor).setScale(3, RoundingMode.HALF_UP);
        return new PurchaseOrderItem(product.getId(), line.unitCode(), line.quantity(), factor, qtyBase,
                line.unitCost(), qtyBase.multiply(line.unitCost()).setScale(2, RoundingMode.HALF_UP));
    }

    @Transactional
    public PurchaseOrder approve(Long id) {
        PurchaseOrder order = load(id);
        if (order.getStatus() != PurchaseOrder.Status.AGUARDANDO_APROVACAO) {
            throw new BusinessRuleException("Apenas pedido aguardando aprovacao pode ser aprovado.");
        }
        if (!currentUser.can(APROVAR)) {
            throw new AccessDeniedException("Voce nao tem alcada para aprovar compras.");
        }
        order.approve(currentUser.username());
        auditService.recordChange(AuditAction.COMPRA_APROVADA, "PurchaseOrder", order.getId(), null,
                Map.of("total", order.getTotal()), null);
        return orderRepository.save(order);
    }

    @Transactional
    public PurchaseOrder cancel(Long id, String reason) {
        PurchaseOrder order = load(id);
        if (order.getStatus() == PurchaseOrder.Status.CANCELADO) {
            throw new BusinessRuleException("Pedido ja cancelado.");
        }
        if (order.getStatus() == PurchaseOrder.Status.RECEBIDO
                || order.getStatus() == PurchaseOrder.Status.RECEBIDO_PARCIAL) {
            throw new BusinessRuleException("Mercadoria ja recebida neste pedido. "
                    + "Acerte pelo estoque e pelo titulo a pagar.");
        }
        if (!canCancel(order)) {
            throw new AccessDeniedException("Voce nao tem permissao para cancelar este pedido.");
        }
        order.cancel(currentUser.username(), reason.trim());
        auditService.recordChange(AuditAction.COMPRA_CANCELADA, "PurchaseOrder", order.getId(), null,
                Map.of("total", order.getTotal()), reason.trim());
        return orderRepository.save(order);
    }

    // Quem lancou desiste enquanto nao foi aprovado; depois disso so a alcada.
    public boolean canCancel(PurchaseOrder order) {
        if (order.getStatus() == PurchaseOrder.Status.CANCELADO || order.getStatus() == PurchaseOrder.Status.RECEBIDO
                || order.getStatus() == PurchaseOrder.Status.RECEBIDO_PARCIAL) {
            return false;
        }
        return currentUser.can(APROVAR) || (order.getStatus() == PurchaseOrder.Status.AGUARDANDO_APROVACAO
                && order.getCreatedBy().equals(currentUser.username()));
    }

    public boolean canApprove(PurchaseOrder order) {
        return order.getStatus() == PurchaseOrder.Status.AGUARDANDO_APROVACAO && currentUser.can(APROVAR);
    }

    public PurchaseOrder load(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido de compra nao encontrado: " + id));
    }

    public BigDecimal approvalThreshold() {
        return parameterRepository.findById(SystemParameter.PURCHASE_APPROVAL)
                .map(p -> new BigDecimal(p.getValue()))
                .orElse(BigDecimal.ZERO);
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

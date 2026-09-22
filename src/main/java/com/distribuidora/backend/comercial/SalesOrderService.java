package com.distribuidora.backend.comercial;

import com.distribuidora.backend.audit.AuditAction;
import com.distribuidora.backend.audit.AuditService;
import com.distribuidora.backend.cadastros.Customer;
import com.distribuidora.backend.cadastros.CustomerRepository;
import com.distribuidora.backend.cadastros.PaymentTerm;
import com.distribuidora.backend.cadastros.PaymentTermRepository;
import com.distribuidora.backend.cadastros.Unit;
import com.distribuidora.backend.cadastros.UnitRepository;
import com.distribuidora.backend.comercial.PricingService.ResolvedPrice;
import com.distribuidora.backend.comercial.SalesDtos.OrderItemRequest;
import com.distribuidora.backend.comercial.SalesDtos.OrderRequest;
import com.distribuidora.backend.comercial.SalesOrder.Block;
import com.distribuidora.backend.estoque.StockReservationService;
import com.distribuidora.backend.financeiro.ReceivableRepository;
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
import java.text.NumberFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class SalesOrderService {

    static final String VER_TODOS = "pedidos.ver_todos";
    static final String CLIENTES_VER_TODOS = "clientes.ver_todos";
    static final String CANCELAR = "pedidos.cancelar";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final SalesOrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final PaymentTermRepository paymentTermRepository;
    private final ProductRepository productRepository;
    private final UnitRepository unitRepository;
    private final PricingService pricingService;
    private final StockReservationService reservationService;
    private final ReceivableRepository receivableRepository;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    public SalesOrderService(SalesOrderRepository orderRepository, CustomerRepository customerRepository,
                             PaymentTermRepository paymentTermRepository, ProductRepository productRepository,
                             UnitRepository unitRepository, PricingService pricingService,
                             StockReservationService reservationService, ReceivableRepository receivableRepository,
                             CurrentUser currentUser, AuditService auditService) {
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.paymentTermRepository = paymentTermRepository;
        this.productRepository = productRepository;
        this.unitRepository = unitRepository;
        this.pricingService = pricingService;
        this.reservationService = reservationService;
        this.receivableRepository = receivableRepository;
        this.currentUser = currentUser;
        this.auditService = auditService;
    }

    @Transactional
    public SalesOrder create(OrderRequest request) {
        Customer customer = visibleCustomer(request.customerId());
        if (customer.getStatus() == Customer.Status.INATIVO) {
            throw new BusinessRuleException("Cliente inativo nao pode comprar. Reative o cadastro antes.");
        }
        SalesOrder order = new SalesOrder(customer.getId(),
                customer.getSellerId() != null ? customer.getSellerId() : currentUser.id(),
                customer.getPaymentTermId(), customer.getPriceTableId(), request.expectedDeliveryOn(),
                blank(request.notes()), currentUser.username());

        BigDecimal maxDiscount = pricingService.maxDiscountPercent();
        Map<String, Unit> units = unitRepository.findAll().stream().collect(Collectors.toMap(Unit::getCode, u -> u));
        Map<Long, BigDecimal> requested = new LinkedHashMap<>();
        Map<Long, Product> products = new LinkedHashMap<>();

        for (OrderItemRequest line : request.items()) {
            Product product = productRepository.findById(line.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Produto nao encontrado: " + line.productId()));
            if (!product.isActive()) {
                throw new BusinessRuleException(product.getName() + " esta inativo e nao pode ser vendido.");
            }
            SalesOrderItem item = buildItem(customer, product, line, units, maxDiscount, order);
            order.addItem(item);
            requested.merge(product.getId(), item.getQtyBase(), BigDecimal::add);
            products.put(product.getId(), product);
        }
        order.recalculate();

        // Sem estoque disponivel o pedido nem nasce: venda sob encomenda nao
        // e permitida (docs/erp, regra R-C4).
        for (Map.Entry<Long, BigDecimal> entry : requested.entrySet()) {
            BigDecimal available = reservationService.availableForSale(entry.getKey());
            if (available.compareTo(entry.getValue()) < 0) {
                Product p = products.get(entry.getKey());
                throw new BusinessRuleException("Estoque insuficiente de " + p.getName() + ": disponivel "
                        + qty(available) + " " + p.getBaseUnit().toLowerCase() + ", pedido "
                        + qty(entry.getValue()) + ".");
            }
        }

        checkCredit(customer, order);

        // Quem lanca e ja tem alcada para um bloqueio aprova na hora (fica registrado).
        order.pendingBlocks().stream()
                .filter(block -> currentUser.can(block.getType().getPermission()))
                .forEach(block -> block.resolve(currentUser.username()));

        orderRepository.save(order);
        if (order.pendingBlocks().isEmpty()) {
            approveAndReserve(order);
        }
        auditService.recordChange(AuditAction.PEDIDO_CRIADO, "SalesOrder", order.getId(), null, snapshot(order), null);
        return order;
    }

    private SalesOrderItem buildItem(Customer customer, Product product, OrderItemRequest line, Map<String, Unit> units,
                                     BigDecimal maxDiscount, SalesOrder order) {
        BigDecimal factor = BigDecimal.ONE;
        boolean nominal = false;
        if (!line.unitCode().equals(product.getBaseUnit())) {
            ProductUnit conversion = product.getUnits().stream()
                    .filter(u -> u.getUnitCode().equals(line.unitCode()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessRuleException(product.getName() + " nao e vendido em "
                            + line.unitCode() + "."));
            factor = conversion.getFactor();
            nominal = conversion.isNominal();
        }
        BigDecimal qtyBase = line.quantity().multiply(factor).setScale(3, RoundingMode.HALF_UP);
        Unit base = units.get(product.getBaseUnit());
        if (base != null && !base.isAllowsDecimal() && qtyBase.stripTrailingZeros().scale() > 0) {
            throw new BusinessRuleException(product.getName() + ": quantidade precisa ser inteira em "
                    + base.getName().toLowerCase() + ".");
        }

        ResolvedPrice resolved = pricingService.resolve(customer, product);
        if (resolved == null) {
            throw new BusinessRuleException(product.getName() + " nao tem preco cadastrado.");
        }
        BigDecimal listPrice = resolved.price().setScale(2, RoundingMode.HALF_UP);
        BigDecimal unitPrice = (line.unitPrice() != null ? line.unitPrice() : listPrice).setScale(2, RoundingMode.HALF_UP);
        BigDecimal discount = listPrice.signum() == 0 || unitPrice.compareTo(listPrice) >= 0 ? BigDecimal.ZERO
                : listPrice.subtract(unitPrice).multiply(HUNDRED).divide(listPrice, 2, RoundingMode.HALF_UP);
        BigDecimal cost = product.getAverageCost() != null ? product.getAverageCost() : product.getCostPrice();

        if (discount.compareTo(maxDiscount) > 0) {
            order.addBlock(Block.Type.DESCONTO, product.getName() + ": desconto de " + percent(discount)
                    + " (limite " + percent(maxDiscount) + ")");
        }
        if (cost != null && unitPrice.compareTo(cost) < 0) {
            order.addBlock(Block.Type.ABAIXO_CUSTO, product.getName() + ": " + money(unitPrice) + "/"
                    + product.getBaseUnit().toLowerCase() + " abaixo do custo");
        }
        return new SalesOrderItem(product.getId(), line.unitCode(), line.quantity(), factor, nominal, qtyBase, listPrice,
                unitPrice, discount, qtyBase.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP), cost,
                resolved.source());
    }

    // Exposicao = pedidos ainda nao faturados + titulos a receber em aberto +
    // este pedido. O pedido sai da primeira parcela e entra na segunda quando
    // vira nota, entao nada e contado duas vezes.
    private void checkCredit(Customer customer, SalesOrder order) {
        if (customer.getStatus() == Customer.Status.BLOQUEADO) {
            order.addBlock(Block.Type.CLIENTE_BLOQUEADO, "Cliente com cadastro bloqueado pelo financeiro");
        }
        if (isCashTerm(customer.getPaymentTermId())) {
            return;
        }
        BigDecimal exposure = exposureOf(customer.getId()).add(order.getTotal());
        if (exposure.compareTo(customer.getCreditLimit()) > 0) {
            order.addBlock(Block.Type.LIMITE_CREDITO, "Exposicao " + money(exposure) + " com este pedido; limite "
                    + money(customer.getCreditLimit()));
        }
    }

    public BigDecimal exposureOf(Long customerId) {
        return orderRepository.openTotalForCustomer(customerId)
                .add(receivableRepository.openTotalForCustomer(customerId));
    }

    boolean isCashTerm(Long paymentTermId) {
        if (paymentTermId == null) {
            return false;
        }
        return paymentTermRepository.findById(paymentTermId)
                .map(PaymentTerm::getInstallmentDays)
                .map(days -> days.size() == 1 && days.get(0) == 0)
                .orElse(false);
    }

    @Transactional
    public SalesOrder approve(Long id) {
        SalesOrder order = visibleOrder(id);
        if (order.getStatus() != SalesOrder.Status.AGUARDANDO_APROVACAO) {
            throw new BusinessRuleException("Apenas pedidos aguardando aprovacao podem ser aprovados.");
        }
        List<Block> pending = order.pendingBlocks();
        List<String> missing = pending.stream()
                .filter(b -> !currentUser.can(b.getType().getPermission()))
                .map(b -> b.getType().getLabel().toLowerCase())
                .distinct()
                .toList();
        if (!missing.isEmpty()) {
            throw new AccessDeniedException("Voce nao tem alcada para liberar: " + String.join(", ", missing) + ".");
        }
        pending.forEach(b -> b.resolve(currentUser.username()));
        approveAndReserve(order);

        Map<String, Object> resolved = new LinkedHashMap<>();
        resolved.put("liberados", pending.stream().map(b -> b.getType().getLabel() + " - " + b.getDetail()).toList());
        auditService.recordChange(AuditAction.PEDIDO_APROVADO, "SalesOrder", order.getId(), null, resolved, null);
        return order;
    }

    // O estoque pode ter mudado desde o lancamento: a reserva confere de novo
    // e, faltando, desfaz a aprovacao inteira (mesma transacao).
    private void approveAndReserve(SalesOrder order) {
        for (SalesOrderItem item : order.getItems()) {
            Product product = productRepository.findById(item.getProductId()).orElseThrow();
            reservationService.reserve(item.getId(), item.getProductId(), item.getQtyBase(), product.getName());
        }
        order.approve(currentUser.username());
    }

    @Transactional
    public SalesOrder cancel(Long id, String reason) {
        SalesOrder order = visibleOrder(id);
        if (order.getStatus() == SalesOrder.Status.CANCELADO) {
            throw new BusinessRuleException("Pedido ja cancelado.");
        }
        // a mercadoria ja saiu ou ja esta na doca: desfazer e pelo outro lado
        if (order.getStatus() == SalesOrder.Status.FATURADO) {
            throw new BusinessRuleException("Pedido faturado. Cancele o faturamento antes.");
        }
        if (order.getStatus() == SalesOrder.Status.EM_SEPARACAO || order.getStatus() == SalesOrder.Status.SEPARADO) {
            throw new BusinessRuleException("O pedido esta na expedicao. Cancele a separacao antes.");
        }
        if (!canCancel(order)) {
            throw new AccessDeniedException("Voce nao tem permissao para cancelar este pedido.");
        }
        reservationService.release(order.getItems().stream().map(SalesOrderItem::getId).toList());
        order.cancel(currentUser.username(), reason.trim());
        auditService.recordChange(AuditAction.PEDIDO_CANCELADO, "SalesOrder", order.getId(), null,
                Map.of("total", order.getTotal()), reason.trim());
        return order;
    }

    // Quem lancou pode desistir enquanto o pedido nao foi aprovado; depois
    // disso so quem tem "pedidos.cancelar".
    public boolean canCancel(SalesOrder order) {
        if (order.getStatus() != SalesOrder.Status.AGUARDANDO_APROVACAO
                && order.getStatus() != SalesOrder.Status.APROVADO) {
            return false;
        }
        return currentUser.can(CANCELAR) || (order.getStatus() == SalesOrder.Status.AGUARDANDO_APROVACAO
                && Objects.equals(order.getCreatedBy(), currentUser.username()));
    }

    public boolean canApprove(SalesOrder order) {
        return order.getStatus() == SalesOrder.Status.AGUARDANDO_APROVACAO
                && order.pendingBlocks().stream().allMatch(b -> currentUser.can(b.getType().getPermission()));
    }

    SalesOrder visibleOrder(Long id) {
        SalesOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido nao encontrado: " + id));
        if (!currentUser.can(VER_TODOS) && !Objects.equals(order.getSellerId(), currentUser.id())
                && !Objects.equals(order.getCreatedBy(), currentUser.username())) {
            throw new ResourceNotFoundException("Pedido nao encontrado: " + id);
        }
        return order;
    }

    Customer visibleCustomer(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente nao encontrado: " + id));
        if (!currentUser.can(CLIENTES_VER_TODOS) && !Objects.equals(customer.getSellerId(), currentUser.id())) {
            throw new ResourceNotFoundException("Cliente nao encontrado: " + id);
        }
        return customer;
    }

    private static Map<String, Object> snapshot(SalesOrder order) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("customerId", order.getCustomerId());
        values.put("status", order.getStatus());
        values.put("total", order.getTotal());
        values.put("discountTotal", order.getDiscountTotal());
        values.put("items", order.getItems().size());
        values.put("blocks", order.getBlocks().stream()
                .map(b -> b.getType().getLabel() + (b.getResolvedBy() != null ? " (liberado por " + b.getResolvedBy() + ")" : ""))
                .toList());
        return values;
    }

    private static String money(BigDecimal value) {
        return NumberFormat.getCurrencyInstance(new Locale("pt", "BR")).format(value);
    }

    private static String percent(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString().replace('.', ',') + "%";
    }

    private static String qty(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

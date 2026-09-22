package com.distribuidora.backend.comercial;

import com.distribuidora.backend.cadastros.Customer;
import com.distribuidora.backend.cadastros.CustomerRepository;
import com.distribuidora.backend.cadastros.PaymentTerm;
import com.distribuidora.backend.cadastros.PaymentTermRepository;
import com.distribuidora.backend.comercial.SalesDtos.*;
import com.distribuidora.backend.estoque.Lot;
import com.distribuidora.backend.estoque.LotRepository;
import com.distribuidora.backend.estoque.StockReservation;
import com.distribuidora.backend.estoque.StockReservationService;
import com.distribuidora.backend.estoque.Warehouse;
import com.distribuidora.backend.estoque.WarehouseRepository;
import com.distribuidora.backend.exception.ResourceNotFoundException;
import com.distribuidora.backend.model.Product;
import com.distribuidora.backend.model.User;
import com.distribuidora.backend.repository.ProductRepository;
import com.distribuidora.backend.repository.UserRepository;
import com.distribuidora.backend.security.CurrentUser;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SalesQueryService {

    static final String CUSTO_VER = "produtos.custo.ver";

    private final SalesOrderRepository orderRepository;
    private final SalesOrderService orderService;
    private final CustomerRepository customerRepository;
    private final PaymentTermRepository paymentTermRepository;
    private final PriceTableRepository priceTableRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final LotRepository lotRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockReservationService reservationService;
    private final PricingService pricingService;
    private final CurrentUser currentUser;

    public SalesQueryService(SalesOrderRepository orderRepository, SalesOrderService orderService,
                             CustomerRepository customerRepository, PaymentTermRepository paymentTermRepository,
                             PriceTableRepository priceTableRepository, ProductRepository productRepository,
                             UserRepository userRepository, LotRepository lotRepository,
                             WarehouseRepository warehouseRepository, StockReservationService reservationService,
                             PricingService pricingService, CurrentUser currentUser) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.customerRepository = customerRepository;
        this.paymentTermRepository = paymentTermRepository;
        this.priceTableRepository = priceTableRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.lotRepository = lotRepository;
        this.warehouseRepository = warehouseRepository;
        this.reservationService = reservationService;
        this.pricingService = pricingService;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public Page<OrderSummary> search(SalesOrder.Status status, Long customerId, Long sellerId, String search,
                                     boolean pendingOnly, int page, int size) {
        Specification<SalesOrder> spec = Specification.where(null);
        if (!currentUser.can(SalesOrderService.VER_TODOS)) {
            Long me = currentUser.id();
            String username = currentUser.username();
            spec = spec.and((r, q, cb) -> cb.or(cb.equal(r.get("sellerId"), me), cb.equal(r.get("createdBy"), username)));
        }
        if (status != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), status));
        }
        if (pendingOnly) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), SalesOrder.Status.AGUARDANDO_APROVACAO));
        }
        if (customerId != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("customerId"), customerId));
        }
        if (sellerId != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("sellerId"), sellerId));
        }
        if (search != null && !search.isBlank()) {
            String like = "%" + search.trim().toLowerCase() + "%";
            spec = spec.and((r, q, cb) -> {
                Subquery<Long> customers = q.subquery(Long.class);
                var c = customers.from(Customer.class);
                customers.select(c.get("id")).where(cb.or(
                        cb.like(cb.lower(c.get("legalName")), like),
                        cb.like(cb.lower(c.get("tradeName")), like)));
                return cb.in(r.get("customerId")).value(customers);
            });
        }
        Page<SalesOrder> orders = orderRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        Map<Long, Customer> customers = byId(customerRepository.findAllById(
                orders.stream().map(SalesOrder::getCustomerId).distinct().toList()), Customer::getId);
        Map<Long, String> sellers = sellerNames(orders.stream().map(SalesOrder::getSellerId).toList());

        return orders.map(o -> new OrderSummary(o.getId(), o.getStatus(), o.getCustomerId(),
                customers.get(o.getCustomerId()).displayName(), sellers.get(o.getSellerId()), o.getTotal(),
                o.isHasEstimatedWeight(), o.getItems().size(),
                o.pendingBlocks().stream().map(b -> b.getType().getLabel()).distinct().toList(),
                o.getCreatedBy(), o.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    public OrderView view(Long id) {
        return toView(orderService.visibleOrder(id));
    }

    @Transactional(readOnly = true)
    public OrderView toView(SalesOrder order) {
        Customer customer = customerRepository.findById(order.getCustomerId()).orElseThrow();
        Map<Long, Product> products = byId(productRepository.findAllById(
                order.getItems().stream().map(SalesOrderItem::getProductId).toList()), Product::getId);
        List<StockReservation> reservations = reservationService.activeFor(
                order.getItems().stream().map(SalesOrderItem::getId).toList());
        Map<Long, Lot> lots = byId(lotRepository.findAllById(
                reservations.stream().map(StockReservation::getLotId).filter(Objects::nonNull).toList()), Lot::getId);
        Map<Long, String> warehouses = warehouseRepository.findAll().stream()
                .collect(Collectors.toMap(Warehouse::getId, Warehouse::getName));
        Map<Long, List<StockReservation>> byItem = reservations.stream()
                .collect(Collectors.groupingBy(StockReservation::getOrderItemId));
        boolean seesCost = currentUser.can(CUSTO_VER);

        List<OrderItemView> items = order.getItems().stream().map(i -> {
            Product p = products.get(i.getProductId());
            List<ReservationView> res = byItem.getOrDefault(i.getId(), List.of()).stream().map(r -> {
                Lot lot = r.getLotId() == null ? null : lots.get(r.getLotId());
                return new ReservationView(lot == null ? null : lot.getCode(), lot == null ? null : lot.getExpiresOn(),
                        warehouses.get(r.getWarehouseId()), r.getQuantity());
            }).toList();
            return new OrderItemView(i.getId(), p.getId(), p.getName(), p.getSku(), p.getBaseUnit(), i.getUnitCode(),
                    i.getQuantity(), i.getFactor(), i.isNominal(), i.getQtyBase(), i.getListPrice(), i.getUnitPrice(),
                    i.getDiscountPercent(), i.getLineTotal(), i.getPriceSource().name(), res);
        }).toList();

        List<BlockView> blocks = order.getBlocks().stream().map(b -> new BlockView(b.getId(), b.getType().name(),
                b.getType().getLabel(), b.getDetail(), b.getType().getPermission(), b.getResolvedBy(),
                b.getResolvedAt())).toList();

        BigDecimal margin = seesCost && order.getEstimatedCost() != null && order.getTotal().signum() > 0
                ? order.getTotal().subtract(order.getEstimatedCost()).multiply(BigDecimal.valueOf(100))
                .divide(order.getTotal(), 1, RoundingMode.HALF_UP) : null;

        return new OrderView(order.getId(), order.getStatus(), customer.getId(), customer.displayName(),
                customer.getDocument(), order.getSellerId(), sellerNames(List.of(order.getSellerId())).get(order.getSellerId()),
                order.getPaymentTermId() == null ? null
                        : paymentTermRepository.findById(order.getPaymentTermId()).map(PaymentTerm::getName).orElse(null),
                order.getPriceTableId() == null ? null
                        : priceTableRepository.findById(order.getPriceTableId()).map(PriceTable::getName).orElse(null),
                order.getExpectedDeliveryOn(), order.getNotes(), order.getSubtotal(), order.getDiscountTotal(),
                order.getTotal(), seesCost ? order.getEstimatedCost() : null, margin, order.isHasEstimatedWeight(),
                order.getCreatedBy(), order.getCreatedAt(), order.getApprovedBy(), order.getApprovedAt(),
                order.getCancelledBy(), order.getCancelledAt(), order.getCancelReason(), items, blocks,
                orderService.canApprove(order), orderService.canCancel(order), order.getVersion());
    }

    // Preco, conversoes e disponibilidade de um produto para um cliente: o
    // que o formulario de pedido precisa para mostrar o preco antes de salvar.
    @Transactional(readOnly = true)
    public Quote quote(Long customerId, Long productId) {
        Customer customer = orderService.visibleCustomer(customerId);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Produto nao encontrado: " + productId));
        PricingService.ResolvedPrice price = pricingService.resolve(customer, product);
        BigDecimal cost = product.getAverageCost() != null ? product.getAverageCost() : product.getCostPrice();
        return new Quote(product.getId(), product.getName(), product.getSku(), product.getBaseUnit(),
                product.isVariableWeight(),
                product.getUnits().stream().map(u -> new UnitOption(u.getUnitCode(), u.getFactor(), u.isNominal())).toList(),
                price == null ? null : price.price(), price == null ? null : price.source().name(),
                reservationService.availableForSale(productId),
                currentUser.can(CUSTO_VER) ? cost : null, pricingService.maxDiscountPercent());
    }

    @Transactional(readOnly = true)
    public CreditView credit(Long customerId) {
        Customer customer = orderService.visibleCustomer(customerId);
        BigDecimal open = orderRepository.openTotalForCustomer(customerId);
        boolean cash = orderService.isCashTerm(customer.getPaymentTermId());
        return new CreditView(customer.getId(), customer.getStatus().name(), customer.getCreditLimit(), open,
                customer.getCreditLimit().subtract(open), cash,
                customer.getPaymentTermId() == null ? null
                        : paymentTermRepository.findById(customer.getPaymentTermId()).map(PaymentTerm::getName).orElse(null),
                customer.getPriceTableId() == null ? null
                        : priceTableRepository.findById(customer.getPriceTableId()).map(PriceTable::getName).orElse(null));
    }

    private Map<Long, String> sellerNames(Collection<Long> ids) {
        return userRepository.findAllById(ids.stream().filter(Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, u -> u.getFullName() != null ? u.getFullName() : u.getUsername()));
    }

    private static <T> Map<Long, T> byId(List<T> items, Function<T, Long> id) {
        return items.stream().collect(Collectors.toMap(id, Function.identity(), (a, b) -> a));
    }
}

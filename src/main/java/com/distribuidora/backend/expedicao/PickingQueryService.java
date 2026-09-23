package com.distribuidora.backend.expedicao;

import com.distribuidora.backend.cadastros.Customer;
import com.distribuidora.backend.cadastros.CustomerRepository;
import com.distribuidora.backend.comercial.SalesOrder;
import com.distribuidora.backend.comercial.SalesOrderItem;
import com.distribuidora.backend.comercial.SalesOrderRepository;
import com.distribuidora.backend.estoque.Lot;
import com.distribuidora.backend.estoque.LotRepository;
import com.distribuidora.backend.estoque.StockBalance;
import com.distribuidora.backend.estoque.StockBalanceRepository;
import com.distribuidora.backend.estoque.Warehouse;
import com.distribuidora.backend.estoque.WarehouseRepository;
import com.distribuidora.backend.expedicao.PickingDtos.DivergenceView;
import com.distribuidora.backend.expedicao.PickingDtos.LotOption;
import com.distribuidora.backend.expedicao.PickingDtos.PendingOrder;
import com.distribuidora.backend.expedicao.PickingDtos.PickingItemView;
import com.distribuidora.backend.expedicao.PickingDtos.PickingLineView;
import com.distribuidora.backend.expedicao.PickingDtos.PickingSummary;
import com.distribuidora.backend.expedicao.PickingDtos.PickingView;
import com.distribuidora.backend.faturamento.Invoice;
import com.distribuidora.backend.faturamento.InvoiceRepository;
import com.distribuidora.backend.model.Product;
import com.distribuidora.backend.repository.ProductRepository;
import com.distribuidora.backend.security.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PickingQueryService {

    private final PickingListRepository pickingRepository;
    private final PickingService pickingService;
    private final SalesOrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final LotRepository lotRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockBalanceRepository balanceRepository;
    private final InvoiceRepository invoiceRepository;
    private final CurrentUser currentUser;
    private final Clock clock;

    public PickingQueryService(PickingListRepository pickingRepository, PickingService pickingService,
                               SalesOrderRepository orderRepository, CustomerRepository customerRepository,
                               ProductRepository productRepository, LotRepository lotRepository,
                               WarehouseRepository warehouseRepository, StockBalanceRepository balanceRepository,
                               InvoiceRepository invoiceRepository, CurrentUser currentUser, Clock clock) {
        this.pickingRepository = pickingRepository;
        this.pickingService = pickingService;
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.lotRepository = lotRepository;
        this.warehouseRepository = warehouseRepository;
        this.balanceRepository = balanceRepository;
        this.invoiceRepository = invoiceRepository;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<PickingSummary> search(PickingList.Status status, int page, int size) {
        Specification<PickingList> spec = Specification.where(null);
        if (status != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), status));
        }
        Page<PickingList> pickings = pickingRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        Map<Long, SalesOrder> orders = orderRepository.findAllById(pickings.stream()
                .map(PickingList::getOrderId).toList()).stream()
                .collect(Collectors.toMap(SalesOrder::getId, Function.identity()));
        Map<Long, Customer> customers = customerRepository.findAllById(orders.values().stream()
                .map(SalesOrder::getCustomerId).distinct().toList()).stream()
                .collect(Collectors.toMap(Customer::getId, Function.identity()));

        return pickings.map(p -> {
            SalesOrder order = orders.get(p.getOrderId());
            Customer customer = order == null ? null : customers.get(order.getCustomerId());
            return new PickingSummary(p.getId(), p.getOrderId(), p.getStatus(), p.getStatus().getLabel(),
                    customer == null ? null : customer.displayName(),
                    order == null ? null : order.getExpectedDeliveryOn(),
                    (int) p.getItems().stream().map(PickingItem::getOrderItemId).distinct().count(),
                    p.getDivergences().size(), p.getCreatedBy(), p.getCreatedAt(), p.getSeparatedBy(),
                    p.getCheckedBy());
        });
    }

    // Fila da expedicao: pedidos aprovados que ainda nao viraram separacao.
    @Transactional(readOnly = true)
    public List<PendingOrder> pending() {
        List<SalesOrder> orders = orderRepository.findAll((r, q, cb) ->
                cb.equal(r.get("status"), SalesOrder.Status.APROVADO));
        Map<Long, Customer> customers = customerRepository.findAllById(orders.stream()
                .map(SalesOrder::getCustomerId).distinct().toList()).stream()
                .collect(Collectors.toMap(Customer::getId, Function.identity()));
        return orders.stream()
                .sorted(Comparator.comparing(SalesOrder::getExpectedDeliveryOn,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(SalesOrder::getId))
                .map(o -> new PendingOrder(o.getId(),
                        customers.containsKey(o.getCustomerId()) ? customers.get(o.getCustomerId()).displayName() : null,
                        o.getExpectedDeliveryOn(), o.getItems().size(), o.getTotal(), o.isHasEstimatedWeight(),
                        o.getApprovedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PickingView view(Long id) {
        return toView(pickingService.load(id));
    }

    @Transactional(readOnly = true)
    public PickingView toView(PickingList picking) {
        SalesOrder order = orderRepository.findById(picking.getOrderId()).orElseThrow();
        Customer customer = customerRepository.findById(order.getCustomerId()).orElse(null);
        Map<Long, Product> products = productRepository.findAllById(order.getItems().stream()
                .map(SalesOrderItem::getProductId).distinct().toList()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, String> warehouses = warehouseRepository.findAll().stream()
                .collect(Collectors.toMap(Warehouse::getId, Warehouse::getName));
        Map<Long, List<PickingItem>> byOrderItem = picking.getItems().stream()
                .collect(Collectors.groupingBy(PickingItem::getOrderItemId));
        Map<Long, Lot> lots = lotRepository.findAllById(picking.getItems().stream()
                .map(PickingItem::getLotId).filter(Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(Lot::getId, Function.identity()));
        boolean editable = picking.getStatus() == PickingList.Status.ABERTA;

        List<PickingItemView> items = order.getItems().stream().map(item -> {
            Product product = products.get(item.getProductId());
            List<PickingLineView> lines = byOrderItem.getOrDefault(item.getId(), List.of()).stream().map(line -> {
                Lot lot = line.getLotId() == null ? null : lots.get(line.getLotId());
                return new PickingLineView(line.getId(), line.getLotId(), lot == null ? null : lot.getCode(),
                        lot == null ? null : lot.getExpiresOn(), line.getWarehouseId(),
                        warehouses.get(line.getWarehouseId()), line.getQtyPlanned(), line.getQtyPicked(),
                        line.getQtyChecked());
            }).toList();
            BigDecimal picked = lines.stream().map(PickingLineView::qtyPicked).reduce(BigDecimal.ZERO, BigDecimal::add);
            // null = ainda nao conferido (diferente de conferido e deu zero)
            BigDecimal checked = lines.stream().anyMatch(l -> l.qtyChecked() != null)
                    ? lines.stream().map(PickingLineView::qtyChecked).filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    : null;
            // o que esta reservado para esta linha continua a disposicao dela
            Map<String, BigDecimal> mine = byOrderItem.getOrDefault(item.getId(), List.of()).stream()
                    .collect(Collectors.toMap(l -> l.getWarehouseId() + ":" + l.getLotId(), PickingItem::getQtyPicked,
                            BigDecimal::add));
            return new PickingItemView(item.getId(), product.getId(), product.getName(), product.getSku(),
                    product.getBaseUnit(), product.isVariableWeight() || item.isNominal(), item.getUnitCode(),
                    item.getQuantity(), item.getQtyBase(), picked, checked, item.getUnitPrice(), lines,
                    editable ? lotOptions(product, warehouses, mine) : List.of());
        }).toList();

        List<DivergenceView> divergences = picking.getDivergences().stream()
                .map(d -> new DivergenceView(d.getId(), d.getType().name(), d.getType().getLabel(), d.getProductId(),
                        products.containsKey(d.getProductId()) ? products.get(d.getProductId()).getName() : null,
                        d.getQtyExpected(), d.getQtyFound(), d.getDetail(), d.getCreatedBy(), d.getCreatedAt()))
                .toList();

        BigDecimal pickedTotal = picking.getItems().stream()
                .map(i -> i.getQtyPicked().multiply(unitPrice(order, i.getOrderItemId())))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, java.math.RoundingMode.HALF_UP);
        Long invoiceId = invoiceRepository.findByOrderIdAndStatus(order.getId(), Invoice.Status.EMITIDA)
                .map(Invoice::getId).orElse(null);

        return new PickingView(picking.getId(), order.getId(), picking.getStatus(), picking.getStatus().getLabel(),
                order.getCustomerId(), customer == null ? null : customer.displayName(),
                order.getExpectedDeliveryOn(), picking.getNotes(), pickingService.weightTolerancePercent(),
                pickingService.checkerMustDiffer(), picking.getCreatedBy(), picking.getCreatedAt(),
                picking.getSeparatedBy(), picking.getSeparatedAt(), picking.getCheckedBy(), picking.getCheckedAt(),
                picking.getCancelledBy(), picking.getCancelledAt(), picking.getCancelReason(), order.getTotal(),
                pickedTotal, items, divergences,
                editable && currentUser.can(PickingService.SEPARAR),
                picking.getStatus() == PickingList.Status.SEPARADA && currentUser.can(PickingService.CONFERIR),
                picking.getStatus() != PickingList.Status.CANCELADA && order.getStatus() != SalesOrder.Status.FATURADO
                        && currentUser.can(PickingService.SEPARAR),
                picking.getStatus() == PickingList.Status.CONFERIDA && order.getStatus() != SalesOrder.Status.FATURADO,
                invoiceId, picking.getVersion());
    }

    // Lotes que o separador pode usar: o saldo livre mais o que ja esta
    // reservado para esta propria linha (senao o lote sugerido pelo FEFO
    // sumiria da lista, por estar todo reservado para este pedido).
    // Vencido fica de fora.
    private List<LotOption> lotOptions(Product product, Map<Long, String> warehouses,
                                       Map<String, BigDecimal> reservedHere) {
        LocalDate today = LocalDate.now(clock);
        List<StockBalance> balances = balanceRepository.findByProductId(product.getId());
        Map<Long, Lot> lots = lotRepository.findAllById(balances.stream().map(StockBalance::getLotId)
                .filter(Objects::nonNull).toList()).stream()
                .collect(Collectors.toMap(Lot::getId, Function.identity()));
        return balances.stream()
                .filter(b -> b.getLotId() == null || !lots.get(b.getLotId()).isExpired(today))
                .map(b -> {
                    Lot lot = b.getLotId() == null ? null : lots.get(b.getLotId());
                    BigDecimal usable = b.available()
                            .add(reservedHere.getOrDefault(b.getWarehouseId() + ":" + b.getLotId(), BigDecimal.ZERO));
                    return new LotOption(b.getLotId(), lot == null ? null : lot.getCode(),
                            lot == null ? null : lot.getExpiresOn(), b.getWarehouseId(),
                            warehouses.get(b.getWarehouseId()), usable);
                })
                .filter(option -> option.available().signum() > 0)
                .sorted(Comparator.comparing(LotOption::expiresOn, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private static BigDecimal unitPrice(SalesOrder order, Long orderItemId) {
        return order.getItems().stream().filter(i -> Objects.equals(i.getId(), orderItemId))
                .map(SalesOrderItem::getUnitPrice).findFirst().orElse(BigDecimal.ZERO);
    }
}

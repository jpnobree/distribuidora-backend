package com.distribuidora.backend.faturamento;

import com.distribuidora.backend.audit.AuditAction;
import com.distribuidora.backend.audit.AuditService;
import com.distribuidora.backend.cadastros.Customer;
import com.distribuidora.backend.cadastros.CustomerRepository;
import com.distribuidora.backend.cadastros.PaymentTerm;
import com.distribuidora.backend.cadastros.PaymentTermRepository;
import com.distribuidora.backend.comercial.SalesOrder;
import com.distribuidora.backend.comercial.SalesOrderItem;
import com.distribuidora.backend.comercial.SalesOrderRepository;
import com.distribuidora.backend.comercial.SystemParameter;
import com.distribuidora.backend.comercial.SystemParameterRepository;
import com.distribuidora.backend.estoque.StockReservation;
import com.distribuidora.backend.estoque.StockReservationService;
import com.distribuidora.backend.estoque.StockService;
import com.distribuidora.backend.exception.BusinessRuleException;
import com.distribuidora.backend.exception.ResourceNotFoundException;
import com.distribuidora.backend.expedicao.PickingItem;
import com.distribuidora.backend.expedicao.PickingList;
import com.distribuidora.backend.expedicao.PickingService;
import com.distribuidora.backend.financeiro.Receivable;
import com.distribuidora.backend.financeiro.ReceivableService;
import com.distribuidora.backend.model.Product;
import com.distribuidora.backend.repository.ProductRepository;
import com.distribuidora.backend.security.CurrentUser;
import org.springframework.security.access.AccessDeniedException;
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

// Faturamento: transforma a separacao conferida em documento de saida, baixa
// fisica do estoque e titulos a receber. Tudo numa transacao so.
@Service
public class InvoiceService {

    public static final String EMITIR = "faturamento.emitir";
    public static final String CANCELAR = "faturamento.cancelar";
    static final String CREDITO_LIBERAR = "credito.liberar";

    private final InvoiceRepository invoiceRepository;
    private final PickingService pickingService;
    private final SalesOrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final PaymentTermRepository paymentTermRepository;
    private final ProductRepository productRepository;
    private final StockService stockService;
    private final StockReservationService reservationService;
    private final ReceivableService receivableService;
    private final FiscalDocumentProvider fiscalProvider;
    private final SystemParameterRepository parameterRepository;
    private final CurrentUser currentUser;
    private final AuditService auditService;
    private final Clock clock;

    public InvoiceService(InvoiceRepository invoiceRepository, PickingService pickingService,
                          SalesOrderRepository orderRepository, CustomerRepository customerRepository,
                          PaymentTermRepository paymentTermRepository, ProductRepository productRepository,
                          StockService stockService, StockReservationService reservationService,
                          ReceivableService receivableService, FiscalDocumentProvider fiscalProvider,
                          SystemParameterRepository parameterRepository, CurrentUser currentUser,
                          AuditService auditService, Clock clock) {
        this.invoiceRepository = invoiceRepository;
        this.pickingService = pickingService;
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.paymentTermRepository = paymentTermRepository;
        this.productRepository = productRepository;
        this.stockService = stockService;
        this.reservationService = reservationService;
        this.receivableService = receivableService;
        this.fiscalProvider = fiscalProvider;
        this.parameterRepository = parameterRepository;
        this.currentUser = currentUser;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public Invoice issue(Long pickingId) {
        PickingList picking = pickingService.load(pickingId);
        if (picking.getStatus() != PickingList.Status.CONFERIDA) {
            throw new BusinessRuleException("So separacao conferida pode ser faturada. Esta esta "
                    + picking.getStatus().getLabel().toLowerCase() + ".");
        }
        SalesOrder order = orderRepository.findById(picking.getOrderId()).orElseThrow();
        if (order.getStatus() == SalesOrder.Status.FATURADO) {
            throw new BusinessRuleException("Pedido ja faturado.");
        }
        if (order.getStatus() == SalesOrder.Status.CANCELADO) {
            throw new BusinessRuleException("Pedido cancelado nao pode ser faturado.");
        }
        Customer customer = customerRepository.findById(order.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Cliente nao encontrado."));
        // o cliente pode ter sido bloqueado entre a aprovacao e a saida
        if (customer.getStatus() != Customer.Status.ATIVO && !currentUser.can(CREDITO_LIBERAR)) {
            throw new AccessDeniedException("Cliente " + customer.getStatus().name().toLowerCase()
                    + ": o faturamento precisa de liberacao do financeiro.");
        }

        Map<Long, Product> products = productRepository.findAllById(order.getItems().stream()
                .map(SalesOrderItem::getProductId).distinct().toList()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Invoice invoice = new Invoice(invoiceRepository.nextNumber(), series(), order.getId(), picking.getId(),
                customer.getId(), order.getPaymentTermId(), currentUser.username());

        for (SalesOrderItem item : order.getItems()) {
            BigDecimal quantity = shippedFor(picking, item.getId());
            if (quantity.signum() == 0) {
                continue;
            }
            Product product = products.get(item.getProductId());
            BigDecimal cost = product.getAverageCost() != null ? product.getAverageCost() : product.getCostPrice();
            invoice.addItem(new InvoiceItem(item.getId(), product.getId(), product.getName(), product.getBaseUnit(),
                    quantity, item.getQtyBase(), item.getUnitPrice(), item.getDiscountPercent(),
                    quantity.multiply(item.getUnitPrice()).setScale(2, RoundingMode.HALF_UP), cost,
                    cost == null ? null : quantity.multiply(cost).setScale(2, RoundingMode.HALF_UP)));
        }
        if (invoice.getItems().isEmpty()) {
            throw new BusinessRuleException("Nada foi separado neste pedido: nao ha o que faturar.");
        }
        invoice.recalculate();
        invoiceRepository.save(invoice);

        shipStock(picking, order, products, invoice);

        order.moveTo(SalesOrder.Status.FATURADO);
        List<Receivable> titles = receivableService.generate(invoice.getId(), customer.getId(), invoice.getNumber(),
                invoice.getTotal(), installmentDays(order.getPaymentTermId()), today());

        FiscalDocumentProvider.FiscalResult fiscal = fiscalProvider.issue(invoice);
        invoice.applyFiscal(fiscal.status(), fiscal.number(), fiscal.key(), fiscal.message());

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("documento", invoice.display());
        values.put("pedido", order.getId());
        values.put("cliente", customer.displayName());
        values.put("total", invoice.getTotal());
        values.put("titulos", titles.stream().map(t -> t.getDocument() + " vence " + t.getDueDate()).toList());
        auditService.recordChange(AuditAction.PEDIDO_FATURADO, "Invoice", invoice.getId(), null, values, null);
        return invoiceRepository.save(invoice);
    }

    // A reserva feita na separacao vira saida fisica lote a lote: e isso que
    // responde depois "quais clientes receberam o lote X".
    private void shipStock(PickingList picking, SalesOrder order, Map<Long, Product> products, Invoice invoice) {
        List<Long> orderItemIds = order.getItems().stream().map(SalesOrderItem::getId).toList();
        Map<String, StockReservation> reservations = reservationService.activeFor(orderItemIds).stream()
                .collect(Collectors.toMap(r -> key(r.getOrderItemId(), r.getWarehouseId(), r.getLotId()),
                        Function.identity(), (a, b) -> a));

        for (PickingItem item : picking.getItems()) {
            BigDecimal quantity = shipped(item);
            if (quantity.signum() == 0) {
                continue;
            }
            StockReservation reservation = reservations.get(key(item.getOrderItemId(), item.getWarehouseId(),
                    item.getLotId()));
            if (reservation == null) {
                throw new BusinessRuleException("A reserva desta separacao mudou. Refaca a separacao do pedido "
                        + order.getId() + ".");
            }
            stockService.shipSale(item.getWarehouseId(), products.get(item.getProductId()), item.getLotId(),
                    quantity, reservation.getQuantity(), invoice.getId(), invoice.display());
        }
        reservationService.markConsumed(orderItemIds);
    }

    // Cancelar a nota devolve a mercadoria ao deposito, cancela os titulos e
    // deixa o pedido pronto para faturar de novo. Nada e apagado.
    @Transactional
    public Invoice cancel(Long id, String reason) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Documento nao encontrado: " + id));
        if (invoice.getStatus() == Invoice.Status.CANCELADA) {
            throw new BusinessRuleException("Documento ja cancelado.");
        }
        receivableService.cancelForInvoice(invoice.getId());

        PickingList picking = pickingService.load(invoice.getPickingId());
        SalesOrder order = orderRepository.findById(invoice.getOrderId()).orElseThrow();
        Map<Long, Product> products = productRepository.findAllById(picking.getItems().stream()
                .map(PickingItem::getProductId).distinct().toList()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        for (PickingItem item : picking.getItems()) {
            BigDecimal quantity = shipped(item);
            if (quantity.signum() == 0) {
                continue;
            }
            Product product = products.get(item.getProductId());
            stockService.returnSale(item.getWarehouseId(), product, item.getLotId(), quantity, invoice.getId(),
                    invoice.display(), reason.trim());
            // volta a ficar reservada para o mesmo pedido
            reservationService.reserveExact(item.getOrderItemId(), item.getProductId(), item.getWarehouseId(),
                    item.getLotId(), quantity, product.getName());
        }
        invoice.cancel(currentUser.username(), reason.trim());
        order.moveTo(SalesOrder.Status.SEPARADO);

        FiscalDocumentProvider.FiscalResult fiscal = fiscalProvider.cancel(invoice, reason.trim());
        invoice.applyFiscal(fiscal.status(), fiscal.number(), fiscal.key(), fiscal.message());

        auditService.recordChange(AuditAction.FATURA_CANCELADA, "Invoice", invoice.getId(), null,
                Map.of("documento", invoice.display(), "pedido", order.getId(), "total", invoice.getTotal()),
                reason.trim());
        return invoiceRepository.save(invoice);
    }

    // ------------------------------------------------------------- apoio

    // A conferencia manda: qtyChecked e o que sai de verdade.
    private static BigDecimal shipped(PickingItem item) {
        return item.getQtyChecked() != null ? item.getQtyChecked() : item.getQtyPicked();
    }

    private static BigDecimal shippedFor(PickingList picking, Long orderItemId) {
        return picking.getItems().stream()
                .filter(i -> Objects.equals(i.getOrderItemId(), orderItemId))
                .map(InvoiceService::shipped)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    List<Integer> installmentDays(Long paymentTermId) {
        if (paymentTermId == null) {
            return List.of(0);
        }
        return paymentTermRepository.findById(paymentTermId)
                .map(PaymentTerm::getInstallmentDays)
                .filter(days -> !days.isEmpty())
                .orElse(List.of(0));
    }

    private String series() {
        return parameterRepository.findById(SystemParameter.INVOICE_SERIES)
                .map(SystemParameter::getValue)
                .orElse("1");
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private static String key(Long orderItemId, Long warehouseId, Long lotId) {
        return orderItemId + ":" + warehouseId + ":" + lotId;
    }
}

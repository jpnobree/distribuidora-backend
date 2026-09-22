package com.distribuidora.backend.expedicao;

import com.distribuidora.backend.audit.AuditAction;
import com.distribuidora.backend.audit.AuditService;
import com.distribuidora.backend.comercial.SalesOrder;
import com.distribuidora.backend.comercial.SalesOrderItem;
import com.distribuidora.backend.comercial.SalesOrderRepository;
import com.distribuidora.backend.comercial.SystemParameter;
import com.distribuidora.backend.comercial.SystemParameterRepository;
import com.distribuidora.backend.estoque.Lot;
import com.distribuidora.backend.estoque.LotRepository;
import com.distribuidora.backend.estoque.StockReservation;
import com.distribuidora.backend.estoque.StockReservationService;
import com.distribuidora.backend.estoque.Warehouse;
import com.distribuidora.backend.estoque.WarehouseRepository;
import com.distribuidora.backend.exception.BusinessRuleException;
import com.distribuidora.backend.exception.ResourceNotFoundException;
import com.distribuidora.backend.expedicao.PickingDtos.CheckLine;
import com.distribuidora.backend.expedicao.PickingDtos.CheckRequest;
import com.distribuidora.backend.expedicao.PickingDtos.PickItemRequest;
import com.distribuidora.backend.expedicao.PickingDtos.PickLine;
import com.distribuidora.backend.expedicao.PickingDtos.SeparationRequest;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

// Separacao e conferencia. Aqui o pedido encontra a balanca: o peso real
// substitui o peso estimado e a reserva passa a apontar para o lote que
// realmente saiu da camara.
@Service
public class PickingService {

    public static final String VER = "expedicao.ver";
    public static final String SEPARAR = "expedicao.separar";
    public static final String CONFERIR = "expedicao.conferir";

    private final PickingListRepository pickingRepository;
    private final SalesOrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final LotRepository lotRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockReservationService reservationService;
    private final SystemParameterRepository parameterRepository;
    private final CurrentUser currentUser;
    private final AuditService auditService;
    private final Clock clock;

    public PickingService(PickingListRepository pickingRepository, SalesOrderRepository orderRepository,
                          ProductRepository productRepository, LotRepository lotRepository,
                          WarehouseRepository warehouseRepository, StockReservationService reservationService,
                          SystemParameterRepository parameterRepository, CurrentUser currentUser,
                          AuditService auditService, Clock clock) {
        this.pickingRepository = pickingRepository;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.lotRepository = lotRepository;
        this.warehouseRepository = warehouseRepository;
        this.reservationService = reservationService;
        this.parameterRepository = parameterRepository;
        this.currentUser = currentUser;
        this.auditService = auditService;
        this.clock = clock;
    }

    // ------------------------------------------------------------- geracao

    // A separacao nasce da reserva feita na aprovacao: cada lote reservado
    // pelo FEFO vira uma linha para o separador conferir na camara.
    @Transactional
    public PickingList generate(Long orderId) {
        SalesOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido nao encontrado: " + orderId));
        if (order.getStatus() != SalesOrder.Status.APROVADO) {
            throw new BusinessRuleException("So pedido aprovado vai para separacao. Este esta "
                    + order.getStatus().getLabel().toLowerCase() + ".");
        }
        pickingRepository.findByOrderIdAndStatusNot(orderId, PickingList.Status.CANCELADA).ifPresent(existing -> {
            throw new BusinessRuleException("O pedido ja tem a separacao #" + existing.getId() + ".");
        });

        PickingList picking = new PickingList(orderId, currentUser.username());
        List<StockReservation> reservations = reservationService.activeFor(
                order.getItems().stream().map(SalesOrderItem::getId).toList());
        if (reservations.isEmpty()) {
            throw new BusinessRuleException("O pedido nao tem estoque reservado. Aprove o pedido novamente.");
        }
        for (StockReservation reservation : reservations) {
            // ja vem preenchido com o sugerido: peso fixo confirma sem digitar
            picking.addItem(new PickingItem(reservation.getOrderItemId(), reservation.getProductId(),
                    reservation.getWarehouseId(), reservation.getLotId(), reservation.getQuantity(),
                    reservation.getQuantity()));
        }
        order.moveTo(SalesOrder.Status.EM_SEPARACAO);
        return pickingRepository.save(picking);
    }

    // ---------------------------------------------------------- separacao

    @Transactional
    public PickingList saveSeparation(Long id, SeparationRequest request) {
        PickingList picking = load(id);
        if (picking.getStatus() != PickingList.Status.ABERTA) {
            throw new BusinessRuleException("Esta separacao esta " + picking.getStatus().getLabel().toLowerCase() + ".");
        }
        SalesOrder order = orderRepository.findById(picking.getOrderId()).orElseThrow();
        Map<Long, SalesOrderItem> orderItems = order.getItems().stream()
                .collect(Collectors.toMap(SalesOrderItem::getId, Function.identity()));
        Map<Long, Product> products = productRepository.findAllById(order.getItems().stream()
                .map(SalesOrderItem::getProductId).distinct().toList()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, Warehouse> warehouses = warehouseRepository.findAll().stream()
                .collect(Collectors.toMap(Warehouse::getId, Function.identity()));
        BigDecimal tolerance = weightTolerancePercent();
        LocalDate today = LocalDate.now(clock);

        // o planejado original serve para apontar troca de lote
        Map<String, BigDecimal> planned = picking.getItems().stream()
                .collect(Collectors.toMap(i -> key(i.getOrderItemId(), i.getWarehouseId(), i.getLotId()),
                        PickingItem::getQtyPlanned, BigDecimal::add));

        // a reserva e refeita do zero com o que realmente foi separado
        reservationService.release(orderItems.keySet());
        picking.clearItems();
        // o que a separacao apontou e recalculado; o que a conferencia
        // encontrou fica no historico, senao o erro desaparece ao refazer
        picking.getDivergences().removeIf(d -> d.getType() != PickingDivergence.Type.CONFERENCIA);
        picking.setNotes(blank(request.notes()));

        BigDecimal pickedGrandTotal = BigDecimal.ZERO;
        for (PickItemRequest line : request.items()) {
            SalesOrderItem orderItem = orderItems.get(line.orderItemId());
            if (orderItem == null) {
                throw new BusinessRuleException("Item " + line.orderItemId() + " nao e deste pedido.");
            }
            Product product = products.get(orderItem.getProductId());
            BigDecimal picked = BigDecimal.ZERO;
            List<String> pickedLots = new ArrayList<>();

            for (PickLine pick : line.lines()) {
                if (pick.quantity().signum() == 0) {
                    continue;
                }
                if (pick.quantity().signum() < 0) {
                    throw new BusinessRuleException("Quantidade separada nao pode ser negativa.");
                }
                Warehouse warehouse = warehouses.get(pick.warehouseId());
                if (warehouse == null || !warehouse.isActive()) {
                    throw new BusinessRuleException("Deposito invalido na separacao de " + product.getName() + ".");
                }
                Lot lot = checkedLot(product, pick.lotId(), today);
                BigDecimal quantity = pick.quantity().setScale(3, RoundingMode.HALF_UP);
                String describe = product.getName() + (lot == null ? "" : " lote " + lot.getCode());
                reservationService.reserveExact(orderItem.getId(), product.getId(), warehouse.getId(),
                        pick.lotId(), quantity, describe);
                picking.addItem(new PickingItem(orderItem.getId(), product.getId(), warehouse.getId(), pick.lotId(),
                        planned.getOrDefault(key(orderItem.getId(), warehouse.getId(), pick.lotId()), BigDecimal.ZERO),
                        quantity));
                picked = picked.add(quantity);
                if (lot != null) {
                    pickedLots.add(lot.getCode());
                }
                if (!planned.containsKey(key(orderItem.getId(), warehouse.getId(), pick.lotId())) && lot != null) {
                    picking.addDivergence(new PickingDivergence(orderItem.getId(), product.getId(),
                            PickingDivergence.Type.TROCA_LOTE, BigDecimal.ZERO, quantity,
                            "Separado do lote " + lot.getCode() + ", fora da sugestao do sistema",
                            currentUser.username()));
                }
            }
            registerWeightDivergence(picking, orderItem, product, picked, tolerance, blank(line.note()), pickedLots);
            pickedGrandTotal = pickedGrandTotal.add(picked);
        }
        if (pickedGrandTotal.signum() == 0) {
            throw new BusinessRuleException("Nada foi separado. Informe as quantidades ou cancele a separacao.");
        }
        picking.markSeparated(currentUser.username());

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("pedido", order.getId());
        values.put("linhas", picking.getItems().size());
        values.put("divergencias", picking.getDivergences().stream()
                .map(d -> d.getType().getLabel() + ": " + d.getDetail()).toList());
        auditService.recordChange(AuditAction.SEPARACAO_CONFIRMADA, "PickingList", picking.getId(), null, values, null);
        return pickingRepository.save(picking);
    }

    // Peso variavel pode sair diferente do pedido (a peca nao e exata); o que
    // nao pode e sair calado. Peso fixo nao aceita sobra.
    private void registerWeightDivergence(PickingList picking, SalesOrderItem orderItem, Product product,
                                          BigDecimal picked, BigDecimal tolerance, String note,
                                          List<String> pickedLots) {
        BigDecimal ordered = orderItem.getQtyBase();
        boolean variable = product.isVariableWeight() || orderItem.isNominal();
        WeightRules.Outcome outcome = WeightRules.evaluate(ordered, picked, variable, tolerance);
        if (outcome == WeightRules.Outcome.EXATO || outcome == WeightRules.Outcome.DENTRO_TOLERANCIA) {
            return;
        }
        String unit = product.getBaseUnit().toLowerCase();
        String detail = (note != null ? note + ". " : "") + "Pedido " + qty(ordered) + " " + unit
                + ", separado " + qty(picked) + " " + unit
                + (pickedLots.isEmpty() ? "" : " (lotes " + String.join(", ", pickedLots) + ")");

        if (outcome == WeightRules.Outcome.SOBRA_PROIBIDA) {
            throw new BusinessRuleException(product.getName() + " nao e de peso variavel: nao da para separar "
                    + qty(picked) + " " + unit + " para um pedido de " + qty(ordered) + " " + unit + ".");
        }
        if (outcome == WeightRules.Outcome.FORA_TOLERANCIA) {
            picking.addDivergence(new PickingDivergence(orderItem.getId(), product.getId(),
                    PickingDivergence.Type.PESO_FORA_TOLERANCIA, ordered, picked,
                    detail + ". Variacao de " + qty(WeightRules.deviationPercent(ordered, picked))
                            + "%, tolerancia " + qty(tolerance) + "%",
                    currentUser.username()));
            return;
        }
        picking.addDivergence(new PickingDivergence(orderItem.getId(), product.getId(),
                PickingDivergence.Type.FALTA, ordered, picked, detail, currentUser.username()));
    }

    // --------------------------------------------------------- conferencia

    @Transactional
    public PickingList check(Long id, CheckRequest request) {
        PickingList picking = load(id);
        if (picking.getStatus() != PickingList.Status.SEPARADA) {
            throw new BusinessRuleException("So separacao concluida pode ser conferida.");
        }
        if (checkerMustDiffer() && Objects.equals(picking.getSeparatedBy(), currentUser.username())) {
            throw new AccessDeniedException("Quem separou nao pode conferir. "
                    + "Peca a conferencia a outra pessoa ou desligue a exigencia nos parametros.");
        }
        Map<Long, BigDecimal> checked = request.lines().stream()
                .collect(Collectors.toMap(CheckLine::pickingItemId, CheckLine::quantity));
        Map<Long, Product> products = productRepository.findAllById(picking.getItems().stream()
                .map(PickingItem::getProductId).distinct().toList()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        boolean diverged = false;
        for (PickingItem item : picking.getItems()) {
            BigDecimal value = checked.get(item.getId());
            if (value == null) {
                throw new BusinessRuleException("Confira todas as linhas da separacao antes de concluir.");
            }
            BigDecimal conferred = value.setScale(3, RoundingMode.HALF_UP);
            item.check(conferred);
            if (conferred.compareTo(item.getQtyPicked()) != 0) {
                diverged = true;
                Product product = products.get(item.getProductId());
                picking.addDivergence(new PickingDivergence(item.getOrderItemId(), item.getProductId(),
                        PickingDivergence.Type.CONFERENCIA, item.getQtyPicked(), conferred,
                        "Conferencia encontrou " + qty(conferred) + " onde a separacao registrou "
                                + qty(item.getQtyPicked()) + " " + product.getBaseUnit().toLowerCase(),
                        currentUser.username()));
            }
        }

        SalesOrder order = orderRepository.findById(picking.getOrderId()).orElseThrow();
        if (diverged) {
            // a mercadoria precisa voltar para a balanca: separacao reaberta
            picking.backToPicking();
            auditService.recordChange(AuditAction.SEPARACAO_CONFERIDA, "PickingList", picking.getId(), null,
                    Map.of("pedido", order.getId(), "resultado", "divergencia, separacao reaberta"), null);
            return pickingRepository.save(picking);
        }
        picking.markChecked(currentUser.username());
        order.moveTo(SalesOrder.Status.SEPARADO);
        auditService.recordChange(AuditAction.SEPARACAO_CONFERIDA, "PickingList", picking.getId(), null,
                Map.of("pedido", order.getId(), "resultado", "conferida, liberada para faturamento"), null);
        return pickingRepository.save(picking);
    }

    // ------------------------------------------------------------ cancelar

    // Cancelar devolve o pedido para aprovado e recoloca a reserva pelo FEFO:
    // o pedido aprovado sempre tem estoque reservado.
    @Transactional
    public PickingList cancel(Long id, String reason) {
        PickingList picking = load(id);
        if (picking.getStatus() == PickingList.Status.CANCELADA) {
            throw new BusinessRuleException("Separacao ja cancelada.");
        }
        SalesOrder order = orderRepository.findById(picking.getOrderId()).orElseThrow();
        if (order.getStatus() == SalesOrder.Status.FATURADO) {
            throw new BusinessRuleException("O pedido ja foi faturado. Cancele o faturamento primeiro.");
        }
        Map<Long, Product> products = productRepository.findAllById(order.getItems().stream()
                .map(SalesOrderItem::getProductId).distinct().toList()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        reservationService.release(order.getItems().stream().map(SalesOrderItem::getId).toList());
        for (SalesOrderItem item : order.getItems()) {
            reservationService.reserve(item.getId(), item.getProductId(), item.getQtyBase(),
                    products.get(item.getProductId()).getName());
        }
        picking.cancel(currentUser.username(), reason.trim());
        order.moveTo(SalesOrder.Status.APROVADO);
        auditService.recordChange(AuditAction.SEPARACAO_CANCELADA, "PickingList", picking.getId(), null,
                Map.of("pedido", order.getId()), reason.trim());
        return pickingRepository.save(picking);
    }

    // -------------------------------------------------------------- apoio

    public PickingList load(Long id) {
        return pickingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Separacao nao encontrada: " + id));
    }

    private Lot checkedLot(Product product, Long lotId, LocalDate today) {
        if (!product.isLotControl()) {
            if (lotId != null) {
                throw new BusinessRuleException(product.getName() + " nao controla lote.");
            }
            return null;
        }
        if (lotId == null) {
            throw new BusinessRuleException("Informe o lote separado de " + product.getName() + ".");
        }
        Lot lot = lotRepository.findById(lotId)
                .orElseThrow(() -> new ResourceNotFoundException("Lote nao encontrado: " + lotId));
        if (!lot.getProductId().equals(product.getId())) {
            throw new BusinessRuleException("O lote informado e de outro produto.");
        }
        if (lot.isExpired(today)) {
            throw new BusinessRuleException("O lote " + lot.getCode() + " esta vencido e nao pode ser expedido.");
        }
        return lot;
    }

    public BigDecimal weightTolerancePercent() {
        return parameterRepository.findById(SystemParameter.WEIGHT_TOLERANCE)
                .map(p -> new BigDecimal(p.getValue()))
                .orElse(BigDecimal.ZERO);
    }

    public boolean checkerMustDiffer() {
        return parameterRepository.findById(SystemParameter.DIFFERENT_CHECKER)
                .map(p -> Boolean.parseBoolean(p.getValue()))
                .orElse(false);
    }

    private static String key(Long orderItemId, Long warehouseId, Long lotId) {
        return orderItemId + ":" + warehouseId + ":" + lotId;
    }

    private static String qty(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

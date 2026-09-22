package com.distribuidora.backend.estoque;

import com.distribuidora.backend.cadastros.SupplierRepository;
import com.distribuidora.backend.cadastros.Unit;
import com.distribuidora.backend.cadastros.UnitRepository;
import com.distribuidora.backend.estoque.StockDtos.BlockRequest;
import com.distribuidora.backend.estoque.StockDtos.EntryRequest;
import com.distribuidora.backend.estoque.StockDtos.ExitRequest;
import com.distribuidora.backend.estoque.StockDtos.FefoAllocation;
import com.distribuidora.backend.estoque.StockDtos.FefoResponse;
import com.distribuidora.backend.estoque.StockDtos.LossRequest;
import com.distribuidora.backend.estoque.StockDtos.Qty;
import com.distribuidora.backend.estoque.StockDtos.TransferRequest;
import com.distribuidora.backend.estoque.StockEnums.LossReason;
import com.distribuidora.backend.estoque.StockEnums.MovementType;
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
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

// Unico ponto que altera saldo de estoque. Todo metodo publico grava o
// movimento e o saldo na mesma transacao.
@Service
public class StockService {

    static final String CUSTO_ALTERAR = "produtos.custo.alterar";

    private final StockBalanceRepository balanceRepository;
    private final StockMovementRepository movementRepository;
    private final LotRepository lotRepository;
    private final WarehouseRepository warehouseRepository;
    private final InventoryCountRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final UnitRepository unitRepository;
    private final SupplierRepository supplierRepository;
    private final CurrentUser currentUser;
    private final Clock clock;

    public StockService(StockBalanceRepository balanceRepository, StockMovementRepository movementRepository,
                        LotRepository lotRepository, WarehouseRepository warehouseRepository,
                        InventoryCountRepository inventoryRepository, ProductRepository productRepository,
                        UnitRepository unitRepository, SupplierRepository supplierRepository,
                        CurrentUser currentUser, Clock clock) {
        this.balanceRepository = balanceRepository;
        this.movementRepository = movementRepository;
        this.lotRepository = lotRepository;
        this.warehouseRepository = warehouseRepository;
        this.inventoryRepository = inventoryRepository;
        this.productRepository = productRepository;
        this.unitRepository = unitRepository;
        this.supplierRepository = supplierRepository;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ entrada

    @Transactional
    public StockMovement entry(EntryRequest request) {
        Product product = activeProduct(request.productId());
        Warehouse warehouse = activeWarehouse(request.warehouseId());
        ensureNoOpenInventory(warehouse.getId());
        BigDecimal quantity = toBase(product, request.qty());
        Lot lot = resolveEntryLot(product, request);

        BigDecimal unitCost = request.unitCost();
        if (unitCost != null) {
            if (!currentUser.can(CUSTO_ALTERAR)) {
                throw new AccessDeniedException("Voce nao tem permissao para informar custo. Deixe o custo em branco.");
            }
            updateAverageCost(product, quantity, unitCost);
        } else {
            unitCost = product.getAverageCost();
        }

        MovementType type = request.kind() == StockDtos.EntryKind.IMPLANTACAO
                ? MovementType.IMPLANTACAO : MovementType.ENTRADA_MANUAL;
        return apply(type, warehouse.getId(), product, lot, quantity, unitCost, null, request.reason(),
                request.document(), null, null, null);
    }

    // Custo medio ponderado pelo saldo de todos os depositos.
    private void updateAverageCost(Product product, BigDecimal quantity, BigDecimal unitCost) {
        BigDecimal current = balanceRepository.findByProductId(product.getId()).stream()
                .map(StockBalance::getQtyPhysical).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = product.getAverageCost();
        BigDecimal newAverage = average == null || current.signum() == 0
                ? unitCost
                : current.multiply(average).add(quantity.multiply(unitCost))
                        .divide(current.add(quantity), 4, RoundingMode.HALF_UP);
        product.setAverageCost(newAverage);
        product.setCostPrice(unitCost);
    }

    private Lot resolveEntryLot(Product product, EntryRequest request) {
        if (!product.isLotControl()) {
            if (request.lotCode() != null && !request.lotCode().isBlank()) {
                throw new BusinessRuleException("Este produto nao controla lote. Deixe o lote em branco.");
            }
            return null;
        }
        if (request.lotCode() == null || request.lotCode().isBlank()) {
            throw new BusinessRuleException("Informe o lote: este produto controla lote.");
        }
        if (product.isExpiryControl() && request.expiresOn() == null) {
            throw new BusinessRuleException("Informe a validade do lote.");
        }
        if (request.expiresOn() != null && request.expiresOn().isBefore(today())) {
            throw new BusinessRuleException("Lote ja vencido nao pode entrar como disponivel.");
        }
        if (request.supplierId() != null && !supplierRepository.existsById(request.supplierId())) {
            throw new BusinessRuleException("Fornecedor inexistente.");
        }
        String code = request.lotCode().trim().toUpperCase();
        return lotRepository.findByProductIdAndCode(product.getId(), code)
                .map(existing -> {
                    if (request.expiresOn() != null && !request.expiresOn().equals(existing.getExpiresOn())) {
                        throw new BusinessRuleException("O lote " + code + " ja existe com validade "
                                + existing.getExpiresOn() + ". Confira o lote ou a validade.");
                    }
                    return existing;
                })
                .orElseGet(() -> lotRepository.save(new Lot(product.getId(), code, request.supplierId(),
                        request.manufacturedOn(), request.expiresOn(), request.document())));
    }

    // ------------------------------------------------------------------ saidas

    @Transactional
    public StockMovement exit(ExitRequest request) {
        Product product = productOf(request.productId());
        ensureNoOpenInventory(request.warehouseId());
        Lot lot = existingLot(product, request.lotId());
        rejectExpired(lot, "Lote vencido: registre como perda por vencimento.");
        return apply(MovementType.SAIDA_MANUAL, request.warehouseId(), product, lot, toBase(product, request.qty()),
                product.getAverageCost(), null, request.reason(), request.document(), null, null, null);
    }

    @Transactional
    public StockMovement loss(LossRequest request) {
        Product product = productOf(request.productId());
        ensureNoOpenInventory(request.warehouseId());
        Lot lot = existingLot(product, request.lotId());
        MovementType type = switch (request.kind()) {
            case PERDA -> MovementType.PERDA;
            case AVARIA -> MovementType.AVARIA;
            case BAIXA_AVARIADO -> MovementType.PERDA_AVARIADO;
            case BAIXA_BLOQUEADO -> MovementType.PERDA_BLOQUEADO;
        };
        if (request.lossReason() == LossReason.OUTROS && (request.reason() == null || request.reason().isBlank())) {
            throw new BusinessRuleException("Descreva a perda quando o motivo for \"Outros\".");
        }
        return apply(type, request.warehouseId(), product, lot, toBase(product, request.qty()),
                product.getAverageCost(), request.lossReason(), request.reason(), null, null, null, null);
    }

    @Transactional
    public StockMovement block(BlockRequest request) {
        Product product = productOf(request.productId());
        ensureNoOpenInventory(request.warehouseId());
        Lot lot = existingLot(product, request.lotId());
        if (!request.block()) {
            rejectExpired(lot, "Lote vencido nao pode ser liberado: registre a baixa do bloqueado.");
        }
        return apply(request.block() ? MovementType.BLOQUEIO : MovementType.DESBLOQUEIO, request.warehouseId(),
                product, lot, toBase(product, request.qty()), product.getAverageCost(), null, request.reason(),
                null, null, null, null);
    }

    @Transactional
    public List<StockMovement> transfer(TransferRequest request) {
        if (request.fromWarehouseId().equals(request.toWarehouseId())) {
            throw new BusinessRuleException("Escolha depositos diferentes.");
        }
        Product product = activeProduct(request.productId());
        activeWarehouse(request.toWarehouseId());
        ensureNoOpenInventory(request.fromWarehouseId());
        ensureNoOpenInventory(request.toWarehouseId());
        Lot lot = existingLot(product, request.lotId());
        rejectExpired(lot, "Lote vencido nao pode ser transferido.");
        BigDecimal quantity = toBase(product, request.qty());
        UUID group = UUID.randomUUID();
        StockMovement out = apply(MovementType.TRANSFERENCIA_SAIDA, request.fromWarehouseId(), product, lot, quantity,
                product.getAverageCost(), null, request.reason(), null, null, null, group);
        StockMovement in = apply(MovementType.TRANSFERENCIA_ENTRADA, request.toWarehouseId(), product, lot, quantity,
                product.getAverageCost(), null, request.reason(), null, null, null, group);
        return List.of(out, in);
    }

    // ------------------------------------------------------------------ FEFO

    // Sugestao de lotes para separar: vence primeiro, sai primeiro. Ignora
    // vencidos e o que esta bloqueado/avariado. Sem lote: FIFO do saldo unico.
    @Transactional(readOnly = true)
    public FefoResponse fefo(Long productId, BigDecimal quantity, Long warehouseId) {
        productOf(productId);
        LocalDate today = today();
        List<StockBalance> balances = balanceRepository.findByProductId(productId).stream()
                .filter(b -> warehouseId == null || b.getWarehouseId().equals(warehouseId))
                .filter(b -> b.available().signum() > 0)
                .toList();
        Map<Long, Lot> lots = lotRepository.findAllById(balances.stream().map(StockBalance::getLotId)
                .filter(Objects::nonNull).toList()).stream().collect(Collectors.toMap(Lot::getId, l -> l));

        List<StockBalance> ordered = balances.stream()
                .filter(b -> b.getLotId() == null || !lots.get(b.getLotId()).isExpired(today))
                .sorted(Comparator
                        .comparing((StockBalance b) -> b.getLotId() == null ? null : lots.get(b.getLotId()).getExpiresOn(),
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(b -> b.getLotId() == null ? null : lots.get(b.getLotId()).getReceivedAt(),
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        List<FefoAllocation> allocations = new ArrayList<>();
        BigDecimal remaining = quantity;
        for (StockBalance balance : ordered) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal take = balance.available().min(remaining);
            Lot lot = balance.getLotId() == null ? null : lots.get(balance.getLotId());
            allocations.add(new FefoAllocation(balance.getLotId(), lot == null ? null : lot.getCode(),
                    lot == null ? null : lot.getExpiresOn(), balance.getWarehouseId(), take));
            remaining = remaining.subtract(take);
        }
        BigDecimal allocated = quantity.subtract(remaining.max(BigDecimal.ZERO));
        return new FefoResponse(quantity, allocated, remaining.max(BigDecimal.ZERO), allocations);
    }

    // ------------------------------------------------------------------ nucleo

    // Usado tambem pelo inventario (que ja congelou o deposito).
    StockMovement apply(MovementType type, Long warehouseId, Product product, Lot lot, BigDecimal quantity,
                        BigDecimal unitCost, LossReason lossReason, String reason, String document,
                        String sourceType, Long sourceId, UUID groupId) {
        if (quantity.signum() <= 0) {
            throw new BusinessRuleException("A quantidade precisa ser maior que zero.");
        }
        Long lotId = lot == null ? null : lot.getId();
        StockBalance balance = (lotId == null
                ? balanceRepository.lockWithoutLot(warehouseId, product.getId())
                : balanceRepository.lockWithLot(warehouseId, product.getId(), lotId))
                .orElseGet(() -> {
                    if (type.from() != StockEnums.Bucket.EXTERNO) {
                        throw new BusinessRuleException("Nao ha saldo deste produto"
                                + (lot == null ? "" : " no lote " + lot.getCode()) + " neste deposito.");
                    }
                    return new StockBalance(warehouseId, product.getId(), lotId);
                });
        balance.move(type.from(), type.to(), quantity);
        balanceRepository.save(balance);

        return movementRepository.save(new StockMovement(type, warehouseId, product.getId(), lotId, quantity,
                unitCost, lossReason, blank(reason), blank(document), currentUser.id(), currentUser.username(),
                sourceType, sourceId, groupId));
    }

    BigDecimal toBase(Product product, Qty qty) {
        Map<String, Unit> units = unitRepository.findAll().stream().collect(Collectors.toMap(Unit::getCode, u -> u));
        Unit base = units.get(product.getBaseUnit());
        BigDecimal quantity;
        if (qty.unitCode().equals(product.getBaseUnit())) {
            quantity = qty.quantity();
        } else {
            ProductUnit conversion = product.getUnits().stream()
                    .filter(u -> u.getUnitCode().equals(qty.unitCode()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessRuleException("O produto nao tem conversao para " + qty.unitCode() + "."));
            if (conversion.isNominal()) {
                // peso de caixa aproximado: estoque precisa do peso real
                throw new BusinessRuleException("Produto de peso variavel: informe o peso real em "
                        + product.getBaseUnit() + ".");
            }
            quantity = qty.quantity().multiply(conversion.getFactor());
        }
        if (base != null && !base.isAllowsDecimal() && quantity.stripTrailingZeros().scale() > 0) {
            throw new BusinessRuleException("A quantidade em " + base.getName().toLowerCase() + " precisa ser inteira.");
        }
        return quantity.setScale(3, RoundingMode.HALF_UP);
    }

    void ensureNoOpenInventory(Long warehouseId) {
        if (inventoryRepository.existsByWarehouseIdAndStatus(warehouseId, InventoryCount.Status.ABERTO)) {
            throw new BusinessRuleException("Ha um inventario em andamento neste deposito. "
                    + "Feche ou cancele o inventario antes de movimentar.");
        }
    }

    private Lot existingLot(Product product, Long lotId) {
        if (!product.isLotControl()) {
            if (lotId != null) {
                throw new BusinessRuleException("Este produto nao controla lote.");
            }
            return null;
        }
        if (lotId == null) {
            throw new BusinessRuleException("Escolha o lote.");
        }
        Lot lot = lotRepository.findById(lotId)
                .orElseThrow(() -> new ResourceNotFoundException("Lote nao encontrado: " + lotId));
        if (!lot.getProductId().equals(product.getId())) {
            throw new BusinessRuleException("O lote informado e de outro produto.");
        }
        return lot;
    }

    private void rejectExpired(Lot lot, String message) {
        if (lot != null && lot.isExpired(today())) {
            throw new BusinessRuleException(message);
        }
    }

    private Product productOf(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produto nao encontrado: " + id));
    }

    private Product activeProduct(Long id) {
        Product product = productOf(id);
        if (!product.isActive()) {
            throw new BusinessRuleException("Produto inativo nao recebe estoque.");
        }
        return product;
    }

    Warehouse activeWarehouse(Long id) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Deposito nao encontrado: " + id));
        if (!warehouse.isActive()) {
            throw new BusinessRuleException("Deposito inativo.");
        }
        return warehouse;
    }

    LocalDate today() {
        return LocalDate.now(clock);
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

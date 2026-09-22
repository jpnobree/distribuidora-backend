package com.distribuidora.backend.estoque;

import com.distribuidora.backend.estoque.StockDtos.FefoAllocation;
import com.distribuidora.backend.estoque.StockDtos.FefoResponse;
import com.distribuidora.backend.exception.BusinessRuleException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

// Reserva de estoque para pedidos: separa por lote (FEFO) o que foi vendido
// para que outro pedido nao venda a mesma mercadoria. Sempre dentro da
// transacao do pedido (MANDATORY): pedido e reserva ficam juntos ou nada.
@Service
public class StockReservationService {

    private final StockService stockService;
    private final StockBalanceRepository balanceRepository;
    private final StockReservationRepository reservationRepository;

    public StockReservationService(StockService stockService, StockBalanceRepository balanceRepository,
                                   StockReservationRepository reservationRepository) {
        this.stockService = stockService;
        this.balanceRepository = balanceRepository;
        this.reservationRepository = reservationRepository;
    }

    // Quanto pode ser vendido agora (todos os depositos, sem vencidos).
    @Transactional(readOnly = true)
    public BigDecimal availableForSale(Long productId) {
        return stockService.fefo(productId, new BigDecimal("999999999"), null).allocated();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<StockReservation> reserve(Long orderItemId, Long productId, BigDecimal quantity, String productName) {
        FefoResponse plan = stockService.fefo(productId, quantity, null);
        if (plan.missing().signum() > 0) {
            throw new BusinessRuleException("Estoque insuficiente de " + productName + ": disponivel "
                    + plan.allocated().stripTrailingZeros().toPlainString() + ", pedido "
                    + quantity.stripTrailingZeros().toPlainString() + ".");
        }
        return plan.allocations().stream().map(allocation -> lockAndReserve(orderItemId, productId, allocation)).toList();
    }

    private StockReservation lockAndReserve(Long orderItemId, Long productId, FefoAllocation allocation) {
        StockBalance balance = (allocation.lotId() == null
                ? balanceRepository.lockWithoutLot(allocation.warehouseId(), productId)
                : balanceRepository.lockWithLot(allocation.warehouseId(), productId, allocation.lotId()))
                .orElseThrow(() -> new BusinessRuleException("Saldo mudou durante a reserva. Tente de novo."));
        // outro pedido pode ter reservado entre a sugestao e o bloqueio da linha
        balance.reserve(allocation.quantity());
        balanceRepository.save(balance);
        return reservationRepository.save(new StockReservation(orderItemId, allocation.warehouseId(), productId,
                allocation.lotId(), allocation.quantity()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void release(Collection<Long> orderItemIds) {
        for (StockReservation reservation : reservationRepository.findByOrderItemIdInAndReleasedAtIsNull(orderItemIds)) {
            StockBalance balance = (reservation.getLotId() == null
                    ? balanceRepository.lockWithoutLot(reservation.getWarehouseId(), reservation.getProductId())
                    : balanceRepository.lockWithLot(reservation.getWarehouseId(), reservation.getProductId(),
                    reservation.getLotId()))
                    .orElseThrow();
            balance.release(reservation.getQuantity());
            balanceRepository.save(balance);
            reservation.markReleased();
        }
    }

    @Transactional(readOnly = true)
    public List<StockReservation> activeFor(Collection<Long> orderItemIds) {
        return reservationRepository.findByOrderItemIdInAndReleasedAtIsNull(orderItemIds);
    }
}

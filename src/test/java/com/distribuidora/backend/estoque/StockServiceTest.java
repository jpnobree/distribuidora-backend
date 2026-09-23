package com.distribuidora.backend.estoque;

import com.distribuidora.backend.cadastros.SupplierRepository;
import com.distribuidora.backend.cadastros.Unit;
import com.distribuidora.backend.cadastros.UnitRepository;
import com.distribuidora.backend.estoque.StockDtos.EntryKind;
import com.distribuidora.backend.estoque.StockDtos.EntryRequest;
import com.distribuidora.backend.estoque.StockDtos.ExitRequest;
import com.distribuidora.backend.estoque.StockDtos.FefoResponse;
import com.distribuidora.backend.estoque.StockDtos.Qty;
import com.distribuidora.backend.exception.BusinessRuleException;
import com.distribuidora.backend.model.Product;
import com.distribuidora.backend.model.ProductUnit;
import com.distribuidora.backend.repository.ProductRepository;
import com.distribuidora.backend.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 22);

    @Mock StockBalanceRepository balanceRepository;
    @Mock StockMovementRepository movementRepository;
    @Mock LotRepository lotRepository;
    @Mock WarehouseRepository warehouseRepository;
    @Mock InventoryCountRepository inventoryRepository;
    @Mock ProductRepository productRepository;
    @Mock UnitRepository unitRepository;
    @Mock SupplierRepository supplierRepository;
    @Mock CurrentUser currentUser;

    private StockService service;
    private Product costela;

    @BeforeEach
    void setUp() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-09-22T15:00:00Z"), ZoneId.of("America/Fortaleza"));
        service = new StockService(balanceRepository, movementRepository, lotRepository, warehouseRepository,
                inventoryRepository, productRepository, unitRepository, supplierRepository, currentUser, clock);

        var ctor = Unit.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        Unit kg = ctor.newInstance();
        ReflectionTestUtils.setField(kg, "code", "KG");
        ReflectionTestUtils.setField(kg, "name", "Quilograma");
        ReflectionTestUtils.setField(kg, "allowsDecimal", true);
        when(unitRepository.findAll()).thenReturn(List.of(kg));

        costela = new Product();
        costela.setId(10L);
        costela.setName("Costela");
        costela.setBaseUnit("KG");
        costela.setLotControl(true);
        costela.setExpiryControl(true);
        costela.getUnits().add(new ProductUnit("CX", new BigDecimal("20"), true, null));
        when(productRepository.findById(10L)).thenReturn(Optional.of(costela));

        Warehouse principal = new Warehouse(1L, "PRINCIPAL", "Principal");
        ReflectionTestUtils.setField(principal, "id", 1L);
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(principal));
        when(movementRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(lotRepository.save(any())).thenAnswer(i -> {
            Lot lot = i.getArgument(0);
            ReflectionTestUtils.setField(lot, "id", 50L);
            return lot;
        });
        when(balanceRepository.lockWithLot(any(), any(), any())).thenReturn(Optional.empty());
        when(balanceRepository.findByProductId(10L)).thenReturn(List.of());
    }

    private EntryRequest entry(String qty, String unit, String lot, LocalDate expires, BigDecimal cost) {
        return new EntryRequest(10L, 1L, new Qty(new BigDecimal(qty), unit), EntryKind.IMPLANTACAO, lot, null,
                expires, null, cost, null, null);
    }

    @Test
    void entradaEmCaixaDePesoVariavel_exigePesoReal() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.entry(entry("3", "CX", "L1", TODAY.plusDays(90), null)));
        assertEquals("Produto de peso variavel: informe o peso real em KG.", ex.getMessage());
    }

    @Test
    void produtoComLote_exigeLoteEValidade_eRecusaLoteVencido() {
        assertThrows(BusinessRuleException.class, () -> service.entry(entry("60", "KG", null, TODAY.plusDays(90), null)));
        assertThrows(BusinessRuleException.class, () -> service.entry(entry("60", "KG", "L1", null, null)));
        assertThrows(BusinessRuleException.class, () -> service.entry(entry("60", "KG", "L1", TODAY.minusDays(1), null)));
        verify(movementRepository, never()).save(any());
    }

    @Test
    void entradaComCusto_exigePermissao_eRecalculaCustoMedio() {
        when(currentUser.can(StockService.CUSTO_ALTERAR)).thenReturn(false);
        assertThrows(AccessDeniedException.class,
                () -> service.entry(entry("60", "KG", "L1", TODAY.plusDays(90), new BigDecimal("20"))));

        when(currentUser.can(StockService.CUSTO_ALTERAR)).thenReturn(true);
        costela.setAverageCost(new BigDecimal("18.0000"));
        StockBalance existing = new StockBalance(1L, 10L, 7L);
        existing.move(StockEnums.Bucket.EXTERNO, StockEnums.Bucket.DISPONIVEL, new BigDecimal("40"));
        when(balanceRepository.findByProductId(10L)).thenReturn(List.of(existing));

        service.entry(entry("60", "KG", "L2", TODAY.plusDays(90), new BigDecimal("20")));

        // (40 x 18 + 60 x 20) / 100 = 19,20
        assertEquals(0, new BigDecimal("19.2").compareTo(costela.getAverageCost()));
        assertEquals(0, new BigDecimal("20").compareTo(costela.getCostPrice()));
    }

    @Test
    void inventarioAbertoCongelaODeposito() {
        when(inventoryRepository.existsByWarehouseIdAndStatus(1L, InventoryCount.Status.ABERTO)).thenReturn(true);
        assertThrows(BusinessRuleException.class, () -> service.entry(entry("60", "KG", "L1", TODAY.plusDays(90), null)));
    }

    @Test
    void saidaManualDeLoteVencidoEhRecusada() {
        Lot vencido = new Lot(10L, "V1", null, null, TODAY.minusDays(2), null);
        ReflectionTestUtils.setField(vencido, "id", 7L);
        when(lotRepository.findById(7L)).thenReturn(Optional.of(vencido));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.exit(
                new ExitRequest(10L, 1L, 7L, new Qty(new BigDecimal("5"), "KG"), "venda balcao", null)));
        assertEquals("Lote vencido: registre como perda por vencimento.", ex.getMessage());
    }

    @Test
    void fefoSugereQuemVencePrimeiro_eIgnoraVencidoEBloqueado() {
        Lot a = lot(1L, "A", TODAY.plusDays(18));
        Lot b = lot(2L, "B", TODAY.plusDays(33));
        Lot c = lot(3L, "C", TODAY.plusDays(54));
        Lot vencido = lot(4L, "V", TODAY.minusDays(1));
        when(lotRepository.findAllById(any())).thenReturn(List.of(a, b, c, vencido));
        when(balanceRepository.findByProductId(10L)).thenReturn(List.of(
                balance(3L, "50", "0"), balance(1L, "30", "10"), balance(4L, "100", "0"), balance(2L, "40", "0")));

        FefoResponse fefo = service.fefo(10L, new BigDecimal("70"), null);

        assertEquals(List.of("A", "B", "C"), fefo.allocations().stream().map(StockDtos.FefoAllocation::lotCode).toList());
        // A tem 30 fisicos, 10 bloqueados: so 20 disponiveis
        assertEquals(0, new BigDecimal("20").compareTo(fefo.allocations().get(0).quantity()));
        assertEquals(0, new BigDecimal("40").compareTo(fefo.allocations().get(1).quantity()));
        assertEquals(0, new BigDecimal("10").compareTo(fefo.allocations().get(2).quantity()));
        assertEquals(0, BigDecimal.ZERO.compareTo(fefo.missing()));
    }

    private static Lot lot(long id, String code, LocalDate expires) {
        Lot lot = new Lot(10L, code, null, null, expires, null);
        ReflectionTestUtils.setField(lot, "id", id);
        return lot;
    }

    private static StockBalance balance(long lotId, String physical, String blocked) {
        StockBalance b = new StockBalance(1L, 10L, lotId);
        b.move(StockEnums.Bucket.EXTERNO, StockEnums.Bucket.DISPONIVEL, new BigDecimal(physical));
        if (new BigDecimal(blocked).signum() > 0) {
            b.move(StockEnums.Bucket.DISPONIVEL, StockEnums.Bucket.BLOQUEADO, new BigDecimal(blocked));
        }
        return b;
    }
}

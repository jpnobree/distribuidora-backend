package com.distribuidora.backend.comercial;

import com.distribuidora.backend.audit.AuditService;
import com.distribuidora.backend.cadastros.Customer;
import com.distribuidora.backend.cadastros.CustomerRepository;
import com.distribuidora.backend.cadastros.PaymentTerm;
import com.distribuidora.backend.cadastros.PaymentTermRepository;
import com.distribuidora.backend.cadastros.Unit;
import com.distribuidora.backend.cadastros.UnitRepository;
import com.distribuidora.backend.comercial.SalesDtos.OrderItemRequest;
import com.distribuidora.backend.comercial.SalesDtos.OrderRequest;
import com.distribuidora.backend.comercial.SalesOrder.Block;
import com.distribuidora.backend.estoque.StockReservationService;
import com.distribuidora.backend.exception.BusinessRuleException;
import com.distribuidora.backend.financeiro.ReceivableRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SalesOrderServiceTest {

    @Mock SalesOrderRepository orderRepository;
    @Mock CustomerRepository customerRepository;
    @Mock PaymentTermRepository paymentTermRepository;
    @Mock ProductRepository productRepository;
    @Mock UnitRepository unitRepository;
    @Mock PricingService pricingService;
    @Mock StockReservationService reservationService;
    @Mock ReceivableRepository receivableRepository;
    @Mock CurrentUser currentUser;
    @Mock AuditService auditService;

    private SalesOrderService service;
    private Customer customer;
    private Product coxao;

    @BeforeEach
    void setUp() throws Exception {
        service = new SalesOrderService(orderRepository, customerRepository, paymentTermRepository, productRepository,
                unitRepository, pricingService, reservationService, receivableRepository, currentUser, auditService);

        var ctor = Unit.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        Unit kg = ctor.newInstance();
        ReflectionTestUtils.setField(kg, "code", "KG");
        ReflectionTestUtils.setField(kg, "name", "Quilograma");
        ReflectionTestUtils.setField(kg, "allowsDecimal", true);
        when(unitRepository.findAll()).thenReturn(List.of(kg));

        customer = new Customer();
        ReflectionTestUtils.setField(customer, "id", 1L);
        customer.setSellerId(7L);
        customer.setPaymentTermId(5L);
        customer.setCreditLimit(new BigDecimal("5000"));
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(paymentTermRepository.findById(5L)).thenReturn(Optional.of(new PaymentTerm("28 dias", List.of(28))));

        coxao = new Product();
        coxao.setId(24L);
        coxao.setName("Coxao Mole");
        coxao.setBaseUnit("KG");
        coxao.setPrice(new BigDecimal("39.90"));
        coxao.setAverageCost(new BigDecimal("31.50"));
        coxao.getUnits().add(new ProductUnit("CX", new BigDecimal("20"), true, null));
        when(productRepository.findById(24L)).thenReturn(Optional.of(coxao));

        when(pricingService.resolve(any(), any())).thenReturn(
                new PricingService.ResolvedPrice(new BigDecimal("39.90"), SalesOrderItem.PriceSource.PRECO_PRODUTO));
        when(pricingService.maxDiscountPercent()).thenReturn(new BigDecimal("5"));
        when(reservationService.availableForSale(24L)).thenReturn(new BigDecimal("500"));
        when(orderRepository.openTotalForCustomer(1L)).thenReturn(BigDecimal.ZERO);
        when(receivableRepository.openTotalForCustomer(1L)).thenReturn(BigDecimal.ZERO);
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // vendedor da carteira, sem alcadas
        when(currentUser.id()).thenReturn(7L);
        when(currentUser.username()).thenReturn("joao.vendas");
    }

    private SalesOrder order(String qty, String unit, String price) {
        return service.create(new OrderRequest(1L, null, null, List.of(
                new OrderItemRequest(24L, unit, new BigDecimal(qty), price == null ? null : new BigDecimal(price)))));
    }

    @Test
    void caixaDePesoVariavel_vendeEstimadoPeloFatorEReservaNaHora() {
        SalesOrder order = order("3", "CX", null);

        assertEquals(SalesOrder.Status.APROVADO, order.getStatus());
        assertTrue(order.isHasEstimatedWeight());
        // 3 CX x 20 kg x R$ 39,90
        assertEquals(0, new BigDecimal("2394.00").compareTo(order.getTotal()));
        verify(reservationService).reserve(any(), eq(24L), eq(new BigDecimal("60.000")), eq("Coxao Mole"));
    }

    @Test
    void descontoAcimaDoLimite_ficaAguardandoAprovacao_semReservar() {
        SalesOrder order = order("10", "KG", "36.00");

        assertEquals(SalesOrder.Status.AGUARDANDO_APROVACAO, order.getStatus());
        Block block = order.pendingBlocks().get(0);
        assertEquals(Block.Type.DESCONTO, block.getType());
        assertEquals("Coxao Mole: desconto de 9,77% (limite 5%)", block.getDetail());
        verify(reservationService, never()).reserve(any(), anyLong(), any(), any());
    }

    @Test
    void gerenteComAlcada_aprovaODescontoAoLancar_eFicaRegistrado() {
        when(currentUser.can("pedidos.aprovar_desconto")).thenReturn(true);
        when(currentUser.can("clientes.ver_todos")).thenReturn(true);
        when(currentUser.username()).thenReturn("gerente");

        SalesOrder order = order("10", "KG", "36.00");

        assertEquals(SalesOrder.Status.APROVADO, order.getStatus());
        assertEquals("gerente", order.getBlocks().get(0).getResolvedBy());
    }

    @Test
    void precoAbaixoDoCusto_eLimiteDeCredito_geramBloqueiosSeparados() {
        when(orderRepository.openTotalForCustomer(1L)).thenReturn(new BigDecimal("4800"));

        SalesOrder order = order("10", "KG", "30.00");

        assertEquals(List.of(Block.Type.DESCONTO, Block.Type.ABAIXO_CUSTO, Block.Type.LIMITE_CREDITO),
                order.pendingBlocks().stream().map(Block::getType).toList());
    }

    @Test
    void vendaAVista_naoConsomeLimiteDeCredito() {
        when(paymentTermRepository.findById(5L)).thenReturn(Optional.of(new PaymentTerm("À vista", List.of(0))));
        customer.setCreditLimit(BigDecimal.ZERO);

        assertEquals(SalesOrder.Status.APROVADO, order("10", "KG", null).getStatus());
    }

    @Test
    void semEstoqueDisponivel_oPedidoNaoENemCriado() {
        when(reservationService.availableForSale(24L)).thenReturn(new BigDecimal("40"));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> order("3", "CX", null));
        assertEquals("Estoque insuficiente de Coxao Mole: disponivel 40 kg, pedido 60.", ex.getMessage());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void clienteInativoOuProdutoInativo_naoCompram() {
        customer.setStatus(Customer.Status.INATIVO);
        assertThrows(BusinessRuleException.class, () -> order("1", "KG", null));

        customer.setStatus(Customer.Status.ATIVO);
        coxao.setActive(false);
        assertThrows(BusinessRuleException.class, () -> order("1", "KG", null));
    }

    @Test
    void vendedorNaoVendeParaClienteDeOutraCarteira() {
        customer.setSellerId(8L);
        assertThrows(com.distribuidora.backend.exception.ResourceNotFoundException.class, () -> order("1", "KG", null));
    }

    @Test
    void pedidoFaturado_soEDesfeitoPeloFaturamento() {
        SalesOrder order = visible(SalesOrder.Status.FATURADO);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.cancel(9L, "erro"));
        assertEquals("Pedido faturado. Cancele o faturamento antes.", ex.getMessage());
        assertTrue(!service.canCancel(order));
    }

    @Test
    void pedidoNaExpedicao_pedeCancelarASeparacaoAntes() {
        visible(SalesOrder.Status.EM_SEPARACAO);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.cancel(9L, "desistiu"));
        assertEquals("O pedido esta na expedicao. Cancele a separacao antes.", ex.getMessage());
    }

    @Test
    void creditoConsideraPedidosEmAbertoETitulosAReceber() {
        when(orderRepository.openTotalForCustomer(1L)).thenReturn(new BigDecimal("1500"));
        when(receivableRepository.openTotalForCustomer(1L)).thenReturn(new BigDecimal("3000"));

        // 1500 em pedidos + 3000 em titulos + 1995 deste pedido passa do limite de 5000
        SalesOrder order = order("50", "KG", null);
        assertEquals(SalesOrder.Status.AGUARDANDO_APROVACAO, order.getStatus());
        assertTrue(order.pendingBlocks().stream().anyMatch(b -> b.getType() == Block.Type.LIMITE_CREDITO));
        assertEquals(new BigDecimal("4500"), service.exposureOf(1L));
    }

    private SalesOrder visible(SalesOrder.Status status) {
        SalesOrder order = order("10", "KG", null);
        ReflectionTestUtils.setField(order, "id", 9L);
        order.moveTo(status);
        when(orderRepository.findById(9L)).thenReturn(Optional.of(order));
        return order;
    }
}

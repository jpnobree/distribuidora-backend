package com.distribuidora.backend.cadastros;

import com.distribuidora.backend.audit.AuditAction;
import com.distribuidora.backend.audit.AuditService;
import com.distribuidora.backend.cadastros.PartyDtos.AddressDto;
import com.distribuidora.backend.cadastros.PartyDtos.CustomerRequest;
import com.distribuidora.backend.cadastros.PartyDtos.CustomerResponse;
import com.distribuidora.backend.cadastros.PartyDtos.PartyFields;
import com.distribuidora.backend.exception.BusinessRuleException;
import com.distribuidora.backend.exception.ConflictException;
import com.distribuidora.backend.exception.ResourceNotFoundException;
import com.distribuidora.backend.repository.UserRepository;
import com.distribuidora.backend.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomerServiceTest {

    private static final String CNPJ = "11.222.333/0001-81";

    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private PaymentTermRepository paymentTermRepository;
    @Mock
    private CustomerSegmentRepository segmentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private CustomerService customerService;

    @BeforeEach
    void defaults() {
        when(paymentTermRepository.findAll()).thenReturn(List.of());
        when(segmentRepository.findAll()).thenReturn(List.of());
        when(userRepository.findAllById(any())).thenReturn(List.of());
    }

    private static CustomerRequest request(String document, BigDecimal credit, Customer.Status status, Long version) {
        PartyFields party = new PartyFields("Churrascaria Boi na Brasa Ltda", "Boi na Brasa", document, null,
                null, null, null, null, new AddressDto("60000-000", null, null, null, null, "Fortaleza", "ce"), null);
        return new CustomerRequest(party, null, 99L, null, credit, status, null, null, version);
    }

    private void loggedAsSeller(long id) {
        when(currentUser.id()).thenReturn(id);
        when(currentUser.can(CustomerService.VER_TODOS)).thenReturn(false);
        when(currentUser.can(CustomerService.CREDITO_ALTERAR)).thenReturn(false);
    }

    private static Customer existing(long id, long sellerId) {
        Customer c = new Customer();
        ReflectionTestUtils.setField(c, "id", id);
        c.setLegalName("Cliente");
        c.setDocument("11222333000181");
        c.setPersonType("PJ");
        c.setSellerId(sellerId);
        c.setCreditLimit(new BigDecimal("5000.00"));
        return c;
    }

    @Test
    void vendedorCadastraClienteNaPropriaCarteira_mesmoPedindoOutroVendedor() {
        loggedAsSeller(7L);
        when(customerRepository.existsByDocument("11222333000181")).thenReturn(false);

        CustomerResponse created = customerService.create(request(CNPJ, BigDecimal.ZERO, Customer.Status.ATIVO, null));

        assertEquals(7L, created.sellerId());
        assertEquals("PJ", created.personType());
        assertEquals("11222333000181", created.document());
        assertEquals("CE", created.address().state());
        assertEquals("60000000", created.address().cep());
        verify(auditService).recordChange(eq(AuditAction.CLIENTE_CRIADO), eq("Customer"), any(), eq(null), any(), eq(null));
    }

    @Test
    void vendedorNaoDefineLimiteDeCredito() {
        loggedAsSeller(7L);
        when(customerRepository.existsByDocument("11222333000181")).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> customerService.create(
                request(CNPJ, new BigDecimal("10000.00"), Customer.Status.ATIVO, null)));
        verify(customerRepository, never()).save(any());
    }

    @Test
    void vendedorNaoVeClienteDeOutraCarteira() {
        loggedAsSeller(7L);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(existing(1L, 8L)));

        assertThrows(ResourceNotFoundException.class, () -> customerService.findOne(1L));
    }

    @Test
    void recusaDocumentoInvalidoEDuplicado() {
        loggedAsSeller(7L);
        assertThrows(BusinessRuleException.class, () -> customerService.create(
                request("11.222.333/0001-82", BigDecimal.ZERO, Customer.Status.ATIVO, null)));

        when(customerRepository.existsByDocument("11222333000181")).thenReturn(true);
        assertThrows(ConflictException.class, () -> customerService.create(
                request(CNPJ, BigDecimal.ZERO, Customer.Status.ATIVO, null)));
    }

    @Test
    void edicaoComVersaoAntigaEhRecusada() {
        loggedAsSeller(7L);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(existing(1L, 7L)));

        assertThrows(ConflictException.class, () -> customerService.update(1L,
                request(CNPJ, new BigDecimal("5000.00"), Customer.Status.ATIVO, 3L)));
    }

    @Test
    void vendedorEditaDadosSemMexerNoCredito_eFinanceiroBloqueiaCliente() {
        loggedAsSeller(7L);
        Customer customer = existing(1L, 7L);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));

        customerService.update(1L, request(CNPJ, new BigDecimal("5000"), Customer.Status.ATIVO, 0L));
        assertThrows(AccessDeniedException.class, () -> customerService.update(1L,
                request(CNPJ, new BigDecimal("5000"), Customer.Status.BLOQUEADO, 0L)));

        when(currentUser.can(CustomerService.CREDITO_ALTERAR)).thenReturn(true);
        customerService.update(1L, request(CNPJ, new BigDecimal("5000"), Customer.Status.BLOQUEADO, 0L));
        assertEquals(Customer.Status.BLOQUEADO, customer.getStatus());
    }
}

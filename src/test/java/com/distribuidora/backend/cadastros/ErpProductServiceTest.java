package com.distribuidora.backend.cadastros;

import com.distribuidora.backend.audit.AuditService;
import com.distribuidora.backend.cadastros.ProductDtos.ErpProductRequest;
import com.distribuidora.backend.cadastros.ProductDtos.ErpProductResponse;
import com.distribuidora.backend.cadastros.ProductDtos.UnitConversion;
import com.distribuidora.backend.exception.BusinessRuleException;
import com.distribuidora.backend.model.Category;
import com.distribuidora.backend.model.Product;
import com.distribuidora.backend.repository.CategoryRepository;
import com.distribuidora.backend.repository.ProductRepository;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ErpProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private UnitRepository unitRepository;
    @Mock
    private BrandRepository brandRepository;
    @Mock
    private SupplierRepository supplierRepository;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private ErpProductService service;

    @BeforeEach
    void setUp() throws Exception {
        var ctor = Unit.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        Unit kg = ctor.newInstance();
        ReflectionTestUtils.setField(kg, "code", "KG");
        ReflectionTestUtils.setField(kg, "name", "Quilograma");
        ReflectionTestUtils.setField(kg, "allowsDecimal", true);
        Unit un = ctor.newInstance();
        ReflectionTestUtils.setField(un, "code", "UN");
        ReflectionTestUtils.setField(un, "name", "Unidade");
        Unit cx = ctor.newInstance();
        ReflectionTestUtils.setField(cx, "code", "CX");
        ReflectionTestUtils.setField(cx, "name", "Caixa");
        when(unitRepository.findAll()).thenReturn(List.of(kg, un, cx));

        Category carnes = new Category("carnes-aves", "Carnes & Aves", null);
        carnes.setId(1L);
        when(categoryRepository.findBySlug("carnes-aves")).thenReturn(Optional.of(carnes));
        when(supplierRepository.findAllById(any())).thenReturn(List.of());
        when(brandRepository.findAll()).thenReturn(List.of());
        when(productRepository.existsBySku(any())).thenReturn(false);
        when(productRepository.existsBySlug(any())).thenReturn(false);
    }

    private static ErpProductRequest request(String baseUnit, List<UnitConversion> units, boolean variableWeight,
                                             BigDecimal cost, boolean expiryControl, Integer shelfLife) {
        return new ErpProductRequest("7340", null, "Costela Bovina Rojao", null, "carnes-aves", null, null,
                baseUnit, null, variableWeight, null, Product.StorageType.CONGELADO, false, expiryControl, shelfLife,
                new BigDecimal("22.90"), cost, new BigDecimal("100"), new BigDecimal("400"), 7, null,
                units, List.of(), true, true, 0L);
    }

    @Test
    void caixaDePesoAproximadoTornaOProdutoDePesoVariavel_eValidadeImplicaLote() {
        when(currentUser.can(ErpProductService.CUSTO_ALTERAR)).thenReturn(true);
        when(currentUser.can(ErpProductService.CUSTO_VER)).thenReturn(true);

        ErpProductResponse created = service.create(request("KG",
                List.of(new UnitConversion("CX", new BigDecimal("20"), true, null)), false,
                new BigDecimal("18.50"), true, 365));

        assertTrue(created.variableWeight());
        assertTrue(created.lotControl());
        assertEquals("kg", created.unitLabel());
        assertEquals("costela-bovina-rojao-7340", created.slug());
        // (22,90 - 18,50) / 22,90
        assertEquals(new BigDecimal("19.2"), created.marginPercent());
    }

    @Test
    void pesoVariavelSoParaProdutoEmKg() {
        assertThrows(BusinessRuleException.class, () -> service.create(
                request("UN", List.of(), true, null, false, null)));
    }

    @Test
    void conversaoFracionadaRecusadaQuandoUnidadeBaseNaoAceitaDecimal() {
        assertThrows(BusinessRuleException.class, () -> service.create(request("UN",
                List.of(new UnitConversion("CX", new BigDecimal("12.5"), false, null)), false, null, false, null)));
    }

    @Test
    void validadeExigeInformarPrazo() {
        assertThrows(BusinessRuleException.class, () -> service.create(request("KG", List.of(), false, null, true, null)));
    }

    @Test
    void semPermissaoNaoDefineCusto_eNaoEnxergaCustoNemMargem() {
        when(currentUser.can(ErpProductService.CUSTO_ALTERAR)).thenReturn(false);
        when(currentUser.can(ErpProductService.CUSTO_VER)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.create(
                request("KG", List.of(), false, new BigDecimal("18.50"), false, null)));

        ErpProductResponse created = service.create(request("KG", List.of(), false, null, false, null));
        assertNull(created.costPrice());
        assertNull(created.marginPercent());
    }

    @Test
    void margemCalculadaSobreOPrecoDeVenda() {
        assertEquals(new BigDecimal("25.0"), ErpProductService.margin(new BigDecimal("40"), new BigDecimal("30")));
        assertNull(ErpProductService.margin(null, new BigDecimal("30")));
    }
}

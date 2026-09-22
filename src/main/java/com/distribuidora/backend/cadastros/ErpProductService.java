package com.distribuidora.backend.cadastros;

import com.distribuidora.backend.audit.AuditAction;
import com.distribuidora.backend.audit.AuditService;
import com.distribuidora.backend.cadastros.ProductDtos.ErpProductRequest;
import com.distribuidora.backend.cadastros.ProductDtos.ErpProductResponse;
import com.distribuidora.backend.cadastros.ProductDtos.SupplierLink;
import com.distribuidora.backend.cadastros.ProductDtos.UnitConversion;
import com.distribuidora.backend.exception.BusinessRuleException;
import com.distribuidora.backend.exception.ConflictException;
import com.distribuidora.backend.exception.ResourceNotFoundException;
import com.distribuidora.backend.model.Category;
import com.distribuidora.backend.model.Product;
import com.distribuidora.backend.model.ProductSupplier;
import com.distribuidora.backend.model.ProductUnit;
import com.distribuidora.backend.repository.CategoryRepository;
import com.distribuidora.backend.repository.ProductRepository;
import com.distribuidora.backend.security.CurrentUser;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ErpProductService {

    static final String CUSTO_VER = "produtos.custo.ver";
    static final String CUSTO_ALTERAR = "produtos.custo.alterar";
    // cache do catalogo publico (ver ProductService)
    private static final String VITRINE_CACHE = "products";

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final UnitRepository unitRepository;
    private final BrandRepository brandRepository;
    private final SupplierRepository supplierRepository;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    public ErpProductService(ProductRepository productRepository, CategoryRepository categoryRepository,
                             UnitRepository unitRepository, BrandRepository brandRepository,
                             SupplierRepository supplierRepository, CurrentUser currentUser,
                             AuditService auditService) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.unitRepository = unitRepository;
        this.brandRepository = brandRepository;
        this.supplierRepository = supplierRepository;
        this.currentUser = currentUser;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<ErpProductResponse> search(String search, String category, Long supplierId,
                                           Product.StorageType storageType, Boolean active, Pageable pageable) {
        Specification<Product> spec = Specification.where(null);
        if (search != null && !search.isBlank()) {
            String like = "%" + search.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("sku")), like),
                    cb.like(root.get("barcode"), like)));
        }
        if (category != null && !category.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("category"), category));
        }
        if (supplierId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("mainSupplierId"), supplierId));
        }
        if (storageType != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("storageType"), storageType));
        }
        if (active != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("active"), active));
        }
        Page<Product> page = productRepository.findAll(spec, pageable);
        Lookups lookups = lookups(page.getContent());
        return page.map(p -> toResponse(p, lookups));
    }

    @Transactional(readOnly = true)
    public ErpProductResponse findOne(Long id) {
        Product product = get(id);
        return toResponse(product, lookups(List.of(product)));
    }

    @Transactional
    @CacheEvict(cacheNames = VITRINE_CACHE, allEntries = true)
    public ErpProductResponse create(ErpProductRequest request) {
        if (productRepository.existsBySku(request.sku().trim())) {
            throw new ConflictException("Ja existe um produto com o SKU " + request.sku().trim());
        }
        if (request.costPrice() != null) {
            requireCostChange();
        }
        Product product = new Product();
        product.setSlug(uniqueSlug(request.name(), request.sku()));
        apply(product, request);
        product.setCostPrice(request.costPrice());
        product.setAverageCost(request.costPrice());
        productRepository.save(product);

        Map<String, Object> created = snapshot(product);
        created.putAll(costSnapshot(product));
        auditService.recordChange(AuditAction.PRODUTO_CRIADO, "Product", product.getSlug(), null, created, null);
        return toResponse(product, lookups(List.of(product)));
    }

    @Transactional
    @CacheEvict(cacheNames = VITRINE_CACHE, allEntries = true)
    public ErpProductResponse update(Long id, ErpProductRequest request) {
        Product product = get(id);
        PartySupport.checkVersion(request.version(), product.getVersion());
        if (productRepository.existsBySkuAndIdNot(request.sku().trim(), id)) {
            throw new ConflictException("Ja existe um produto com o SKU " + request.sku().trim());
        }
        Map<String, Object> before = snapshot(product);
        Map<String, Object> costBefore = costSnapshot(product);

        // Quem nao ve o custo recebe null na tela e devolve null: isso nao e
        // uma alteracao, e o custo atual e mantido.
        boolean costChanged = request.costPrice() != null
                && (product.getCostPrice() == null || product.getCostPrice().compareTo(request.costPrice()) != 0);
        if (costChanged) {
            requireCostChange();
            product.setCostPrice(request.costPrice());
            if (product.getAverageCost() == null) {
                product.setAverageCost(request.costPrice());
            }
        }
        apply(product, request);

        auditService.recordChange(AuditAction.PRODUTO_ALTERADO, "Product", product.getSlug(), before,
                snapshot(product), null);
        auditService.recordChange(AuditAction.PRODUTO_CUSTO_ALTERADO, "Product", product.getSlug(), costBefore,
                costSnapshot(product), null);
        return toResponse(product, lookups(List.of(product)));
    }

    private Product get(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produto nao encontrado: " + id));
    }

    private void requireCostChange() {
        if (!currentUser.can(CUSTO_ALTERAR)) {
            throw new AccessDeniedException("Voce nao tem permissao para alterar o custo de produtos.");
        }
    }

    private void apply(Product product, ErpProductRequest request) {
        Map<String, Unit> units = unitRepository.findAll().stream()
                .collect(Collectors.toMap(Unit::getCode, u -> u));
        Unit base = units.get(request.baseUnit());
        if (base == null) {
            throw new BusinessRuleException("Unidade base inexistente: " + request.baseUnit());
        }
        Set<ProductUnit> conversions = conversions(request.units(), base, units);
        boolean variableWeight = request.variableWeight() || conversions.stream().anyMatch(ProductUnit::isNominal);
        if (variableWeight && !"KG".equals(base.getCode())) {
            throw new BusinessRuleException("Peso variavel so se aplica a produtos com unidade base KG.");
        }

        Category category = categoryRepository.findBySlug(request.category())
                .filter(c -> c.getParentId() == null)
                .orElseThrow(() -> new BusinessRuleException("Categoria inexistente: " + request.category()));
        if (request.subcategoryId() != null) {
            Category sub = categoryRepository.findById(request.subcategoryId())
                    .orElseThrow(() -> new BusinessRuleException("Subcategoria inexistente."));
            if (!Objects.equals(sub.getParentId(), category.getId())) {
                throw new BusinessRuleException("A subcategoria nao pertence a categoria " + category.getName() + ".");
            }
        }
        if (request.brandId() != null && !brandRepository.existsById(request.brandId())) {
            throw new BusinessRuleException("Marca inexistente.");
        }
        if (request.minStock() != null && request.maxStock() != null
                && request.maxStock().compareTo(request.minStock()) < 0) {
            throw new BusinessRuleException("Estoque maximo nao pode ser menor que o minimo.");
        }
        if (request.expiryControl() && request.shelfLifeDays() == null) {
            throw new BusinessRuleException("Informe a validade (em dias) para produtos com controle de validade.");
        }
        Set<ProductSupplier> suppliers = supplierLinks(request.suppliers(), request.mainSupplierId(), product.getSuppliers());

        product.setSku(request.sku().trim());
        product.setBarcode(PartyDtos.blank(request.barcode()));
        product.setName(request.name().trim());
        product.setDescription(PartyDtos.blank(request.description()));
        product.setCategory(category.getSlug());
        product.setSubcategoryId(request.subcategoryId());
        product.setBrandId(request.brandId());
        product.setBaseUnit(base.getCode());
        product.setUnit(request.unitLabel() == null || request.unitLabel().isBlank()
                ? base.getCode().toLowerCase() : request.unitLabel().trim());
        product.setVariableWeight(variableWeight);
        product.setGrossWeightKg(request.grossWeightKg());
        product.setStorageType(request.storageType());
        // validade e rastreada por lote: controlar validade implica controlar lote
        product.setLotControl(request.lotControl() || request.expiryControl());
        product.setExpiryControl(request.expiryControl());
        product.setShelfLifeDays(request.shelfLifeDays());
        product.setPrice(request.price());
        product.setMinStock(request.minStock());
        product.setMaxStock(request.maxStock());
        product.setLeadTimeDays(request.leadTimeDays());
        product.setMainSupplierId(request.mainSupplierId());
        product.setActive(request.active());
        product.setAvailable(request.available());
        product.getUnits().clear();
        product.getUnits().addAll(conversions);
        product.getSuppliers().clear();
        product.getSuppliers().addAll(suppliers);
    }

    private static Set<ProductUnit> conversions(List<UnitConversion> requested, Unit base, Map<String, Unit> units) {
        Set<ProductUnit> result = new HashSet<>();
        if (requested == null) {
            return result;
        }
        for (UnitConversion conversion : requested) {
            if (conversion.unitCode().equals(base.getCode())) {
                throw new BusinessRuleException("A unidade base nao precisa de conversao.");
            }
            if (!units.containsKey(conversion.unitCode())) {
                throw new BusinessRuleException("Unidade inexistente: " + conversion.unitCode());
            }
            if (!base.isAllowsDecimal() && conversion.factor().stripTrailingZeros().scale() > 0) {
                throw new BusinessRuleException("Com unidade base " + base.getName()
                        + ", a conversao de " + conversion.unitCode() + " precisa ser um numero inteiro.");
            }
            if (!result.add(new ProductUnit(conversion.unitCode(), conversion.factor(), conversion.nominal(),
                    PartyDtos.blank(conversion.barcode())))) {
                throw new BusinessRuleException("Conversao repetida para " + conversion.unitCode() + ".");
            }
        }
        return result;
    }

    private Set<ProductSupplier> supplierLinks(List<SupplierLink> links, Long mainSupplierId,
                                               Set<ProductSupplier> current) {
        // Sem "produtos.custo.ver" o ultimo custo chega nulo da tela: mantem o atual.
        boolean keepCosts = !currentUser.can(CUSTO_VER);
        Map<Long, BigDecimal> currentCosts = new java.util.HashMap<>();
        current.forEach(s -> currentCosts.put(s.getSupplierId(), s.getLastCost()));
        Set<Long> ids = new HashSet<>();
        if (mainSupplierId != null) {
            ids.add(mainSupplierId);
        }
        if (links != null) {
            links.forEach(link -> ids.add(link.supplierId()));
        }
        Map<Long, Supplier> found = supplierRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Supplier::getId, s -> s));
        if (found.size() != ids.size()) {
            throw new BusinessRuleException("Fornecedor inexistente.");
        }
        if (mainSupplierId != null && !found.get(mainSupplierId).isActive()) {
            throw new BusinessRuleException("O fornecedor principal esta inativo.");
        }
        Set<ProductSupplier> result = new HashSet<>();
        if (links != null) {
            for (SupplierLink link : links) {
                BigDecimal lastCost = keepCosts ? currentCosts.get(link.supplierId()) : link.lastCost();
                if (!result.add(new ProductSupplier(link.supplierId(), PartyDtos.blank(link.supplierSku()),
                        lastCost, link.leadTimeDays()))) {
                    throw new BusinessRuleException("Fornecedor repetido na lista.");
                }
            }
        }
        return result;
    }

    private String uniqueSlug(String name, String sku) {
        String base = AuxiliaryService.slugify(name) + "-" + AuxiliaryService.slugify(sku);
        String slug = base;
        for (int i = 2; productRepository.existsBySlug(slug); i++) {
            slug = base + "-" + i;
        }
        return slug;
    }

    private static Map<String, Object> snapshot(Product p) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("sku", p.getSku());
        values.put("barcode", p.getBarcode());
        values.put("name", p.getName());
        values.put("category", p.getCategory());
        values.put("subcategoryId", p.getSubcategoryId());
        values.put("brandId", p.getBrandId());
        values.put("baseUnit", p.getBaseUnit());
        values.put("unit", p.getUnit());
        values.put("variableWeight", p.isVariableWeight());
        values.put("storageType", p.getStorageType());
        values.put("lotControl", p.isLotControl());
        values.put("expiryControl", p.isExpiryControl());
        values.put("shelfLifeDays", p.getShelfLifeDays());
        values.put("price", p.getPrice());
        values.put("minStock", p.getMinStock());
        values.put("maxStock", p.getMaxStock());
        values.put("leadTimeDays", p.getLeadTimeDays());
        values.put("mainSupplierId", p.getMainSupplierId());
        values.put("units", p.getUnits().stream()
                .sorted(Comparator.comparing(ProductUnit::getUnitCode))
                .map(u -> u.getUnitCode() + "=" + u.getFactor().stripTrailingZeros().toPlainString()
                        + (u.isNominal() ? " (aprox.)" : ""))
                .toList());
        values.put("active", p.isActive());
        values.put("available", p.isAvailable());
        return values;
    }

    private static Map<String, Object> costSnapshot(Product p) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("costPrice", p.getCostPrice());
        return values;
    }

    private record Lookups(Map<Long, String> brands, Map<Long, String> suppliers) {
    }

    private Lookups lookups(List<Product> products) {
        Set<Long> supplierIds = products.stream().map(Product::getMainSupplierId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        return new Lookups(
                brandRepository.findAll().stream().collect(Collectors.toMap(Brand::getId, Brand::getName)),
                supplierRepository.findAllById(supplierIds).stream()
                        .collect(Collectors.toMap(Supplier::getId, Supplier::displayName)));
    }

    // Margem sobre o preco de venda, usando o custo medio (ou o de cadastro).
    static BigDecimal margin(BigDecimal price, BigDecimal cost) {
        if (price == null || cost == null || price.signum() == 0) {
            return null;
        }
        return price.subtract(cost).multiply(BigDecimal.valueOf(100)).divide(price, 1, RoundingMode.HALF_UP);
    }

    private ErpProductResponse toResponse(Product p, Lookups lookups) {
        boolean seesCost = currentUser.can(CUSTO_VER);
        BigDecimal cost = p.getAverageCost() != null ? p.getAverageCost() : p.getCostPrice();
        return new ErpProductResponse(
                p.getId(), p.getSlug(), p.getSku(), p.getBarcode(), p.getName(), p.getDescription(),
                p.getCategory(), p.getSubcategoryId(), p.getBrandId(), lookups.brands().get(p.getBrandId()),
                p.getBaseUnit(), p.getUnit(), p.isVariableWeight(), p.getGrossWeightKg(),
                p.getStorageType(), p.isLotControl(), p.isExpiryControl(), p.getShelfLifeDays(),
                p.getPrice(),
                seesCost ? p.getCostPrice() : null,
                seesCost ? p.getAverageCost() : null,
                seesCost ? margin(p.getPrice(), cost) : null,
                p.getMinStock(), p.getMaxStock(), p.getLeadTimeDays(),
                p.getMainSupplierId(), lookups.suppliers().get(p.getMainSupplierId()),
                p.getUnits().stream().sorted(Comparator.comparing(ProductUnit::getUnitCode))
                        .map(u -> new UnitConversion(u.getUnitCode(), u.getFactor(), u.isNominal(), u.getBarcode()))
                        .toList(),
                p.getSuppliers().stream().sorted(Comparator.comparing(ProductSupplier::getSupplierId))
                        .map(s -> new SupplierLink(s.getSupplierId(), s.getSupplierSku(),
                                seesCost ? s.getLastCost() : null, s.getLeadTimeDays()))
                        .toList(),
                p.isActive(), p.isAvailable(), p.getImage(), p.getUpdatedAt(), p.getVersion());
    }
}

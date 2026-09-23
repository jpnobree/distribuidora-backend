package com.distribuidora.backend.service;

import com.distribuidora.backend.audit.AuditAction;
import com.distribuidora.backend.audit.AuditService;
import com.distribuidora.backend.dto.PriceUpdateRequest;
import com.distribuidora.backend.dto.ProductRequest;
import com.distribuidora.backend.exception.ConflictException;
import com.distribuidora.backend.exception.ResourceNotFoundException;
import com.distribuidora.backend.model.Product;
import com.distribuidora.backend.repository.ProductRepository;
import com.distribuidora.backend.repository.ProductSpecifications;
import org.hibernate.Hibernate;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProductService {

    private static final String CACHE_NAME = "products";
    private static final String ENTITY = "Product";

    private final ProductRepository productRepository;
    private final AuditService auditService;

    public ProductService(ProductRepository productRepository, AuditService auditService) {
        this.productRepository = productRepository;
        this.auditService = auditService;
    }

    // Cacheado porque e o endpoint publico mais acessado (toda visita ao
    // catalogo bate aqui). Invalidado inteiro em qualquer escrita - ver
    // @CacheEvict abaixo. Chave inclui category/search/pageable automaticamente.
    //
    // @Transactional + Hibernate.initialize: como o resultado fica em cache
    // (Caffeine), as entidades podem ser devolvidas em uma request diferente
    // daquela que fez a consulta, com a sessao do Hibernate original ja
    // fechada. Sem inicializar "tags" aqui dentro da transacao, o Jackson
    // tenta fazer o lazy-load fora de sessao ao serializar (na requisicao
    // que pegou o cache) e da LazyInitializationException.
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CACHE_NAME)
    public Page<Product> findAll(String category, String search, Pageable pageable) {
        // Produto inativo no ERP nao aparece no catalogo publico.
        Specification<Product> spec = Specification.where(ProductSpecifications.isActive());
        if (category != null && !category.isBlank()) {
            spec = spec.and(ProductSpecifications.hasCategory(category));
        }
        if (search != null && !search.isBlank()) {
            spec = spec.and(ProductSpecifications.matchesSearch(search));
        }
        Page<Product> page = productRepository.findAll(spec, pageable);
        page.getContent().forEach(product -> Hibernate.initialize(product.getTags()));
        return page;
    }

    public Product findBySlug(String slug) {
        return productRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Produto nao encontrado: " + slug));
    }

    @Transactional
    @CacheEvict(cacheNames = CACHE_NAME, allEntries = true)
    public Product create(ProductRequest request) {
        if (productRepository.existsBySlug(request.getSlug())) {
            throw new ConflictException("Ja existe um produto com o id: " + request.getSlug());
        }
        Product product = new Product();
        product.setBaseUnit(Product.baseUnitFromLabel(request.getUnit()));
        applyRequest(product, request);
        Product saved = productRepository.save(product);
        auditService.recordChange(AuditAction.PRODUTO_CRIADO, ENTITY, saved.getSlug(), null, snapshot(saved), null);
        return saved;
    }

    @Transactional
    @CacheEvict(cacheNames = CACHE_NAME, allEntries = true)
    public Product update(String slug, ProductRequest request) {
        Product product = findBySlug(slug);
        Map<String, Object> before = snapshot(product);
        applyRequest(product, request);
        Product saved = productRepository.save(product);
        auditService.recordChange(AuditAction.PRODUTO_ALTERADO, ENTITY, saved.getSlug(), before, snapshot(saved), null);
        return saved;
    }

    @Transactional
    @CacheEvict(cacheNames = CACHE_NAME, allEntries = true)
    public Product updatePrice(String slug, PriceUpdateRequest request) {
        Product product = findBySlug(slug);
        Map<String, Object> before = priceSnapshot(product);
        product.setPrice(request.getPrice());
        Product saved = productRepository.save(product);
        auditService.recordChange(AuditAction.PRODUTO_PRECO_ALTERADO, ENTITY, saved.getSlug(),
                before, priceSnapshot(saved), null);
        return saved;
    }

    @Transactional
    @CacheEvict(cacheNames = CACHE_NAME, allEntries = true)
    // Cadastro nunca e apagado: o produto some da vitrine e das vendas, mas o
    // historico (estoque, pedidos, auditoria) continua apontando para ele.
    public void delete(String slug) {
        Product product = findBySlug(slug);
        product.setActive(false);
        auditService.record(AuditAction.PRODUTO_DESATIVADO, ENTITY, product.getSlug(), null);
    }

    private static Map<String, Object> snapshot(Product product) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("sku", product.getSku());
        values.put("name", product.getName());
        values.put("category", product.getCategory());
        values.put("unit", product.getUnit());
        values.put("price", product.getPrice());
        values.put("available", product.isAvailable());
        values.put("tags", List.copyOf(product.getTags()));
        values.put("image", product.getImage());
        values.put("description", product.getDescription());
        values.put("origin", product.getOrigin());
        return values;
    }

    // null ("consulte o preco") e um valor valido, por isso nao Map.of.
    private static Map<String, Object> priceSnapshot(Product product) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("price", product.getPrice());
        return values;
    }

    private void applyRequest(Product product, ProductRequest request) {
        product.setSlug(request.getSlug());
        product.setSku(request.getSku());
        product.setName(request.getName());
        product.setCategory(request.getCategory());
        product.setUnit(request.getUnit());
        product.setPrice(request.getPrice());
        product.setTags(request.getTags() != null ? request.getTags() : List.of());
        product.setImage(request.getImage());
        product.setDescription(request.getDescription());
        product.setOrigin(request.getOrigin());
        product.setAvailable(request.isAvailable());
    }
}

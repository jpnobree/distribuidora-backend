package com.distribuidora.backend.service;

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

import java.util.List;

@Service
public class ProductService {

    private static final String CACHE_NAME = "products";

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
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
        Specification<Product> spec = Specification.where(null);
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
        applyRequest(product, request);
        return productRepository.save(product);
    }

    @Transactional
    @CacheEvict(cacheNames = CACHE_NAME, allEntries = true)
    public Product update(String slug, ProductRequest request) {
        Product product = findBySlug(slug);
        applyRequest(product, request);
        return productRepository.save(product);
    }

    @Transactional
    @CacheEvict(cacheNames = CACHE_NAME, allEntries = true)
    public Product updatePrice(String slug, PriceUpdateRequest request) {
        Product product = findBySlug(slug);
        product.setPrice(request.getPrice());
        return productRepository.save(product);
    }

    @Transactional
    @CacheEvict(cacheNames = CACHE_NAME, allEntries = true)
    public void delete(String slug) {
        Product product = findBySlug(slug);
        productRepository.delete(product);
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

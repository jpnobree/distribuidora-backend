package com.distribuidora.backend.service;

import com.distribuidora.backend.dto.PriceUpdateRequest;
import com.distribuidora.backend.dto.ProductRequest;
import com.distribuidora.backend.exception.ConflictException;
import com.distribuidora.backend.exception.ResourceNotFoundException;
import com.distribuidora.backend.model.Product;
import com.distribuidora.backend.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<Product> findAll() {
        return productRepository.findAll();
    }

    public Product findBySlug(String slug) {
        return productRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Produto nao encontrado: " + slug));
    }

    @Transactional
    public Product create(ProductRequest request) {
        if (productRepository.existsBySlug(request.getSlug())) {
            throw new ConflictException("Ja existe um produto com o id: " + request.getSlug());
        }
        Product product = new Product();
        applyRequest(product, request);
        return productRepository.save(product);
    }

    @Transactional
    public Product update(String slug, ProductRequest request) {
        Product product = findBySlug(slug);
        applyRequest(product, request);
        return productRepository.save(product);
    }

    @Transactional
    public Product updatePrice(String slug, PriceUpdateRequest request) {
        Product product = findBySlug(slug);
        product.setPrice(request.getPrice());
        return productRepository.save(product);
    }

    @Transactional
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

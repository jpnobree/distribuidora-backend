package com.distribuidora.backend.repository;

import com.distribuidora.backend.model.Product;
import org.springframework.data.jpa.domain.Specification;

// Predicados reutilizaveis para a busca dinamica do catalogo. So sao
// combinados quando o filtro correspondente e realmente informado (ver
// ProductService.findAll) - nunca recebem valor nulo, o que evita o bug de
// tipagem que uma query JPQL com parametro nulo direto causava no Postgres
// (ver historico: "function lower(bytea) does not exist").
public class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> hasCategory(String category) {
        return (root, query, cb) -> cb.equal(root.get("category"), category);
    }

    public static Specification<Product> matchesSearch(String search) {
        String like = "%" + search.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), like),
                cb.like(cb.lower(root.get("sku")), like));
    }
}

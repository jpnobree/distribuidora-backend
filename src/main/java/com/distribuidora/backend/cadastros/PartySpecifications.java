package com.distribuidora.backend.cadastros;

import org.springframework.data.jpa.domain.Specification;

final class PartySpecifications {

    private PartySpecifications() {
    }

    // Busca por razao social, fantasia ou documento (com ou sem mascara).
    static <T extends Party> Specification<T> matches(String search) {
        String like = "%" + search.toLowerCase() + "%";
        String digits = Documents.digits(search);
        return (root, query, cb) -> {
            var byName = cb.or(
                    cb.like(cb.lower(root.get("legalName")), like),
                    cb.like(cb.lower(root.get("tradeName")), like));
            return digits.length() >= 3 ? cb.or(byName, cb.like(root.get("document"), "%" + digits + "%")) : byName;
        };
    }
}

package com.distribuidora.backend.repository;

import com.distribuidora.backend.model.Role;
import com.distribuidora.backend.model.User;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

public class UserSpecifications {

    private UserSpecifications() {
    }

    public static Specification<User> matchesSearch(String search) {
        String like = "%" + search.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("username")), like),
                cb.like(cb.lower(root.get("fullName")), like));
    }

    // Subquery em vez de join: evita linhas duplicadas na paginacao.
    public static Specification<User> hasRole(String roleCode) {
        return (root, query, cb) -> {
            Subquery<Long> sub = query.subquery(Long.class);
            var subUser = sub.from(User.class);
            Join<User, Role> roles = subUser.join("roles");
            sub.select(subUser.get("id"))
                    .where(cb.equal(subUser.get("id"), root.get("id")), cb.equal(roles.get("code"), roleCode));
            return cb.exists(sub);
        };
    }

    public static Specification<User> isActive(boolean active) {
        return (root, query, cb) -> cb.equal(root.get("active"), active);
    }
}

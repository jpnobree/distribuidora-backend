package com.distribuidora.backend.repository;

import com.distribuidora.backend.model.Role;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByCode(String code);

    boolean existsByCode(String code);

    List<Role> findByCodeIn(Collection<String> codes);

    @EntityGraph(attributePaths = {"permissions"})
    List<Role> findAllByOrderByNameAsc();

    @EntityGraph(attributePaths = {"permissions"})
    Optional<Role> findWithPermissionsByCode(String code);
}

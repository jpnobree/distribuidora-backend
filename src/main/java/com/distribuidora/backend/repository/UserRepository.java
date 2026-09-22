package com.distribuidora.backend.repository;

import com.distribuidora.backend.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    // Carrega perfis e permissoes numa consulta so: roda a cada requisicao
    // autenticada (ver CustomUserDetailsService).
    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<User> findWithAccessByUsername(String username);

    @EntityGraph(attributePaths = {"roles"})
    Optional<User> findWithRolesById(Long id);

    @Query("""
            select count(distinct u) from User u join u.roles r
            where r.code = 'ADMINISTRADOR' and u.active = true and u.id <> :excludedUserId
            """)
    long countOtherActiveAdministrators(@Param("excludedUserId") Long excludedUserId);

    @Query("select count(u) from User u join u.roles r where r.id = :roleId")
    long countByRoleId(@Param("roleId") Long roleId);

    @Transactional
    @Modifying
    @Query("update User u set u.lastLoginAt = :at where u.id = :id")
    void updateLastLoginAt(@Param("id") Long id, @Param("at") Instant at);
}

package com.distribuidora.backend.repository;

import com.distribuidora.backend.model.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PermissionRepository extends JpaRepository<Permission, String> {
    List<Permission> findAllByOrderByModuleAscCodeAsc();
}

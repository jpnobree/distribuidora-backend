package com.distribuidora.backend.audit;

import com.distribuidora.backend.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Tag(name = "Auditoria")
@RestController
@RequestMapping("/api/audit-logs")
@PreAuthorize("hasAuthority('auditoria.ver')")
public class AuditController {

    private final AuditLogRepository auditLogRepository;

    public AuditController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Operation(summary = "Consulta o registro de auditoria (paginado, mais recente primeiro)")
    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> search(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 50, sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Specification<AuditLog> spec = Specification.where(null);
        if (username != null && !username.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(cb.lower(root.get("username")), username.toLowerCase()));
        }
        if (action != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("action"), action));
        }
        if (entityType != null && !entityType.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("entityType"), entityType));
        }
        if (entityId != null && !entityId.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("entityId"), entityId));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThan(root.get("occurredAt"), to));
        }

        return PageResponse.from(auditLogRepository.findAll(spec, pageable).map(AuditLogResponse::from));
    }

    @Operation(summary = "Tipos de ação auditada (para filtros)")
    @GetMapping("/actions")
    public List<ActionOption> actions() {
        return Arrays.stream(AuditAction.values())
                .map(action -> new ActionOption(action.name(), action.getLabel()))
                .toList();
    }

    public record ActionOption(String code, String label) {
    }
}

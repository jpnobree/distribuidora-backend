package com.distribuidora.backend.audit;

import java.time.Instant;
import java.util.Map;

public record AuditLogResponse(
        Long id,
        Instant occurredAt,
        Long userId,
        String username,
        String action,
        String actionLabel,
        String entityType,
        String entityId,
        Map<String, Object> oldValue,
        Map<String, Object> newValue,
        String reason,
        String ip,
        String userAgent) {

    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getOccurredAt(),
                log.getUserId(),
                log.getUsername(),
                log.getAction().name(),
                log.getAction().getLabel(),
                log.getEntityType(),
                log.getEntityId(),
                log.getOldValue(),
                log.getNewValue(),
                log.getReason(),
                log.getIp(),
                log.getUserAgent());
    }
}

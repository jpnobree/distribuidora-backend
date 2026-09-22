package com.distribuidora.backend.audit;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

// Somente inclusao: o banco recusa UPDATE/DELETE (trigger na V3).
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Instant occurredAt = Instant.now();

    @Column(updatable = false)
    private Long userId;

    @Column(updatable = false)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AuditAction action;

    @Column(updatable = false)
    private String entityType;

    @Column(updatable = false)
    private String entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(updatable = false)
    private Map<String, Object> oldValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(updatable = false)
    private Map<String, Object> newValue;

    @Column(updatable = false)
    private String reason;

    @Column(updatable = false)
    private String ip;

    @Column(updatable = false)
    private String userAgent;

    protected AuditLog() {
    }

    AuditLog(Long userId, String username, AuditAction action, String entityType, String entityId,
             Map<String, Object> oldValue, Map<String, Object> newValue, String reason, String ip, String userAgent) {
        this.userId = userId;
        this.username = username;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.reason = reason;
        this.ip = ip;
        this.userAgent = userAgent;
    }

    public Long getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public Map<String, Object> getOldValue() {
        return oldValue;
    }

    public Map<String, Object> getNewValue() {
        return newValue;
    }

    public String getReason() {
        return reason;
    }

    public String getIp() {
        return ip;
    }

    public String getUserAgent() {
        return userAgent;
    }
}

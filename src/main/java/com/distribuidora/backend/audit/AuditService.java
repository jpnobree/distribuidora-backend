package com.distribuidora.backend.audit;

import com.distribuidora.backend.security.AppUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.time.temporal.TemporalAccessor;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

// Chamado explicitamente pelos servicos, dentro da transacao da operacao:
// se a auditoria falhar, a alteracao tambem e desfeita.
@Service
public class AuditService {

    private static final int MAX_USER_AGENT = 500;

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    // Grava so os campos que mudaram. Criacao: before = null. Exclusao:
    // after = null. Se nada mudou, nao grava nada.
    @Transactional
    public void recordChange(AuditAction action, String entityType, Object entityId,
                             Map<String, Object> before, Map<String, Object> after, String reason) {
        Actor actor = currentActor();
        save(actor.id(), actor.username(), action, entityType, entityId, before, after, reason);
    }

    @Transactional
    public void record(AuditAction action, String entityType, Object entityId, String reason) {
        recordChange(action, entityType, entityId, null, null, reason);
    }

    // Para quando ainda nao ha usuario autenticado no contexto (login).
    @Transactional
    public void recordAs(Long userId, String username, AuditAction action, String entityType, Object entityId,
                         String reason) {
        save(userId, username, action, entityType, entityId, null, null, reason);
    }

    private void save(Long userId, String username, AuditAction action, String entityType, Object entityId,
                      Map<String, Object> before, Map<String, Object> after, String reason) {
        Map<String, Object> oldValue = normalize(before);
        Map<String, Object> newValue = normalize(after);

        if (oldValue != null && newValue != null) {
            Map<String, Object> changedOld = new LinkedHashMap<>();
            Map<String, Object> changedNew = new LinkedHashMap<>();
            for (String key : unionKeys(oldValue, newValue)) {
                Object previous = oldValue.get(key);
                Object current = newValue.get(key);
                if (!sameValue(previous, current)) {
                    changedOld.put(key, previous);
                    changedNew.put(key, current);
                }
            }
            if (changedNew.isEmpty()) {
                return;
            }
            oldValue = changedOld;
            newValue = changedNew;
        }

        HttpServletRequest request = currentRequest();
        auditLogRepository.save(new AuditLog(
                userId,
                username,
                action,
                entityType,
                entityId == null ? null : entityId.toString(),
                oldValue,
                newValue,
                reason,
                request == null ? null : clientIp(request),
                request == null ? null : truncate(request.getHeader("User-Agent"))));
    }

    static boolean sameValue(Object a, Object b) {
        if (a instanceof BigDecimal x && b instanceof BigDecimal y) {
            return x.compareTo(y) == 0;
        }
        return Objects.equals(a, b);
    }

    private static Iterable<String> unionKeys(Map<String, Object> a, Map<String, Object> b) {
        LinkedHashMap<String, Object> keys = new LinkedHashMap<>(a);
        keys.putAll(b);
        return keys.keySet();
    }

    private static Map<String, Object> normalize(Map<String, Object> values) {
        if (values == null) {
            return null;
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        values.forEach((key, value) -> normalized.put(key, normalizeValue(value)));
        return normalized;
    }

    private static Object normalizeValue(Object value) {
        if (value instanceof TemporalAccessor || value instanceof Enum<?>) {
            return value.toString();
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(AuditService::normalizeValue).toList();
        }
        return value;
    }

    private Actor currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return new Actor(null, null);
        }
        if (authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            return new Actor(principal.getId(), principal.getUsername());
        }
        return new Actor(null, authentication.getName());
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private static String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_USER_AGENT) {
            return value;
        }
        return value.substring(0, MAX_USER_AGENT);
    }

    private record Actor(Long id, String username) {
    }
}

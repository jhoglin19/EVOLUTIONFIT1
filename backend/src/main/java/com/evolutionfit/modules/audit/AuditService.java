package com.evolutionfit.modules.audit;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra cambios en audit_logs dentro de la misma transacción de la operación auditada:
 * si la operación se revierte, su registro de auditoría también.
 */
@Service
public class AuditService {

    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_UPDATE = "UPDATE";
    public static final String ACTION_STATUS_CHANGE = "STATUS_CHANGE";
    public static final String ACTION_CANCEL = "CANCEL";
    public static final String ACTION_EXTEND = "EXTEND";
    public static final String ACTION_RESCHEDULE = "RESCHEDULE";
    public static final String ACTION_ACTIVATE = "ACTIVATE";
    public static final String ACTION_DISCOUNT = "DISCOUNT";
    public static final String ACTION_VOID = "VOID";
    public static final String ACTION_DELETE = "DELETE";

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void record(String entityType, Long entityId, String action,
                       Object before, Object after, Long userId) {
        AuditLog log = new AuditLog();
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setAction(action);
        log.setBeforeData(toJson(before));
        log.setAfterData(toJson(after));
        log.setUserId(userId);
        repository.save(log);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("No se pudo serializar el registro de auditoría", e);
        }
    }
}

package com.kamal.lc.changelog.service;

import com.kamal.lc.changelog.entity.ChangeLog;
import com.kamal.lc.changelog.repository.ChangeLogRepository;
import com.kamal.lc.common.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Central engine for recording data changes in the {@code changelog} table.
 *
 * <p><b>Design decisions:</b>
 * <ul>
 *   <li>Uses <b>Reflection-based diff</b> — compares a snapshot of field values (captured before
 *       the update) against the current entity state, automatically detecting which fields changed.
 *       Adding new columns requires zero code changes here.</li>
 *   <li>Writes happen in an <b>after-commit listener</b> so the changelog insert never causes
 *       the business transaction to fail, and rollbacks are not logged as false positives.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChangeLogWriter {

    private final ChangeLogRepository changeLogRepo;
    private final ApplicationEventPublisher eventPublisher;

    // ─────────── Public API ───────────

    /**
     * Compares a before-snapshot with the current entity state and publishes
     * one change event per field that differs. The events are persisted after
     * the current transaction commits.
     *
     * @param entityType  logical category (SHIFT, CAPTAIN, VEHICLE, etc.)
     * @param entityId    PK of the entity
     * @param entityLabel human-readable name (captain name, plate number, etc.)
     * @param before      snapshot of field values captured <em>before</em> the update
     * @param after       snapshot of field values captured <em>after</em> the update
     * @param fieldLabels mapping from technical field name → Arabic display label
     * @param changedBy   username of the actor
     */
    public void logFieldChanges(String entityType, UUID entityId, String entityLabel,
                                Map<String, Object> before, Map<String, Object> after,
                                Map<String, String> fieldLabels, String changedBy) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        OffsetDateTime now = OffsetDateTime.now();

        List<ChangeLog> entries = new ArrayList<>();
        for (Map.Entry<String, Object> entry : after.entrySet()) {
            String field = entry.getKey();
            Object newVal = entry.getValue();
            Object oldVal = before.get(field);

            String oldStr = str(oldVal);
            String newStr = str(newVal);

            // Skip if both are effectively blank (null/empty/zero) — not a real change
            if (isBlankOrZero(oldStr) && isBlankOrZero(newStr)) continue;

            if (!Objects.equals(oldStr, newStr)) {
                entries.add(ChangeLog.builder()
                        .tenantId(tenantId)
                        .entityType(entityType)
                        .entityId(entityId)
                        .entityLabel(truncate(entityLabel, 255))
                        .action("UPDATE")
                        .fieldName(field)
                        .fieldLabel(fieldLabels.getOrDefault(field, field))
                        .oldValue(truncate(str(oldVal), 2000))
                        .newValue(truncate(str(newVal), 2000))
                        .changedBy(changedBy)
                        .changedAt(now)
                        .build());
            }
        }

        if (!entries.isEmpty()) {
            eventPublisher.publishEvent(new ChangeLogBatchEvent(entries));
        }
    }

    /**
     * Records an entity-level action (e.g. LINK, UNLINK, EXCLUDE, RESTORE).
     */
    public void logAction(String entityType, UUID entityId, String entityLabel,
                          String action, String oldValue, String newValue,
                          String reason, String changedBy) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        ChangeLog entry = ChangeLog.builder()
                .tenantId(tenantId)
                .entityType(entityType)
                .entityId(entityId)
                .entityLabel(truncate(entityLabel, 255))
                .action(action)
                .oldValue(truncate(oldValue, 2000))
                .newValue(truncate(newValue, 2000))
                .changeReason(reason)
                .changedBy(changedBy)
                .changedAt(OffsetDateTime.now())
                .build();

        eventPublisher.publishEvent(new ChangeLogBatchEvent(List.of(entry)));
    }

    // ─────────── Snapshot Utility ───────────

    /**
     * Captures a snapshot of field values from an entity instance using reflection.
     * Only captures fields whose names are present in the fieldLabels map.
     */
    public static Map<String, Object> snapshot(Object entity, Map<String, String> fieldLabels) {
        if (entity == null) return Map.of();
        Map<String, Object> snap = new LinkedHashMap<>();
        Class<?> clazz = entity.getClass();

        for (String fieldName : fieldLabels.keySet()) {
            try {
                Field field = findField(clazz, fieldName);
                if (field != null) {
                    field.setAccessible(true);
                    snap.put(fieldName, field.get(entity));
                }
            } catch (IllegalAccessException e) {
                log.warn("Cannot access field {} on {}", fieldName, clazz.getSimpleName(), e);
            }
        }
        return snap;
    }

    // ─────────── After-Commit Listener ───────────

    /**
     * Persists changelog entries only <em>after</em> the originating transaction
     * commits successfully. Failures here are logged but never propagate.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onChangeLogEvent(ChangeLogBatchEvent event) {
        try {
            changeLogRepo.saveAll(event.entries());
        } catch (Exception e) {
            log.error("Failed to persist {} changelog entries: {}", event.entries().size(), e.getMessage(), e);
        }
    }

    // ─────────── Internal Event ───────────

    public record ChangeLogBatchEvent(List<ChangeLog> entries) {}

    // ─────────── Helpers ───────────

    private static Field findField(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static String str(Object value) {
        if (value == null) return null;
        // Normalize BigDecimal so 0.00 and 0 produce the same string
        if (value instanceof BigDecimal bd) {
            return bd.stripTrailingZeros().toPlainString();
        }
        return value.toString();
    }

    /** Returns true if the string is null, blank, or a zero-equivalent ("0", "0.00", etc.). */
    private static boolean isBlankOrZero(String s) {
        if (s == null || s.isBlank()) return true;
        try {
            return new BigDecimal(s).compareTo(BigDecimal.ZERO) == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String truncate(String value, int maxLen) {
        if (value == null) return null;
        return value.length() > maxLen ? value.substring(0, maxLen) : value;
    }
}

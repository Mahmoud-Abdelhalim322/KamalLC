package com.kamal.lc.changelog.service;

import com.kamal.lc.changelog.dto.ChangeLogFilter;
import com.kamal.lc.changelog.dto.ChangeLogResponse;
import com.kamal.lc.changelog.entity.ChangeLog;
import com.kamal.lc.changelog.repository.ChangeLogRepository;
import com.kamal.lc.common.security.TenantContext;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChangeLogService {

    private final ChangeLogRepository repo;

    public Page<ChangeLogResponse> search(ChangeLogFilter filter, Pageable pageable) {
        return repo.findAll(toSpecification(filter), pageable).map(ChangeLogService::toResponse);
    }

    public List<ChangeLogResponse> searchAll(ChangeLogFilter filter, Sort sort) {
        return repo.findAll(toSpecification(filter), sort)
                .stream().map(ChangeLogService::toResponse).toList();
    }

    public List<String> getChangedByUsers() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return repo.findDistinctChangedByForTenant(tenantId);
    }

    private Specification<ChangeLog> toSpecification(ChangeLogFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Always filter by current tenant
            UUID tenantId = TenantContext.getCurrentTenantId();
            if (tenantId != null) {
                predicates.add(cb.equal(root.get("tenantId"), tenantId));
            }

            if (filter.getEntityType() != null && !filter.getEntityType().isBlank()) {
                predicates.add(cb.equal(root.get("entityType"), filter.getEntityType()));
            }
            if (filter.getEntityId() != null) {
                predicates.add(cb.equal(root.get("entityId"), filter.getEntityId()));
            }
            if (filter.getAction() != null && !filter.getAction().isBlank()) {
                predicates.add(cb.equal(root.get("action"), filter.getAction()));
            }
            if (filter.getChangedBy() != null && !filter.getChangedBy().isBlank()) {
                predicates.add(cb.equal(root.get("changedBy"), filter.getChangedBy()));
            }
            if (filter.getFrom() != null) {
                OffsetDateTime fromStart = filter.getFrom().atStartOfDay().atOffset(ZoneOffset.UTC);
                predicates.add(cb.greaterThanOrEqualTo(root.get("changedAt"), fromStart));
            }
            if (filter.getTo() != null) {
                OffsetDateTime toEnd = filter.getTo().atTime(LocalTime.MAX).atOffset(ZoneOffset.UTC);
                predicates.add(cb.lessThanOrEqualTo(root.get("changedAt"), toEnd));
            }
            if (filter.getSearch() != null && !filter.getSearch().isBlank()) {
                String term = "%" + filter.getSearch().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("entityLabel")), term),
                        cb.like(cb.lower(root.get("fieldLabel")), term),
                        cb.like(cb.lower(root.get("oldValue")), term),
                        cb.like(cb.lower(root.get("newValue")), term),
                        cb.like(cb.lower(root.get("changedBy")), term)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    static ChangeLogResponse toResponse(ChangeLog c) {
        return ChangeLogResponse.builder()
                .id(c.getId())
                .entityType(c.getEntityType())
                .entityId(c.getEntityId())
                .entityLabel(c.getEntityLabel())
                .action(c.getAction())
                .fieldName(c.getFieldName())
                .fieldLabel(c.getFieldLabel())
                .oldValue(c.getOldValue())
                .newValue(c.getNewValue())
                .changeReason(c.getChangeReason())
                .changedBy(c.getChangedBy())
                .changedAt(c.getChangedAt())
                .build();
    }
}

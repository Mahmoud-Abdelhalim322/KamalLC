package com.kamal.lc.changelog.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class ChangeLogResponse {

    private UUID id;
    private String entityType;
    private UUID entityId;
    private String entityLabel;
    private String action;
    private String fieldName;
    private String fieldLabel;
    private String oldValue;
    private String newValue;
    private String changeReason;
    private String changedBy;
    private OffsetDateTime changedAt;
}

package com.kamal.lc.changelog.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Filter criteria for querying the changelog. Every field is optional.
 */
@Data
public class ChangeLogFilter {
    /** SHIFT, SUPPLIER_ID, VEHICLE, ASSET_ITEM, CAPTAIN — or null for all. */
    private String entityType;
    private UUID entityId;
    /** UPDATE, EXCLUDE, RESTORE, LINK, UNLINK, etc. */
    private String action;
    private String changedBy;
    private LocalDate from;
    private LocalDate to;
    /** Free-text search across entityLabel, fieldName, oldValue, newValue. */
    private String search;
}

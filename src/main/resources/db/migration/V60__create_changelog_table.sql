-- V60: Create tenant-level change log for tracking all significant data changes
CREATE TABLE IF NOT EXISTS changelog (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL REFERENCES tenant(id),
    entity_type   VARCHAR(50)  NOT NULL,   -- SHIFT, SUPPLIER_ID, VEHICLE, ASSET_ITEM, CAPTAIN
    entity_id     UUID,
    entity_label  VARCHAR(255),
    action        VARCHAR(30)  NOT NULL,   -- UPDATE, EXCLUDE, RESTORE, LINK, UNLINK, CREATE, DELETE, EXCLUDE_DEDUCTION, RESTORE_DEDUCTION
    field_name    VARCHAR(100),
    field_label   VARCHAR(100),
    old_value     TEXT,
    new_value     TEXT,
    change_reason TEXT,
    changed_by    VARCHAR(100) NOT NULL,
    changed_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Composite index: most queries filter by tenant + entity type + date range
CREATE INDEX IF NOT EXISTS idx_changelog_tenant_type_date
    ON changelog (tenant_id, entity_type, changed_at DESC);

-- Index for searching by entity
CREATE INDEX IF NOT EXISTS idx_changelog_entity
    ON changelog (tenant_id, entity_id);

-- Index for searching by user
CREATE INDEX IF NOT EXISTS idx_changelog_user
    ON changelog (tenant_id, changed_by, changed_at DESC);

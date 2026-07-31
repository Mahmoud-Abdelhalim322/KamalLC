-- V23: Unified custody ledger — tracks every asset (vehicle, SIM, phone, custom item)
-- assigned to a captain, with full audit trail. No hard deletes; items are returned.

CREATE TABLE custody_record (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    captain_id    UUID         NOT NULL REFERENCES captain(id),
    asset_type    VARCHAR(30)  NOT NULL,  -- VEHICLE | SIM_CARD | PHONE | custom category name
    reference_id  UUID         NOT NULL,  -- points to vehicle_assignment.id or asset_item.id
    asset_label   VARCHAR(255) NOT NULL,  -- human-readable label for display, e.g. "تويوتا ABC-1234"
    assigned_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    returned_at   TIMESTAMPTZ,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | RETURNED
    assigned_by   VARCHAR(100),
    returned_by   VARCHAR(100),
    notes         TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_custody_captain       ON custody_record(captain_id);
CREATE INDEX idx_custody_status        ON custody_record(status);
CREATE INDEX idx_custody_asset_type    ON custody_record(asset_type);
CREATE INDEX idx_custody_reference     ON custody_record(reference_id);

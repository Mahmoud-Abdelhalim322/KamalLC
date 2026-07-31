-- V24: Maintenance module — maintenance orders with parts tracking and cost liability.

CREATE TABLE vehicle_maintenance (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vehicle_id       UUID         NOT NULL REFERENCES vehicle(id),
    status           VARCHAR(20)  NOT NULL DEFAULT 'IN_PROGRESS',  -- IN_PROGRESS | COMPLETED | CANCELLED
    workshop_name    VARCHAR(255),
    fault_reason     TEXT         NOT NULL,
    cost_bearer      VARCHAR(20)  NOT NULL DEFAULT 'COMPANY',      -- COMPANY | CAPTAIN
    captain_id       UUID                  REFERENCES captain(id), -- the responsible captain (nullable)
    total_cost       DECIMAL(12,2),                                -- filled on close
    odometer_on_close INTEGER,                                     -- updated km reading on close
    deduction_id     UUID,                                         -- FK → captain_adjustment when captain pays
    notes            TEXT,
    opened_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    closed_at        TIMESTAMPTZ,
    created_by       VARCHAR(100)
);

CREATE TABLE maintenance_part (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    maintenance_id   UUID         NOT NULL REFERENCES vehicle_maintenance(id) ON DELETE CASCADE,
    part_name        VARCHAR(255) NOT NULL,
    part_cost        DECIMAL(12,2),
    sort_order       INTEGER      NOT NULL DEFAULT 0
);

CREATE INDEX idx_maintenance_vehicle ON vehicle_maintenance(vehicle_id);
CREATE INDEX idx_maintenance_status  ON vehicle_maintenance(status);
CREATE INDEX idx_maintenance_captain ON vehicle_maintenance(captain_id);
CREATE INDEX idx_maint_part_maint    ON maintenance_part(maintenance_id);

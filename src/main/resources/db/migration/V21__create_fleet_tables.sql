-- V21: Fleet management — vehicles owned or rented from external agents,
-- assigned to captains on a schedule, with automatic deductions for late returns.

CREATE TABLE vehicle (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plate_number    VARCHAR(30)  NOT NULL UNIQUE,
    ownership_type  VARCHAR(20)  NOT NULL DEFAULT 'OWNED',  -- OWNED | RENTED
    brand           VARCHAR(100),
    model           VARCHAR(100),
    year            INTEGER,
    color           VARCHAR(50),
    odometer        INTEGER,                                 -- current km reading
    inspection_date DATE,                                    -- next periodic inspection
    status          VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE', -- AVAILABLE | ASSIGNED | MAINTENANCE | DECOMMISSIONED

    -- Rental-agent fields (populated only when ownership_type = 'RENTED')
    rental_agent_name  VARCHAR(255),
    rental_start_date  DATE,
    rental_end_date    DATE,

    notes       TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE vehicle_assignment (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vehicle_id          UUID        NOT NULL REFERENCES vehicle(id),
    captain_id          UUID        NOT NULL REFERENCES captain(id),
    next_captain_id     UUID                 REFERENCES captain(id),  -- optional hand-over target
    assigned_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    expected_return_at  TIMESTAMPTZ NOT NULL,
    actual_return_at    TIMESTAMPTZ,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | RETURNED | LATE
    late_deduction_id   UUID,       -- FK to captain_adjustment created on late return (nullable)
    notes               TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_vehicle_assignment_vehicle  ON vehicle_assignment(vehicle_id);
CREATE INDEX idx_vehicle_assignment_captain  ON vehicle_assignment(captain_id);
CREATE INDEX idx_vehicle_assignment_status   ON vehicle_assignment(status);

-- Settings for the automatic late-return deduction
INSERT INTO app_setting (setting_key, setting_group, label_ar, value, value_type, unit_label, effective_from) VALUES
('VEHICLE_LATE_RETURN_DEDUCTION',        'FLEET', 'مبلغ خصم التأخير عن إرجاع السيارة',  '100', 'DECIMAL', 'ر.س', '2026-01-01'),
('VEHICLE_LATE_RETURN_ALERT_HOURS',      'FLEET', 'التنبيه قبل موعد الاستلام',           '24',  'INTEGER', 'ساعة', '2026-01-01'),
('VEHICLE_RENTAL_EXPIRY_ALERT_DAYS',     'FLEET', 'التنبيه قبل انتهاء عقد التأجير',      '7',   'INTEGER', 'يوم',  '2026-01-01');

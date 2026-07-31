-- V8: Shift table — one row per imported shift
CREATE TABLE shift (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_shift_id    VARCHAR(50)  NOT NULL,
    ninja_captain_id     VARCHAR(50)  NOT NULL,
    captain_id           UUID         REFERENCES captain(id),  -- NULL if unmatched
    shift_date           DATE         NOT NULL,
    supplier_id          VARCHAR(50),
    supplier_name        VARCHAR(255),
    city                 VARCHAR(100),
    shift_name           VARCHAR(255),
    ninja_captain_name   VARCHAR(255),
    scheduled_start      TIMESTAMPTZ,
    scheduled_end        TIMESTAMPTZ,
    actual_checkin       TIMESTAMPTZ,
    attendance_flag      VARCHAR(50),
    delay_minutes        NUMERIC(10,4),
    working_hours        NUMERIC(10,4),
    shift_type_hours     NUMERIC(10,4),
    total_delivered      INTEGER,
    offline_time         NUMERIC(10,4),
    free_time            NUMERIC(10,4),
    busy_time            NUMERIC(10,4),
    away_from_zone       NUMERIC(10,4),
    busy_utilization     NUMERIC(10,6),        -- fraction (0.7625), NOT percentage
    delivery_ontime      NUMERIC(10,6),        -- fraction
    hourly_utr           NUMERIC(10,4),

    -- Computed by the system:
    qualifies_full_rate  BOOLEAN,
    shift_income         NUMERIC(12,2),
    late_deduction       NUMERIC(12,2),
    net_shift_value      NUMERIC(12,2),
    calc_note            VARCHAR(500),

    import_batch_id      UUID         REFERENCES import_batch(id),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uk_shift_external UNIQUE (external_shift_id, ninja_captain_id)
);

CREATE INDEX idx_shift_captain ON shift(captain_id);
CREATE INDEX idx_shift_date ON shift(shift_date);
CREATE INDEX idx_shift_batch ON shift(import_batch_id);
CREATE INDEX idx_shift_ninja_captain ON shift(ninja_captain_id);

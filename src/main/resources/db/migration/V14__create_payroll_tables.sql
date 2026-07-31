-- V14: Payroll runs and their per-captain lines.
--
-- A payroll run is a snapshot, not a view. Once approved, its numbers must survive
-- a settings change or a re-import of the same period — otherwise re-importing a
-- month would silently rewrite salaries that were already paid.

CREATE TABLE payroll_run (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    period_from       DATE         NOT NULL,
    period_to         DATE         NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',   -- DRAFT | APPROVED | PAID
    title             VARCHAR(255),
    generated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    generated_by      VARCHAR(100),
    approved_at       TIMESTAMPTZ,
    approved_by       VARCHAR(100),
    -- The settings actually used, frozen at generation time.
    settings_snapshot JSONB,
    notes             TEXT,

    CONSTRAINT ck_payroll_period CHECK (period_to >= period_from)
);

CREATE INDEX idx_payroll_run_period ON payroll_run(period_from, period_to);
CREATE INDEX idx_payroll_run_status ON payroll_run(status);

-- Only one non-cancelled run may cover a given period start.
CREATE UNIQUE INDEX uk_payroll_run_period ON payroll_run(period_from, period_to);

CREATE TABLE payroll_line (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payroll_run_id         UUID          NOT NULL REFERENCES payroll_run(id) ON DELETE CASCADE,
    captain_id             UUID          NOT NULL REFERENCES captain(id),

    -- Denormalised so an approved run still reads correctly if a captain is renamed.
    captain_code           VARCHAR(50)   NOT NULL,
    captain_name           VARCHAR(255)  NOT NULL,
    captain_type           VARCHAR(20)   NOT NULL,
    supplier_external_ids  VARCHAR(255),

    -- Activity
    attended_shifts        INTEGER       NOT NULL DEFAULT 0,
    absent_shifts          INTEGER       NOT NULL DEFAULT 0,
    total_orders           INTEGER       NOT NULL DEFAULT 0,
    total_hours            NUMERIC(12,4) NOT NULL DEFAULT 0,
    avg_busy_utilization   NUMERIC(10,6),
    avg_hourly_utr         NUMERIC(10,4),
    avg_delivery_ontime    NUMERIC(10,6),

    -- Earnings
    base_pay               NUMERIC(12,2) NOT NULL DEFAULT 0,
    late_deductions        NUMERIC(12,2) NOT NULL DEFAULT 0,
    daily_incentives       NUMERIC(12,2) NOT NULL DEFAULT 0,
    fuel_allowances        NUMERIC(12,2) NOT NULL DEFAULT 0,
    target_bonus           NUMERIC(12,2) NOT NULL DEFAULT 0,
    extra_order_pay        NUMERIC(12,2) NOT NULL DEFAULT 0,
    gross                  NUMERIC(12,2) NOT NULL DEFAULT 0,
    adjustments_total      NUMERIC(12,2) NOT NULL DEFAULT 0,
    net                    NUMERIC(12,2) NOT NULL DEFAULT 0,

    -- Profitability
    supplier_revenue_total NUMERIC(12,2) NOT NULL DEFAULT 0,
    margin                 NUMERIC(12,2) NOT NULL DEFAULT 0,

    notes                  TEXT,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uk_payroll_line UNIQUE (payroll_run_id, captain_id)
);

CREATE INDEX idx_payroll_line_run ON payroll_line(payroll_run_id);
CREATE INDEX idx_payroll_line_captain ON payroll_line(captain_id);

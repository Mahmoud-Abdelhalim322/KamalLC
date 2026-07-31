-- =============================================================
-- V28: Staff payroll lines (separate from captain payroll)
-- =============================================================

CREATE TABLE staff_payroll_line (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payroll_run_id      UUID NOT NULL REFERENCES payroll_run(id) ON DELETE CASCADE,
    staff_id            UUID NOT NULL REFERENCES staff(id),

    staff_code          VARCHAR(50)   NOT NULL,
    staff_name          VARCHAR(255)  NOT NULL,
    staff_type          VARCHAR(20)   NOT NULL,      -- WORKER | MANAGER
    job_title           VARCHAR(100),

    -- Salary breakdown
    basic_salary        NUMERIC(12,2) NOT NULL DEFAULT 0,
    housing_allowance   NUMERIC(12,2) NOT NULL DEFAULT 0,
    other_allowances    NUMERIC(12,2) NOT NULL DEFAULT 0,

    -- Adjustments (bonuses/deductions added on top)
    adjustments_total   NUMERIC(12,2) NOT NULL DEFAULT 0,

    -- Computed
    gross               NUMERIC(12,2) NOT NULL DEFAULT 0,
    net                 NUMERIC(12,2) NOT NULL DEFAULT 0,

    notes               TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE(payroll_run_id, staff_id)
);

CREATE INDEX idx_spl_run   ON staff_payroll_line(payroll_run_id);
CREATE INDEX idx_spl_staff ON staff_payroll_line(staff_id);

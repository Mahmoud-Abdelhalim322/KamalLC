-- =============================================================
-- V29: Staff manual adjustments
-- =============================================================

CREATE TABLE staff_adjustment (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    staff_id            UUID NOT NULL REFERENCES staff(id),
    payroll_run_id      UUID REFERENCES payroll_run(id) ON DELETE SET NULL,

    period_from         DATE NOT NULL,
    period_to           DATE NOT NULL,

    kind                VARCHAR(20) NOT NULL,      -- BONUS | DEDUCTION
    adjustment_type     VARCHAR(40) NOT NULL,
    label_ar            VARCHAR(255),
    amount              NUMERIC(12,2) NOT NULL,
    reason              TEXT,

    created_by          VARCHAR(100),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_staff_adj_staff_period ON staff_adjustment(staff_id, period_from, period_to);
CREATE INDEX idx_staff_adj_run ON staff_adjustment(payroll_run_id);

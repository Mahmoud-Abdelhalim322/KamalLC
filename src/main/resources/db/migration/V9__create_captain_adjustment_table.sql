-- V9: Captain adjustments table (manual bonuses and deductions)
CREATE TABLE captain_adjustment (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    captain_id  UUID         NOT NULL REFERENCES captain(id) ON DELETE CASCADE,
    period_from DATE         NOT NULL,
    period_to   DATE         NOT NULL,
    kind        VARCHAR(20)  NOT NULL,         -- BONUS | DEDUCTION
    label_ar    VARCHAR(255),
    amount      NUMERIC(12,2) NOT NULL,
    reason      TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_adjustment_captain ON captain_adjustment(captain_id);
CREATE INDEX idx_adjustment_period ON captain_adjustment(period_from, period_to);

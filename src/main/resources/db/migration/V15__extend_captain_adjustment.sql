-- V15: Named adjustment types and payroll linkage.
--
-- The manual columns in Kamal's payroll sheet (بدل سكن / مكافأة / غرامات نينجا /
-- سلف / فروقات البنزين) become typed rows here instead of free-text labels, so the
-- payroll export can place each one in its own column.

ALTER TABLE captain_adjustment
    ADD COLUMN adjustment_type VARCHAR(40),
    ADD COLUMN payroll_run_id  UUID REFERENCES payroll_run(id) ON DELETE SET NULL,
    ADD COLUMN created_by      VARCHAR(100);

-- Existing rows (if any) carry no type — classify them by their kind.
UPDATE captain_adjustment
SET adjustment_type = CASE WHEN kind = 'BONUS' THEN 'OTHER_BONUS' ELSE 'OTHER_DEDUCTION' END
WHERE adjustment_type IS NULL;

ALTER TABLE captain_adjustment
    ALTER COLUMN adjustment_type SET NOT NULL;

ALTER TABLE captain_adjustment
    ADD CONSTRAINT ck_adjustment_type CHECK (adjustment_type IN (
        'HOUSING_ALLOWANCE', 'REWARD', 'FUEL_DIFFERENCE', 'OTHER_BONUS',
        'SUPPLIER_PENALTY', 'LOAN', 'OTHER_DEDUCTION'
    ));

CREATE INDEX idx_adjustment_run ON captain_adjustment(payroll_run_id);

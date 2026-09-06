-- V54: Add paid_at and paid_by audit fields to payroll_run
ALTER TABLE payroll_run
    ADD COLUMN paid_at TIMESTAMPTZ,
    ADD COLUMN paid_by VARCHAR(100);

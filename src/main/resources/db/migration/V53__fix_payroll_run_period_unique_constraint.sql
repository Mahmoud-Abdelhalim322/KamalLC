-- V53: Fix payroll_run unique constraint to include tenant_id for multi-tenant support
DROP INDEX IF EXISTS uk_payroll_run_period;
CREATE UNIQUE INDEX uk_payroll_run_period ON payroll_run (tenant_id, period_from, period_to);

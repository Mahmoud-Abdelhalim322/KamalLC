-- V40: Add supplier_deductions column to payroll_line table
ALTER TABLE payroll_line ADD COLUMN IF NOT EXISTS supplier_deductions NUMERIC(12,2) NOT NULL DEFAULT 0;

-- V59: Add supplier deduction exclusion tracking to shift table
ALTER TABLE shift ADD COLUMN IF NOT EXISTS supplier_deduction_excluded BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE shift ADD COLUMN IF NOT EXISTS supplier_deduction_original NUMERIC(12,2);
ALTER TABLE shift ADD COLUMN IF NOT EXISTS supplier_deduction_exclusion_reason VARCHAR(500);

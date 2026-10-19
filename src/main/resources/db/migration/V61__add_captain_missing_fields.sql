-- =========================================================================
-- V61: Add missing captain fields matching standard roster import
-- =========================================================================

-- Iqama & Identity
ALTER TABLE captain ADD COLUMN IF NOT EXISTS id_expiry_date DATE;
ALTER TABLE captain ADD COLUMN IF NOT EXISTS border_number VARCHAR(50);

-- Driving Licence
ALTER TABLE captain ADD COLUMN IF NOT EXISTS licence_type VARCHAR(50);

-- Salary & Deductions
ALTER TABLE captain ADD COLUMN IF NOT EXISTS salary_deductions NUMERIC(12,2) DEFAULT 0;
ALTER TABLE captain ADD COLUMN IF NOT EXISTS net_salary NUMERIC(12,2) DEFAULT 0;

-- Add captain_name_en column to payroll_line for English name in payslips
ALTER TABLE payroll_line ADD COLUMN IF NOT EXISTS captain_name_en VARCHAR(255);

-- Backfill from captain table
UPDATE payroll_line pl
SET captain_name_en = c.full_name_en
FROM captain c
WHERE pl.captain_id = c.id
  AND pl.captain_name_en IS NULL;

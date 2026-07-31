-- V18: Repair fuel allowance eligibility for captains driving their own vehicle.
--
-- V13 added fuel_allowance_eligible and backfilled it once, but no create/update path
-- ever wrote it: every captain saved through the UI kept the false default, so the daily
-- fuel allowance never reached their shifts or their payroll line. The write path is now
-- wired to the own-vehicle flag; this aligns the rows that were saved while it was not.

UPDATE captain
SET fuel_allowance_eligible = true
WHERE uses_own_vehicle = true
  AND fuel_allowance_eligible = false;

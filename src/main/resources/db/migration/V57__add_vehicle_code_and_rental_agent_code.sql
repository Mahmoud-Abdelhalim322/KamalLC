-- =========================================================================
-- V57: Add vehicle_code and rental_agent_code to vehicle table
-- =========================================================================

ALTER TABLE vehicle ADD COLUMN IF NOT EXISTS vehicle_code VARCHAR(30);
ALTER TABLE vehicle ADD COLUMN IF NOT EXISTS rental_agent_code VARCHAR(20);

CREATE INDEX IF NOT EXISTS idx_vehicle_code ON vehicle(vehicle_code);

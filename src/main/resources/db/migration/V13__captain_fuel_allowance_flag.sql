-- V13: Per-captain fuel allowance eligibility.
--
-- The 35 SAR/day fuel allowance is decided captain by captain, not by type — so it
-- is a flag on the captain rather than a rule in the calculator. The default below
-- just seeds the common case (freelancers driving their own vehicle).

ALTER TABLE captain
    ADD COLUMN fuel_allowance_eligible BOOLEAN NOT NULL DEFAULT false;

UPDATE captain
SET fuel_allowance_eligible = true
WHERE captain_type = 'FREELANCE' AND uses_own_vehicle = true;

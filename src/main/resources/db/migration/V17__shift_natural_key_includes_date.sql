-- V17: the shift's natural key is (shift_id, captain_id, shift_date), not the pair alone.
--
-- V8 assumed a supplier shift_id identified one rostered slot for all time. It does not:
-- the supplier reuses a shift_id across days, so a captain who works the same slot twice
-- in a month produces two rows sharing a key. Under the old constraint the import found
-- the first row and updated it in place, and the earlier day was silently overwritten —
-- an import that reported success while losing shifts.
--
-- Widening a unique key can never make existing rows conflict, so this needs no cleanup.
-- Days lost to the old key are recovered by re-importing the file they came from.

ALTER TABLE shift DROP CONSTRAINT uk_shift_external;

ALTER TABLE shift
    ADD CONSTRAINT uk_shift_external UNIQUE (external_shift_id, ninja_captain_id, shift_date);

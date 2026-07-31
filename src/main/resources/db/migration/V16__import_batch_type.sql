-- V16: Tell the two kinds of import apart.
--
-- Captain files and supplier shift reports have always shared import_batch, so the
-- history screen mixed them and its columns meant different things per row. A batch
-- now carries its own type, letting each import screen show only its own history.

ALTER TABLE import_batch ADD COLUMN batch_type VARCHAR(20);

-- Only the shift importer ever recorded a period, so it identifies existing rows.
UPDATE import_batch
SET batch_type = CASE WHEN period_from IS NOT NULL THEN 'SHIFTS' ELSE 'CAPTAINS' END
WHERE batch_type IS NULL;

ALTER TABLE import_batch ALTER COLUMN batch_type SET DEFAULT 'CAPTAINS';
ALTER TABLE import_batch ALTER COLUMN batch_type SET NOT NULL;

ALTER TABLE import_batch
    ADD CONSTRAINT ck_import_batch_type CHECK (batch_type IN ('CAPTAINS', 'SHIFTS'));

CREATE INDEX idx_import_batch_type ON import_batch(batch_type, uploaded_at DESC);

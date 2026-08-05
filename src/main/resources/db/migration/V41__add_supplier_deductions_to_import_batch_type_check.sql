ALTER TABLE import_batch DROP CONSTRAINT IF EXISTS ck_import_batch_type;
ALTER TABLE import_batch
    ADD CONSTRAINT ck_import_batch_type CHECK (batch_type IN ('CAPTAINS', 'SHIFTS', 'SUPPLIER_DEDUCTIONS'));

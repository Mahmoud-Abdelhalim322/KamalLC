ALTER TABLE import_batch
ADD COLUMN supplier_id UUID CONSTRAINT fk_import_batch_supplier REFERENCES supplier(id);

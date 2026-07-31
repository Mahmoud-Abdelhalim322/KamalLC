-- V10: Multi-supplier support
-- Adds a supplier table and refactors ninja-specific tables to be supplier-generic.

-- 1. Create supplier table
CREATE TABLE supplier (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    code        VARCHAR(50)  NOT NULL UNIQUE,
    color       VARCHAR(20)  DEFAULT '#4CAF50',
    is_active   BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 2. Seed Ninja as the first supplier
INSERT INTO supplier (name, code, color) VALUES ('نينجا', 'NINJA', '#E53935');

-- 3. Rename ninja tables to generic supplier tables
ALTER TABLE ninja_id_slot RENAME TO supplier_id_slot;
ALTER TABLE ninja_id_assignment RENAME TO supplier_id_assignment;

-- 4. Add supplier reference to supplier_id_slot
ALTER TABLE supplier_id_slot ADD COLUMN supplier_id UUID REFERENCES supplier(id);
UPDATE supplier_id_slot SET supplier_id = (SELECT id FROM supplier WHERE code = 'NINJA');
ALTER TABLE supplier_id_slot ALTER COLUMN supplier_id SET NOT NULL;

-- 5. Rename columns to be supplier-generic
ALTER TABLE supplier_id_slot RENAME COLUMN ninja_id TO external_id;
ALTER TABLE supplier_id_assignment RENAME COLUMN ninja_id_slot_id TO supplier_id_slot_id;
ALTER TABLE supplier_id_assignment RENAME COLUMN ninja_side_name TO supplier_side_name;

-- 6. Replace single-column unique with composite unique (supplier + external_id)
ALTER TABLE supplier_id_slot DROP CONSTRAINT ninja_id_slot_ninja_id_key;
ALTER TABLE supplier_id_slot ADD CONSTRAINT uk_supplier_external_id UNIQUE(supplier_id, external_id);

-- 7. Add index for supplier lookup
CREATE INDEX idx_supplier_id_slot_supplier ON supplier_id_slot(supplier_id);

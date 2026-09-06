-- V50: Fix unique constraint on asset_category.name and seed default categories (SIM_CARD, PHONE) for all tenants.

-- Drop global unique constraint on asset_category.name if present
ALTER TABLE asset_category DROP CONSTRAINT IF EXISTS asset_category_name_key;

-- Add tenant-scoped unique constraint for name if not already exists
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'asset_category_name_tenant_unique'
    ) THEN
        ALTER TABLE asset_category ADD CONSTRAINT asset_category_name_tenant_unique UNIQUE (tenant_id, name);
    END IF;
END $$;

-- Insert SIM_CARD category for any tenant missing it
INSERT INTO asset_category (id, name, label_ar, system_defined, tenant_id, created_at)
SELECT gen_random_uuid(), 'SIM_CARD', 'شريحة', TRUE, t.id, NOW()
FROM tenant t
WHERE NOT EXISTS (
    SELECT 1 FROM asset_category ac WHERE ac.tenant_id = t.id AND ac.name = 'SIM_CARD'
);

-- Insert PHONE category for any tenant missing it
INSERT INTO asset_category (id, name, label_ar, system_defined, tenant_id, created_at)
SELECT gen_random_uuid(), 'PHONE', 'هاتف', TRUE, t.id, NOW()
FROM tenant t
WHERE NOT EXISTS (
    SELECT 1 FROM asset_category ac WHERE ac.tenant_id = t.id AND ac.name = 'PHONE'
);

-- Insert field templates for SIM_CARD where missing
INSERT INTO asset_category_field_template (id, category_id, field_key, label_ar, field_order, required)
SELECT gen_random_uuid(), ac.id, 'sim_type', 'نوع الشريحة', 1, TRUE
FROM asset_category ac
WHERE ac.name = 'SIM_CARD'
  AND NOT EXISTS (
      SELECT 1 FROM asset_category_field_template ft WHERE ft.category_id = ac.id AND ft.field_key = 'sim_type'
  );

INSERT INTO asset_category_field_template (id, category_id, field_key, label_ar, field_order, required)
SELECT gen_random_uuid(), ac.id, 'phone_number', 'الرقم', 2, TRUE
FROM asset_category ac
WHERE ac.name = 'SIM_CARD'
  AND NOT EXISTS (
      SELECT 1 FROM asset_category_field_template ft WHERE ft.category_id = ac.id AND ft.field_key = 'phone_number'
  );

-- Insert field templates for PHONE where missing
INSERT INTO asset_category_field_template (id, category_id, field_key, label_ar, field_order, required)
SELECT gen_random_uuid(), ac.id, 'imei', 'IMEI', 1, TRUE
FROM asset_category ac
WHERE ac.name = 'PHONE'
  AND NOT EXISTS (
      SELECT 1 FROM asset_category_field_template ft WHERE ft.category_id = ac.id AND ft.field_key = 'imei'
  );

INSERT INTO asset_category_field_template (id, category_id, field_key, label_ar, field_order, required)
SELECT gen_random_uuid(), ac.id, 'phone_type', 'نوع الجوال', 2, TRUE
FROM asset_category ac
WHERE ac.name = 'PHONE'
  AND NOT EXISTS (
      SELECT 1 FROM asset_category_field_template ft WHERE ft.category_id = ac.id AND ft.field_key = 'phone_type'
  );

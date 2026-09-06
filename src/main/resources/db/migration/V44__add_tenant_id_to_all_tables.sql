-- =============================================
-- V44: Add tenant_id to ALL existing tables
-- =============================================
-- Assigns all existing data to the default tenant.
-- Uses '00000000-0000-0000-0000-000000000001' as the default tenant.

-- ---- captain ----
ALTER TABLE captain ADD COLUMN tenant_id UUID REFERENCES tenant(id);
UPDATE captain SET tenant_id = '00000000-0000-0000-0000-000000000001';
ALTER TABLE captain ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_captain_tenant ON captain(tenant_id);

-- ---- captain_document ----
ALTER TABLE captain_document ADD COLUMN tenant_id UUID REFERENCES tenant(id);
UPDATE captain_document SET tenant_id = '00000000-0000-0000-0000-000000000001';
ALTER TABLE captain_document ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_captain_document_tenant ON captain_document(tenant_id);

-- ---- staff ----
ALTER TABLE staff ADD COLUMN tenant_id UUID REFERENCES tenant(id);
UPDATE staff SET tenant_id = '00000000-0000-0000-0000-000000000001';
ALTER TABLE staff ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_staff_tenant ON staff(tenant_id);

-- ---- staff_document ----
ALTER TABLE staff_document ADD COLUMN tenant_id UUID REFERENCES tenant(id);
UPDATE staff_document SET tenant_id = '00000000-0000-0000-0000-000000000001';
ALTER TABLE staff_document ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_staff_document_tenant ON staff_document(tenant_id);

-- ---- shift ----
ALTER TABLE shift ADD COLUMN tenant_id UUID REFERENCES tenant(id);
UPDATE shift SET tenant_id = '00000000-0000-0000-0000-000000000001';
ALTER TABLE shift ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_shift_tenant ON shift(tenant_id);

-- ---- supplier ----
ALTER TABLE supplier ADD COLUMN tenant_id UUID REFERENCES tenant(id);
UPDATE supplier SET tenant_id = '00000000-0000-0000-0000-000000000001';
ALTER TABLE supplier ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_supplier_tenant ON supplier(tenant_id);

-- ---- supplier_id_slot ----
ALTER TABLE supplier_id_slot ADD COLUMN tenant_id UUID REFERENCES tenant(id);
UPDATE supplier_id_slot SET tenant_id = '00000000-0000-0000-0000-000000000001';
ALTER TABLE supplier_id_slot ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_supplier_id_slot_tenant ON supplier_id_slot(tenant_id);

-- ---- supplier_id_assignment ----
ALTER TABLE supplier_id_assignment ADD COLUMN tenant_id UUID REFERENCES tenant(id);
UPDATE supplier_id_assignment SET tenant_id = '00000000-0000-0000-0000-000000000001';
ALTER TABLE supplier_id_assignment ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_supplier_id_assignment_tenant ON supplier_id_assignment(tenant_id);

-- ---- supplier_adjustment ----
ALTER TABLE supplier_adjustment ADD COLUMN tenant_id UUID REFERENCES tenant(id);
UPDATE supplier_adjustment SET tenant_id = '00000000-0000-0000-0000-000000000001';
ALTER TABLE supplier_adjustment ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_supplier_adjustment_tenant ON supplier_adjustment(tenant_id);

-- ---- vehicle ----
ALTER TABLE vehicle ADD COLUMN tenant_id UUID REFERENCES tenant(id);
UPDATE vehicle SET tenant_id = '00000000-0000-0000-0000-000000000001';
ALTER TABLE vehicle ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_vehicle_tenant ON vehicle(tenant_id);

-- ---- vehicle_assignment ----
ALTER TABLE vehicle_assignment ADD COLUMN tenant_id UUID REFERENCES tenant(id);
UPDATE vehicle_assignment SET tenant_id = '00000000-0000-0000-0000-000000000001';
ALTER TABLE vehicle_assignment ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_vehicle_assignment_tenant ON vehicle_assignment(tenant_id);

-- ---- vehicle_maintenance ----
ALTER TABLE vehicle_maintenance ADD COLUMN tenant_id UUID REFERENCES tenant(id);
UPDATE vehicle_maintenance SET tenant_id = '00000000-0000-0000-0000-000000000001';
ALTER TABLE vehicle_maintenance ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_vehicle_maintenance_tenant ON vehicle_maintenance(tenant_id);

-- ---- asset_category ----
ALTER TABLE asset_category ADD COLUMN tenant_id UUID REFERENCES tenant(id);
UPDATE asset_category SET tenant_id = '00000000-0000-0000-0000-000000000001';
ALTER TABLE asset_category ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_asset_category_tenant ON asset_category(tenant_id);

-- ---- asset_item ----
ALTER TABLE asset_item ADD COLUMN tenant_id UUID REFERENCES tenant(id);
UPDATE asset_item SET tenant_id = '00000000-0000-0000-0000-000000000001';
ALTER TABLE asset_item ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_asset_item_tenant ON asset_item(tenant_id);

-- ---- custody_record ----
ALTER TABLE custody_record ADD COLUMN tenant_id UUID REFERENCES tenant(id);
UPDATE custody_record SET tenant_id = '00000000-0000-0000-0000-000000000001';
ALTER TABLE custody_record ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_custody_record_tenant ON custody_record(tenant_id);

-- ---- loan ----
ALTER TABLE loan ADD COLUMN tenant_id UUID REFERENCES tenant(id);
UPDATE loan SET tenant_id = '00000000-0000-0000-0000-000000000001';
ALTER TABLE loan ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_loan_tenant ON loan(tenant_id);

-- ---- loan_payment ----
ALTER TABLE loan_payment ADD COLUMN tenant_id UUID REFERENCES tenant(id);
UPDATE loan_payment SET tenant_id = '00000000-0000-0000-0000-000000000001';
ALTER TABLE loan_payment ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_loan_payment_tenant ON loan_payment(tenant_id);

-- ---- payroll_run ----
ALTER TABLE payroll_run ADD COLUMN tenant_id UUID REFERENCES tenant(id);
UPDATE payroll_run SET tenant_id = '00000000-0000-0000-0000-000000000001';
ALTER TABLE payroll_run ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_payroll_run_tenant ON payroll_run(tenant_id);

-- ---- payroll_line ----
ALTER TABLE payroll_line ADD COLUMN tenant_id UUID REFERENCES tenant(id);
UPDATE payroll_line SET tenant_id = '00000000-0000-0000-0000-000000000001';
ALTER TABLE payroll_line ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_payroll_line_tenant ON payroll_line(tenant_id);

-- ---- staff_payroll_line ----
ALTER TABLE staff_payroll_line ADD COLUMN tenant_id UUID REFERENCES tenant(id);
UPDATE staff_payroll_line SET tenant_id = '00000000-0000-0000-0000-000000000001';
ALTER TABLE staff_payroll_line ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_staff_payroll_line_tenant ON staff_payroll_line(tenant_id);

-- ---- captain_adjustment ----
ALTER TABLE captain_adjustment ADD COLUMN tenant_id UUID REFERENCES tenant(id);
UPDATE captain_adjustment SET tenant_id = '00000000-0000-0000-0000-000000000001';
ALTER TABLE captain_adjustment ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_captain_adjustment_tenant ON captain_adjustment(tenant_id);

-- ---- staff_adjustment ----
ALTER TABLE staff_adjustment ADD COLUMN tenant_id UUID REFERENCES tenant(id);
UPDATE staff_adjustment SET tenant_id = '00000000-0000-0000-0000-000000000001';
ALTER TABLE staff_adjustment ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_staff_adjustment_tenant ON staff_adjustment(tenant_id);

-- ---- app_setting ----
ALTER TABLE app_setting ADD COLUMN tenant_id UUID REFERENCES tenant(id);
UPDATE app_setting SET tenant_id = '00000000-0000-0000-0000-000000000001';
ALTER TABLE app_setting ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_app_setting_tenant ON app_setting(tenant_id);

-- ---- import_batch ----
ALTER TABLE import_batch ADD COLUMN tenant_id UUID REFERENCES tenant(id);
UPDATE import_batch SET tenant_id = '00000000-0000-0000-0000-000000000001';
ALTER TABLE import_batch ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_import_batch_tenant ON import_batch(tenant_id);

-- ---- import_row_issue ----
ALTER TABLE import_row_issue ADD COLUMN tenant_id UUID REFERENCES tenant(id);
UPDATE import_row_issue SET tenant_id = '00000000-0000-0000-0000-000000000001';
ALTER TABLE import_row_issue ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_import_row_issue_tenant ON import_row_issue(tenant_id);

-- ---- job_title ----
ALTER TABLE job_title ADD COLUMN tenant_id UUID REFERENCES tenant(id);
UPDATE job_title SET tenant_id = '00000000-0000-0000-0000-000000000001';
ALTER TABLE job_title ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_job_title_tenant ON job_title(tenant_id);

-- ---- Drop unique constraints that must now be per-tenant ----
-- captain.kamal_code must be unique per tenant, not globally
ALTER TABLE captain DROP CONSTRAINT IF EXISTS captain_kamal_code_key;
ALTER TABLE captain ADD CONSTRAINT captain_kamal_code_tenant_unique UNIQUE (tenant_id, kamal_code);

-- staff.staff_code must be unique per tenant
ALTER TABLE staff DROP CONSTRAINT IF EXISTS staff_staff_code_key;
ALTER TABLE staff ADD CONSTRAINT staff_staff_code_tenant_unique UNIQUE (tenant_id, staff_code);

-- supplier.code must be unique per tenant
ALTER TABLE supplier DROP CONSTRAINT IF EXISTS supplier_code_key;
ALTER TABLE supplier ADD CONSTRAINT supplier_code_tenant_unique UNIQUE (tenant_id, code);

-- vehicle.plate_number must be unique per tenant
ALTER TABLE vehicle DROP CONSTRAINT IF EXISTS vehicle_plate_number_key;
ALTER TABLE vehicle ADD CONSTRAINT vehicle_plate_number_tenant_unique UNIQUE (tenant_id, plate_number);

-- asset_item.serial_code must be unique per tenant
ALTER TABLE asset_item DROP CONSTRAINT IF EXISTS asset_item_serial_code_key;
ALTER TABLE asset_item ADD CONSTRAINT asset_item_serial_code_tenant_unique UNIQUE (tenant_id, serial_code);

-- job_title.name must be unique per tenant
ALTER TABLE job_title DROP CONSTRAINT IF EXISTS job_title_name_key;
ALTER TABLE job_title ADD CONSTRAINT job_title_name_tenant_unique UNIQUE (tenant_id, name);

-- app_user.username remains globally unique (login across tenants)

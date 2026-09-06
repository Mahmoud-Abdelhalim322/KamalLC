-- V52: Add vacation_commission table, column to payroll_line, and batch_type check constraint update

ALTER TABLE payroll_line ADD COLUMN IF NOT EXISTS vacation_commission NUMERIC(12,2) NOT NULL DEFAULT 0;

ALTER TABLE import_batch DROP CONSTRAINT IF EXISTS ck_import_batch_type;
ALTER TABLE import_batch
    ADD CONSTRAINT ck_import_batch_type CHECK (batch_type IN ('CAPTAINS', 'SHIFTS', 'SUPPLIER_DEDUCTIONS', 'VACATION_COMMISSION'));

CREATE TABLE IF NOT EXISTS vacation_commission (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    days_code VARCHAR(100) NOT NULL,
    supplier_id UUID,
    supplier_name VARCHAR(150),
    external_captain_id VARCHAR(100) NOT NULL,
    captain_id UUID,
    zone_name VARCHAR(150),
    flag VARCHAR(100),
    note TEXT,
    amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    vacation_date DATE NOT NULL,
    import_batch_id UUID,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_vacation_comm_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id) ON DELETE CASCADE,
    CONSTRAINT fk_vacation_comm_supplier FOREIGN KEY (supplier_id) REFERENCES supplier(id) ON DELETE SET NULL,
    CONSTRAINT fk_vacation_comm_captain FOREIGN KEY (captain_id) REFERENCES captain(id) ON DELETE SET NULL,
    CONSTRAINT fk_vacation_comm_batch FOREIGN KEY (import_batch_id) REFERENCES import_batch(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_vacation_comm_tenant_date ON vacation_commission(tenant_id, vacation_date);
CREATE INDEX IF NOT EXISTS idx_vacation_comm_captain ON vacation_commission(captain_id);

-- =============================================================
-- V33: Create supplier_adjustment table
-- =============================================================

CREATE TABLE supplier_adjustment (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_id         UUID NOT NULL REFERENCES supplier(id) ON DELETE CASCADE,
    type                VARCHAR(20) NOT NULL,
    category            VARCHAR(100) NOT NULL,
    amount              NUMERIC(12,2) NOT NULL,
    adjustment_date     DATE NOT NULL,
    notes               VARCHAR(500),
    created_by          VARCHAR(100),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_supplier_adj_supplier_date ON supplier_adjustment(supplier_id, adjustment_date);

-- =============================================================
-- V42: Add supplier_deduction_reason column to shift table
-- Stores optional reason / note for per-shift supplier (Ninja) deduction
-- =============================================================

ALTER TABLE shift ADD COLUMN supplier_deduction_reason VARCHAR(500);

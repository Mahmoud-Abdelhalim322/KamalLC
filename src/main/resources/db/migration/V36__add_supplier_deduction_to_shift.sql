-- =============================================================
-- V36: Add supplier_deduction column to shift table
-- Stores per-shift penalties from the supplier (Ninja)
-- imported via a separate daily deduction sheet.
-- =============================================================

ALTER TABLE shift ADD COLUMN supplier_deduction NUMERIC(12,2) NOT NULL DEFAULT 0;

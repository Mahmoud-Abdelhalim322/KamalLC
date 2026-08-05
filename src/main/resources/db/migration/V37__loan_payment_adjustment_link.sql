-- =============================================================
-- V37: Link loan_payment to manual payroll adjustments
--      Adds optional FK columns so that when an admin adds a
--      manual LOAN deduction in the payroll run the resulting
--      LoanPayment row can be traced back to its adjustment.
-- =============================================================

ALTER TABLE loan_payment
    ADD COLUMN captain_adjustment_id UUID REFERENCES captain_adjustment(id) ON DELETE SET NULL,
    ADD COLUMN staff_adjustment_id   UUID REFERENCES staff_adjustment(id)   ON DELETE SET NULL;

CREATE INDEX idx_loan_payment_captain_adj ON loan_payment(captain_adjustment_id);
CREATE INDEX idx_loan_payment_staff_adj   ON loan_payment(staff_adjustment_id);

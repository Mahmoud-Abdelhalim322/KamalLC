-- V12: Captain-pay and supplier-revenue columns on shift.
--
-- V8 only modelled the revenue side (shift_income / late_deduction / net_shift_value).
-- The captain-pay side is a separate calculation with its own rules, so it gets its
-- own columns rather than overloading the existing ones.

ALTER TABLE shift
    ADD COLUMN is_attended            BOOLEAN,
    ADD COLUMN daily_wage             NUMERIC(12,2),
    ADD COLUMN daily_incentive        NUMERIC(12,2),
    ADD COLUMN extra_orders           INTEGER,
    ADD COLUMN fuel_allowance         NUMERIC(12,2),
    ADD COLUMN net_shift_pay          NUMERIC(12,2),
    ADD COLUMN supplier_revenue       NUMERIC(12,2),
    ADD COLUMN supplier_extra_revenue NUMERIC(12,2),
    ADD COLUMN shift_margin           NUMERIC(12,2),
    ADD COLUMN is_excluded            BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN exclusion_reason       VARCHAR(500),
    ADD COLUMN zone_name              VARCHAR(255),
    ADD COLUMN supplier_ref_id        UUID REFERENCES supplier(id),
    -- effective_from of the settings row set used to compute this shift, so a
    -- recalculation can tell which shifts were priced under which rules.
    ADD COLUMN settings_version       DATE;

CREATE INDEX idx_shift_captain_date ON shift(captain_id, shift_date);
CREATE INDEX idx_shift_unmatched ON shift(shift_date) WHERE captain_id IS NULL;

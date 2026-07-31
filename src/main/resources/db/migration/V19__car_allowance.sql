-- V19: Car allowance, paid separately from the fuel allowance.
--
-- A captain driving his own vehicle is compensated for the vehicle itself (31 SAR) on a
-- shift that reaches the full 11 hours. That is a different compensation from the fuel he
-- burns, so each allowance carries its own switch, rate and hours threshold: a period can
-- run the car allowance alone, the fuel allowance alone, both, or neither.

ALTER TABLE shift
    ADD COLUMN car_allowance NUMERIC(12,2);

ALTER TABLE payroll_line
    ADD COLUMN car_allowances NUMERIC(12,2) NOT NULL DEFAULT 0;

-- CAR_ALLOWANCE_DAILY was seeded in V6 and closed on 2025-12-31 by V11, which had folded
-- the vehicle into the fuel allowance. It reopens from the day after, so the key has no gap.
--
-- The switch starts off: turning it on is an effective-dated decision taken from the
-- settings screen, exactly like a rate change, so no already-priced shift moves by itself.
INSERT INTO app_setting (setting_key, setting_group, label_ar, value, value_type, unit_label, effective_from) VALUES
('CAR_ALLOWANCE_ENABLED',         'ALLOWANCE', 'تفعيل بدل السيارة',                        'false', 'BOOLEAN',  '',     '2026-01-01'),
('CAR_ALLOWANCE_DAILY',           'ALLOWANCE', 'بدل السيارة اليومي',                       '31',    'CURRENCY', 'ريال', '2026-01-01'),
('CAR_ALLOWANCE_HOURS_THRESHOLD', 'ALLOWANCE', 'الحد الأدنى للساعات لاستحقاق بدل السيارة', '11',    'DECIMAL',  'ساعة', '2026-01-01'),
('FUEL_ALLOWANCE_ENABLED',        'ALLOWANCE', 'تفعيل بدل البنزين',                        'true',  'BOOLEAN',  '',     '2026-01-01');

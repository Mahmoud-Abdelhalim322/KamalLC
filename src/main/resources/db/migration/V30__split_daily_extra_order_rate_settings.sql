-- V30: Split captain daily extra order rate into company car / employee vs own car.

-- 1. Close active DAILY_EXTRA_ORDER_RATE row
UPDATE app_setting
SET effective_to = DATE '2025-12-31'
WHERE effective_to IS NULL
  AND setting_key = 'DAILY_EXTRA_ORDER_RATE';

-- 2. Insert new split settings
INSERT INTO app_setting (setting_key, setting_group, label_ar, value, value_type, unit_label, effective_from) VALUES
('DAILY_EXTRA_ORDER_RATE_COMPANY_CAR', 'SHIFT_INCOME', 'حافز الطلب الإضافي — كفالة / بسيارة الشركة', '3', 'CURRENCY', 'ريال', '2026-01-01'),
('DAILY_EXTRA_ORDER_RATE_OWN_CAR',     'SHIFT_INCOME', 'حافز الطلب الإضافي — فريلانس بسيارته الخاصة', '5', 'CURRENCY', 'ريال', '2026-01-01');

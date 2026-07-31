-- V11: Re-seed settings for the captain payroll rules agreed with Kamal.
--
-- app_setting is effective-dated: a rate change must never rewrite a payroll that
-- was already computed. So we close the V6 rows instead of updating them, and open
-- new rows from 2026-01-01 onwards.

-- 1. Close the rows the new rules replace.
--    FIRST_HOUR_LATE_PENALTY / SUBSEQUENT_HOUR_LATE_PENALTY are superseded by the
--    LATE_TIER_* ladder; CAR_ALLOWANCE_DAILY is superseded by the fuel allowance.
UPDATE app_setting
SET effective_to = DATE '2025-12-31'
WHERE effective_to IS NULL
  AND setting_key IN (
      'FIRST_HOUR_LATE_PENALTY',
      'SUBSEQUENT_HOUR_LATE_PENALTY',
      'CAR_ALLOWANCE_DAILY',
      'FREELANCE_EXTRA_ORDER_RATE',
      'EMPLOYEE_EXTRA_ORDER_RATE',
      'ORDER_BONUS_THRESHOLD',
      'EXTRA_ORDER_RATE'
  );

-- 2. Captain pay basis
INSERT INTO app_setting (setting_key, setting_group, label_ar, value, value_type, unit_label, effective_from) VALUES
('CAPTAIN_DAILY_RATE',   'SALARY', 'يومية الكابتن',                '57',   'CURRENCY', 'ريال', '2026-01-01'),
('STANDARD_WORK_DAYS',   'SALARY', 'عدد أيام العمل القياسية',      '26',   'INTEGER',  'يوم',  '2026-01-01'),
('MIN_ATTENDED_HOURS',   'SALARY', 'الحد الأدنى لاحتساب الشفت حضورًا', '1', 'DECIMAL',  'ساعة', '2026-01-01');

-- 3. Late-hour deduction ladder.
--    Read as: find the highest LATE_TIER_MIN_HOURS_n that working_hours reaches,
--    and apply the matching LATE_TIER_DEDUCTION_n. Tier 1 (>= 11h) means no deduction.
INSERT INTO app_setting (setting_key, setting_group, label_ar, value, value_type, unit_label, effective_from) VALUES
('LATE_TIER_MIN_HOURS_1', 'PENALTY', 'شريحة 1 — من (ساعة)',  '11', 'DECIMAL',  'ساعة', '2026-01-01'),
('LATE_TIER_DEDUCTION_1', 'PENALTY', 'شريحة 1 — الخصم',      '0',  'CURRENCY', 'ريال', '2026-01-01'),
('LATE_TIER_MIN_HOURS_2', 'PENALTY', 'شريحة 2 — من (ساعة)',  '10', 'DECIMAL',  'ساعة', '2026-01-01'),
('LATE_TIER_DEDUCTION_2', 'PENALTY', 'شريحة 2 — الخصم',      '25', 'CURRENCY', 'ريال', '2026-01-01'),
('LATE_TIER_MIN_HOURS_3', 'PENALTY', 'شريحة 3 — من (ساعة)',  '8',  'DECIMAL',  'ساعة', '2026-01-01'),
('LATE_TIER_DEDUCTION_3', 'PENALTY', 'شريحة 3 — الخصم',      '35', 'CURRENCY', 'ريال', '2026-01-01'),
('LATE_TIER_MIN_HOURS_4', 'PENALTY', 'شريحة 4 — من (ساعة)',  '7',  'DECIMAL',  'ساعة', '2026-01-01'),
('LATE_TIER_DEDUCTION_4', 'PENALTY', 'شريحة 4 — الخصم',      '45', 'CURRENCY', 'ريال', '2026-01-01'),
('LATE_TIER_MIN_HOURS_5', 'PENALTY', 'شريحة 5 — من (ساعة)',  '0',  'DECIMAL',  'ساعة', '2026-01-01'),
('LATE_TIER_DEDUCTION_5', 'PENALTY', 'شريحة 5 — الخصم',      '57', 'CURRENCY', 'ريال', '2026-01-01');

-- 4. Daily order incentive: 5 SAR for every order above the threshold.
--    Kamal receives 10 SAR per extra order from Ninja and passes 5 on to the captain.
INSERT INTO app_setting (setting_key, setting_group, label_ar, value, value_type, unit_label, effective_from) VALUES
('DAILY_ORDER_THRESHOLD',  'SHIFT_INCOME', 'حد الطلبات للحافز اليومي',      '18', 'INTEGER',  'طلب',  '2026-01-01'),
('DAILY_EXTRA_ORDER_RATE', 'SHIFT_INCOME', 'حافز الكابتن لكل طلب إضافي',   '5',  'CURRENCY', 'ريال', '2026-01-01'),
('SUPPLIER_EXTRA_ORDER_RATE', 'SHIFT_INCOME', 'إيراد المورد لكل طلب إضافي', '10', 'CURRENCY', 'ريال', '2026-01-01');

-- 5. Fuel allowance
INSERT INTO app_setting (setting_key, setting_group, label_ar, value, value_type, unit_label, effective_from) VALUES
('FUEL_HOURS_THRESHOLD', 'ALLOWANCE', 'الحد الأدنى للساعات لاستحقاق بدل البنزين', '11', 'DECIMAL', 'ساعة', '2026-01-01');
-- FUEL_ALLOWANCE_DAILY (35) is already seeded in V6 and still current.

-- 6. Monthly target
INSERT INTO app_setting (setting_key, setting_group, label_ar, value, value_type, unit_label, effective_from) VALUES
('POST_TARGET_RATE_EMPLOYEE',              'TARGET', 'بعد الهدف — كفالة (لكل طلب)',                '3', 'CURRENCY', 'ريال', '2026-01-01'),
('POST_TARGET_RATE_FREELANCE_COMPANY_CAR', 'TARGET', 'بعد الهدف — فريلانس بسيارة الشركة (لكل طلب)', '3', 'CURRENCY', 'ريال', '2026-01-01'),
('POST_TARGET_RATE_FREELANCE_OWN_CAR',     'TARGET', 'بعد الهدف — فريلانس بسيارته (لكل طلب)',      '5', 'CURRENCY', 'ريال', '2026-01-01');
-- MONTHLY_ORDER_TARGET (350) and TARGET_ACHIEVEMENT_BONUS (1000) already seeded in V6.
-- HOURS_THRESHOLD (11), UTILIZATION_THRESHOLD (0.75), FULL_SHIFT_RATE (230) and
-- PER_ORDER_RATE (10) stay as-is — they now describe the supplier revenue side only.

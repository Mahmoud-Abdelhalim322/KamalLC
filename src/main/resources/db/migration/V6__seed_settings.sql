-- V6: Seed all default settings
INSERT INTO app_setting (setting_key, setting_group, label_ar, value, value_type, unit_label, effective_from) VALUES
('HOURS_THRESHOLD',                     'SHIFT_INCOME', 'الحد الأدنى لساعات العمل',              '11',    'DECIMAL',    'ساعة',      '2024-01-01'),
('UTILIZATION_THRESHOLD',               'SHIFT_INCOME', 'الحد الأدنى لنسبة الانشغال',            '0.75',  'PERCENTAGE', '%',         '2024-01-01'),
('FULL_SHIFT_RATE',                     'SHIFT_INCOME', 'قيمة الشفت الكامل',                     '230',   'CURRENCY',   'ريال',      '2024-01-01'),
('ORDER_BONUS_THRESHOLD',               'SHIFT_INCOME', 'حد الأوردرات للعمولة الإضافية',         '18',    'INTEGER',    'أوردر',     '2024-01-01'),
('REQUIRE_ORDER_THRESHOLD_FOR_FULL_RATE','SHIFT_INCOME', 'اشتراط 18 أوردر لتحقيق الشفت',        'false', 'BOOLEAN',    '',          '2024-01-01'),
('EXTRA_ORDER_RATE',                    'SHIFT_INCOME', 'قيمة الأوردر الإضافي',                  '10',    'CURRENCY',   'ريال',      '2024-01-01'),
('PER_ORDER_RATE',                      'SHIFT_INCOME', 'قيمة الأوردر (شفت غير مكتمل)',          '10',    'CURRENCY',   'ريال',      '2024-01-01'),
('FIRST_HOUR_LATE_PENALTY',             'PENALTY',      'خصم أول ساعة تأخير',                    '25',    'CURRENCY',   'ريال',      '2024-01-01'),
('SUBSEQUENT_HOUR_LATE_PENALTY',        'PENALTY',      'خصم كل ساعة تأخير إضافية',              '10',    'CURRENCY',   'ريال',      '2024-01-01'),
('MONTHLY_ORDER_TARGET',                'TARGET',       'هدف الأوردرات الشهري',                  '350',   'INTEGER',    'أوردر',     '2024-01-01'),
('TARGET_ACHIEVEMENT_BONUS',            'TARGET',       'مكافأة تحقيق الهدف',                    '1000',  'CURRENCY',   'ريال',      '2024-01-01'),
('BASE_SALARY',                         'SALARY',       'الراتب الأساسي',                        '1500',  'CURRENCY',   'ريال',      '2024-01-01'),
('CAR_ALLOWANCE_DAILY',                 'ALLOWANCE',    'بدل السيارة اليومي',                    '31',    'CURRENCY',   'ريال',      '2024-01-01'),
('FUEL_ALLOWANCE_DAILY',                'ALLOWANCE',    'بدل البنزين اليومي',                    '35',    'CURRENCY',   'ريال',      '2024-01-01'),
('FREELANCE_EXTRA_ORDER_RATE',          'SALARY',       'عمولة الأوردر الإضافي — فريلانس',       '5',     'CURRENCY',   'ريال',      '2024-01-01'),
('EMPLOYEE_EXTRA_ORDER_RATE',           'SALARY',       'عمولة الأوردر الإضافي — كفالة',         '3',     'CURRENCY',   'ريال',      '2024-01-01');

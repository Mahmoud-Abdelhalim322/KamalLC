-- V46: Seed default appearance/theme settings
--
-- Adds APPEARANCE group settings (THEME_LOGO, THEME_SIDEBAR_BG, THEME_TOPBAR_BG, THEME_BRAND_TEXT)
-- to the default tenant ('00000000-0000-0000-0000-000000000001') and all active tenants.

-- 1. Insert into default tenant if not exists
INSERT INTO app_setting (id, setting_key, setting_group, label_ar, value, value_type, unit_label, effective_from, effective_to, tenant_id, updated_at)
SELECT gen_random_uuid(), 'THEME_LOGO', 'APPEARANCE', 'شعار الشركة (اللوجو)', 'logo-ksclc.png', 'STRING', NULL, '2025-01-01', NULL, '00000000-0000-0000-0000-000000000001', NOW()
WHERE NOT EXISTS (SELECT 1 FROM app_setting WHERE tenant_id = '00000000-0000-0000-0000-000000000001' AND setting_key = 'THEME_LOGO' AND effective_to IS NULL);

INSERT INTO app_setting (id, setting_key, setting_group, label_ar, value, value_type, unit_label, effective_from, effective_to, tenant_id, updated_at)
SELECT gen_random_uuid(), 'THEME_SIDEBAR_BG', 'APPEARANCE', 'لون السايدبار', '#F5B000', 'COLOR', NULL, '2025-01-01', NULL, '00000000-0000-0000-0000-000000000001', NOW()
WHERE NOT EXISTS (SELECT 1 FROM app_setting WHERE tenant_id = '00000000-0000-0000-0000-000000000001' AND setting_key = 'THEME_SIDEBAR_BG' AND effective_to IS NULL);

INSERT INTO app_setting (id, setting_key, setting_group, label_ar, value, value_type, unit_label, effective_from, effective_to, tenant_id, updated_at)
SELECT gen_random_uuid(), 'THEME_TOPBAR_BG', 'APPEARANCE', 'لون التوب بار', '#F5B000', 'COLOR', NULL, '2025-01-01', NULL, '00000000-0000-0000-0000-000000000001', NOW()
WHERE NOT EXISTS (SELECT 1 FROM app_setting WHERE tenant_id = '00000000-0000-0000-0000-000000000001' AND setting_key = 'THEME_TOPBAR_BG' AND effective_to IS NULL);

INSERT INTO app_setting (id, setting_key, setting_group, label_ar, value, value_type, unit_label, effective_from, effective_to, tenant_id, updated_at)
SELECT gen_random_uuid(), 'THEME_BRAND_TEXT', 'APPEARANCE', 'اسم الشركة / النص الظاهر', 'شركة كمال بن خلف السلمي للخدمات اللوجستية', 'STRING', NULL, '2025-01-01', NULL, '00000000-0000-0000-0000-000000000001', NOW()
WHERE NOT EXISTS (SELECT 1 FROM app_setting WHERE tenant_id = '00000000-0000-0000-0000-000000000001' AND setting_key = 'THEME_BRAND_TEXT' AND effective_to IS NULL);

-- 2. Seed default appearance settings for any other tenant that does not have them yet
INSERT INTO app_setting (id, setting_key, setting_group, label_ar, value, value_type, unit_label, effective_from, effective_to, tenant_id, updated_at)
SELECT 
    gen_random_uuid(),
    s.setting_key,
    s.setting_group,
    s.label_ar,
    s.value,
    s.value_type,
    s.unit_label,
    s.effective_from,
    s.effective_to,
    t.id,
    NOW()
FROM tenant t
CROSS JOIN app_setting s
WHERE s.tenant_id = '00000000-0000-0000-0000-000000000001'
  AND s.setting_group = 'APPEARANCE'
  AND s.effective_to IS NULL
  AND t.id <> '00000000-0000-0000-0000-000000000001'
  AND NOT EXISTS (
      SELECT 1 FROM app_setting existing 
      WHERE existing.tenant_id = t.id AND existing.setting_key = s.setting_key AND existing.effective_to IS NULL
  );

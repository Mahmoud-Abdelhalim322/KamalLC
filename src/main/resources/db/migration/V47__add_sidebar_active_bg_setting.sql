-- V47: Add THEME_SIDEBAR_ACTIVE_BG setting for active page highlight color in sidebar

INSERT INTO app_setting (id, setting_key, setting_group, label_ar, value, value_type, unit_label, effective_from, effective_to, tenant_id, updated_at)
SELECT gen_random_uuid(), 'THEME_SIDEBAR_ACTIVE_BG', 'APPEARANCE', 'لون تحديد الصفحة في السايدبار', '#D98E04', 'COLOR', NULL, '2025-01-01', NULL, '00000000-0000-0000-0000-000000000001', NOW()
WHERE NOT EXISTS (SELECT 1 FROM app_setting WHERE tenant_id = '00000000-0000-0000-0000-000000000001' AND setting_key = 'THEME_SIDEBAR_ACTIVE_BG' AND effective_to IS NULL);

-- Seed for existing tenants
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
  AND s.setting_key = 'THEME_SIDEBAR_ACTIVE_BG'
  AND s.effective_to IS NULL
  AND t.id <> '00000000-0000-0000-0000-000000000001'
  AND NOT EXISTS (
      SELECT 1 FROM app_setting existing 
      WHERE existing.tenant_id = t.id AND existing.setting_key = 'THEME_SIDEBAR_ACTIVE_BG' AND existing.effective_to IS NULL
  );

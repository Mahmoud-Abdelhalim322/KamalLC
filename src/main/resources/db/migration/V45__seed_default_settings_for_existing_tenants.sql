-- V45: Seed default settings for any tenant missing them.
--
-- Copies all currently active settings (effective_to IS NULL) from the default tenant
-- ('00000000-0000-0000-0000-000000000001') into any existing non-default tenant that
-- has 0 setting records.

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
  AND s.effective_to IS NULL
  AND t.id <> '00000000-0000-0000-0000-000000000001'
  AND NOT EXISTS (
      SELECT 1 FROM app_setting existing WHERE existing.tenant_id = t.id
  );

-- V32: Seed Qiwa Contract alert setting in DOCUMENT_ALERT group
INSERT INTO app_setting (setting_key, setting_group, label_ar, value, value_type, unit_label, effective_from)
VALUES ('QIWA_CONTRACT_ALERT_DAYS', 'DOCUMENT_ALERT', 'مدة التنبيه قبل انتهاء عقد قوى', '30', 'INTEGER', 'يوم', '2026-01-01');

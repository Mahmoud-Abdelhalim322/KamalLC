-- V39: Extended vehicle fields (registration, VIN, insurance, operating card, inspection expiry)
-- and document alert settings for vehicle expiry.

ALTER TABLE vehicle
    ADD COLUMN registration_number        VARCHAR(100),
    ADD COLUMN vin                        VARCHAR(100),
    ADD COLUMN registration_expiry_date   DATE,
    ADD COLUMN operating_card_number      VARCHAR(100),
    ADD COLUMN insurance_company          VARCHAR(100),
    ADD COLUMN inspection_expiry_date     DATE;

INSERT INTO app_setting (setting_key, setting_group, label_ar, value, value_type, unit_label, effective_from) VALUES
('VEHICLE_REGISTRATION_EXPIRY_ALERT_DAYS', 'DOCUMENT_ALERT', 'التنبيه قبل انتهاء استمارة السيارة', '30', 'INTEGER', 'يوم', '2026-01-01'),
('VEHICLE_INSPECTION_EXPIRY_ALERT_DAYS',   'DOCUMENT_ALERT', 'التنبيه قبل انتهاء فحص السيارة',    '30', 'INTEGER', 'يوم', '2026-01-01');

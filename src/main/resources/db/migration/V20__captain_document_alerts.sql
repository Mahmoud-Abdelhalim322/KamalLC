-- V20: Document expiry alerts.
--
-- HR tracks the validity of each captain document by hand, in a sheet whose every date
-- column is followed by a status column reading ساري / على وشك الانتهاء / منتهى. Two of
-- the documents it tracks had no home here at all: the Qiwa work permit (licence_expiry
-- is the *driving* licence, a different document) and medical insurance.
--
-- The alert window is a per-document setting rather than one constant, because HR does not
-- treat them alike: a work permit is chased 60 days out, everything else 30. Both live in
-- app_setting so the window moves from the settings screen, not from a release.

ALTER TABLE captain
    ADD COLUMN work_permit_expiry DATE,
    ADD COLUMN insurance_number   VARCHAR(50),
    ADD COLUMN insurance_company  VARCHAR(255),
    ADD COLUMN insurance_expiry   DATE;

INSERT INTO app_setting (setting_key, setting_group, label_ar, value, value_type, unit_label, effective_from) VALUES
('IQAMA_ALERT_DAYS',       'DOCUMENT_ALERT', 'مدة التنبيه قبل انتهاء الإقامة',            '30', 'INTEGER', 'يوم', '2026-01-01'),
('WORK_PERMIT_ALERT_DAYS', 'DOCUMENT_ALERT', 'مدة التنبيه قبل انتهاء رخصة العمل (قوى)',  '60', 'INTEGER', 'يوم', '2026-01-01'),
('INSURANCE_ALERT_DAYS',   'DOCUMENT_ALERT', 'مدة التنبيه قبل انتهاء التأمين الطبي',      '30', 'INTEGER', 'يوم', '2026-01-01'),
('PASSPORT_ALERT_DAYS',    'DOCUMENT_ALERT', 'مدة التنبيه قبل انتهاء الجواز',             '30', 'INTEGER', 'يوم', '2026-01-01'),
('LICENCE_ALERT_DAYS',     'DOCUMENT_ALERT', 'مدة التنبيه قبل انتهاء رخصة القيادة',       '30', 'INTEGER', 'يوم', '2026-01-01');

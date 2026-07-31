-- V5: Application settings table (versioned — never overwrite, close old row and insert new)
CREATE TABLE app_setting (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    setting_key     VARCHAR(100) NOT NULL,
    setting_group   VARCHAR(50),              -- SHIFT_INCOME | PENALTY | ALLOWANCE | TARGET | SALARY
    label_ar        VARCHAR(255),
    value           VARCHAR(255) NOT NULL,
    value_type      VARCHAR(30),              -- INTEGER | DECIMAL | CURRENCY | PERCENTAGE | BOOLEAN
    unit_label      VARCHAR(50),
    effective_from  DATE         NOT NULL,
    effective_to    DATE,                     -- NULL = currently active
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_setting_key ON app_setting(setting_key);
CREATE INDEX idx_setting_active ON app_setting(setting_key, effective_to) WHERE effective_to IS NULL;

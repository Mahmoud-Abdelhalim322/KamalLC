-- V22: Asset inventory — SIM cards, phones, and user-defined categories.
-- Uses an EAV (Entity-Attribute-Value) design so custom categories get their own
-- fields without schema changes.

CREATE TABLE asset_category (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(50)  NOT NULL UNIQUE,   -- SIM_CARD | PHONE | user-defined slug
    label_ar        VARCHAR(100) NOT NULL,
    system_defined  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE asset_item (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id  UUID         NOT NULL REFERENCES asset_category(id),
    serial_code  VARCHAR(50)  NOT NULL UNIQUE,      -- auto-generated, e.g. SIM-0001, PHN-0001
    status       VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE',  -- AVAILABLE | ASSIGNED | INACTIVE
    notes        TEXT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_asset_item_category ON asset_item(category_id);
CREATE INDEX idx_asset_item_status   ON asset_item(status);

-- Dynamic fields for each item (EAV pattern)
CREATE TABLE asset_item_field (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    item_id     UUID         NOT NULL REFERENCES asset_item(id) ON DELETE CASCADE,
    field_key   VARCHAR(100) NOT NULL,  -- e.g. sim_type, phone_number, imei, phone_type
    field_value TEXT
);

CREATE INDEX idx_asset_item_field_item ON asset_item_field(item_id);

-- Template fields: what each category should ask for on its add/edit form
CREATE TABLE asset_category_field_template (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id   UUID         NOT NULL REFERENCES asset_category(id) ON DELETE CASCADE,
    field_key     VARCHAR(100) NOT NULL,
    label_ar      VARCHAR(100) NOT NULL,
    field_order   INTEGER      NOT NULL DEFAULT 0,
    required      BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_category_field_template ON asset_category_field_template(category_id);

-- Seed the two system-defined categories
INSERT INTO asset_category (name, label_ar, system_defined) VALUES
('SIM_CARD', 'شريحة', TRUE),
('PHONE',    'هاتف',  TRUE);

-- SIM card field templates
INSERT INTO asset_category_field_template (category_id, field_key, label_ar, field_order, required)
SELECT id, 'sim_type',     'نوع الشريحة', 1, TRUE  FROM asset_category WHERE name = 'SIM_CARD'
UNION ALL
SELECT id, 'phone_number', 'الرقم',        2, TRUE  FROM asset_category WHERE name = 'SIM_CARD';

-- Phone field templates
INSERT INTO asset_category_field_template (category_id, field_key, label_ar, field_order, required)
SELECT id, 'imei',       'IMEI',       1, TRUE  FROM asset_category WHERE name = 'PHONE'
UNION ALL
SELECT id, 'phone_type', 'نوع الجوال', 2, TRUE  FROM asset_category WHERE name = 'PHONE';

-- =============================================
-- V43: Multi-Tenant SaaS Foundation
-- =============================================

-- 1. Tenant (Company) table
CREATE TABLE tenant (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    slug            VARCHAR(100) NOT NULL UNIQUE,
    logo            VARCHAR(500),
    primary_color   VARCHAR(20) DEFAULT '#1976D2',
    secondary_color VARCHAR(20) DEFAULT '#424242',
    contact_email   VARCHAR(255),
    contact_phone   VARCHAR(50),
    address         TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    trial_ends_at   DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 2. Subscription Plan table (no quotas — pay and use everything)
CREATE TABLE subscription_plan (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(100) NOT NULL,
    name_ar         VARCHAR(100) NOT NULL,
    description     TEXT,
    description_ar  TEXT,
    monthly_price   NUMERIC(12,2) NOT NULL,
    yearly_price    NUMERIC(12,2),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    features        JSONB,
    sort_order      INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 3. Tenant Subscription table
CREATE TABLE tenant_subscription (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenant(id),
    plan_id         UUID NOT NULL REFERENCES subscription_plan(id),
    start_date      DATE NOT NULL,
    end_date        DATE NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    paid_amount     NUMERIC(12,2),
    billing_cycle   VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    auto_renew      BOOLEAN NOT NULL DEFAULT TRUE,
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 4. Tenant Invoice table (billing / payment tracking)
CREATE TABLE tenant_invoice (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenant(id),
    subscription_id UUID REFERENCES tenant_subscription(id),
    invoice_number  VARCHAR(50) NOT NULL UNIQUE,
    amount          NUMERIC(12,2) NOT NULL,
    tax_amount      NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_amount    NUMERIC(12,2) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    due_date        DATE NOT NULL,
    paid_at         TIMESTAMPTZ,
    payment_method  VARCHAR(50),
    payment_ref     VARCHAR(255),
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 5. Audit Log table (tracks all platform admin actions)
CREATE TABLE audit_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_username  VARCHAR(100) NOT NULL,
    actor_role      VARCHAR(50) NOT NULL,
    tenant_id       UUID REFERENCES tenant(id),
    action          VARCHAR(100) NOT NULL,
    entity_type     VARCHAR(100),
    entity_id       VARCHAR(255),
    details         JSONB,
    ip_address      VARCHAR(50),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 6. Seed a default tenant for existing data migration
INSERT INTO tenant (id, name, slug, status)
VALUES ('00000000-0000-0000-0000-000000000001', 'الشركة الافتراضية', 'default', 'ACTIVE');

-- 7. Seed a default subscription plan
INSERT INTO subscription_plan (id, name, name_ar, description, description_ar, monthly_price, yearly_price, sort_order)
VALUES (
    '00000000-0000-0000-0000-000000000010',
    'Standard',
    'الخطة الأساسية',
    'Full access to all features with no limits',
    'وصول كامل لجميع المميزات بدون حدود',
    500.00,
    5000.00,
    1
);

-- 8. Create a default subscription for the default tenant
INSERT INTO tenant_subscription (tenant_id, plan_id, start_date, end_date, status, billing_cycle)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000010',
    CURRENT_DATE,
    CURRENT_DATE + INTERVAL '1 year',
    'ACTIVE',
    'YEARLY'
);

-- 9. Add tenant_id to app_user (nullable for PLATFORM_ADMIN)
ALTER TABLE app_user ADD COLUMN tenant_id UUID REFERENCES tenant(id);

-- 10. Assign existing users to the default tenant
UPDATE app_user SET tenant_id = '00000000-0000-0000-0000-000000000001';

-- 11. Rename SUPER_ADMIN role to ADMIN for existing users
UPDATE app_user SET role = 'ADMIN' WHERE role = 'SUPER_ADMIN';

-- 12. Create the Platform Admin account (password: admin@kslsc bcrypt-hashed)
-- BCrypt hash of 'admin@kslsc' = $2a$10$ placeholder — will be set by AdminSeeder at startup
INSERT INTO app_user (id, username, password_hash, role, display_name, active, tenant_id, created_at, updated_at)
VALUES (
    gen_random_uuid(),
    'kslsc',
    '$2a$10$dummyHashWillBeReplacedBySeeder000000000000000000000',
    'PLATFORM_ADMIN',
    'مدير المنصة',
    true,
    NULL,
    now(),
    now()
);

-- 13. Indexes
CREATE INDEX idx_tenant_subscription_tenant ON tenant_subscription(tenant_id);
CREATE INDEX idx_tenant_subscription_status ON tenant_subscription(status);
CREATE INDEX idx_tenant_invoice_tenant ON tenant_invoice(tenant_id);
CREATE INDEX idx_tenant_invoice_status ON tenant_invoice(status);
CREATE INDEX idx_audit_log_tenant ON audit_log(tenant_id);
CREATE INDEX idx_audit_log_actor ON audit_log(actor_username);
CREATE INDEX idx_audit_log_action ON audit_log(action);
CREATE INDEX idx_audit_log_created ON audit_log(created_at);
CREATE INDEX idx_app_user_tenant ON app_user(tenant_id);

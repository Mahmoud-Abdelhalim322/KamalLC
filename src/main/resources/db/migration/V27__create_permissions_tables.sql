-- =============================================================
-- V27: Permissions system — job titles with configurable permissions
-- =============================================================

-- Job titles for managers (dynamic — admin can add more)
CREATE TABLE job_title (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(100) NOT NULL UNIQUE,
    label_ar        VARCHAR(255) NOT NULL,
    system_defined  BOOLEAN NOT NULL DEFAULT FALSE,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- M2M: each job title has a set of permissions
CREATE TABLE job_title_permission (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_title_id    UUID NOT NULL REFERENCES job_title(id) ON DELETE CASCADE,
    permission      VARCHAR(50) NOT NULL,
    UNIQUE(job_title_id, permission)
);

CREATE INDEX idx_jtp_job_title ON job_title_permission(job_title_id);

-- Extend app_user to link with staff (managers) and store permissions
ALTER TABLE app_user ADD COLUMN display_name VARCHAR(255);
ALTER TABLE app_user ADD COLUMN staff_id UUID REFERENCES staff(id);
ALTER TABLE app_user ADD COLUMN job_title_id UUID REFERENCES job_title(id);

CREATE INDEX idx_app_user_staff ON app_user(staff_id);

-- Seed default job titles with their permissions
INSERT INTO job_title (id, name, label_ar, system_defined) VALUES
    (gen_random_uuid(), 'OPERATIONS_MANAGER',  'مدير العمليات',  TRUE),
    (gen_random_uuid(), 'TRAFFIC_MANAGER',     'مدير الحركة',    TRUE),
    (gen_random_uuid(), 'FINANCE_MANAGER',     'مدير المالية',   TRUE),
    (gen_random_uuid(), 'HR',                  'اتش ار',        TRUE);

-- Assign default permissions to each job title
-- Operations Manager
INSERT INTO job_title_permission (id, job_title_id, permission)
SELECT gen_random_uuid(), jt.id, p.perm
FROM job_title jt
CROSS JOIN (VALUES
    ('MEMBERS_VIEW'), ('FLEET_VIEW'), ('FLEET_MANAGE'),
    ('SHIFTS_VIEW'), ('SHIFTS_MANAGE'), ('PAYROLL_VIEW'),
    ('REPORTS_VIEW'), ('INVENTORY_VIEW'), ('INVENTORY_MANAGE'),
    ('CUSTODY_VIEW'), ('IMPORTS_MANAGE')
) AS p(perm)
WHERE jt.name = 'OPERATIONS_MANAGER';

-- Traffic Manager
INSERT INTO job_title_permission (id, job_title_id, permission)
SELECT gen_random_uuid(), jt.id, p.perm
FROM job_title jt
CROSS JOIN (VALUES
    ('MEMBERS_VIEW'), ('FLEET_VIEW'), ('FLEET_MANAGE'),
    ('SHIFTS_VIEW'), ('SHIFTS_MANAGE'),
    ('REPORTS_VIEW'), ('INVENTORY_VIEW'), ('CUSTODY_VIEW'), ('IMPORTS_MANAGE')
) AS p(perm)
WHERE jt.name = 'TRAFFIC_MANAGER';

-- Finance Manager
INSERT INTO job_title_permission (id, job_title_id, permission)
SELECT gen_random_uuid(), jt.id, p.perm
FROM job_title jt
CROSS JOIN (VALUES
    ('MEMBERS_VIEW'), ('FLEET_VIEW'),
    ('SHIFTS_VIEW'), ('PAYROLL_VIEW'), ('PAYROLL_MANAGE'),
    ('REPORTS_VIEW'), ('INVENTORY_VIEW'), ('CUSTODY_VIEW')
) AS p(perm)
WHERE jt.name = 'FINANCE_MANAGER';

-- HR
INSERT INTO job_title_permission (id, job_title_id, permission)
SELECT gen_random_uuid(), jt.id, p.perm
FROM job_title jt
CROSS JOIN (VALUES
    ('MEMBERS_VIEW'), ('MEMBERS_MANAGE'),
    ('SHIFTS_VIEW'), ('PAYROLL_VIEW'),
    ('REPORTS_VIEW'), ('CUSTODY_VIEW'), ('IMPORTS_MANAGE')
) AS p(perm)
WHERE jt.name = 'HR';

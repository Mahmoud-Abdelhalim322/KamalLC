-- =============================================================
-- V26: Staff table for workers (WORKER) and managers (MANAGER)
-- Mirrors the Excel spreadsheet's 42-column layout.
-- =============================================================

CREATE TABLE staff (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Identity
    staff_code              VARCHAR(50)  NOT NULL UNIQUE,
    staff_type              VARCHAR(20)  NOT NULL,           -- WORKER | MANAGER
    job_title               VARCHAR(100),                    -- for MANAGER: مدير العمليات, مدير الحركة, etc.

    full_name_ar            VARCHAR(255) NOT NULL,
    full_name_en            VARCHAR(255),
    gender                  VARCHAR(20),
    nationality             VARCHAR(100),
    date_of_birth           DATE,
    phone                   VARCHAR(30),
    place_of_residence      VARCHAR(255),
    home_address            VARCHAR(500),
    email                   VARCHAR(255),

    -- Passport
    passport_number         VARCHAR(50),
    passport_issue_date     DATE,
    passport_expiry         DATE,
    passport_status         VARCHAR(50),

    -- Iqama / Residence
    iqama_number            VARCHAR(50),
    iqama_occupation        VARCHAR(255),
    iqama_expiry            DATE,
    iqama_expiry_renewed    DATE,          -- تاريخ الانتهاء بعد آخر تجديد
    iqama_status            VARCHAR(50),

    -- Qiwa contract
    qiwa_contract_number    VARCHAR(50),
    establishment_number    VARCHAR(50),
    qiwa_contract_start     DATE,
    qiwa_contract_end       DATE,
    qiwa_contract_status    VARCHAR(50),

    -- Work permit
    work_permit_expiry      DATE,
    work_permit_status      VARCHAR(50),

    -- Company affiliation
    affiliated_company      VARCHAR(255),
    cr_number               VARCHAR(50),

    -- Insurance
    insurance_number        VARCHAR(50),
    insurance_category      VARCHAR(20),
    insurance_issue_date    DATE,
    insurance_expiry        DATE,
    insurance_company       VARCHAR(255),
    insurance_status        VARCHAR(50),

    -- Trial period
    trial_period_days       INT,

    -- Driving licence
    has_licence             BOOLEAN DEFAULT FALSE,
    licence_expiry          DATE,
    licence_status          VARCHAR(50),

    -- Other
    blood_type              VARCHAR(10),
    bank_account_number     VARCHAR(50),
    bank_name               VARCHAR(100),

    -- Salary (for payroll)
    basic_salary            NUMERIC(12,2) DEFAULT 0,
    housing_allowance       NUMERIC(12,2) DEFAULT 0,
    other_allowances        NUMERIC(12,2) DEFAULT 0,

    -- Status
    status                  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    join_date               DATE,
    leave_date              DATE,
    notes                   TEXT,

    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_staff_type   ON staff(staff_type);
CREATE INDEX idx_staff_status ON staff(status);
CREATE INDEX idx_staff_code   ON staff(staff_code);

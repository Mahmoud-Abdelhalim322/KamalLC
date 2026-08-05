-- =============================================================
-- V35: Loan (سُلف) management — track advances and monthly deductions
-- =============================================================

CREATE TABLE loan (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Polymorphic: exactly one of captain_id / staff_id is non-null
    captain_id          UUID REFERENCES captain(id),
    staff_id            UUID REFERENCES staff(id),
    member_type         VARCHAR(20) NOT NULL,  -- CAPTAIN, WORKER, MANAGER
    original_amount     NUMERIC(12,2) NOT NULL,
    remaining           NUMERIC(12,2) NOT NULL,
    monthly_deduction   NUMERIC(12,2) NOT NULL DEFAULT 0,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE, PAID_OFF, CANCELLED
    notes               TEXT,
    created_by          VARCHAR(100),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_loan_member CHECK (
        (captain_id IS NOT NULL AND staff_id IS NULL)
        OR (captain_id IS NULL AND staff_id IS NOT NULL)
    ),
    CONSTRAINT ck_loan_amount CHECK (original_amount > 0),
    CONSTRAINT ck_loan_remaining CHECK (remaining >= 0)
);

CREATE INDEX idx_loan_captain ON loan(captain_id);
CREATE INDEX idx_loan_staff ON loan(staff_id);
CREATE INDEX idx_loan_status ON loan(status);
CREATE INDEX idx_loan_member_type ON loan(member_type);

CREATE TABLE loan_payment (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_id         UUID NOT NULL REFERENCES loan(id) ON DELETE CASCADE,
    amount          NUMERIC(12,2) NOT NULL,
    source          VARCHAR(40) NOT NULL DEFAULT 'MANUAL',  -- PAYROLL_DEDUCTION, MANUAL
    payroll_run_id  UUID REFERENCES payroll_run(id),
    notes           TEXT,
    created_by      VARCHAR(100),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_payment_amount CHECK (amount > 0)
);

CREATE INDEX idx_loan_payment_loan ON loan_payment(loan_id);
CREATE INDEX idx_loan_payment_run ON loan_payment(payroll_run_id);

-- Seed LOANS_VIEW and LOANS_MANAGE permissions into existing job titles
-- Finance Manager gets both, Operations Manager gets view only
INSERT INTO job_title_permission (id, job_title_id, permission)
SELECT gen_random_uuid(), jt.id, p.perm
FROM job_title jt
CROSS JOIN (VALUES ('LOANS_VIEW'), ('LOANS_MANAGE')) AS p(perm)
WHERE jt.name = 'FINANCE_MANAGER'
  AND NOT EXISTS (
    SELECT 1 FROM job_title_permission jtp
    WHERE jtp.job_title_id = jt.id AND jtp.permission = p.perm
  );

INSERT INTO job_title_permission (id, job_title_id, permission)
SELECT gen_random_uuid(), jt.id, 'LOANS_VIEW'
FROM job_title jt
WHERE jt.name IN ('OPERATIONS_MANAGER', 'HR')
  AND NOT EXISTS (
    SELECT 1 FROM job_title_permission jtp
    WHERE jtp.job_title_id = jt.id AND jtp.permission = 'LOANS_VIEW'
  );

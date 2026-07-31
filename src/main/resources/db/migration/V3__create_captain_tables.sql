-- V3: Captain and captain documents tables
CREATE TABLE captain (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kamal_code          VARCHAR(50)  NOT NULL UNIQUE,
    captain_type        VARCHAR(20)  NOT NULL,       -- EMPLOYEE | FREELANCE
    full_name_ar        VARCHAR(255) NOT NULL,
    full_name_en        VARCHAR(255),
    nationality         VARCHAR(100),
    date_of_birth       DATE,
    phone               VARCHAR(30),
    email               VARCHAR(255),
    iqama_number        VARCHAR(50),
    iqama_expiry        DATE,
    passport_number     VARCHAR(50),
    passport_expiry     DATE,
    driving_licence_no  VARCHAR(50),
    licence_expiry      DATE,
    uses_own_vehicle    BOOLEAN      NOT NULL DEFAULT false,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | INACTIVE | TERMINATED
    join_date           DATE,
    leave_date          DATE,
    notes               TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_captain_kamal_code ON captain(kamal_code);
CREATE INDEX idx_captain_status ON captain(status);
CREATE INDEX idx_captain_type ON captain(captain_type);

CREATE TABLE captain_document (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    captain_id      UUID         NOT NULL REFERENCES captain(id) ON DELETE CASCADE,
    document_type   VARCHAR(50)  NOT NULL,  -- PERSONAL_PHOTO | ID_CARD | IQAMA | PASSPORT | DRIVING_LICENCE | VEHICLE_REGISTRATION | CONTRACT | OTHER
    file_name       VARCHAR(500) NOT NULL,
    stored_path     VARCHAR(1000) NOT NULL,
    content_type    VARCHAR(100),
    file_size       BIGINT,
    uploaded_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    note            VARCHAR(500)
);

CREATE INDEX idx_captain_document_captain ON captain_document(captain_id);

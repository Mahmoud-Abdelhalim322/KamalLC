-- =============================================
-- Captain mobile app: OTP sign-in, push devices and document renewal requests
-- =============================================

-- One-time sign-in codes. Not tenant-filtered: the code is issued before the
-- captain (and therefore the tenant) is known to the request.
CREATE TABLE captain_otp (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    captain_id   UUID         NOT NULL REFERENCES captain(id) ON DELETE CASCADE,
    phone_key    VARCHAR(20)  NOT NULL,
    code_hash    VARCHAR(100) NOT NULL,
    expires_at   TIMESTAMPTZ  NOT NULL,
    attempts     INT          NOT NULL DEFAULT 0,
    consumed_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_captain_otp_phone ON captain_otp(phone_key, created_at DESC);

-- Push-notification registrations, one row per installed app.
CREATE TABLE captain_device (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    captain_id   UUID         NOT NULL REFERENCES captain(id) ON DELETE CASCADE,
    push_token   VARCHAR(500) NOT NULL UNIQUE,
    platform     VARCHAR(20)  NOT NULL,
    locale       VARCHAR(10),
    app_version  VARCHAR(30),
    tenant_id    UUID         NOT NULL REFERENCES tenant(id),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_captain_device_captain ON captain_device(captain_id);

-- A renewed document sent from the app, held for a supervisor to approve
-- before it overwrites the captain's recorded number and expiry.
CREATE TABLE captain_document_request (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    captain_id         UUID         NOT NULL REFERENCES captain(id) ON DELETE CASCADE,
    document_kind      VARCHAR(30)  NOT NULL,   -- DocumentKind name
    document_number    VARCHAR(100),
    new_expiry         DATE         NOT NULL,
    front_document_id  UUID REFERENCES captain_document(id) ON DELETE SET NULL,
    back_document_id   UUID REFERENCES captain_document(id) ON DELETE SET NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING / APPROVED / REJECTED
    review_note        TEXT,
    reviewed_by        VARCHAR(100),
    reviewed_at        TIMESTAMPTZ,
    tenant_id          UUID         NOT NULL REFERENCES tenant(id),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_captain_doc_request_captain ON captain_document_request(captain_id, created_at DESC);
CREATE INDEX idx_captain_doc_request_status ON captain_document_request(tenant_id, status);

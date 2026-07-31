-- V7: Import batch and row issue tables
CREATE TABLE import_batch (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_name       VARCHAR(500),
    file_hash       VARCHAR(128),              -- SHA-256 to detect re-upload
    uploaded_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    row_count       INTEGER      NOT NULL DEFAULT 0,
    imported_count  INTEGER      NOT NULL DEFAULT 0,
    skipped_count   INTEGER      NOT NULL DEFAULT 0,
    warning_count   INTEGER      NOT NULL DEFAULT 0,
    error_count     INTEGER      NOT NULL DEFAULT 0,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PREVIEW',  -- PREVIEW | COMMITTED | CANCELLED | FAILED
    period_from     DATE,
    period_to       DATE
);

CREATE INDEX idx_import_batch_status ON import_batch(status);

CREATE TABLE import_row_issue (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    import_batch_id UUID         NOT NULL REFERENCES import_batch(id) ON DELETE CASCADE,
    row_number      INTEGER,
    severity        VARCHAR(20)  NOT NULL,     -- ERROR | WARNING | INFO
    issue_code      VARCHAR(50)  NOT NULL,
    message_ar      VARCHAR(500),
    raw_row         JSONB
);

CREATE INDEX idx_issue_batch ON import_row_issue(import_batch_id);

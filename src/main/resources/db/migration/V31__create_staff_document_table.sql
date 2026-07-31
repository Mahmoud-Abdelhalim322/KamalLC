-- V31: Create staff document table
CREATE TABLE staff_document (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    staff_id        UUID         NOT NULL REFERENCES staff(id) ON DELETE CASCADE,
    document_type   VARCHAR(50)  NOT NULL,  -- PERSONAL_PHOTO | ID_CARD | IQAMA | PASSPORT | DRIVING_LICENCE | CONTRACT | WORK_PERMIT | INSURANCE | OTHER
    file_name       VARCHAR(500) NOT NULL,
    stored_path     VARCHAR(1000) NOT NULL,
    content_type    VARCHAR(100),
    file_size       BIGINT,
    uploaded_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    note            VARCHAR(500)
);

CREATE INDEX idx_staff_document_staff ON staff_document(staff_id);

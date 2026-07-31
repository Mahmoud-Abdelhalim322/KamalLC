-- V4: Ninja ID slot and assignment tables (the registry)
-- A Ninja ID is a "slot" that can be held by different captains over time.

CREATE TABLE ninja_id_slot (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ninja_id    VARCHAR(50) NOT NULL UNIQUE,
    is_active   BOOLEAN     NOT NULL DEFAULT true,
    notes       TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ninja_id_slot_ninja_id ON ninja_id_slot(ninja_id);

CREATE TABLE ninja_id_assignment (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ninja_id_slot_id UUID        NOT NULL REFERENCES ninja_id_slot(id) ON DELETE CASCADE,
    captain_id       UUID        NOT NULL REFERENCES captain(id) ON DELETE CASCADE,
    valid_from       DATE        NOT NULL,
    valid_to         DATE,                    -- NULL = currently active
    ninja_side_name  VARCHAR(255),            -- Ninja's name for this captain
    reason           TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_assignment_slot ON ninja_id_assignment(ninja_id_slot_id);
CREATE INDEX idx_assignment_captain ON ninja_id_assignment(captain_id);
CREATE INDEX idx_assignment_dates ON ninja_id_assignment(valid_from, valid_to);

-- Exclusion constraint: no two assignments for the same slot may overlap in time.
-- We use daterange to enforce this. valid_to NULL means open-ended (use 'infinity').
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE ninja_id_assignment
    ADD CONSTRAINT no_overlapping_assignments
    EXCLUDE USING gist (
        ninja_id_slot_id WITH =,
        daterange(valid_from, COALESCE(valid_to, '9999-12-31'::date), '[]') WITH &&
    );

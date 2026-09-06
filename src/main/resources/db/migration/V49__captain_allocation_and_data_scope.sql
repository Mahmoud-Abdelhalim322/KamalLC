-- Add data_scope column to app_user table (default 'ALL_CAPTAINS')
ALTER TABLE app_user ADD COLUMN data_scope VARCHAR(30) DEFAULT 'ALL_CAPTAINS';

-- Create table for assigning specific captains to users (supervisors/managers)
CREATE TABLE user_captain_assignment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    captain_id UUID NOT NULL REFERENCES captain(id) ON DELETE CASCADE,
    assigned_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    assigned_by UUID REFERENCES app_user(id),
    CONSTRAINT uk_user_captain UNIQUE (user_id, captain_id)
);

CREATE INDEX idx_user_captain_user ON user_captain_assignment(user_id);
CREATE INDEX idx_user_captain_captain ON user_captain_assignment(captain_id);
CREATE INDEX idx_user_captain_tenant ON user_captain_assignment(tenant_id);

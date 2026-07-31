-- V2: Seed the super admin account
-- Password: Admin@12345 (bcrypt hash)
INSERT INTO app_user (id, username, password_hash, role, active)
VALUES (
    gen_random_uuid(),
    'admin',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'SUPER_ADMIN',
    true
);

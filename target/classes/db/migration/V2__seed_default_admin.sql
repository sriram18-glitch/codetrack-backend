-- Seeds a default admin account so there's something to log in with
-- on a fresh install. CHANGE THIS PASSWORD after first login in any
-- real deployment — this hash and password are published in this repo.
--
-- Login email:    admin@codetrack.local
-- Login password: Admin@12345

INSERT INTO admins (email, password_hash, full_name, college_name, enabled)
VALUES (
    'admin@codetrack.local',
    '$2b$12$PjbaLmhGcFGVSKjXIwJnzeFh29KFiovpdG27RjG7S/wDRNZSWe2Qm',
    'Default Admin',
    'CodeTrack Demo College',
    TRUE
);

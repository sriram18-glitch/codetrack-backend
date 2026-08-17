-- Add GitHub / LinkedIn profile URLs. Columns stay nullable so existing students
-- are not affected; new registrations enforce the values at the application layer.
ALTER TABLE students ADD COLUMN github_profile_url VARCHAR(255);
ALTER TABLE students ADD COLUMN linkedin_profile_url VARCHAR(255);

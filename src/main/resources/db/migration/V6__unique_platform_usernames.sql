-- =====================================================================
-- CodeTrack — V6: enforce that a platform username belongs to at most
-- one student, and normalize existing branch/section values to upper
-- case so they match what new registrations/admin edits store.
--
-- Functional unique indexes (LOWER(...)) make the uniqueness check
-- case-insensitive while still allowing multiple NULLs (students who
-- have not provided a username for a given platform).
-- =====================================================================

UPDATE students SET branch = UPPER(branch) WHERE branch IS NOT NULL AND branch <> UPPER(branch);
UPDATE students SET section = UPPER(section) WHERE section IS NOT NULL AND section <> UPPER(section);

CREATE UNIQUE INDEX IF NOT EXISTS uq_coding_profiles_leetcode_username
    ON coding_profiles (LOWER(leetcode_username));

CREATE UNIQUE INDEX IF NOT EXISTS uq_coding_profiles_codeforces_username
    ON coding_profiles (LOWER(codeforces_username));

CREATE UNIQUE INDEX IF NOT EXISTS uq_coding_profiles_codechef_username
    ON coding_profiles (LOWER(codechef_username));

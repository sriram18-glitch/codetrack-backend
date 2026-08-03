-- =====================================================================
-- CodeTrack — V3: add consistency score column for the overall
-- score engine (LeetCode 40% / Codeforces 25% / CodeChef 15% /
-- Consistency 20%). Kept in sync with the Performance JPA entity.
-- =====================================================================

ALTER TABLE performance ADD COLUMN consistency_score NUMERIC(4,2);

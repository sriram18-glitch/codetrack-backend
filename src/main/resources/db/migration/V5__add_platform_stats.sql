-- =====================================================================
-- CodeTrack — V5: add per-platform performance stats for Codeforces and
-- CodeChef so platform cards can show problems solved, max rating, rank,
-- contest count, stars and global rank. Kept in sync with the Performance
-- JPA entity. Metrics a platform does not expose are simply left null and
-- rendered as "Unavailable" by the UI.
-- =====================================================================

ALTER TABLE performance ADD COLUMN codeforces_solved INTEGER;
ALTER TABLE performance ADD COLUMN codeforces_max_rating INTEGER;
ALTER TABLE performance ADD COLUMN codeforces_rank VARCHAR(30);
ALTER TABLE performance ADD COLUMN codeforces_contest_count INTEGER;
ALTER TABLE performance ADD COLUMN codechef_solved INTEGER;
ALTER TABLE performance ADD COLUMN codechef_stars VARCHAR(10);
ALTER TABLE performance ADD COLUMN codechef_global_rank INTEGER;
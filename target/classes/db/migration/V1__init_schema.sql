-- =====================================================================
-- CodeTrack — Initial schema
-- Column types/lengths are deliberately kept in exact sync with the
-- JPA entity @Column annotations to avoid Hibernate ddl-auto=validate
-- mismatches at startup.
-- =====================================================================
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ---------------------------------------------------------------------
-- ADMINS (only authenticated role in Phase 1)
-- ---------------------------------------------------------------------
CREATE TABLE admins (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(150) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(150),
    college_name    VARCHAR(200),
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_admins_email ON admins (LOWER(email));

-- ---------------------------------------------------------------------
-- STUDENTS
-- ---------------------------------------------------------------------
CREATE TABLE students (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    roll_number   VARCHAR(30) NOT NULL UNIQUE,
    name          VARCHAR(150) NOT NULL,
    email         VARCHAR(150) NOT NULL UNIQUE,
    branch        VARCHAR(100),
    year          INTEGER,
    section       VARCHAR(10),
    phone         VARCHAR(20),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_students_roll_number ON students (roll_number);
CREATE INDEX idx_students_name ON students (LOWER(name));
CREATE INDEX idx_students_email ON students (LOWER(email));

-- ---------------------------------------------------------------------
-- CODING PROFILES  (one row per student — platform usernames)
-- ---------------------------------------------------------------------
CREATE TABLE coding_profiles (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id            UUID NOT NULL UNIQUE REFERENCES students(id) ON DELETE CASCADE,
    leetcode_username     VARCHAR(100),
    codeforces_username   VARCHAR(100),
    codechef_username     VARCHAR(100),
    atcoder_username      VARCHAR(100),
    gfg_username          VARCHAR(100),
    hackerrank_username   VARCHAR(100)
);

-- ---------------------------------------------------------------------
-- PERFORMANCE  (current snapshot, one row per student)
-- ---------------------------------------------------------------------
CREATE TABLE performance (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id         UUID NOT NULL UNIQUE REFERENCES students(id) ON DELETE CASCADE,
    overall_score      NUMERIC(4,2),
    leetcode_rating    INTEGER,
    leetcode_solved    INTEGER,
    leetcode_easy      INTEGER,
    leetcode_medium    INTEGER,
    leetcode_hard      INTEGER,
    codeforces_rating  INTEGER,
    codechef_rating    INTEGER,
    last_updated       TIMESTAMPTZ
);

-- ---------------------------------------------------------------------
-- PERFORMANCE HISTORY  (time series snapshots for trend graphs)
-- ---------------------------------------------------------------------
CREATE TABLE performance_history (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id       UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    platform         VARCHAR(30) NOT NULL,
    rating           INTEGER,
    problems_solved  INTEGER,
    captured_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_performance_history_student ON performance_history (student_id, captured_at DESC);

COMMIT;

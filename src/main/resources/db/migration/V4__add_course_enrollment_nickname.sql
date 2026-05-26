ALTER TABLE course_enrollments
    ADD COLUMN IF NOT EXISTS nickname VARCHAR(100);

UPDATE course_enrollments
SET nickname = 'User ' || user_id
WHERE nickname IS NULL OR btrim(nickname) = '';

ALTER TABLE course_enrollments
    ALTER COLUMN nickname SET DEFAULT 'User';

ALTER TABLE course_enrollments
    ALTER COLUMN nickname SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_course_enrollments_course_leaderboard
    ON course_enrollments (course_id, progress_percent DESC, completed_tasks_count DESC, total_score DESC, updated_at ASC, user_id ASC);

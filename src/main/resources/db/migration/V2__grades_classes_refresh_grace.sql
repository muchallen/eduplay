-- App team requests (v1.1): school grades, teacher classes, refresh-token retry grace period.

-- 1. Grades. The same topic ids exist in every grade, so the grade is part of each record's identity.
--    Rows written before grades existed belonged to ECD A, the only grade at the time.
--    ADD COLUMN ... DEFAULT does not fire the append-only UPDATE trigger on game_results.
ALTER TABLE children ADD COLUMN grade_id VARCHAR(32) NOT NULL DEFAULT 'ecd_a';
ALTER TABLE children ALTER COLUMN grade_id DROP DEFAULT;

ALTER TABLE game_results ADD COLUMN grade_id VARCHAR(32) NOT NULL DEFAULT 'ecd_a';
ALTER TABLE game_results ALTER COLUMN grade_id DROP DEFAULT;
CREATE INDEX ix_game_results_child_grade_played_at ON game_results (child_id, grade_id, played_at);

ALTER TABLE lesson_progress ADD COLUMN grade_id VARCHAR(32) NOT NULL DEFAULT 'ecd_a';
ALTER TABLE lesson_progress ALTER COLUMN grade_id DROP DEFAULT;
ALTER TABLE lesson_progress DROP CONSTRAINT ux_lesson_progress_lesson;
ALTER TABLE lesson_progress
    ADD CONSTRAINT ux_lesson_progress_lesson UNIQUE (child_id, grade_id, section_id, topic_id, lesson_id);

-- 2. Teacher classes. A child is owned by the account that created it (children.parent_id: a
--    parent, or a teacher for pupils without a parent account) and can be linked to any number of
--    classes. Teachers of a linked class may read, report on and sync the child; only the owner
--    may edit or delete it.
CREATE TABLE classrooms (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    teacher_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name       VARCHAR(80) NOT NULL,
    grade_id   VARCHAR(32),
    join_code  VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_classrooms_join_code UNIQUE (join_code)
);
CREATE INDEX ix_classrooms_teacher_id ON classrooms (teacher_id);

CREATE TABLE classroom_children (
    classroom_id UUID        NOT NULL REFERENCES classrooms (id) ON DELETE CASCADE,
    child_id     UUID        NOT NULL REFERENCES children (id) ON DELETE CASCADE,
    added_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (classroom_id, child_id)
);
CREATE INDEX ix_classroom_children_child_id ON classroom_children (child_id);

-- 3. Refresh-token rotation history, so a retried refresh within the grace period can be answered
--    with the same replacement token instead of being treated as theft.
ALTER TABLE refresh_tokens ADD COLUMN rotated_at TIMESTAMPTZ;
ALTER TABLE refresh_tokens ADD COLUMN replaced_by_id UUID REFERENCES refresh_tokens (id) ON DELETE SET NULL;

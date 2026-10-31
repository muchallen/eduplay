package com.eduplay.backend.progress.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Read model for a child's position in one lesson. Writes go through
 * {@link com.eduplay.backend.progress.repository.SyncRepository}, which merges atomically in SQL.
 */
@Entity
@Table(name = "lesson_progress")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LessonProgress {

    @Id
    private UUID id;

    @Column(name = "child_id", nullable = false)
    private UUID childId;

    @Column(name = "grade_id", nullable = false, length = 32)
    private String gradeId;

    @Column(name = "section_id", nullable = false, length = 64)
    private String sectionId;

    @Column(name = "topic_id", nullable = false, length = 64)
    private String topicId;

    @Column(name = "lesson_id", nullable = false, length = 64)
    private String lessonId;

    @Column(name = "current_game_index", nullable = false)
    private int currentGameIndex;

    @Column(nullable = false)
    private boolean completed;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

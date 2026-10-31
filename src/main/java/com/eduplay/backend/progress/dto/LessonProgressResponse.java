package com.eduplay.backend.progress.dto;

import com.eduplay.backend.progress.domain.LessonProgress;
import java.time.Instant;

public record LessonProgressResponse(
        String sectionId,
        String topicId,
        String lessonId,
        int currentGameIndex,
        boolean completed,
        Instant completedAt,
        Instant updatedAt) {

    public static LessonProgressResponse from(LessonProgress progress) {
        return new LessonProgressResponse(progress.getSectionId(), progress.getTopicId(), progress.getLessonId(),
                progress.getCurrentGameIndex(), progress.isCompleted(), progress.getCompletedAt(),
                progress.getUpdatedAt());
    }
}

package com.eduplay.backend.progress.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProgressResponse(UUID childId, Instant serverTime, List<LessonProgressResponse> lessonProgress) {
}

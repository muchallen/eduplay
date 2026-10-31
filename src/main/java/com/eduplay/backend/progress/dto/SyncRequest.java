package com.eduplay.backend.progress.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SyncRequest(
        @Valid @Size(max = 500, message = "at most 500 lesson progress entries per request")
        List<@Valid LessonProgressItem> lessonProgress,
        @Valid @Size(max = 500, message = "at most 500 game results per request")
        List<@Valid GameResultItem> gameResults) {

    public List<LessonProgressItem> lessonProgressOrEmpty() {
        return lessonProgress == null ? List.of() : lessonProgress;
    }

    public List<GameResultItem> gameResultsOrEmpty() {
        return gameResults == null ? List.of() : gameResults;
    }
}

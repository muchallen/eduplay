package com.eduplay.backend.user.dto;

import com.eduplay.backend.child.dto.ChildResponse;
import com.eduplay.backend.classroom.dto.ClassroomResponse;
import com.eduplay.backend.progress.dto.GameResultResponse;
import com.eduplay.backend.progress.dto.LessonProgressResponse;
import com.eduplay.backend.subscription.dto.SubscriptionResponse;
import java.time.Instant;
import java.util.List;

/** Everything the service stores about an account, its children and its classes (data portability request). */
public record DataExportResponse(
        Instant exportedAt,
        UserResponse account,
        List<ConsentResponse> consents,
        SubscriptionResponse subscription,
        List<ChildExport> children,
        List<ClassroomResponse> classes) {

    public record ChildExport(
            ChildResponse profile,
            List<LessonProgressResponse> lessonProgress,
            List<GameResultResponse> gameResults) {
    }
}

package com.eduplay.backend.progress.service;

import com.eduplay.backend.child.service.ChildAccessService;
import com.eduplay.backend.common.response.PageResponse;
import com.eduplay.backend.grade.GradeCatalog;
import com.eduplay.backend.progress.dto.GameResultResponse;
import com.eduplay.backend.progress.dto.LessonProgressResponse;
import com.eduplay.backend.progress.dto.ProgressResponse;
import com.eduplay.backend.progress.repository.GameResultRepository;
import com.eduplay.backend.progress.repository.LessonProgressRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProgressService {

    private final ChildAccessService childAccessService;
    private final GradeCatalog gradeCatalog;
    private final LessonProgressRepository lessonProgressRepository;
    private final GameResultRepository gameResultRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ProgressResponse getProgress(UUID userId, UUID childId) {
        childAccessService.requireAccessibleChild(userId, childId);
        return new ProgressResponse(childId, clock.instant(), lessonProgress(childId));
    }

    @Transactional(readOnly = true)
    public PageResponse<GameResultResponse> getGameResults(UUID userId, UUID childId, String gradeId, int page,
                                                           int size) {
        childAccessService.requireAccessibleChild(userId, childId);
        gradeCatalog.requireIfPresent(gradeId);
        PageRequest pageable = PageRequest.of(page, size, Sort.by("playedAt").descending().and(Sort.by("id")));
        return PageResponse.of(gradeId == null
                ? gameResultRepository.findByChildId(childId, pageable)
                : gameResultRepository.findByChildIdAndGradeId(childId, gradeId, pageable), GameResultResponse::from);
    }

    /** Lesson progress for a child the caller has already been granted access to. */
    List<LessonProgressResponse> lessonProgress(UUID childId) {
        return lessonProgressRepository.findByChildIdOrderByGradeIdAscSectionIdAscTopicIdAscLessonIdAsc(childId).stream()
                .map(LessonProgressResponse::from)
                .toList();
    }
}

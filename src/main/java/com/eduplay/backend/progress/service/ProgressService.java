package com.eduplay.backend.progress.service;

import com.eduplay.backend.child.service.ChildAccessService;
import com.eduplay.backend.common.response.PageResponse;
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
    private final LessonProgressRepository lessonProgressRepository;
    private final GameResultRepository gameResultRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ProgressResponse getProgress(UUID parentId, UUID childId) {
        childAccessService.requireOwnedChild(parentId, childId);
        return new ProgressResponse(childId, clock.instant(), lessonProgress(childId));
    }

    @Transactional(readOnly = true)
    public PageResponse<GameResultResponse> getGameResults(UUID parentId, UUID childId, int page, int size) {
        childAccessService.requireOwnedChild(parentId, childId);
        PageRequest pageable = PageRequest.of(page, size, Sort.by("playedAt").descending().and(Sort.by("id")));
        return PageResponse.of(gameResultRepository.findByChildId(childId, pageable), GameResultResponse::from);
    }

    /** Lesson progress for a child whose ownership the caller has already verified. */
    List<LessonProgressResponse> lessonProgress(UUID childId) {
        return lessonProgressRepository.findByChildIdOrderBySectionIdAscTopicIdAscLessonIdAsc(childId).stream()
                .map(LessonProgressResponse::from)
                .toList();
    }
}

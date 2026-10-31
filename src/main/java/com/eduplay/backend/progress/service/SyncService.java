package com.eduplay.backend.progress.service;

import com.eduplay.backend.child.service.ChildAccessService;
import com.eduplay.backend.progress.dto.GameResultItem;
import com.eduplay.backend.progress.dto.LessonProgressItem;
import com.eduplay.backend.progress.dto.SyncRequest;
import com.eduplay.backend.progress.dto.SyncResponse;
import com.eduplay.backend.progress.repository.SyncRepository;
import com.eduplay.backend.progress.repository.SyncRepository.GameResultRow;
import com.eduplay.backend.progress.repository.SyncRepository.LessonProgressRow;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies a batch of progress recorded offline on the device.
 *
 * <p>The whole batch is one transaction and every step is idempotent: game results are keyed by
 * their client-generated UUID and duplicates are skipped, and lesson progress is merged with
 * monotonic rules. A client that loses the response (or whose access token expired mid-flight)
 * can therefore refresh its token and resend the identical batch safely.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncService {

    private final ChildAccessService childAccessService;
    private final SyncRepository syncRepository;
    private final ProgressService progressService;
    private final Clock clock;

    @Transactional
    public SyncResponse sync(UUID parentId, UUID childId, SyncRequest request) {
        childAccessService.requireOwnedChild(parentId, childId);
        Instant now = clock.instant();

        List<LessonProgressRow> progressRows = request.lessonProgressOrEmpty().stream()
                .map(item -> toRow(childId, item, now))
                .toList();
        syncRepository.upsertLessonProgress(progressRows);

        List<GameResultRow> resultRows = request.gameResultsOrEmpty().stream()
                .map(item -> toRow(childId, item, now))
                .toList();
        int accepted = syncRepository.insertGameResults(resultRows, now);
        int duplicates = resultRows.size() - accepted;

        log.info("Sync for child {}: {} lesson updates, {} results accepted, {} duplicates",
                childId, progressRows.size(), accepted, duplicates);
        return new SyncResponse(childId, now, accepted, duplicates, progressService.lessonProgress(childId));
    }

    private static LessonProgressRow toRow(UUID childId, LessonProgressItem item, Instant now) {
        Instant updatedAt = notInFuture(item.updatedAt() != null ? item.updatedAt() : now, now);
        boolean completed = item.completed();
        return new LessonProgressRow(childId, item.sectionId(), item.topicId(), item.lessonId(),
                item.currentGameIndex(), completed, completed ? updatedAt : null, updatedAt);
    }

    private static GameResultRow toRow(UUID childId, GameResultItem item, Instant now) {
        return new GameResultRow(item.id(), childId, item.sectionId(), item.topicId(), item.lessonId(),
                item.gameIndex(), item.gameType(), item.score(), item.attempts(), item.mistakes(),
                item.durationSeconds(), item.completed(), notInFuture(item.playedAt(), now));
    }

    /** Device clocks drift; a timestamp from the future is clamped to the server time. */
    private static Instant notInFuture(Instant timestamp, Instant now) {
        return timestamp.isAfter(now) ? now : timestamp;
    }
}

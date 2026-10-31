package com.eduplay.backend.report.service;

import com.eduplay.backend.child.domain.Child;
import com.eduplay.backend.child.service.ChildAccessService;
import com.eduplay.backend.config.EduplayProperties;
import com.eduplay.backend.report.dto.ChildReportResponse;
import com.eduplay.backend.report.dto.ChildReportResponse.TopicSummary;
import com.eduplay.backend.report.dto.TopicPerformance;
import com.eduplay.backend.report.dto.TopicPerformance.Rating;
import com.eduplay.backend.report.repository.ReportRepository;
import com.eduplay.backend.report.repository.ReportRepository.GameTotals;
import com.eduplay.backend.report.repository.ReportRepository.TopicGameStats;
import com.eduplay.backend.report.repository.ReportRepository.TopicLessonStats;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ChildAccessService childAccessService;
    private final ReportRepository reportRepository;
    private final EduplayProperties properties;
    private final Clock clock;

    /**
     * Builds the dashboard for a child owned by the authenticated parent.
     *
     * @param days optional reporting window; null means all time
     */
    @Transactional(readOnly = true)
    public ChildReportResponse report(UUID parentId, UUID childId, Integer days) {
        Child child = childAccessService.requireOwnedChild(parentId, childId);
        Instant now = clock.instant();
        Instant since = days == null ? null : now.minus(Duration.ofDays(days));
        EduplayProperties.Report config = properties.report();

        GameTotals totals = reportRepository.gameTotals(childId, since);
        long lessonsCompleted = reportRepository.lessonsCompleted(childId, since);
        List<TopicPerformance> topics = topics(childId, since, config);

        List<TopicSummary> strong = topics.stream()
                .filter(t -> t.rating() == Rating.STRONG)
                .sorted(Comparator.comparing(TopicPerformance::averageScore).reversed())
                .map(ReportService::summary)
                .toList();
        List<TopicSummary> weak = topics.stream()
                .filter(t -> t.rating() == Rating.NEEDS_IMPROVEMENT)
                .sorted(Comparator.comparing(TopicPerformance::averageScore))
                .map(ReportService::summary)
                .toList();

        ChildReportResponse.RecentActivity recentActivity = new ChildReportResponse.RecentActivity(
                reportRepository.gameTotals(childId, null).lastPlayedAt(),
                reportRepository.activeDays(childId, now.minus(Duration.ofDays(7))),
                reportRepository.activeDays(childId, now.minus(Duration.ofDays(30))),
                reportRepository.recentGames(childId, config.recentActivityLimit()));

        return new ChildReportResponse(
                child.getId(),
                child.getDisplayName(),
                now,
                since,
                new ChildReportResponse.Totals(lessonsCompleted, totals.gamesPlayed(), totals.gamesCompleted(),
                        totals.totalSeconds(), round(totals.averageScore())),
                topics,
                strong,
                weak,
                recentActivity);
    }

    private List<TopicPerformance> topics(UUID childId, Instant since, EduplayProperties.Report config) {
        Map<String, TopicLessonStats> lessonStats = new TreeMap<>();
        for (TopicLessonStats stats : reportRepository.topicLessonStats(childId)) {
            lessonStats.put(key(stats.sectionId(), stats.topicId()), stats);
        }
        Map<String, TopicPerformance> topics = new TreeMap<>();
        for (TopicGameStats game : reportRepository.topicGameStats(childId, since)) {
            String key = key(game.sectionId(), game.topicId());
            TopicLessonStats lessons = lessonStats.remove(key);
            topics.put(key, new TopicPerformance(game.sectionId(), game.topicId(), game.gamesPlayed(),
                    round(game.averageScore()), round(game.averageMistakes()), game.totalSeconds(),
                    lessons == null ? 0 : lessons.lessonsCompleted(),
                    lessons == null ? 0 : lessons.lessonsStarted(),
                    game.lastPlayedAt(),
                    rate(game.gamesPlayed(), game.averageScore(), config)));
        }
        // Topics with lesson progress but no games in the window.
        for (TopicLessonStats lessons : lessonStats.values()) {
            topics.put(key(lessons.sectionId(), lessons.topicId()), new TopicPerformance(lessons.sectionId(),
                    lessons.topicId(), 0, null, null, 0, lessons.lessonsCompleted(), lessons.lessonsStarted(),
                    null, Rating.NOT_ENOUGH_DATA));
        }
        return List.copyOf(topics.values());
    }

    static Rating rate(long gamesPlayed, Double averageScore, EduplayProperties.Report config) {
        if (averageScore == null || gamesPlayed < config.minGamesForRating()) {
            return Rating.NOT_ENOUGH_DATA;
        }
        if (averageScore >= config.strongScoreThreshold()) {
            return Rating.STRONG;
        }
        if (averageScore < config.weakScoreThreshold()) {
            return Rating.NEEDS_IMPROVEMENT;
        }
        return Rating.DEVELOPING;
    }

    private static TopicSummary summary(TopicPerformance topic) {
        return new TopicSummary(topic.sectionId(), topic.topicId(), topic.averageScore(), topic.gamesPlayed());
    }

    private static String key(String sectionId, String topicId) {
        return sectionId + '/' + topicId;
    }

    private static Double round(Double value) {
        return value == null ? null : Math.round(value * 100.0) / 100.0;
    }
}

package com.eduplay.backend.progress.repository;

import com.eduplay.backend.progress.domain.LessonProgress;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LessonProgressRepository extends JpaRepository<LessonProgress, UUID> {

    List<LessonProgress> findByChildIdOrderBySectionIdAscTopicIdAscLessonIdAsc(UUID childId);
}

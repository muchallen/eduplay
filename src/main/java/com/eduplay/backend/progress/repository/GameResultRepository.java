package com.eduplay.backend.progress.repository;

import com.eduplay.backend.progress.domain.GameResult;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameResultRepository extends JpaRepository<GameResult, UUID> {

    Page<GameResult> findByChildId(UUID childId, Pageable pageable);

    Page<GameResult> findByChildIdAndGradeId(UUID childId, String gradeId, Pageable pageable);

    List<GameResult> findByChildIdOrderByPlayedAtAsc(UUID childId);

    long countByChildId(UUID childId);
}

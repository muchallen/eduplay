package com.eduplay.backend.user.repository;

import com.eduplay.backend.user.domain.ConsentRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, UUID> {

    List<ConsentRecord> findByUserIdOrderByAcceptedAtDesc(UUID userId);

    Optional<ConsentRecord> findFirstByUserIdOrderByAcceptedAtDesc(UUID userId);
}

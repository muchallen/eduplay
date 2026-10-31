package com.eduplay.backend.subscription.repository;

import com.eduplay.backend.subscription.domain.Subscription;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByUserId(UUID userId);

    @Modifying
    @Query("""
            update Subscription s set s.status = com.eduplay.backend.subscription.domain.SubscriptionStatus.EXPIRED,
                s.updatedAt = :now
            where s.status in (com.eduplay.backend.subscription.domain.SubscriptionStatus.TRIAL,
                               com.eduplay.backend.subscription.domain.SubscriptionStatus.ACTIVE)
              and s.endsAt is not null and s.endsAt <= :now
            """)
    int expireLapsed(@Param("now") Instant now);
}

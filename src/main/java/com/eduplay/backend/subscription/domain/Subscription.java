package com.eduplay.backend.subscription.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Current subscription of a parent account (one row per user). */
@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Subscription {

    public static final String TRIAL_PLAN = "TRIAL";
    public static final String INTERNAL_PROVIDER = "INTERNAL";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 32)
    private String plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SubscriptionStatus status;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(name = "provider_reference", length = 128)
    private String providerReference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Subscription(UUID userId, String plan, SubscriptionStatus status, Instant startsAt, Instant endsAt,
                        String provider, Instant now) {
        this.userId = userId;
        this.plan = plan;
        this.status = status;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.provider = provider;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public boolean hasLapsed(Instant now) {
        return status.grantsAccess() && endsAt != null && !endsAt.isAfter(now);
    }
}

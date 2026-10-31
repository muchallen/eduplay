package com.eduplay.backend.subscription.dto;

import com.eduplay.backend.subscription.domain.Subscription;
import com.eduplay.backend.subscription.domain.SubscriptionStatus;
import java.time.Duration;
import java.time.Instant;

public record SubscriptionResponse(
        String plan,
        SubscriptionStatus status,
        boolean active,
        Instant startsAt,
        Instant endsAt,
        Long daysRemaining,
        String provider) {

    public static SubscriptionResponse from(Subscription subscription, Instant now) {
        boolean active = subscription.getStatus().grantsAccess()
                && (subscription.getEndsAt() == null || subscription.getEndsAt().isAfter(now));
        Long daysRemaining = active && subscription.getEndsAt() != null
                ? Duration.between(now, subscription.getEndsAt()).toDays()
                : null;
        return new SubscriptionResponse(subscription.getPlan(), subscription.getStatus(), active,
                subscription.getStartsAt(), subscription.getEndsAt(), daysRemaining, subscription.getProvider());
    }
}

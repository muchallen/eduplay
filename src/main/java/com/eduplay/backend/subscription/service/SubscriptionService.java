package com.eduplay.backend.subscription.service;

import com.eduplay.backend.common.exception.ApiException;
import com.eduplay.backend.config.EduplayProperties;
import com.eduplay.backend.subscription.domain.Subscription;
import com.eduplay.backend.subscription.domain.SubscriptionStatus;
import com.eduplay.backend.subscription.dto.SubscriptionResponse;
import com.eduplay.backend.subscription.dto.UpdateSubscriptionRequest;
import com.eduplay.backend.subscription.repository.SubscriptionRepository;
import com.eduplay.backend.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final EduplayProperties properties;
    private final Clock clock;

    @Transactional
    public Subscription startTrial(UUID userId) {
        Instant now = clock.instant();
        return subscriptionRepository.save(new Subscription(userId, Subscription.TRIAL_PLAN, SubscriptionStatus.TRIAL,
                now, now.plus(properties.subscription().trialPeriod()), Subscription.INTERNAL_PROVIDER, now));
    }

    /** Current subscription; a lapsed trial or paid period is marked EXPIRED on read. */
    @Transactional
    public SubscriptionResponse getCurrent(UUID userId) {
        Subscription subscription = subscriptionRepository.findByUserId(userId)
                .orElseThrow(() -> ApiException.notFound("SUBSCRIPTION_NOT_FOUND", "No subscription found"));
        Instant now = clock.instant();
        expireIfLapsed(subscription, now);
        return SubscriptionResponse.from(subscription, now);
    }

    @Transactional
    public SubscriptionResponse findCurrent(UUID userId) {
        Instant now = clock.instant();
        return subscriptionRepository.findByUserId(userId)
                .map(subscription -> {
                    expireIfLapsed(subscription, now);
                    return SubscriptionResponse.from(subscription, now);
                })
                .orElse(null);
    }

    /** Admin / payment-provider update. Creates the subscription row if the user has none. */
    @Transactional
    public SubscriptionResponse update(UUID userId, UpdateSubscriptionRequest request) {
        if (request.endsAt() != null && !request.endsAt().isAfter(request.startsAt())) {
            throw ApiException.badRequest("INVALID_PERIOD", "endsAt must be after startsAt");
        }
        userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
        Instant now = clock.instant();
        Subscription subscription = subscriptionRepository.findByUserId(userId)
                .orElseGet(() -> new Subscription(userId, request.plan(), request.status(), request.startsAt(),
                        request.endsAt(), request.provider(), now));
        subscription.setPlan(request.plan());
        subscription.setStatus(request.status());
        subscription.setStartsAt(request.startsAt());
        subscription.setEndsAt(request.endsAt());
        subscription.setProvider(request.provider());
        subscription.setProviderReference(request.providerReference());
        subscription.setUpdatedAt(now);
        subscription = subscriptionRepository.save(subscription);
        log.info("Subscription for user {} set to {} ({})", userId, request.status(), request.plan());
        return SubscriptionResponse.from(subscription, now);
    }

    @Scheduled(cron = "${eduplay.subscription.expiry-cron}", zone = "UTC")
    @Transactional
    public void expireLapsedSubscriptions() {
        int expired = subscriptionRepository.expireLapsed(clock.instant());
        if (expired > 0) {
            log.info("Marked {} lapsed subscriptions as EXPIRED", expired);
        }
    }

    private void expireIfLapsed(Subscription subscription, Instant now) {
        if (subscription.hasLapsed(now)) {
            subscription.setStatus(SubscriptionStatus.EXPIRED);
            subscription.setUpdatedAt(now);
        }
    }
}

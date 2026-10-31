package com.eduplay.backend.subscription.dto;

import com.eduplay.backend.subscription.domain.SubscriptionStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Administrative or payment-provider driven change to a parent's subscription. */
public record UpdateSubscriptionRequest(
        @NotBlank @Size(max = 32) String plan,
        @NotNull SubscriptionStatus status,
        @NotNull Instant startsAt,
        Instant endsAt,
        @NotBlank @Size(max = 32) String provider,
        @Size(max = 128) String providerReference) {
}

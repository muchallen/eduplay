package com.eduplay.backend.subscription.controller;

import com.eduplay.backend.security.AuthenticatedUser;
import com.eduplay.backend.subscription.dto.SubscriptionResponse;
import com.eduplay.backend.subscription.dto.UpdateSubscriptionRequest;
import com.eduplay.backend.subscription.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Subscription")
@RestController
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @Operation(operationId = "getSubscription", summary = "Current subscription status of the signed-in parent")
    @GetMapping("/api/v1/subscription")
    public SubscriptionResponse current(@AuthenticationPrincipal AuthenticatedUser user) {
        return subscriptionService.getCurrent(user.id());
    }

    @Operation(operationId = "setUserSubscription", summary = "Set a user's subscription (ADMIN only)",
            description = "Used by operators or a payment-provider integration to activate, extend or cancel.")
    @PutMapping("/api/v1/admin/users/{userId}/subscription")
    public SubscriptionResponse update(@PathVariable UUID userId,
                                       @Valid @RequestBody UpdateSubscriptionRequest request) {
        return subscriptionService.update(userId, request);
    }
}

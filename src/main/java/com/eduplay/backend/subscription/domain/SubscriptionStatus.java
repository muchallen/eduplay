package com.eduplay.backend.subscription.domain;

public enum SubscriptionStatus {
    TRIAL,
    ACTIVE,
    EXPIRED,
    CANCELLED;

    public boolean grantsAccess() {
        return this == TRIAL || this == ACTIVE;
    }
}

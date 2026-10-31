package com.eduplay.backend.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Immutable record that a parent accepted a given privacy policy version. */
@Entity
@Table(name = "consent_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "policy_version", nullable = false, updatable = false, length = 32)
    private String policyVersion;

    @Column(name = "accepted_at", nullable = false, updatable = false)
    private Instant acceptedAt;

    @Column(name = "ip_address", updatable = false, length = 45)
    private String ipAddress;

    public ConsentRecord(UUID userId, String policyVersion, Instant acceptedAt, String ipAddress) {
        this.userId = userId;
        this.policyVersion = policyVersion;
        this.acceptedAt = acceptedAt;
        this.ipAddress = ipAddress;
    }
}

package com.eduplay.backend.auth.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "email_verification_tokens")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerificationToken extends OneTimeToken {

    public EmailVerificationToken(UUID userId, String tokenHash, Instant createdAt, Instant expiresAt) {
        super(userId, tokenHash, createdAt, expiresAt);
    }
}

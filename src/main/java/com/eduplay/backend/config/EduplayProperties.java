package com.eduplay.backend.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed view of the {@code eduplay.*} configuration tree. Defaults live in application.yml.
 */
@Validated
@ConfigurationProperties(prefix = "eduplay")
public record EduplayProperties(
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull RefreshToken refreshToken,
        @Valid @NotNull TokenTtl passwordReset,
        @Valid @NotNull TokenTtl emailVerification,
        @Valid @NotNull Privacy privacy,
        @Valid @NotNull Retention retention,
        @Valid @NotNull Subscription subscription,
        @Valid @NotNull Children children,
        @Valid @NotNull Grades grades,
        @Valid @NotNull Report report,
        @Valid @NotNull Cors cors,
        @Valid @NotNull Mail mail,
        @Valid @NotNull RateLimit rateLimit) {

    public record Jwt(
            @NotBlank(message = "JWT_SECRET must be set") String secret,
            List<String> previousSecrets,
            @NotBlank String issuer,
            @NotNull Duration accessTokenTtl) {

        public List<String> previousSecrets() {
            return previousSecrets == null ? List.of() : previousSecrets.stream().filter(s -> !s.isBlank()).toList();
        }
    }

    public record TokenTtl(@NotNull Duration ttl) {
    }

    /**
     * @param reuseGrace how long a rotated refresh token may be presented again (a retry after a
     *                   lost response) and receive the same replacement instead of revoking
     *                   every session
     */
    public record RefreshToken(@NotNull Duration ttl, @NotNull Duration reuseGrace) {
    }

    public record Privacy(@NotBlank String policyVersion) {
    }

    public record Retention(@NotNull Duration deletedAccountRetention, @NotBlank String purgeCron) {
    }

    public record Subscription(@NotNull Duration trialPeriod, @NotBlank String expiryCron) {
    }

    public record Children(@Min(1) int maxPerParent, @Min(1) int maxPerTeacher) {
    }

    /** School grades the app teaches, e.g. ecd_a and ecd_b. */
    public record Grades(@NotEmpty List<String> allowed) {
    }

    public record Report(
            double strongScoreThreshold,
            double weakScoreThreshold,
            @Min(1) int minGamesForRating,
            @Min(1) int recentActivityLimit) {
    }

    public record Cors(List<String> allowedOrigins) {

        public List<String> allowedOrigins() {
            return allowedOrigins == null ? List.of() : allowedOrigins.stream().filter(s -> !s.isBlank()).toList();
        }
    }

    public record Mail(
            boolean enabled,
            @NotBlank String from,
            @NotBlank String passwordResetUrl,
            @NotBlank String emailVerificationUrl) {
    }

    public record RateLimit(
            boolean enabled,
            @Valid @NotNull Rule loginPerIp,
            @Valid @NotNull Rule loginPerAccount,
            @Valid @NotNull Rule registerPerIp,
            @Valid @NotNull Rule passwordResetPerIp,
            @Valid @NotNull Rule passwordResetPerAccount,
            @Valid @NotNull Rule emailVerificationPerAccount,
            @Valid @NotNull Rule classJoinPerAccount) {
    }

    public record Rule(@Min(1) int capacity, @NotNull Duration window) {
    }
}

package com.eduplay.backend.auth.service;

import com.eduplay.backend.auth.domain.RefreshToken;
import com.eduplay.backend.auth.repository.RefreshTokenRepository;
import com.eduplay.backend.common.exception.ApiException;
import com.eduplay.backend.common.util.SecureTokens;
import com.eduplay.backend.config.EduplayProperties;
import com.eduplay.backend.user.domain.User;
import com.eduplay.backend.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh-token lifecycle: one opaque token per signed-in device, rotated on every use.
 *
 * <p>Presenting a token that was already rotated or revoked normally means it was copied (stolen)
 * or replayed, so every session of that user is revoked and they must sign in again.
 *
 * <p>Exception for weak connections: if the response to a refresh is lost, the app retries with
 * the same token. Within the reuse grace period (60 s by default), a retry of a <em>rotated</em>
 * token whose replacement has not been used yet receives that same replacement again. This works
 * without storing raw tokens: the replacement is derived from the presented token with an HMAC
 * keyed by a server secret, so only a holder of the old token can obtain it.
 */
@Slf4j
@Service
public class RefreshTokenService {

    private static final String HMAC = "HmacSHA256";
    private static final byte[] ROTATION_CONTEXT = "eduplay-refresh-rotation-v1:".getBytes(StandardCharsets.UTF_8);

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final EduplayProperties properties;
    private final Clock clock;
    private final SecretKeySpec rotationKey;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, UserRepository userRepository,
                               EduplayProperties properties, Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.properties = properties;
        this.clock = clock;
        // Separate key from the JWT signing key, derived from the same configured secret.
        byte[] seed = (new String(ROTATION_CONTEXT, StandardCharsets.UTF_8) + properties.jwt().secret().trim())
                .getBytes(StandardCharsets.UTF_8);
        this.rotationKey = new SecretKeySpec(SecureTokens.sha256(seed), HMAC);
    }

    /** Creates a new session; must run inside the caller's transaction. */
    @Transactional
    public IssuedRefreshToken create(UUID userId, String deviceLabel) {
        return save(userId, deviceLabel, SecureTokens.generate(), clock.instant());
    }

    /**
     * Exchanges a valid refresh token for a new one. Not rolled back on ApiException so that the
     * revocation performed on reuse detection is committed before the 401 is returned.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public RotatedSession rotate(String rawToken) {
        Instant now = clock.instant();
        RefreshToken current = refreshTokenRepository.findByTokenHashForUpdate(SecureTokens.sha256Hex(rawToken))
                .orElseThrow(RefreshTokenService::invalid);

        if (current.isRevoked()) {
            Optional<RotatedSession> retry = retryWithinGrace(current, rawToken, now);
            if (retry.isPresent()) {
                return retry.get();
            }
            int revoked = refreshTokenRepository.revokeAllForUser(current.getUserId(), now);
            log.warn("Refresh token reuse detected for user {}; revoked {} active sessions",
                    current.getUserId(), revoked);
            throw ApiException.unauthorized("REFRESH_TOKEN_REUSED",
                    "Session is no longer valid. Please sign in again.");
        }
        if (current.isExpired(now)) {
            throw invalid();
        }
        User user = userRepository.findByIdAndDeletedAtIsNull(current.getUserId()).orElse(null);
        if (user == null) {
            current.revoke(now);
            throw invalid();
        }

        String successorValue = successorOf(rawToken);
        IssuedRefreshToken next = save(user.getId(), current.getDeviceLabel(), successorValue, now);
        current.markRotated(next.id(), now);
        return new RotatedSession(user, next);
    }

    /** Revokes one session (logout). Unknown or already revoked tokens are ignored. */
    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(SecureTokens.sha256Hex(rawToken))
                .ifPresent(token -> token.revoke(clock.instant()));
    }

    @Transactional
    public int revokeAll(UUID userId) {
        return refreshTokenRepository.revokeAllForUser(userId, clock.instant());
    }

    /**
     * A rotated (not logged-out) token presented again within the grace period gets its existing
     * replacement, provided that replacement is still unused and valid.
     */
    private Optional<RotatedSession> retryWithinGrace(RefreshToken current, String rawToken, Instant now) {
        if (current.getRotatedAt() == null || current.getReplacedById() == null
                || !now.isBefore(current.getRotatedAt().plus(properties.refreshToken().reuseGrace()))) {
            return Optional.empty();
        }
        RefreshToken successor = refreshTokenRepository.findById(current.getReplacedById()).orElse(null);
        if (successor == null || successor.isRevoked() || successor.isExpired(now)) {
            return Optional.empty();
        }
        String successorValue = successorOf(rawToken);
        if (!SecureTokens.sha256Hex(successorValue).equals(successor.getTokenHash())) {
            return Optional.empty();
        }
        return userRepository.findByIdAndDeletedAtIsNull(current.getUserId())
                .map(user -> {
                    log.info("Refresh retried within grace period for user {}; returning the same replacement",
                            user.getId());
                    return new RotatedSession(user,
                            new IssuedRefreshToken(successor.getId(), successorValue, successor.getExpiresAt()));
                });
    }

    private IssuedRefreshToken save(UUID userId, String deviceLabel, String rawValue, Instant now) {
        Instant expiresAt = now.plus(properties.refreshToken().ttl());
        RefreshToken token = refreshTokenRepository.save(
                new RefreshToken(userId, SecureTokens.sha256Hex(rawValue), deviceLabel, now, expiresAt));
        return new IssuedRefreshToken(token.getId(), rawValue, expiresAt);
    }

    /** Deterministic, unguessable replacement for a token: HMAC-SHA256(server key, token). */
    private String successorOf(String rawToken) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(rotationKey);
            byte[] digest = mac.doFinal(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 not available", e);
        }
    }

    private static ApiException invalid() {
        return ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Refresh token is invalid or expired");
    }

    public record IssuedRefreshToken(UUID id, String value, Instant expiresAt) {
    }

    public record RotatedSession(User user, IssuedRefreshToken refreshToken) {
    }
}

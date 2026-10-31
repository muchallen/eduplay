package com.eduplay.backend.auth.service;

import com.eduplay.backend.auth.domain.RefreshToken;
import com.eduplay.backend.auth.repository.RefreshTokenRepository;
import com.eduplay.backend.common.exception.ApiException;
import com.eduplay.backend.common.util.SecureTokens;
import com.eduplay.backend.config.EduplayProperties;
import com.eduplay.backend.user.domain.User;
import com.eduplay.backend.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh-token lifecycle: one opaque token per signed-in device, rotated on every use.
 *
 * <p>Presenting a token that was already rotated or revoked means it was copied (stolen) or
 * replayed, so every session of that user is revoked and they must sign in again.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final EduplayProperties properties;
    private final Clock clock;

    /** Creates a new session; must run inside the caller's transaction. */
    @Transactional
    public IssuedRefreshToken create(UUID userId, String deviceLabel) {
        Instant now = clock.instant();
        String raw = SecureTokens.generate();
        Instant expiresAt = now.plus(properties.refreshToken().ttl());
        refreshTokenRepository.save(new RefreshToken(userId, SecureTokens.sha256Hex(raw), deviceLabel, now, expiresAt));
        return new IssuedRefreshToken(raw, expiresAt);
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

        current.revoke(now);
        IssuedRefreshToken next = create(user.getId(), current.getDeviceLabel());
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

    private static ApiException invalid() {
        return ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Refresh token is invalid or expired");
    }

    public record IssuedRefreshToken(String value, Instant expiresAt) {
    }

    public record RotatedSession(User user, IssuedRefreshToken refreshToken) {
    }
}

package com.eduplay.backend.auth.service;

import com.eduplay.backend.auth.domain.PasswordResetToken;
import com.eduplay.backend.auth.repository.PasswordResetTokenRepository;
import com.eduplay.backend.common.exception.ApiException;
import com.eduplay.backend.common.util.AfterCommit;
import com.eduplay.backend.common.util.Emails;
import com.eduplay.backend.common.util.SecureTokens;
import com.eduplay.backend.config.EduplayProperties;
import com.eduplay.backend.email.EmailService;
import com.eduplay.backend.security.RateLimitService;
import com.eduplay.backend.user.domain.User;
import com.eduplay.backend.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final EmailService emailService;
    private final RateLimitService rateLimitService;
    private final PasswordEncoder passwordEncoder;
    private final EduplayProperties properties;
    private final Clock clock;

    /**
     * Starts a reset. The caller always receives the same generic response, whether or not the
     * address belongs to an account, so the endpoint cannot be used to discover registered emails.
     */
    @Transactional
    public void requestReset(String rawEmail, String clientIp) {
        String email = Emails.normalize(rawEmail);
        rateLimitService.consume("reset-ip", clientIp, properties.rateLimit().passwordResetPerIp());
        rateLimitService.consume("reset-account", email, properties.rateLimit().passwordResetPerAccount());

        userRepository.findByEmailAndDeletedAtIsNull(email).ifPresent(user -> {
            Instant now = clock.instant();
            Duration ttl = properties.passwordReset().ttl();
            tokenRepository.invalidateAllForUser(user.getId(), now);
            String raw = SecureTokens.generate();
            tokenRepository.save(new PasswordResetToken(user.getId(), SecureTokens.sha256Hex(raw), now, now.plus(ttl)));
            String name = user.getFullName();
            AfterCommit.run(() -> emailService.sendPasswordReset(email, name, raw, ttl));
            log.info("Password reset requested for user {}", user.getId());
        });
    }

    /** Sets a new password and signs the account out of every device. */
    @Transactional
    public void confirmReset(String rawToken, String newPassword) {
        Instant now = clock.instant();
        PasswordResetToken token = tokenRepository.findByTokenHashForUpdate(SecureTokens.sha256Hex(rawToken))
                .filter(t -> t.isUsable(now))
                .orElseThrow(PasswordResetService::invalidToken);
        User user = userRepository.findByIdAndDeletedAtIsNull(token.getUserId())
                .orElseThrow(PasswordResetService::invalidToken);

        token.markUsed(now);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        if (!user.isEmailVerified()) {
            // Receiving the reset email proves control of the address.
            user.setEmailVerifiedAt(now);
        }
        user.setUpdatedAt(now);
        refreshTokenService.revokeAll(user.getId());
        log.info("Password reset completed for user {}", user.getId());
    }

    private static ApiException invalidToken() {
        return ApiException.badRequest("INVALID_OR_EXPIRED_TOKEN", "Reset link is invalid or has expired");
    }
}

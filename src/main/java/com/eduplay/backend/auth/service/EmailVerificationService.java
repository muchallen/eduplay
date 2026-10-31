package com.eduplay.backend.auth.service;

import com.eduplay.backend.auth.domain.EmailVerificationToken;
import com.eduplay.backend.auth.repository.EmailVerificationTokenRepository;
import com.eduplay.backend.common.exception.ApiException;
import com.eduplay.backend.common.util.AfterCommit;
import com.eduplay.backend.common.util.SecureTokens;
import com.eduplay.backend.config.EduplayProperties;
import com.eduplay.backend.email.EmailService;
import com.eduplay.backend.security.RateLimitService;
import com.eduplay.backend.user.domain.User;
import com.eduplay.backend.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final RateLimitService rateLimitService;
    private final EduplayProperties properties;
    private final Clock clock;

    /** Issues a fresh verification link, invalidating earlier ones. Runs in the caller's transaction. */
    @Transactional
    public void sendVerification(User user) {
        Instant now = clock.instant();
        Duration ttl = properties.emailVerification().ttl();
        tokenRepository.invalidateAllForUser(user.getId(), now);
        String raw = SecureTokens.generate();
        tokenRepository.save(new EmailVerificationToken(user.getId(), SecureTokens.sha256Hex(raw), now, now.plus(ttl)));
        String email = user.getEmail();
        String name = user.getFullName();
        AfterCommit.run(() -> emailService.sendEmailVerification(email, name, raw, ttl));
    }

    /** @return true if a new email was sent, false if the address was already verified */
    @Transactional
    public boolean resend(UUID userId) {
        rateLimitService.consume("verify-email", userId.toString(),
                properties.rateLimit().emailVerificationPerAccount());
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "Account not found"));
        if (user.isEmailVerified()) {
            return false;
        }
        sendVerification(user);
        return true;
    }

    @Transactional
    public void verify(String rawToken) {
        Instant now = clock.instant();
        EmailVerificationToken token = tokenRepository.findByTokenHashForUpdate(SecureTokens.sha256Hex(rawToken))
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> ApiException.badRequest("INVALID_OR_EXPIRED_TOKEN",
                        "Verification link is invalid or has expired"));
        User user = userRepository.findByIdAndDeletedAtIsNull(token.getUserId())
                .orElseThrow(() -> ApiException.badRequest("INVALID_OR_EXPIRED_TOKEN",
                        "Verification link is invalid or has expired"));
        token.markUsed(now);
        if (!user.isEmailVerified()) {
            user.setEmailVerifiedAt(now);
            user.setUpdatedAt(now);
        }
    }
}

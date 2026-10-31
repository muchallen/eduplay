package com.eduplay.backend.infrastructure;

import com.eduplay.backend.auth.repository.EmailVerificationTokenRepository;
import com.eduplay.backend.auth.repository.PasswordResetTokenRepository;
import com.eduplay.backend.auth.repository.RefreshTokenRepository;
import com.eduplay.backend.child.repository.ChildRepository;
import com.eduplay.backend.config.EduplayProperties;
import com.eduplay.backend.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nightly hard deletion of data past its retention period. Deleting a user or child cascades in
 * the database to progress, game results, tokens, consents and subscriptions. All statements are
 * idempotent, so running the job on several replicas at once is harmless.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataRetentionJob {

    private static final Duration EXPIRED_TOKEN_GRACE = Duration.ofDays(1);

    private final UserRepository userRepository;
    private final ChildRepository childRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final EduplayProperties properties;
    private final Clock clock;

    @Scheduled(cron = "${eduplay.retention.purge-cron}", zone = "UTC")
    @Transactional
    public void purge() {
        Instant now = clock.instant();
        Instant cutoff = now.minus(properties.retention().deletedAccountRetention());
        int users = userRepository.purgeDeletedBefore(cutoff);
        int children = childRepository.purgeDeletedBefore(cutoff);
        Instant tokenCutoff = now.minus(EXPIRED_TOKEN_GRACE);
        int tokens = refreshTokenRepository.deleteExpiredBefore(tokenCutoff)
                + passwordResetTokenRepository.deleteExpiredBefore(tokenCutoff)
                + emailVerificationTokenRepository.deleteExpiredBefore(tokenCutoff);
        log.info("Retention purge: {} accounts, {} child profiles, {} expired tokens permanently deleted",
                users, children, tokens);
    }
}

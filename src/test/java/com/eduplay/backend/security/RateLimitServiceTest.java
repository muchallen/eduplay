package com.eduplay.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eduplay.backend.common.exception.RateLimitExceededException;
import com.eduplay.backend.config.EduplayProperties;
import com.eduplay.backend.support.MutableClock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class RateLimitServiceTest {

    private final MutableClock clock = new MutableClock();
    private final EduplayProperties.Rule rule = new EduplayProperties.Rule(3, Duration.ofMinutes(15));

    @Test
    void allowsCapacityThenRejectsWithRetryAfter() {
        RateLimitService service = service(true);
        for (int i = 0; i < 3; i++) {
            service.consume("login", "1.2.3.4", rule);
        }
        assertThatThrownBy(() -> service.consume("login", "1.2.3.4", rule))
                .isInstanceOfSatisfying(RateLimitExceededException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isBetween(1L, 300L));
    }

    @Test
    void tokensRefillOverTime() {
        RateLimitService service = service(true);
        for (int i = 0; i < 3; i++) {
            service.consume("login", "k", rule);
        }
        clock.advance(Duration.ofMinutes(5));
        assertThatCode(() -> service.consume("login", "k", rule)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.consume("login", "k", rule)).isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void keysAreIndependent() {
        RateLimitService service = service(true);
        for (int i = 0; i < 3; i++) {
            service.consume("login", "a", rule);
        }
        assertThatCode(() -> service.consume("login", "b", rule)).doesNotThrowAnyException();
        assertThatCode(() -> service.consume("register", "a", rule)).doesNotThrowAnyException();
    }

    @Test
    void disabledLimiterNeverRejects() {
        RateLimitService service = service(false);
        for (int i = 0; i < 10; i++) {
            service.consume("login", "k", rule);
        }
    }

    private RateLimitService service(boolean enabled) {
        EduplayProperties.RateLimit rateLimit = new EduplayProperties.RateLimit(enabled, rule, rule, rule, rule, rule, rule, rule);
        EduplayProperties properties = new EduplayProperties(null, null, null, null, null, null, null, null, null,
                null, null, null, rateLimit);
        return new RateLimitService(properties, clock);
    }
}

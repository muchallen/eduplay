package com.eduplay.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eduplay.backend.config.EduplayProperties;
import com.eduplay.backend.support.MutableClock;
import com.eduplay.backend.user.domain.Role;
import com.eduplay.backend.user.domain.User;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtServiceTest {

    private static final String SECRET = "dGVzdC1vbmx5LXNlY3JldC1rZXktZm9yLWVkdXBsYXktaW50ZWdyYXRpb24tdGVzdHMtMDAwMQ==";
    private static final String OTHER_SECRET = "cHJldmlvdXMtdGVzdC1zZWNyZXQta2V5LWZvci1lZHVwbGF5LXJvdGF0aW9uLXRlc3RzLTAwMDI=";

    private final MutableClock clock = new MutableClock();

    @Test
    void issuedTokenRoundTrips() {
        JwtService service = service(SECRET, List.of());
        User user = user(Role.PARENT);
        JwtService.AccessToken token = service.issue(user);

        assertThat(token.expiresAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(15)));
        AuthenticatedUser parsed = service.parse(token.value());
        assertThat(parsed.id()).isEqualTo(user.getId());
        assertThat(parsed.role()).isEqualTo(Role.PARENT);
    }

    @Test
    void tokenExpiresAfterFifteenMinutes() {
        JwtService service = service(SECRET, List.of());
        String token = service.issue(user(Role.PARENT)).value();
        clock.advance(Duration.ofMinutes(15).plusSeconds(31));
        assertThatThrownBy(() -> service.parse(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tokenSignedWithUnknownKeyIsRejected() {
        String token = service(OTHER_SECRET, List.of()).issue(user(Role.PARENT)).value();
        assertThatThrownBy(() -> service(SECRET, List.of()).parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void previousSecretStillVerifiesAfterRotation() {
        String issuedBeforeRotation = service(OTHER_SECRET, List.of()).issue(user(Role.ADMIN)).value();
        JwtService rotated = service(SECRET, List.of(OTHER_SECRET));
        assertThat(rotated.parse(issuedBeforeRotation).role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void shortOrInvalidSecretsFailFast() {
        assertThatThrownBy(() -> service("c2hvcnQ=", List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256 bits");
        assertThatThrownBy(() -> service("not base64 !!", List.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unsignedTokenIsRejected() {
        JwtService service = service(SECRET, List.of());
        String unsigned = io.jsonwebtoken.Jwts.builder().subject(UUID.randomUUID().toString())
                .claim("role", "ADMIN").issuer("test").compact();
        assertThatThrownBy(() -> service.parse(unsigned)).isInstanceOf(JwtException.class);
    }

    private JwtService service(String secret, List<String> previous) {
        EduplayProperties.Jwt jwt = new EduplayProperties.Jwt(secret, previous, "test", Duration.ofMinutes(15));
        EduplayProperties properties = new EduplayProperties(jwt, null, null, null, null, null, null, null, null,
                null, null, null);
        return new JwtService(properties, clock);
    }

    private User user(Role role) {
        User user = new User("a@example.com", "hash", "A", null, role, Instant.now());
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }
}

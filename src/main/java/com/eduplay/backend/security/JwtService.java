package com.eduplay.backend.security;

import com.eduplay.backend.common.util.SecureTokens;
import com.eduplay.backend.config.EduplayProperties;
import com.eduplay.backend.user.domain.Role;
import com.eduplay.backend.user.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Locator;
import io.jsonwebtoken.ProtectedHeader;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Issues and validates HS256 access tokens.
 *
 * <p>JWT_SECRET (base64, at least 256 bits) signs new tokens. JWT_PREVIOUS_SECRETS lists retired
 * secrets that are still accepted for verification so a rotation does not sign everyone out;
 * each token's {@code kid} header identifies the key that signed it.
 */
@Service
public class JwtService {

    private static final String ROLE_CLAIM = "role";
    private static final int MIN_KEY_BYTES = 32;

    private final Clock clock;
    private final String issuer;
    private final Duration ttl;
    private final String signingKeyId;
    private final SecretKey signingKey;
    private final Map<String, SecretKey> verificationKeys = new LinkedHashMap<>();

    public JwtService(EduplayProperties properties, Clock clock) {
        this.clock = clock;
        this.issuer = properties.jwt().issuer();
        this.ttl = properties.jwt().accessTokenTtl();
        this.signingKey = toKey(properties.jwt().secret(), "JWT_SECRET");
        this.signingKeyId = keyId(signingKey);
        verificationKeys.put(signingKeyId, signingKey);
        for (String previous : properties.jwt().previousSecrets()) {
            SecretKey key = toKey(previous, "JWT_PREVIOUS_SECRETS");
            verificationKeys.putIfAbsent(keyId(key), key);
        }
    }

    public AccessToken issue(User user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);
        String token = Jwts.builder()
                .header().keyId(signingKeyId).and()
                .id(UUID.randomUUID().toString())
                .issuer(issuer)
                .subject(user.getId().toString())
                .claim(ROLE_CLAIM, user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
        return new AccessToken(token, expiresAt);
    }

    /**
     * @throws ExpiredJwtException if the token is well-formed and signed but expired
     * @throws JwtException        for any other invalid token
     */
    public AuthenticatedUser parse(String token) {
        Locator<Key> keyLocator = header -> {
            String kid = header instanceof ProtectedHeader protectedHeader ? protectedHeader.getKeyId() : null;
            SecretKey key = kid == null ? null : verificationKeys.get(kid);
            if (key == null) {
                throw new JwtException("Unknown signing key");
            }
            return key;
        };
        Claims claims = Jwts.parser()
                .keyLocator(keyLocator)
                .requireIssuer(issuer)
                .clock(() -> Date.from(clock.instant()))
                .clockSkewSeconds(30)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        try {
            return new AuthenticatedUser(UUID.fromString(claims.getSubject()),
                    Role.valueOf(claims.get(ROLE_CLAIM, String.class)));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new JwtException("Malformed token claims");
        }
    }

    private static SecretKey toKey(String base64Secret, String name) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Secret.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(name + " must be base64 encoded");
        }
        if (bytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(name + " must decode to at least 32 bytes (256 bits)");
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    private static String keyId(SecretKey key) {
        return HexFormat.of().formatHex(SecureTokens.sha256(key.getEncoded())).substring(0, 16);
    }

    public record AccessToken(String value, Instant expiresAt) {
    }
}

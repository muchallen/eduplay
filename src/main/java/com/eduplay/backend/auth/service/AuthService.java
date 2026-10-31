package com.eduplay.backend.auth.service;

import com.eduplay.backend.auth.dto.AuthResponse;
import com.eduplay.backend.auth.dto.LoginRequest;
import com.eduplay.backend.auth.dto.RegisterRequest;
import com.eduplay.backend.common.exception.ApiException;
import com.eduplay.backend.common.util.Emails;
import com.eduplay.backend.config.EduplayProperties;
import com.eduplay.backend.security.JwtService;
import com.eduplay.backend.security.RateLimitService;
import com.eduplay.backend.subscription.service.SubscriptionService;
import com.eduplay.backend.user.domain.ConsentRecord;
import com.eduplay.backend.user.domain.Role;
import com.eduplay.backend.user.domain.User;
import com.eduplay.backend.user.dto.UserResponse;
import com.eduplay.backend.user.repository.ConsentRecordRepository;
import com.eduplay.backend.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final ConsentRecordRepository consentRecordRepository;
    private final RefreshTokenService refreshTokenService;
    private final EmailVerificationService emailVerificationService;
    private final SubscriptionService subscriptionService;
    private final JwtService jwtService;
    private final RateLimitService rateLimitService;
    private final PasswordEncoder passwordEncoder;
    private final EduplayProperties properties;
    private final Clock clock;

    /** Precomputed hash compared against when the email is unknown, so timing does not leak existence. */
    private String dummyHash;

    /**
     * Creates a parent account with its consent record and trial subscription, sends the
     * verification email after commit, and signs the new parent in on this device.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request, String clientIp) {
        rateLimitService.consume("register-ip", clientIp, properties.rateLimit().registerPerIp());

        String currentPolicy = properties.privacy().policyVersion();
        if (!currentPolicy.equals(request.policyVersion())) {
            throw ApiException.badRequest("POLICY_VERSION_MISMATCH",
                    "Please accept the current privacy policy (version " + currentPolicy + ")");
        }
        String email = Emails.normalize(request.email());
        if (userRepository.existsByEmailAndDeletedAtIsNull(email)) {
            throw ApiException.conflict("EMAIL_ALREADY_REGISTERED", "An account with this email already exists");
        }

        Instant now = clock.instant();
        User user = new User(email, passwordEncoder.encode(request.password()), request.fullName().trim(),
                blankToNull(request.phone()), Role.PARENT, now);
        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("EMAIL_ALREADY_REGISTERED", "An account with this email already exists");
        }
        consentRecordRepository.save(new ConsentRecord(user.getId(), currentPolicy, now, clientIp));
        subscriptionService.startTrial(user.getId());
        emailVerificationService.sendVerification(user);
        log.info("Registered parent account {}", user.getId());
        return issueSession(user, request.deviceLabel());
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String clientIp) {
        String email = Emails.normalize(request.email());
        rateLimitService.consume("login-ip", clientIp, properties.rateLimit().loginPerIp());
        rateLimitService.consume("login-account", email, properties.rateLimit().loginPerAccount());

        User user = userRepository.findByEmailAndDeletedAtIsNull(email).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash());
            throw invalidCredentials();
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.info("Failed login for user {}", user.getId());
            throw invalidCredentials();
        }
        return issueSession(user, request.deviceLabel());
    }

    public AuthResponse refresh(String refreshToken) {
        RefreshTokenService.RotatedSession session = refreshTokenService.rotate(refreshToken);
        JwtService.AccessToken accessToken = jwtService.issue(session.user());
        return toResponse(session.user(), accessToken, session.refreshToken());
    }

    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    public void logoutEverywhere(java.util.UUID userId) {
        refreshTokenService.revokeAll(userId);
    }

    private AuthResponse issueSession(User user, String deviceLabel) {
        RefreshTokenService.IssuedRefreshToken refreshToken =
                refreshTokenService.create(user.getId(), blankToNull(deviceLabel));
        return toResponse(user, jwtService.issue(user), refreshToken);
    }

    private static AuthResponse toResponse(User user, JwtService.AccessToken accessToken,
                                           RefreshTokenService.IssuedRefreshToken refreshToken) {
        return new AuthResponse("Bearer", accessToken.value(), accessToken.expiresAt(), refreshToken.value(),
                refreshToken.expiresAt(), UserResponse.from(user));
    }

    private String dummyHash() {
        if (dummyHash == null) {
            dummyHash = passwordEncoder.encode("timing-equaliser-not-a-real-password");
        }
        return dummyHash;
    }

    private static ApiException invalidCredentials() {
        return ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid email or password");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

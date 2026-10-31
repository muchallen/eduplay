package com.eduplay.backend.auth.controller;

import com.eduplay.backend.auth.dto.AuthResponse;
import com.eduplay.backend.auth.dto.LoginRequest;
import com.eduplay.backend.auth.dto.LogoutRequest;
import com.eduplay.backend.auth.dto.PasswordResetConfirmRequest;
import com.eduplay.backend.auth.dto.PasswordResetRequest;
import com.eduplay.backend.auth.dto.RefreshRequest;
import com.eduplay.backend.auth.dto.RegisterRequest;
import com.eduplay.backend.auth.dto.VerifyEmailRequest;
import com.eduplay.backend.auth.service.AuthService;
import com.eduplay.backend.auth.service.EmailVerificationService;
import com.eduplay.backend.auth.service.PasswordResetService;
import com.eduplay.backend.common.response.MessageResponse;
import com.eduplay.backend.common.util.ClientIp;
import com.eduplay.backend.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String RESET_REQUESTED_MESSAGE =
            "If an account exists for that email, a password reset link has been sent.";

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final EmailVerificationService emailVerificationService;

    @Operation(summary = "Register a parent account", description = "Records parental consent, starts a trial "
            + "subscription, sends a verification email and returns tokens for this device.")
    @SecurityRequirements
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        return authService.register(request, ClientIp.of(http));
    }

    @Operation(summary = "Sign in and create a session for this device")
    @SecurityRequirements
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request, ClientIp.of(http));
    }

    @Operation(summary = "Rotate a refresh token", description = "Returns a new access token and a new refresh "
            + "token; the presented refresh token is revoked. Re-using a revoked refresh token signs the account "
            + "out of every device. Persist the new refresh token before using it.")
    @SecurityRequirements
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @Operation(summary = "Sign out this device (revokes the refresh token)")
    @SecurityRequirements
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
    }

    @Operation(summary = "Sign out every device of the current account")
    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutAll(@AuthenticationPrincipal AuthenticatedUser user) {
        authService.logoutEverywhere(user.id());
    }

    @Operation(summary = "Request a password reset email", description = "Always returns 202 with the same "
            + "message, whether or not the email is registered.")
    @SecurityRequirements
    @PostMapping("/password-reset/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse requestPasswordReset(@Valid @RequestBody PasswordResetRequest request,
                                                HttpServletRequest http) {
        passwordResetService.requestReset(request.email(), ClientIp.of(http));
        return new MessageResponse(RESET_REQUESTED_MESSAGE);
    }

    @Operation(summary = "Set a new password using the emailed reset token",
            description = "Signs the account out of every device.")
    @SecurityRequirements
    @PostMapping("/password-reset/confirm")
    public MessageResponse confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirmReset(request.token(), request.newPassword());
        return new MessageResponse("Your password has been updated. Please sign in again.");
    }

    @Operation(summary = "Confirm an email address using the emailed verification token")
    @SecurityRequirements
    @PostMapping("/verify-email")
    public MessageResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        emailVerificationService.verify(request.token());
        return new MessageResponse("Your email address has been verified.");
    }

    @Operation(summary = "Re-send the verification email to the current account")
    @PostMapping("/verify-email/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse resendVerification(@AuthenticationPrincipal AuthenticatedUser user) {
        boolean sent = emailVerificationService.resend(user.id());
        return new MessageResponse(sent ? "A new verification email has been sent."
                : "Your email address is already verified.");
    }
}

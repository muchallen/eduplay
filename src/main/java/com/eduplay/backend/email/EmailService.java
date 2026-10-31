package com.eduplay.backend.email;

import com.eduplay.backend.config.EduplayProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sends transactional email over SMTP. Delivery is asynchronous so that SMTP latency never slows
 * down API responses, and so that password-reset timing does not reveal whether an account exists.
 * Neither addresses nor tokens are ever written to the logs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final EduplayProperties properties;

    @Async
    public void sendPasswordReset(String to, String fullName, String token, Duration validFor) {
        String link = withToken(properties.mail().passwordResetUrl(), token);
        send("password-reset", to, "Reset your Eduplay password", """
                Hello %s,

                We received a request to reset the password for your Eduplay parent account.
                Open this link to choose a new password (valid for %d minutes):

                %s

                If the link does not open the app, enter this reset code instead:
                %s

                If you did not request a password reset you can ignore this email; your password
                will not change.

                The Eduplay team
                """.formatted(fullName, validFor.toMinutes(), link, token));
    }

    @Async
    public void sendEmailVerification(String to, String fullName, String token, Duration validFor) {
        String link = withToken(properties.mail().emailVerificationUrl(), token);
        send("email-verification", to, "Confirm your Eduplay email address", """
                Hello %s,

                Welcome to Eduplay! Please confirm your email address (link valid for %d hours):

                %s

                Verification code: %s

                The Eduplay team
                """.formatted(fullName, validFor.toHours(), link, token));
    }

    private void send(String type, String to, String subject, String body) {
        if (!properties.mail().enabled()) {
            log.info("Email delivery disabled; skipped {} email", type);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.mail().from());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        try {
            mailSender.send(message);
            log.info("Sent {} email", type);
        } catch (MailException e) {
            log.error("Failed to send {} email: {}", type, e.getClass().getSimpleName());
        }
    }

    private static String withToken(String baseUrl, String token) {
        String separator = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + separator + "token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }
}

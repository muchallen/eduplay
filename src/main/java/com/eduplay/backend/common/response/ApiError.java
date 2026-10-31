package com.eduplay.backend.common.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * Uniform error body returned by every endpoint, including security failures.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        @Schema(example = "VALIDATION_FAILED", description = """
                Stable machine-readable error code. Clients should switch on this, not on `message`.
                400: VALIDATION_FAILED (see fieldErrors), MALFORMED_REQUEST, INVALID_PARAMETER, INVALID_GRADE,
                INVALID_BIRTH_YEAR, POLICY_VERSION_MISMATCH, INVALID_OR_EXPIRED_TOKEN (reset or verification link),
                INVALID_PERIOD (admin).
                401: AUTHENTICATION_REQUIRED, TOKEN_EXPIRED (refresh, then retry), INVALID_TOKEN, INVALID_CREDENTIALS,
                INVALID_REFRESH_TOKEN, REFRESH_TOKEN_REUSED (every session revoked; sign in again), ACCOUNT_NOT_FOUND.
                403: FORBIDDEN (wrong role), CHILD_NOT_OWNED (teacher tried to change a parent's child).
                404: CHILD_NOT_FOUND, CLASS_NOT_FOUND, SUBSCRIPTION_NOT_FOUND, USER_NOT_FOUND, NOT_FOUND.
                409: EMAIL_ALREADY_REGISTERED, CHILD_LIMIT_REACHED, DATA_CONFLICT.
                429: RATE_LIMITED (wait for the Retry-After header).
                500: INTERNAL_ERROR.
                Other framework errors use the HTTP status name, e.g. METHOD_NOT_ALLOWED, UNSUPPORTED_MEDIA_TYPE.""")
        String code,
        String message,
        String path,
        String requestId,
        List<FieldViolation> fieldErrors) {

    public record FieldViolation(String field, String message) {
    }
}

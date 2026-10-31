package com.eduplay.backend.common.response;

import java.time.Instant;
import java.util.List;

/**
 * Uniform error body returned by every endpoint, including security failures.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        String requestId,
        List<FieldViolation> fieldErrors) {

    public record FieldViolation(String field, String message) {
    }
}

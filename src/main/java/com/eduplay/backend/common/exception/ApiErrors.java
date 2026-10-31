package com.eduplay.backend.common.exception;

import com.eduplay.backend.common.response.ApiError;
import com.eduplay.backend.infrastructure.RequestIdFilter;
import java.time.Instant;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public final class ApiErrors {

    private ApiErrors() {
    }

    public static ApiError build(HttpStatusCode status, String code, String message, String path,
                                 List<ApiError.FieldViolation> fieldErrors) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return new ApiError(
                Instant.now(),
                status.value(),
                resolved != null ? resolved.getReasonPhrase() : "Error",
                code,
                message,
                path,
                MDC.get(RequestIdFilter.MDC_KEY),
                fieldErrors == null || fieldErrors.isEmpty() ? null : fieldErrors);
    }

    public static ApiError build(HttpStatusCode status, String code, String message, String path) {
        return build(status, code, message, path, null);
    }
}

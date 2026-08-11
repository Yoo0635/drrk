package com.drrk.global.error;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        List<FieldErrorResponse> fieldErrors
) {

    public ErrorResponse {
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(path, "path");
        fieldErrors = List.copyOf(Objects.requireNonNull(fieldErrors, "fieldErrors"));
    }

    public static ErrorResponse from(ErrorCode errorCode, String path) {
        return from(errorCode, path, List.of());
    }

    public static ErrorResponse from(
            ErrorCode errorCode,
            String path,
            List<FieldErrorResponse> fieldErrors
    ) {
        Objects.requireNonNull(errorCode, "errorCode");
        return new ErrorResponse(
                Instant.now(),
                errorCode.getStatus(),
                errorCode.getCode(),
                errorCode.getMessage(),
                path,
                fieldErrors
        );
    }
}

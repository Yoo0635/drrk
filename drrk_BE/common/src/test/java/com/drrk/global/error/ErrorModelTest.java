package com.drrk.global.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class ErrorModelTest {

    @Test
    void commonErrorCodeProvidesStableExternalContract() {
        assertEquals(400, CommonErrorCode.INVALID_REQUEST.getStatus());
        assertEquals("COMMON-400-001", CommonErrorCode.INVALID_REQUEST.getCode());
        assertEquals("요청 값이 올바르지 않습니다.", CommonErrorCode.INVALID_REQUEST.getMessage());

        assertEquals(500, CommonErrorCode.INTERNAL_SERVER_ERROR.getStatus());
        assertEquals("COMMON-500-001", CommonErrorCode.INTERNAL_SERVER_ERROR.getCode());
        assertEquals("서버 내부 오류가 발생했습니다.", CommonErrorCode.INTERNAL_SERVER_ERROR.getMessage());
    }

    @Test
    void businessExceptionKeepsErrorCodeAndPublicMessage() {
        BusinessException exception = new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND);

        assertEquals(CommonErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
        assertEquals("요청한 리소스를 찾을 수 없습니다.", exception.getMessage());
    }

    @Test
    void businessExceptionPreservesCauseForLogging() {
        IllegalStateException cause = new IllegalStateException("storage timeout");

        BusinessException exception = new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR, cause);

        assertEquals(CommonErrorCode.INTERNAL_SERVER_ERROR, exception.getErrorCode());
        assertSame(cause, exception.getCause());
        assertEquals("서버 내부 오류가 발생했습니다.", exception.getMessage());
    }

    @Test
    void businessExceptionRequiresErrorCode() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new BusinessException(null)
        );
        assertEquals("errorCode", exception.getMessage());
    }

    @Test
    void errorResponseAlwaysHasNonNullFieldErrors() {
        ErrorResponse response = ErrorResponse.from(CommonErrorCode.TYPE_MISMATCH, "/api/users");

        assertNotNull(response.timestamp());
        assertEquals(400, response.status());
        assertEquals("COMMON-400-003", response.code());
        assertEquals("요청 값의 형식이 올바르지 않습니다.", response.message());
        assertEquals("/api/users", response.path());
        assertEquals(List.of(), response.fieldErrors());
    }

    @Test
    void errorResponseCopiesFieldErrorsDefensively() {
        List<FieldErrorResponse> fieldErrors = List.of(
                new FieldErrorResponse("email", "올바른 이메일 형식이어야 합니다.")
        );

        ErrorResponse response = ErrorResponse.from(CommonErrorCode.INVALID_REQUEST, "/api/users", fieldErrors);

        assertEquals(
                List.of(new FieldErrorResponse("email", "올바른 이메일 형식이어야 합니다.")),
                response.fieldErrors()
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> response.fieldErrors().add(new FieldErrorResponse("name", "필수입니다."))
        );
        assertFalse(response.fieldErrors().isEmpty());
    }
}

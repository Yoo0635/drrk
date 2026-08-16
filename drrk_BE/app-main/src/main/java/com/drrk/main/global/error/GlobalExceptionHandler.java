package com.drrk.main.global.error;

import com.drrk.global.error.BusinessException;
import com.drrk.global.error.CommonErrorCode;
import com.drrk.global.error.ErrorCode;
import com.drrk.global.error.ErrorResponse;
import com.drrk.global.error.FieldErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = exception.getErrorCode();
        if (exception.getCause() == null) {
            log.warn("Business exception. code={}, path={}", errorCode.getCode(), request.getRequestURI());
        } else {
            log.warn("Business exception. code={}, path={}", errorCode.getCode(), request.getRequestURI(), exception.getCause());
        }
        return build(errorCode, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(fieldError ->
                errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage())
        );
        List<FieldErrorResponse> fieldErrors = errors.entrySet().stream()
                .map(entry -> new FieldErrorResponse(entry.getKey(), entry.getValue()))
                .toList();

        log.debug("Validation failed. code={}, path={}, fields={}",
                CommonErrorCode.INVALID_REQUEST.getCode(),
                request.getRequestURI(),
                fieldErrors.stream().map(FieldErrorResponse::field).toList());

        return build(CommonErrorCode.INVALID_REQUEST, request, fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation ->
                errors.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage())
        );
        List<FieldErrorResponse> fieldErrors = errors.entrySet().stream()
                .map(entry -> new FieldErrorResponse(entry.getKey(), entry.getValue()))
                .toList();

        log.debug("Constraint violation. code={}, path={}, fields={}",
                CommonErrorCode.INVALID_REQUEST.getCode(),
                request.getRequestURI(),
                fieldErrors.stream().map(FieldErrorResponse::field).toList());

        return build(CommonErrorCode.INVALID_REQUEST, request, fieldErrors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception,
            HttpServletRequest request
    ) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getParameterValidationResults().forEach(result ->
                result.getResolvableErrors().forEach(error ->
                        errors.putIfAbsent(result.getMethodParameter().getParameterName(), error.getDefaultMessage())
                )
        );
        List<FieldErrorResponse> fieldErrors = errors.entrySet().stream()
                .map(entry -> new FieldErrorResponse(entry.getKey(), entry.getValue()))
                .toList();

        log.debug("Method parameter validation failed. code={}, path={}, fields={}",
                CommonErrorCode.INVALID_REQUEST.getCode(),
                request.getRequestURI(),
                fieldErrors.stream().map(FieldErrorResponse::field).toList());

        return build(CommonErrorCode.INVALID_REQUEST, request, fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        log.debug("Unreadable request body. code={}, path={}",
                CommonErrorCode.INVALID_JSON.getCode(),
                request.getRequestURI());
        return build(CommonErrorCode.INVALID_JSON, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        log.debug("Type mismatch. code={}, path={}",
                CommonErrorCode.TYPE_MISMATCH.getCode(),
                request.getRequestURI());
        return build(CommonErrorCode.TYPE_MISMATCH, request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFoundException(
            NoResourceFoundException exception,
            HttpServletRequest request
    ) {
        log.debug("Resource not found. code={}, path={}",
                CommonErrorCode.RESOURCE_NOT_FOUND.getCode(),
                request.getRequestURI());
        return build(CommonErrorCode.RESOURCE_NOT_FOUND, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request
    ) {
        log.debug("Method not allowed. code={}, path={}",
                CommonErrorCode.METHOD_NOT_ALLOWED.getCode(),
                request.getRequestURI());
        return build(CommonErrorCode.METHOD_NOT_ALLOWED, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error("Unexpected exception. path={}", request.getRequestURI(), exception);
        return build(CommonErrorCode.INTERNAL_SERVER_ERROR, request);
    }

    private ResponseEntity<ErrorResponse> build(ErrorCode errorCode, HttpServletRequest request) {
        return build(errorCode, request, List.of());
    }

    private ResponseEntity<ErrorResponse> build(
            ErrorCode errorCode,
            HttpServletRequest request,
            List<FieldErrorResponse> fieldErrors
    ) {
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.from(errorCode, request.getRequestURI(), fieldErrors));
    }
}

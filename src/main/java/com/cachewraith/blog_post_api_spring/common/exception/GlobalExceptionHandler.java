package com.cachewraith.blog_post_api_spring.common.exception;

import com.cachewraith.blog_post_api_spring.common.response.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusiness(
            BusinessException ex, HttpServletRequest request) {
        ErrorCode code = ex.getErrorCode();
        return ResponseEntity.status(code.getStatus())
                .body(ApiErrorResponse.of(code.name(), ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, List<String>> fields = new HashMap<>();
        ex.getBindingResult()
                .getFieldErrors()
                .forEach(
                        error ->
                                fields.computeIfAbsent(error.getField(), k -> new ArrayList<>())
                                        .add(error.getDefaultMessage()));
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getStatus())
                .body(
                        ApiErrorResponse.withFields(
                                ErrorCode.VALIDATION_FAILED.name(),
                                ErrorCode.VALIDATION_FAILED.getDefaultMessage(),
                                fields,
                                request.getRequestURI()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleUploadSize(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return ResponseEntity.status(ErrorCode.PAYLOAD_TOO_LARGE.getStatus())
                .body(
                        ApiErrorResponse.of(
                                ErrorCode.PAYLOAD_TOO_LARGE.name(),
                                ErrorCode.PAYLOAD_TOO_LARGE.getDefaultMessage(),
                                request.getRequestURI()));
    }

    /** Let Spring Security's own entry point/handler answer these — do not swallow them here. */
    @ExceptionHandler({AuthenticationException.class, AccessDeniedException.class})
    public ResponseEntity<ApiErrorResponse> handleSecurity(RuntimeException ex)
            throws RuntimeException {
        throw ex;
    }

    /**
     * Last resort. The real cause is logged server-side; the client gets a generic message so
     * internals never leak (OWASP A09, A10).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at {}", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                        ApiErrorResponse.of(
                                ErrorCode.INTERNAL_ERROR.name(),
                                ErrorCode.INTERNAL_ERROR.getDefaultMessage(),
                                request.getRequestURI()));
    }
}

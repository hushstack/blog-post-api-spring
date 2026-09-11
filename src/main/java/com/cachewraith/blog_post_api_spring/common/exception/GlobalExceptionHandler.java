package com.cachewraith.blog_post_api_spring.common.exception;

import com.cachewraith.blog_post_api_spring.common.response.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * When true the unexpected-error response carries the exception class and message. On in the
     * dev profile only; absent — and therefore false — in prod, where an exception message can
     * name a table, a file path or a host (OWASP A02, A09).
     */
    @Value("${app.errors.expose-details:false}")
    private boolean exposeDetails;

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

    /**
     * A malformed multipart request is the caller's mistake, not the server's. These used to fall
     * through to {@link #handleUnexpected} and come back as a 500 reading "Something went wrong",
     * which tells someone debugging an upload nothing at all — the part name and the content type
     * the server actually saw are exactly what they need (OWASP A10). {@link MultipartException}
     * is the parser failing outright, typically form-data sent to a JSON endpoint.
     *
     * <p>The reason text here is Spring's own and names only what the client sent, so it carries no
     * server internals. {@code MaxUploadSizeExceededException} extends {@code MultipartException}
     * and is caught by its own, more specific handler above.
     */
    @ExceptionHandler({
        MissingServletRequestPartException.class,
        HttpMediaTypeNotSupportedException.class,
        MultipartException.class
    })
    public ResponseEntity<ApiErrorResponse> handleBadRequestShape(
            Exception ex, HttpServletRequest request) {
        // MultipartException's own message is the wrapper ("Failed to parse ..."); the parser's
        // reason — a truncated body, a missing boundary — is the root cause.
        Throwable root = NestedExceptionUtils.getMostSpecificCause(ex);
        String message =
                root == ex || root.getMessage() == null
                        ? ex.getMessage()
                        : ex.getMessage() + ": " + root.getMessage();
        log.warn("Malformed request at {}: {}", request.getRequestURI(), message);
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getStatus())
                .body(
                        ApiErrorResponse.of(
                                ErrorCode.VALIDATION_FAILED.name(), message, request.getRequestURI()));
    }

    /**
     * A path no handler owns. Spring MVC falls through to the static-resource handler and throws
     * this, which used to surface as a 500 "Something went wrong" — the wrong status and, for
     * someone testing against a stale build, a message that hides the actual problem: the route
     * is not there (OWASP A10).
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoRoute(
            NoResourceFoundException ex, HttpServletRequest request) {
        log.warn("No route for {} {}", request.getMethod(), request.getRequestURI());
        return ResponseEntity.status(ErrorCode.RESOURCE_NOT_FOUND.getStatus())
                .body(
                        ApiErrorResponse.of(
                                ErrorCode.RESOURCE_NOT_FOUND.name(),
                                "No endpoint " + request.getMethod() + " " + request.getRequestURI(),
                                request.getRequestURI()));
    }

    /**
     * A JSON body that is absent or does not parse. Spring's own message quotes the whole handler
     * signature, which is noise to a client and a map of the code base to anyone else, so it is
     * replaced with what the caller can act on (OWASP A02, A10).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        String raw = ex.getMessage() == null ? "" : ex.getMessage();
        String message =
                raw.startsWith("Required request body is missing")
                        ? "Request body is required"
                        : "Request body is not valid JSON";
        log.warn("Unreadable body at {}: {}", request.getRequestURI(), raw.split("\\n")[0]);
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getStatus())
                .body(
                        ApiErrorResponse.of(
                                ErrorCode.VALIDATION_FAILED.name(), message, request.getRequestURI()));
    }

    /**
     * A path or query value that does not convert — a non-UUID id, an enum value that does not
     * exist. The caller's mistake, answered with the accepted values when the target is an enum
     * (OWASP A10).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        Class<?> required = ex.getRequiredType();
        String accepted =
                required != null && required.isEnum()
                        ? "; accepted: " + java.util.Arrays.toString(required.getEnumConstants())
                        : "";
        String message = "Invalid value for '" + ex.getName() + "'" + accepted;
        log.warn("Type mismatch at {}: {}", request.getRequestURI(), message);
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getStatus())
                .body(
                        ApiErrorResponse.of(
                                ErrorCode.VALIDATION_FAILED.name(), message, request.getRequestURI()));
    }

    /** Let Spring Security's own entry point/handler answer these — do not swallow them here. */
    @ExceptionHandler({AuthenticationException.class, AccessDeniedException.class})
    public ResponseEntity<ApiErrorResponse> handleSecurity(RuntimeException ex)
            throws RuntimeException {
        throw ex;
    }

    /**
     * Last resort. The real cause is always logged server-side; the client gets it too only when
     * {@code app.errors.expose-details} says so, otherwise a generic message so internals never
     * leak (OWASP A09, A10).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at {}", request.getRequestURI(), ex);
        String message =
                exposeDetails
                        ? ex.getClass().getSimpleName() + ": " + ex.getMessage()
                        : ErrorCode.INTERNAL_ERROR.getDefaultMessage();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                        ApiErrorResponse.of(
                                ErrorCode.INTERNAL_ERROR.name(), message, request.getRequestURI()));
    }
}

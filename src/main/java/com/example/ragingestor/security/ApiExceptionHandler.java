package com.example.ragingestor.security;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import com.example.ragingestor.ingestion.InvalidDocumentException;
import com.example.ragingestor.ingestion.UnsupportedDocumentTypeException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException exception) {
        return error(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "You do not have permission to access this resource.");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String code = switch (status) {
            case NOT_FOUND -> "NOT_FOUND";
            case CONFLICT -> "CONFLICT";
            case BAD_REQUEST -> "BAD_REQUEST";
            case FORBIDDEN -> "ACCESS_DENIED";
            case TOO_MANY_REQUESTS -> "RATE_LIMITED";
            default -> "REQUEST_FAILED";
        };
        String message = exception.getReason() == null ? status.getReasonPhrase() : exception.getReason();
        return error(status, code, message);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
            HttpMessageNotReadableException.class, InvalidDocumentException.class,
            UnsupportedDocumentTypeException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestPartException.class})
    public ResponseEntity<ApiErrorResponse> handleInvalidRequest(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "The request is invalid.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleUploadTooLarge(MaxUploadSizeExceededException exception) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "UPLOAD_TOO_LARGE", "The uploaded file exceeds the size limit.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        log.error("Unhandled request failure", exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred.");
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(status.value(), code, message));
    }
}

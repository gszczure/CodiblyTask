package org.codibly.exception.handler;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.codibly.dto.response.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.ZonedDateTime;

@Slf4j
@ControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    private ResponseEntity<ErrorResponse> illegalArgumentExceptionHandler(IllegalArgumentException ex,
                                                                   HttpServletRequest request) {
        log.warn("No generation data found: {}", ex.getMessage(), ex);
        ErrorResponse errorResponse = buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);

    }

    private ErrorResponse buildErrorResponse(
            HttpStatus status,
            String message,
            String path) {
        return new ErrorResponse(
                ZonedDateTime.now(),
                status.value(),
                status.name(),
                message,
                path
        );
    }
}

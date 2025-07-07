package com.practice1.backend.common.handler;

import com.practice1.backend.common.exception.PracticeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PracticeException.class)
    public ResponseEntity<?> handlePracticeException(PracticeException e) {
        log.warn("Handled PracticeException: {}", e.getMessage());
        return ResponseEntity.status(e.getStatusCode())
                .body(Map.of(
                        "error", e.getClass().getSimpleName(),
                        "message", e.getMessage()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleUnexpectedException(Exception e) {
        log.error("Unhandled Exception: {}", e.getMessage(), e);
        return ResponseEntity.internalServerError()
                .body(Map.of(
                        "error", "InternalServerError",
                        "message", "Unexpected error occurred"
                ));
    }
}
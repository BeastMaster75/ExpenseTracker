package com.expensetracker.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex
    ) {

        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        errors.put(
                                error.getField(),
                                error.getDefaultMessage()
                        )
                );

        Map<String, Object> response = new HashMap<>();

        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("message", "Validation failed");
        response.put("errors", errors);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    // Your custom AppException
    @ExceptionHandler(AppException.class)
    public ResponseEntity<Map<String, Object>> handleAppException(
            AppException ex
    ) {

        Map<String, Object> response = new HashMap<>();

        response.put("status", ex.getStatus().value());
        response.put("message", ex.getMessage());

        return ResponseEntity
                .status(ex.getStatus())
                .body(response);
    }
}

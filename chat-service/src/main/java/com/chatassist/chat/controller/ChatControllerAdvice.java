package com.chatassist.chat.controller;

import com.chatassist.common.dto.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ChatControllerAdvice {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        Map<String, Integer> fieldErrorPriority = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error -> {
            String field = error.getField();
            int priority = getPriority(error);
            Integer existingPriority = fieldErrorPriority.get(field);
            if (existingPriority == null || priority < existingPriority) {
                fieldErrorPriority.put(field, priority);
                fieldErrors.put(field, error.getDefaultMessage());
            }
        });

        ApiErrorResponse response = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Validation failed.",
                fieldErrors
        );
        return ResponseEntity.badRequest().body(response);
    }

    private int getPriority(FieldError error) {
        if (error.getCode() == null) {
            return 99;
        }
        if ("NotBlank".equals(error.getCode()) || "NotNull".equals(error.getCode())) {
            return 1;
        }
        if ("Positive".equals(error.getCode()) || "Size".equals(error.getCode())) {
            return 2;
        }
        if ("Pattern".equals(error.getCode()) || "Email".equals(error.getCode())) {
            return 3;
        }
        return 10;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatusException(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        ApiErrorResponse response = new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                exception.getReason() != null ? exception.getReason() : "Request failed.",
                Map.of()
        );
        return ResponseEntity.status(status).body(response);
    }
}


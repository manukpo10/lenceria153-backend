package com.merceria153.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntime(RuntimeException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "Error interno";
        HttpStatus status = HttpStatus.BAD_REQUEST;
        if (msg.contains("no encontrado")) status = HttpStatus.NOT_FOUND;
        else if (msg.contains("ya existe") || msg.contains("duplicado")) status = HttpStatus.CONFLICT;
        else if (msg.contains("Stock insuficiente")) status = HttpStatus.CONFLICT;
        else if (msg.contains("Credenciales")) status = HttpStatus.UNAUTHORIZED;
        else if (msg.contains("solo administradores")) status = HttpStatus.FORBIDDEN;
        return ResponseEntity.status(status).body(Map.of("error", msg));
    }
}
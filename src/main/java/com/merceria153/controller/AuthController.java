package com.merceria153.controller;

import com.merceria153.dto.AuthResponse;
import com.merceria153.dto.LoginRequest;
import com.merceria153.dto.RegisterRequest;
import com.merceria153.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService svc;

    public AuthController(AuthService svc) {
        this.svc = svc;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(svc.login(req));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.ok(svc.register(req));
    }

    @PostMapping("/seed")
    public ResponseEntity<Map<String, String>> seed() {
        return ResponseEntity.ok(Map.of("message", svc.seed()));
    }
}
package com.merceria153.service;

import com.merceria153.config.JwtUtil;
import com.merceria153.dto.AuthResponse;
import com.merceria153.dto.LoginRequest;
import com.merceria153.dto.RegisterRequest;
import com.merceria153.model.User;
import com.merceria153.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class AuthService {

    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepo, PasswordEncoder encoder, JwtUtil jwtUtil) {
        this.userRepo = userRepo;
        this.encoder = encoder;
        this.jwtUtil = jwtUtil;
    }

    public AuthResponse login(LoginRequest req) {
        User user = userRepo.findByUsername(req.getUsername())
                .orElseThrow(() -> new RuntimeException("Credenciales inválidas"));

        String hash = sha256(req.getPassword());
        if (!user.getPasswordHash().equals(hash)) {
            throw new RuntimeException("Credenciales inválidas");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        return new AuthResponse(token, new AuthResponse.UserDto(user.getId(), user.getUsername(), user.getRole(), user.getNombre()));
    }

    public AuthResponse register(RegisterRequest req) {
        if (userRepo.existsByUsername(req.getUsername())) {
            throw new RuntimeException("El usuario ya existe");
        }
        User user = new User();
        user.setUsername(req.getUsername());
        user.setPasswordHash(sha256(req.getPassword()));
        user.setNombre(req.getNombre());
        user.setRole(req.getRole() != null ? req.getRole() : "vendedor");
        userRepo.save(user);

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        return new AuthResponse(token, new AuthResponse.UserDto(user.getId(), user.getUsername(), user.getRole(), user.getNombre()));
    }

    public String seed() {
        if (userRepo.existsByUsername("admin")) return "Ya existen usuarios";
        String hash = sha256("admin123");
        User admin = new User();
        admin.setUsername("admin");
        admin.setPasswordHash(hash);
        admin.setNombre("Tío");
        admin.setRole("admin");
        userRepo.save(admin);

        User vendedor = new User();
        vendedor.setUsername("vendedor");
        vendedor.setPasswordHash(sha256("vendedor123"));
        vendedor.setNombre("Vendedor");
        vendedor.setRole("vendedor");
        userRepo.save(vendedor);
        return "2 usuarios creados";
    }

    private String sha256(String input) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            byte[] h = d.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
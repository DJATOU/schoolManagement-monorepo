package com.school.management.controller;

import com.school.management.dto.auth.LoginRequest;
import com.school.management.dto.auth.LoginResponse;
import com.school.management.dto.auth.RegisterRequest;
import com.school.management.persistance.AdministratorEntity;
import com.school.management.security.JwtTokenProvider;
import com.school.management.service.AdministratorService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final AdministratorService administratorService;
    private final JwtTokenProvider tokenProvider;

    @Autowired
    public AuthController(AuthenticationManager authenticationManager,
                         AdministratorService administratorService,
                         JwtTokenProvider tokenProvider) {
        this.authenticationManager = authenticationManager;
        this.administratorService = administratorService;
        this.tokenProvider = tokenProvider;
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getUsername(),
                            loginRequest.getPassword()
                    )
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            String jwt = tokenProvider.generateToken(authentication);

            AdministratorEntity administrator = administratorService.findByUsername(loginRequest.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            List<String> roles = administrator.getRoles().stream()
                    .map(role -> role.getName().name())
                    .collect(java.util.stream.Collectors.toList());

            LoginResponse response = new LoginResponse(
                    jwt,
                    administrator.getId(),
                    administrator.getUsername(),
                    administrator.getEmail(),
                    administrator.getFirstName(),
                    administrator.getLastName(),
                    roles
            );

            logger.info("User authenticated successfully: {}", loginRequest.getUsername());
            return ResponseEntity.ok(response);

        } catch (AuthenticationException e) {
            logger.warn("Authentication failed for user: {}", loginRequest.getUsername());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid username or password");
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest registerRequest) {
        try {
            if (administratorService.existsByUsername(registerRequest.getUsername())) {
                return ResponseEntity.badRequest()
                        .body("Username is already taken");
            }

            AdministratorEntity administrator = AdministratorEntity.builder()
                    .username(registerRequest.getUsername())
                    .password(registerRequest.getPassword()) // Will be encoded in service
                    .email(registerRequest.getEmail())
                    .firstName(registerRequest.getFirstName())
                    .lastName(registerRequest.getLastName())
                    .phoneNumber(registerRequest.getPhoneNumber())
                    .build();

            AdministratorEntity savedAdmin = administratorService.createAdministrator(administrator);

            String jwt = tokenProvider.generateTokenFromUsername(savedAdmin.getUsername());

            List<String> roles = savedAdmin.getRoles().stream()
                    .map(role -> role.getName().name())
                    .collect(java.util.stream.Collectors.toList());

            LoginResponse response = new LoginResponse(
                    jwt,
                    savedAdmin.getId(),
                    savedAdmin.getUsername(),
                    savedAdmin.getEmail(),
                    savedAdmin.getFirstName(),
                    savedAdmin.getLastName(),
                    roles
            );

            logger.info("User registered successfully: {}", registerRequest.getUsername());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            logger.error("Registration failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Registration failed: " + e.getMessage());
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
        }

        String username = authentication.getName();
        AdministratorEntity administrator = administratorService.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return ResponseEntity.ok(administrator);
    }
}

package com.school.management.controller;

import com.school.management.dto.security.CreateUserRequestDTO;
import com.school.management.dto.security.ResetPasswordRequestDTO;
import com.school.management.dto.security.UserResponseDTO;
import com.school.management.service.security.UserAccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Gestion des comptes utilisateurs (mince). Protégé <strong>ADMIN</strong> par la chaîne de
 * sécurité ({@code /api/v1/users/**}). La logique réside dans {@link UserAccountService}.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserAccountService userAccountService;

    public UserController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    /** Crée un compte (identifiant unique, mot de passe initial, rôle). */
    @PostMapping
    public ResponseEntity<UserResponseDTO> create(@Valid @RequestBody CreateUserRequestDTO request) {
        return new ResponseEntity<>(userAccountService.create(request), HttpStatus.CREATED);
    }

    /** Liste les comptes (sans mot de passe). */
    @GetMapping
    public ResponseEntity<List<UserResponseDTO>> findAll() {
        return ResponseEntity.ok(userAccountService.findAll());
    }

    /** Désactive un compte. */
    @PatchMapping("/{id}/disable")
    public ResponseEntity<UserResponseDTO> disable(@PathVariable Long id) {
        return ResponseEntity.ok(userAccountService.disable(id));
    }

    /** Réactive un compte. */
    @PatchMapping("/{id}/enable")
    public ResponseEntity<UserResponseDTO> enable(@PathVariable Long id) {
        return ResponseEntity.ok(userAccountService.enable(id));
    }

    /** Réinitialise le mot de passe d'un compte. */
    @PatchMapping("/{id}/reset-password")
    public ResponseEntity<UserResponseDTO> resetPassword(
            @PathVariable Long id,
            @Valid @RequestBody ResetPasswordRequestDTO request) {
        return ResponseEntity.ok(userAccountService.resetPassword(id, request.newPassword()));
    }
}

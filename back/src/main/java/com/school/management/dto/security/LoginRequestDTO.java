package com.school.management.dto.security;

import jakarta.validation.constraints.NotBlank;

/**
 * Requête de connexion : identifiant et mot de passe en clair (transmis en HTTPS).
 *
 * @param username identifiant du compte
 * @param password mot de passe en clair
 */
public record LoginRequestDTO(
        @NotBlank(message = "L'identifiant est obligatoire.") String username,
        @NotBlank(message = "Le mot de passe est obligatoire.") String password) {
}

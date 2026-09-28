package com.school.management.dto.security;

import com.school.management.persistance.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Requête de création d'un compte utilisateur (réservée à un ADMIN).
 *
 * @param username identifiant unique du compte
 * @param password mot de passe initial en clair (haché avant persistance)
 * @param role     rôle attribué (ADMIN / VIEWER)
 */
public record CreateUserRequestDTO(
        @NotBlank(message = "L'identifiant est obligatoire.") String username,
        @NotBlank(message = "Le mot de passe est obligatoire.") String password,
        @NotNull(message = "Le rôle est obligatoire.") Role role) {
}

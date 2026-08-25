package com.school.management.dto.security;

import jakarta.validation.constraints.NotBlank;

/**
 * Requête de réinitialisation du mot de passe d'un compte (réservée à un ADMIN).
 *
 * @param newPassword nouveau mot de passe en clair (ré-encodé en BCrypt)
 */
public record ResetPasswordRequestDTO(
        @NotBlank(message = "Le nouveau mot de passe est obligatoire.") String newPassword) {
}

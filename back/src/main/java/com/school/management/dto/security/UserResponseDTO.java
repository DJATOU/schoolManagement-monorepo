package com.school.management.dto.security;

import com.school.management.persistance.Role;

/**
 * Représentation d'un compte utilisateur renvoyée par l'API.
 *
 * <p><strong>N'expose jamais le mot de passe</strong> (haché ou en clair), conformément à
 * l'exigence de non-exposition des identifiants sensibles.</p>
 *
 * @param id       identifiant technique du compte
 * @param username identifiant de connexion
 * @param role     rôle du compte
 * @param enabled  indicateur d'activation
 */
public record UserResponseDTO(
        Long id,
        String username,
        Role role,
        Boolean enabled) {
}

package com.school.management.dto.security;

import com.school.management.persistance.Role;

import java.time.Instant;

/**
 * Réponse de connexion réussie : jeton d'authentification et informations du compte.
 *
 * @param token     jeton JWT signé à joindre aux requêtes protégées
 * @param username  identifiant du compte connecté
 * @param role      rôle du compte (ADMIN / VIEWER)
 * @param expiresAt date d'expiration du jeton
 */
public record AuthResponseDTO(
        String token,
        String username,
        Role role,
        Instant expiresAt) {
}

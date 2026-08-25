package com.school.management.service.security;

import com.school.management.persistance.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Service d'émission et de validation des jetons JWT (HS256).
 *
 * <p>Logique quasi pure, sans dépendance web ni base de données. La clé de signature provient
 * de {@code security.jwt.secret} (variable d'environnement, aucune valeur en dur) et la durée
 * de vie de {@code security.jwt.expiration-ms}. Un jeton porte l'identifiant en sujet, le rôle
 * en claim, ainsi que les dates d'émission et d'expiration.</p>
 */
@Service
public class JwtService {

    private static final String CLAIM_ROLE = "role";

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.expiration-ms:3600000}") long expirationMs) {
        // Fail-fast si la clé est absente : le contexte ne démarre pas sans secret.
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /** Émet un jeton signé pour l'identifiant et le rôle fournis (sujet + claim role + iat + exp). */
    public String generateToken(String username, Role role) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(expirationMs);
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_ROLE, role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Valide un jeton pour un identifiant donné : signature correcte, non expiré, et sujet
     * correspondant. Renvoie {@code false} pour tout jeton absent, malformé, de signature
     * invalide ou expiré.
     */
    public boolean isTokenValid(String token, String username) {
        try {
            Claims claims = parse(token);
            // L'expiration est vérifiée par jjwt lors du parsing (ExpiredJwtException pour un
            // jeton expiré, interceptée ci-dessous → false). On exige tout de même la présence
            // d'une date d'expiration : un jeton sans exp est refusé.
            return username != null
                    && username.equals(claims.getSubject())
                    && claims.getExpiration() != null;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /** Extrait l'identifiant (sujet) du jeton. */
    public String extractUsername(String token) {
        return parse(token).getSubject();
    }

    /** Extrait le rôle porté par le jeton. */
    public Role extractRole(String token) {
        return Role.valueOf(parse(token).get(CLAIM_ROLE, String.class));
    }

    /** Extrait la date d'expiration du jeton. */
    public Instant extractExpiration(String token) {
        return parse(token).getExpiration().toInstant();
    }

    /** Analyse et vérifie la signature du jeton, renvoyant ses claims. */
    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

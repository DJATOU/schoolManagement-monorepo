package com.school.management.service.security;

import com.school.management.persistance.Role;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires complémentaires de {@link JwtService} couvrant les branches non exercées
 * par les property tests : identifiant nul, sujet différent, jeton sans expiration, et
 * l'extraction de la date d'expiration.
 */
class JwtServiceUnitTest {

    private static final String SECRET = "test-secret-please-change-0123456789-abcdefghijklmnop";
    private final JwtService service = new JwtService(SECRET, 3_600_000L);
    private final SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    @Test
    void extractExpiration_returnsExpiryInFuture() {
        Instant before = Instant.now();
        String token = service.generateToken("alice", Role.ADMIN);

        Instant expiration = service.extractExpiration(token);

        // ~ maintenant + 1h (marge large pour éviter la fragilité).
        assertThat(expiration).isAfter(before.plusSeconds(3000));
        assertThat(expiration).isBefore(before.plusSeconds(4200));
    }

    @Test
    void isTokenValid_falseWhenUsernameNull() {
        String token = service.generateToken("bob", Role.VIEWER);
        assertThat(service.isTokenValid(token, null)).isFalse();
    }

    @Test
    void isTokenValid_falseWhenSubjectMismatch() {
        String token = service.generateToken("bob", Role.VIEWER);
        assertThat(service.isTokenValid(token, "charlie")).isFalse();
    }

    @Test
    void isTokenValid_falseWhenEmptyToken() {
        // Jeton vide → IllegalArgumentException interceptée → false (branche du multi-catch).
        assertThat(service.isTokenValid("", "bob")).isFalse();
    }

    @Test
    void isTokenValid_falseWhenNoExpirationClaim() {
        // Jeton signé valide mais SANS date d'expiration : exerce la branche exp == null.
        String noExp = Jwts.builder()
                .subject("bob")
                .claim("role", Role.VIEWER.name())
                .issuedAt(Date.from(Instant.now()))
                .signWith(key)
                .compact();

        assertThat(service.isTokenValid(noExp, "bob")).isFalse();
    }
}

package com.school.management.service.security;

import com.school.management.persistance.Role;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de propriété (jqwik) pour {@link JwtService}.
 *
 * <p>Le service est instancié directement avec un secret de test (>= 256 bits) : logique pure,
 * pas de contexte Spring nécessaire.</p>
 */
class JwtServicePropertyTest {

    private static final String SECRET = "test-secret-please-change-0123456789-abcdefghijklmnop";
    private final JwtService service = new JwtService(SECRET, 3_600_000L);
    // Service dont les jetons sont déjà expirés (durée négative).
    private final JwtService expiringService = new JwtService(SECRET, -1_000L);

    @Provide
    Arbitrary<String> usernames() {
        return Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(40);
    }

    @Provide
    Arbitrary<Role> roles() {
        return Arbitraries.of(Role.ADMIN, Role.VIEWER);
    }

    // Feature: authentication-authorization, Property 1: Round-trip d'émission/validation du jeton
    @Property(tries = 100)
    void tokenIssuanceValidationRoundTrip(@ForAll("usernames") String username,
                                          @ForAll("roles") Role role) {
        String token = service.generateToken(username, role);

        assertThat(service.isTokenValid(token, username)).isTrue();
        assertThat(service.extractUsername(token)).isEqualTo(username);
        assertThat(service.extractRole(token)).isEqualTo(role);
    }

    // Feature: authentication-authorization, Property 3: Jeton absent, invalide ou expiré refusé sur ressource protégée
    @Property(tries = 100)
    void absentInvalidOrExpiredTokenRejected(@ForAll("usernames") String username,
                                             @ForAll("roles") Role role) {
        // Jeton valide de référence, puis variantes invalides.
        String valid = service.generateToken(username, role);

        // 1) Jeton malformé / signature altérée (dernier caractère modifié).
        String tampered = valid.substring(0, valid.length() - 1)
                + (valid.charAt(valid.length() - 1) == 'A' ? 'B' : 'A');
        assertThat(service.isTokenValid(tampered, username)).isFalse();

        // 2) Chaîne arbitraire non-JWT.
        assertThat(service.isTokenValid("not-a-jwt", username)).isFalse();

        // 3) Jeton tout juste expiré (durée négative).
        String expired = expiringService.generateToken(username, role);
        assertThat(service.isTokenValid(expired, username)).isFalse();
    }
}

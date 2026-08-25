package com.school.management.service.security;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de propriété (jqwik) du round-trip de hachage des mots de passe (BCrypt).
 */
class PasswordHashingPropertyTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    @Provide
    Arbitrary<String> passwords() {
        // Mots de passe variés : vides, longs, non-ASCII (BCrypt tronque au-delà de 72 octets,
        // on borne donc la longueur pour rester dans le comportement spécifié).
        return Arbitraries.strings().ofMinLength(0).ofMaxLength(60);
    }

    // Feature: authentication-authorization, Property 6: Round-trip de hachage des mots de passe
    @Property(tries = 100)
    void passwordHashingRoundTrip(@ForAll("passwords") String plain) {
        String hashed = encoder.encode(plain);

        // La valeur stockée diffère du clair.
        assertThat(hashed).isNotEqualTo(plain);
        // Le clair correspond à son propre hachage.
        assertThat(encoder.matches(plain, hashed)).isTrue();
        // Un mot de passe différent ne correspond pas. On fait différer le PREMIER caractère
        // (et non le dernier) car BCrypt tronque au-delà de 72 octets : deux mots de passe
        // partageant les 72 premiers octets produiraient le même hachage.
        String other = (plain.startsWith("a") ? "b" : "a") + plain;
        assertThat(encoder.matches(other, hashed)).isFalse();
    }
}

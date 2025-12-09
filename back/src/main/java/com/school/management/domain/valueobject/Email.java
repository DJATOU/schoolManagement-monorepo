package com.school.management.domain.valueobject;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value Object représentant une adresse email.
 * Immutable et valide à la construction.
 *
 * Garantit qu'une adresse email est toujours valide grâce à la validation
 * dans le constructeur.
 *
 * Usage:
 * <pre>
 * Email email = Email.of("student@example.com");
 * String address = email.getAddress();
 * </pre>
 *
 * @author Claude Code
 * @since Phase 2 Refactoring
 */
@Embeddable
public class Email implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Pattern de validation d'email (RFC 5322 simplifié)
     * Accepte les formats standards comme: user@domain.com, user.name@domain.co.uk
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@" +
        "(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    );

    /**
     * L'adresse email stockée
     */
    private String email;

    /**
     * Constructeur par défaut requis par JPA
     */
    protected Email() {
    }

    /**
     * Constructeur privé - utiliser la factory method
     */
    private Email(String email) {
        validateEmail(email);
        this.email = normalizeEmail(email);
    }

    /**
     * Crée un Email à partir d'une String
     *
     * @param email l'adresse email
     * @return une instance Email
     * @throws IllegalArgumentException si l'email est invalide
     */
    public static Email of(String email) {
        return new Email(email);
    }

    /**
     * Valide le format de l'email
     *
     * @param email l'email à valider
     * @throws IllegalArgumentException si l'email est invalide
     */
    private void validateEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email cannot be null or empty");
        }

        if (email.length() > 254) {
            throw new IllegalArgumentException("Email exceeds maximum length of 254 characters");
        }

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Invalid email format: " + email);
        }

        // Vérifier que la partie locale ne dépasse pas 64 caractères
        String localPart = email.substring(0, email.indexOf('@'));
        if (localPart.length() > 64) {
            throw new IllegalArgumentException("Email local part exceeds 64 characters");
        }
    }

    /**
     * Normalise l'email (convertit en minuscules, trim)
     *
     * @param email l'email à normaliser
     * @return l'email normalisé
     */
    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    /**
     * Retourne l'adresse email
     *
     * @return l'adresse email
     */
    public String getEmail() {
        return email;
    }

    /**
     * Retourne la partie locale de l'email (avant @)
     *
     * @return la partie locale
     */
    public String getLocalPart() {
        return email.substring(0, email.indexOf('@'));
    }

    /**
     * Retourne le domaine de l'email (après @)
     *
     * @return le domaine
     */
    public String getDomain() {
        return email.substring(email.indexOf('@') + 1);
    }

    /**
     * Masque l'email pour l'affichage (privacy)
     * Exemple: john.doe@example.com → j***e@example.com
     *
     * @return l'email masqué
     */
    public String getMasked() {
        String local = getLocalPart();
        String domain = getDomain();

        if (local.length() <= 2) {
            return "***@" + domain;
        }

        return local.charAt(0) + "***" + local.charAt(local.length() - 1) + "@" + domain;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Email that = (Email) o;
        return Objects.equals(email, that.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(email);
    }

    @Override
    public String toString() {
        return email;
    }
}

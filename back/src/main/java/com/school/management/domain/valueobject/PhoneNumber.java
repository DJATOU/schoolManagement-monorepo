package com.school.management.domain.valueobject;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value Object représentant un numéro de téléphone.
 * Immutable et valide à la construction.
 *
 * Supporte les formats marocains et internationaux.
 * Normalise automatiquement le format.
 *
 * Usage:
 * <pre>
 * PhoneNumber phone = PhoneNumber.of("+212612345678");
 * PhoneNumber phone2 = PhoneNumber.of("0612345678");
 * String formatted = phone.getFormatted(); // +212 6 12 34 56 78
 * </pre>
 *
 * @author Claude Code
 * @since Phase 2 Refactoring
 */
@Embeddable
public class PhoneNumber implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Pattern pour numéro marocain (06, 07, 05)
     * Format accepté: 0612345678, 06 12 34 56 78, 06-12-34-56-78
     */
    private static final Pattern MOROCCO_PATTERN = Pattern.compile(
        "^0[5-7][0-9]{8}$"
    );

    /**
     * Pattern pour numéro international
     * Format: +212612345678, +33612345678, etc.
     */
    private static final Pattern INTERNATIONAL_PATTERN = Pattern.compile(
        "^\\+[1-9][0-9]{8,14}$"
    );

    /**
     * Le numéro de téléphone stocké (normalisé)
     */
    private String phoneNumber;

    /**
     * Constructeur par défaut requis par JPA
     */
    protected PhoneNumber() {
    }

    /**
     * Constructeur privé - utiliser la factory method
     */
    private PhoneNumber(String phoneNumber) {
        this.phoneNumber = normalizeAndValidate(phoneNumber);
    }

    /**
     * Crée un PhoneNumber à partir d'une String
     *
     * @param phoneNumber le numéro de téléphone
     * @return une instance PhoneNumber
     * @throws IllegalArgumentException si le numéro est invalide
     */
    public static PhoneNumber of(String phoneNumber) {
        return new PhoneNumber(phoneNumber);
    }

    /**
     * Normalise et valide le numéro de téléphone
     *
     * @param phone le numéro brut
     * @return le numéro normalisé
     * @throws IllegalArgumentException si le numéro est invalide
     */
    private String normalizeAndValidate(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            throw new IllegalArgumentException("Phone number cannot be null or empty");
        }

        // Retirer tous les caractères non numériques et le +
        String cleaned = phone.replaceAll("[^0-9+]", "");

        // Valider le format
        if (INTERNATIONAL_PATTERN.matcher(cleaned).matches()) {
            return cleaned; // Format international valide
        }

        if (MOROCCO_PATTERN.matcher(cleaned).matches()) {
            // Convertir format marocain local vers international
            return "+212" + cleaned.substring(1);
        }

        // Si commence par 212 sans +, ajouter le +
        if (cleaned.matches("^212[5-7][0-9]{8}$")) {
            return "+" + cleaned;
        }

        throw new IllegalArgumentException("Invalid phone number format: " + phone);
    }

    /**
     * Retourne le numéro au format brut (avec +)
     *
     * @return le numéro normalisé
     */
    public String getPhoneNumber() {
        return phoneNumber;
    }

    /**
     * Retourne le numéro au format national marocain (0612345678)
     * Fonctionne uniquement pour les numéros marocains
     *
     * @return le numéro au format national
     */
    public String getNationalFormat() {
        if (phoneNumber.startsWith("+212")) {
            return "0" + phoneNumber.substring(4);
        }
        return phoneNumber; // Retourne tel quel si non marocain
    }

    /**
     * Retourne le numéro au format international formaté
     * Exemple: +212 6 12 34 56 78
     *
     * @return le numéro formaté
     */
    public String getFormatted() {
        if (phoneNumber.startsWith("+212") && phoneNumber.length() == 13) {
            // Format marocain: +212 6 12 34 56 78
            return String.format("+212 %s %s %s %s %s",
                phoneNumber.substring(4, 5),
                phoneNumber.substring(5, 7),
                phoneNumber.substring(7, 9),
                phoneNumber.substring(9, 11),
                phoneNumber.substring(11, 13));
        }
        // Pour autres pays, format simple: +XX XXXXXXXXXX
        if (phoneNumber.length() > 4) {
            return phoneNumber.substring(0, 3) + " " + phoneNumber.substring(3);
        }
        return phoneNumber;
    }

    /**
     * Vérifie si c'est un numéro marocain
     *
     * @return true si le numéro est marocain
     */
    public boolean isMoroccanNumber() {
        return phoneNumber.startsWith("+212");
    }

    /**
     * Retourne le code pays
     * Exemple: +212 → 212
     *
     * @return le code pays sans le +
     */
    public String getCountryCode() {
        if (phoneNumber.startsWith("+")) {
            // Extraire le code pays (1 à 3 chiffres)
            for (int i = 1; i <= Math.min(4, phoneNumber.length()); i++) {
                String code = phoneNumber.substring(1, i);
                if (code.length() >= 1 && code.length() <= 3) {
                    return code;
                }
            }
        }
        return "";
    }

    /**
     * Masque le numéro pour l'affichage (privacy)
     * Exemple: +212612345678 → +212 6XX XX XX 78
     *
     * @return le numéro masqué
     */
    public String getMasked() {
        if (phoneNumber.length() < 6) {
            return "***";
        }
        String start = phoneNumber.substring(0, 5);
        String end = phoneNumber.substring(phoneNumber.length() - 2);
        return start + "XXX XX XX" + end;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PhoneNumber that = (PhoneNumber) o;
        return Objects.equals(phoneNumber, that.phoneNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(phoneNumber);
    }

    @Override
    public String toString() {
        return getFormatted();
    }
}

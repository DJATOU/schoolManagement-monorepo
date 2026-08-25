package com.school.management.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilitaire <strong>pur</strong> (sans Spring, sans I/O) pour les libellés d'année scolaire
 * ({@code School_Year_Label}).
 *
 * <p>Un libellé valide est de la forme {@code "YYYY-YYYY"} où la seconde année est exactement
 * égale à la première année plus un (par exemple {@code "2025-2026"}). Cette logique est isolée
 * ici afin d'être réutilisée par {@code YearEndWorkflowService} et testée indépendamment.</p>
 */
public final class SchoolYearLabels {

    /** Motif d'un libellé : quatre chiffres, un tiret, quatre chiffres. */
    private static final Pattern LABEL_PATTERN = Pattern.compile("^(\\d{4})-(\\d{4})$");

    private SchoolYearLabels() {
        // Classe utilitaire : pas d'instanciation.
    }

    /**
     * Dérive le libellé de l'année scolaire suivante.
     *
     * <p>Pour un libellé {@code "YYYY-(YYYY+1)"}, retourne {@code "(YYYY+1)-(YYYY+2)"} : les deux
     * années sont incrémentées de un et la seconde reste toujours égale à la première plus un.
     * Par exemple {@code deriveNextLabel("2025-2026")} retourne {@code "2026-2027"}
     * (Requirement 5.1).</p>
     *
     * @param label le libellé de l'année scolaire courante (non nul, de la forme
     *              {@code "YYYY-(YYYY+1)"}).
     * @return le libellé de l'année scolaire suivante.
     * @throws IllegalArgumentException si le libellé est nul ou mal formé (format incorrect ou
     *                                  seconde année différente de la première plus un).
     */
    public static String deriveNextLabel(String label) {
        if (label == null) {
            throw new IllegalArgumentException("Le libellé de l'année scolaire ne peut pas être nul.");
        }

        Matcher matcher = LABEL_PATTERN.matcher(label);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Libellé d'année scolaire mal formé : \"" + label
                            + "\". Format attendu : \"YYYY-YYYY\".");
        }

        int firstYear = Integer.parseInt(matcher.group(1));
        int secondYear = Integer.parseInt(matcher.group(2));

        if (secondYear != firstYear + 1) {
            throw new IllegalArgumentException(
                    "Libellé d'année scolaire mal formé : \"" + label
                            + "\". La seconde année doit être égale à la première plus un.");
        }

        return (firstYear + 1) + "-" + (secondYear + 1);
    }
}

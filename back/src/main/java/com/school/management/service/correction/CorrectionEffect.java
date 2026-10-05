package com.school.management.service.correction;

import java.util.Objects;

/**
 * Un effet d'une correction, énoncé pour l'administratrice (exigence 4.2).
 *
 * <p>La description entre dans l'empreinte de l'Aperçu : elle ne doit porter que des valeurs
 * métier stables entre l'Aperçu et la confirmation — montants, noms, numéros de reçus existants.
 * Jamais un identifiant ou un numéro attribués pendant l'exécution, ni une date du jour : ils
 * changeraient d'une exécution à l'autre, et toute confirmation serait refusée comme périmée.</p>
 *
 * @param type        nature de l'effet
 * @param description phrase en français, par exemple « Imputation de 3 000,00 DA sur « Octobre »
 *                    neutralisée »
 * @param sessionId   séance existante sur laquelle l'administratrice peut agir depuis l'Aperçu, ou
 *                    {@code null}. Posée sur une séance validée qui entre dans la période sans
 *                    présence, ou sur la présence notée à sa place : l'écran y propose « présent » ou
 *                    « absent » (exigence 5.7). Elle entre aussi dans l'empreinte : deux séances du
 *                    même jour et de la même série ont la même description.
 */
public record CorrectionEffect(CorrectionEffectType type, String description, Long sessionId) {

    public CorrectionEffect {
        Objects.requireNonNull(type, "type");
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Un effet de correction doit être décrit.");
        }
    }

    /** Un effet qui ne désigne aucune séance à noter. */
    public CorrectionEffect(CorrectionEffectType type, String description) {
        this(type, description, null);
    }
}

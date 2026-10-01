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
 */
public record CorrectionEffect(CorrectionEffectType type, String description) {

    public CorrectionEffect {
        Objects.requireNonNull(type, "type");
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Un effet de correction doit être décrit.");
        }
    }
}

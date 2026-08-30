package com.school.management.dto.catchup;

import jakarta.validation.constraints.NotNull;

/**
 * Corps de la résolution d'un rattrapage à préciser.
 *
 * <p>Les deux champs sont <strong>obligatoires</strong>, et {@code alreadyPaid} est un
 * {@link Boolean} objet précisément pour que son absence soit détectable : un {@code boolean}
 * primitif vaudrait {@code false} par défaut, c'est-à-dire « à facturer », donc une décision
 * monétaire prise par le désérialiseur au lieu de l'administrateur. C'est exactement le mécanisme
 * de facturation silencieuse que cette fonctionnalité corrige.</p>
 *
 * @param missedSessionId séance manquée rattrapée ; c'est elle qui porte la facturation, dans son
 *                        groupe d'origine
 * @param alreadyPaid     la séance manquée était-elle déjà payée dans sa série d'origine ?
 *                        {@code true} → gratuite côté accueil ; {@code false} → facturée côté
 *                        accueil. Aucune valeur par défaut.
 * @param comment         motif ou précision, conservé dans la trace d'audit
 */
public record ResolveCatchUpRequestDTO(
        @NotNull Long missedSessionId,
        @NotNull Boolean alreadyPaid,
        String comment) {
}

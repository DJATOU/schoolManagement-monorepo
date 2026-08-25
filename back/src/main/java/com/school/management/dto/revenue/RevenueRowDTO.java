package com.school.management.dto.revenue;

import java.math.BigDecimal;

/**
 * Une ligne du rapport de recettes, quel que soit l'axe d'agrégation.
 *
 * @param key       identifiant de l'entité agrégée (groupe, série, séance) ou {@code null}
 *                  pour l'axe mensuel
 * @param label     libellé principal (nom du groupe, de la série, titre de la séance, mois)
 * @param subLabel  libellé secondaire (par exemple le groupe d'une série), peut être nul
 * @param collected montant encaissé brut pour cette ligne
 * @param share     part de cette ligne dans le total encaissé, en pourcentage
 */
public record RevenueRowDTO(
        Long key,
        String label,
        String subLabel,
        BigDecimal collected,
        BigDecimal share) {
}

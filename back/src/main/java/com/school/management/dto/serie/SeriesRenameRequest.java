package com.school.management.dto.serie;

/**
 * Corps de la demande de renommage d'une série.
 *
 * <p>Volontairement réduit au seul nom : le renommage passe par un point d'entrée dédié et non
 * par le {@code PATCH /api/series/{id}} générique, qui projette une {@code Map} arbitraire sur
 * l'entité et permettrait donc au client d'écraser n'importe quel champ (groupe, dates,
 * indicateur actif, champs d'audit).</p>
 *
 * @param name le nouveau nom de la série
 */
public record SeriesRenameRequest(String name) {
}

package com.school.management.dto;

import com.school.management.persistance.DiscountScope;

import java.math.BigDecimal;

/**
 * DTO minimal de création d'une réduction (discount).
 *
 * <p>Porte les données brutes soumises par l'administrateur. La validation métier
 * (portée unique correspondant au scope, taux dans l'intervalle [0.00, 1.00]) est
 * assurée par {@code DiscountService}. Le mapper MapStruct / DTO de réponse sera
 * ajouté ultérieurement (tâche 14.2) ; cette requête reste volontairement minimale
 * et réutilisable.</p>
 *
 * @param studentId identifiant de l'étudiant bénéficiaire
 * @param scope     portée d'application (GROUP, SERIES, SESSION)
 * @param groupId   identifiant du groupe visé (renseigné uniquement si scope == GROUP)
 * @param seriesId  identifiant de la série visée (renseigné uniquement si scope == SERIES)
 * @param sessionId identifiant de la séance visée (renseigné uniquement si scope == SESSION)
 * @param rate      taux de réduction dans l'intervalle [0.00, 1.00]
 */
public record DiscountRequestDTO(
        Long studentId,
        DiscountScope scope,
        Long groupId,
        Long seriesId,
        Long sessionId,
        BigDecimal rate) {
}

package com.school.management.dto;

import com.school.management.persistance.DiscountScope;

import java.math.BigDecimal;

/**
 * DTO de réponse d'une réduction (discount).
 *
 * <p>Produit par {@code DiscountMapper} à partir d'une {@code DiscountEntity} en
 * aplatissant la relation étudiant vers son identifiant ({@code student.id → studentId}).
 * Les identifiants de portée ({@code groupId} / {@code seriesId} / {@code sessionId})
 * sont déjà portés en tant que scalaires par l'entité.</p>
 *
 * @param id        identifiant de la réduction
 * @param studentId identifiant de l'étudiant bénéficiaire
 * @param scope     portée d'application (GROUP, SERIES, SESSION)
 * @param groupId   identifiant du groupe visé (renseigné uniquement si scope == GROUP)
 * @param seriesId  identifiant de la série visée (renseigné uniquement si scope == SERIES)
 * @param sessionId   identifiant de la séance visée (renseigné uniquement si scope == SESSION)
 * @param rate        taux de réduction dans l'intervalle [0.00, 1.00]
 * @param studentName nom complet de l'étudiant, pour l'affichage (résolu hors mapper)
 * @param targetName  libellé de la cible visée (groupe / série / séance), pour l'affichage
 */
public record DiscountResponseDTO(
        Long id,
        Long studentId,
        DiscountScope scope,
        Long groupId,
        Long seriesId,
        Long sessionId,
        BigDecimal rate,
        String studentName,
        String targetName) {
}

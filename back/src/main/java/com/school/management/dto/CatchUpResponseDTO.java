package com.school.management.dto;

import com.school.management.persistance.CatchUpStatus;

import java.util.Date;

/**
 * DTO de réponse d'une demande de rattrapage (catch-up).
 *
 * <p>Miroir complet de l'interface front {@code CatchUpRequest} : il porte l'ensemble
 * des données d'une demande, quel que soit son état dans le cycle de vie
 * (PENDING → SCHEDULED → COMPLETED / CANCELLED). Il est produit par
 * {@code CatchUpRequestMapper} à partir d'une {@code CatchUpRequestEntity} en aplatissant
 * les relations vers leurs identifiants.</p>
 *
 * <p>Le DTO de création {@link CatchUpRequestDTO} (record minimal) reste inchangé et
 * dédié à la requête de création ; ce DTO-ci est dédié aux réponses.</p>
 *
 * @param id                   identifiant de la demande
 * @param studentId            identifiant de l'étudiant concerné
 * @param originalSessionId    identifiant de la séance manquée d'origine
 * @param originalGroupId      identifiant du groupe d'origine
 * @param originalAttendanceId identifiant de la fiche de présence (absence) d'origine
 * @param catchUpSessionId     identifiant de la séance de rattrapage (si planifiée)
 * @param catchUpGroupId       identifiant du groupe de rattrapage (si planifié)
 * @param status               statut du cycle de vie de la demande
 * @param requestDate          date de création de la demande
 * @param scheduledDate        date planifiée du rattrapage (si planifié)
 * @param completedDate        date à laquelle le rattrapage a été effectué (si complété)
 * @param cancellationReason   motif d'annulation (si annulé)
 * @param notes                notes libres facultatives
 * @param studentName          nom complet de l'étudiant (enrichissement liste, sinon null)
 * @param originalSessionName  intitulé de la séance manquée (enrichissement liste, sinon null)
 * @param originalGroupName    nom du groupe d'origine (enrichissement liste, sinon null)
 * @param catchUpSessionName   intitulé de la séance de rattrapage (enrichissement liste, sinon null)
 * @param catchUpGroupName     nom du groupe de rattrapage (enrichissement liste, sinon null)
 */
public record CatchUpResponseDTO(
        Long id,
        Long studentId,
        Long originalSessionId,
        Long originalGroupId,
        Long originalAttendanceId,
        Long catchUpSessionId,
        Long catchUpGroupId,
        CatchUpStatus status,
        Date requestDate,
        Date scheduledDate,
        Date completedDate,
        String cancellationReason,
        String notes,
        String studentName,
        String originalSessionName,
        String originalGroupName,
        String catchUpSessionName,
        String catchUpGroupName) {
}

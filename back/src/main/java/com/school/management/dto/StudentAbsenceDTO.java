package com.school.management.dto;

import java.util.Date;

/**
 * DTO d'une absence éligible au rattrapage.
 *
 * <p>Utilisé par le workflow de rattrapage pour proposer, à la création d'une demande,
 * la liste des séances manquées (absences) d'un étudiant qui peuvent encore donner lieu à
 * un rattrapage. Chaque absence porte les informations nécessaires à l'affichage (nom et
 * date de séance, groupe d'origine) et à la création de la demande
 * ({@code attendanceId}, {@code sessionId}, {@code groupId}).</p>
 *
 * @param attendanceId identifiant de la fiche de présence (absence) d'origine
 * @param sessionId    identifiant de la séance manquée
 * @param sessionTitle intitulé de la séance manquée (peut être nul)
 * @param sessionDate  date/heure de début de la séance manquée (peut être nulle)
 * @param groupId      identifiant du groupe d'origine (peut être nul)
 * @param groupName    nom du groupe d'origine (peut être nul)
 * @param seriesId     identifiant de la série de la séance manquée (peut être nul)
 * @param isJustified  vrai si l'absence est justifiée
 * @param catchUpRight vrai si le droit au rattrapage n'a pas été révoqué
 */
public record StudentAbsenceDTO(
        Long attendanceId,
        Long sessionId,
        String sessionTitle,
        Date sessionDate,
        Long groupId,
        String groupName,
        Long seriesId,
        Boolean isJustified,
        Boolean catchUpRight) {
}

package com.school.management.dto.catchup;

import java.util.Date;

/**
 * Rattrapage à préciser, tel qu'affiché dans la liste des décisions en attente.
 *
 * <p>Chaque ligne est une séance <strong>consommée que personne ne facture encore</strong> : la
 * liste existe pour être vidée. Elle porte donc de quoi décider sans ouvrir un autre écran — qui,
 * quand, dans quel groupe, et à quel niveau et matière — plutôt que de simples identifiants.</p>
 *
 * @param attendanceId  identifiant de la présence de rattrapage à résoudre
 * @param studentId     identifiant de l'étudiant
 * @param studentName   nom complet de l'étudiant
 * @param sessionId     séance suivie (dans le groupe d'accueil)
 * @param sessionTitle  intitulé de la séance suivie
 * @param sessionDate   date de la séance suivie
 * @param hostGroupId   groupe où la séance a été suivie
 * @param hostGroupName nom du groupe d'accueil
 * @param levelName     niveau du groupe d'accueil : c'est l'une des deux dimensions du test de
 *                      routage, elle doit être lisible pour que la décision soit vérifiable
 * @param subjectName   matière du groupe d'accueil, seconde dimension du test
 * @param seriesId      série de la séance suivie
 * @param seriesName    nom de la série de la séance suivie
 */
public record PendingCatchUpDTO(
        Long attendanceId,
        Long studentId,
        String studentName,
        Long sessionId,
        String sessionTitle,
        Date sessionDate,
        Long hostGroupId,
        String hostGroupName,
        String levelName,
        String subjectName,
        Long seriesId,
        String seriesName) {
}

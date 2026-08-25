package com.school.management.dto.group;

import com.school.management.service.group.GroupChangeDetector.GroupChange;

/**
 * Signalement d'un changement de groupe, tel que restitué à l'interface (exigences 10.2, 10.3).
 *
 * <p>Contrepartie de la décision d'unité de facturation : l'agrégation automatique entre groupes
 * sur un mois civil est abandonnée, donc un étudiant qui quitte un groupe et en rejoint un autre
 * au milieu d'un mois doit être <strong>visible</strong> pour que l'administrateur ajuste sa
 * facturation à la main. Le signalement est purement informatif : il n'altère aucun montant et ne
 * bloque aucun encaissement (exigences 10.6, 10.7).</p>
 *
 * <h2>Pourquoi un DTO plutôt que le record du détecteur</h2>
 * Le contrat exposé au client est figé ici, indépendamment de la structure interne du détecteur.
 * Le mois est porté par deux entiers plutôt que par un {@code YearMonth} : deux entiers traversent
 * la sérialisation JSON sans dépendre d'un module de dates, et le client formate le libellé du
 * mois dans sa propre langue.
 *
 * @param year        année civile du changement
 * @param month       mois civil du changement, de 1 à 12
 * @param leftGroup   groupe quitté et ses séances suivies sur ce mois
 * @param joinedGroup groupe rejoint et ses séances suivies sur ce mois
 */
public record GroupChangeDTO(int year, int month, GroupActivityDTO leftGroup,
                             GroupActivityDTO joinedGroup) {

    /**
     * Activité de l'étudiant dans l'un des deux groupes, sur le mois du signalement.
     *
     * <p>Le nom du groupe accompagne son identifiant : un signalement affichant « groupe 42 vers
     * groupe 43 » n'aiderait pas l'administrateur, et l'obligerait à relire la base.</p>
     *
     * @param groupId       identifiant du groupe
     * @param groupName     nom du groupe
     * @param attendedCount séances suivies (présent) dans ce groupe sur le mois
     */
    public record GroupActivityDTO(Long groupId, String groupName, int attendedCount) {
    }

    /** Projette un signalement du détecteur sur le contrat exposé au client. */
    public static GroupChangeDTO from(GroupChange change) {
        return new GroupChangeDTO(
                change.year(),
                change.month(),
                new GroupActivityDTO(change.leftGroup().groupId(),
                        change.leftGroup().groupName(),
                        change.leftGroup().attendedCount()),
                new GroupActivityDTO(change.joinedGroup().groupId(),
                        change.joinedGroup().groupName(),
                        change.joinedGroup().attendedCount()));
    }
}

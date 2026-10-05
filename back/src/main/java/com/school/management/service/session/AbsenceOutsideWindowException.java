package com.school.management.service.session;

import com.school.management.dto.session.RejectedAbsenceDTO;
import com.school.management.service.exception.CustomServiceException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Écriture refusée parce qu'elle laisserait une absence hors Fenêtre_Inscription (exigences 7.3,
 * 7.5) : 409, avec chaque ligne en cause.
 *
 * <p>Le refus est entier : rien n'est écrit, pas même les lignes valides. Valider une partie de la
 * feuille laisserait la séance à moitié pointée, sans qu'on sache laquelle.</p>
 */
public class AbsenceOutsideWindowException extends CustomServiceException {

    private final transient List<RejectedAbsenceDTO> rejected;

    private AbsenceOutsideWindowException(String header, List<RejectedAbsenceDTO> rejected) {
        super(header + " " + rejected.stream().map(RejectedAbsenceDTO::message).collect(Collectors.joining(" ")),
                HttpStatus.CONFLICT);
        this.rejected = List.copyOf(rejected);
    }

    /** Feuille de présence soumise : les lignes refusées sont à retirer avant de revalider. */
    static AbsenceOutsideWindowException onSubmission(List<RejectedAbsenceDTO> rejected) {
        return new AbsenceOutsideWindowException(rejected.size() == 1
                ? "Validation refusée : une absence est notée pour un étudiant que la séance ne concerne "
                        + "pas. Retirez cette ligne et validez de nouveau."
                : "Validation refusée : " + rejected.size() + " absences sont notées pour des étudiants que "
                        + "la séance ne concerne pas. Retirez ces lignes et validez de nouveau.",
                rejected);
    }

    /** Séance déplacée (jour ou groupe) : ses absences sortiraient des fenêtres d'inscription. */
    static AbsenceOutsideWindowException onSessionMove(List<RejectedAbsenceDTO> rejected) {
        return new AbsenceOutsideWindowException(rejected.size() == 1
                ? "Modification refusée : la séance porte une absence qui sortirait de la période "
                        + "d'inscription. Dévalidez la séance ou corrigez cette présence d'abord."
                : "Modification refusée : la séance porte " + rejected.size() + " absences qui sortiraient "
                        + "de la période d'inscription. Dévalidez la séance ou corrigez ces présences d'abord.",
                rejected);
    }

    /** Les absences refusées, dans l'ordre des lignes soumises. */
    public List<RejectedAbsenceDTO> getRejected() {
        return rejected;
    }
}

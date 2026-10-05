package com.school.management.dto.session;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.util.List;

/**
 * Absence refusée : l'étudiant n'était pas concerné par la séance (exigences 7.3, 7.5).
 *
 * <p>Une ligne par absence refusée, pour que l'écran puisse retirer exactement ces lignes et
 * revalider. Le message la nomme en clair ; les champs structurés servent à l'écran.</p>
 *
 * @param studentId  l'étudiant
 * @param sessionId  la séance
 * @param sessionDay le jour de la séance, {@code null} si elle n'est pas datée
 * @param reason     pourquoi la séance ne le concerne pas
 * @param windows    ses fenêtres dans le groupe de la séance, vide s'il n'y est pas inscrit
 * @param message    la ligne en français, prête à afficher
 */
public record RejectedAbsenceDTO(
        Long studentId,
        String firstName,
        String lastName,
        Long sessionId,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate sessionDay,
        Reason reason,
        List<RollCallDTO.Window> windows,
        String message) {

    /** Motif du refus. */
    public enum Reason {
        /** Aucune inscription au groupe de la séance, active ou close. */
        NOT_ENROLLED,
        /** Inscrit au groupe, mais aucune de ses fenêtres ne contient le jour de la séance. */
        OUTSIDE_WINDOW
    }
}

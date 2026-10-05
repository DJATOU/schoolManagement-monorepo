package com.school.management.util;

import com.school.management.dto.session.RejectedAbsenceDTO;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Corps du 409 d'une écriture qui laisserait une absence hors Fenêtre_Inscription (spec
 * admin-corrections, exigences 7.3 et 7.5).
 *
 * @param status    {@code CONFLICT}
 * @param message   motif du refus, chaque ligne nommée
 * @param errorCode {@code ABSENCE_OUTSIDE_WINDOW}
 * @param rejected  les absences refusées, à retirer avant de revalider
 */
public record RejectedAbsencesErrorResponse(HttpStatus status, String message, String errorCode,
                                            List<RejectedAbsenceDTO> rejected) {
}

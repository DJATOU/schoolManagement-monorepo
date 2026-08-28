package com.school.management.service;

import com.school.management.dto.JustificationAuditDTO;
import com.school.management.dto.JustificationUpdateResult;
import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.AttendanceJustificationAuditEntity;
import com.school.management.repository.AttendanceJustificationAuditRepository;
import com.school.management.repository.AttendanceRepository;
import com.school.management.service.exception.CustomServiceException;
import org.springframework.data.domain.AuditorAware;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Modifie la justification d'une absence, en conservant la trace de chaque changement
 * (exigences 4 et 5).
 *
 * <h2>Pourquoi ce service existe</h2>
 * La justification n'était écrite qu'à la saisie initiale en masse. {@code PUT /attendances/{id}}
 * n'acceptait aucun corps de requête et ré-enregistrait la présence sans rien modifier : l'appel
 * réussissait et ne changeait rien. Une erreur de feuille de présence, ou un justificatif remis en
 * retard, restait donc définitive.
 *
 * <h2>La justification ne touche à aucun montant</h2>
 * Ce service ne consulte ni devis, ni calculateur de coût, ni statut de paiement, et c'est
 * structurel : la justification sert au suivi disciplinaire et au droit au rattrapage, pas au
 * calcul. Une absence, justifiée ou non, n'entre jamais dans le montant dû à ce jour, et reste
 * comptée dans le coût de la série puisque la place était réservée (exigences 3.1 à 3.3).
 *
 * <h2>Aucune modification n'est conservée sans sa trace</h2>
 * La présence et son entrée d'audit sont écrites dans la même transaction (exigence 5.4). Si
 * l'audit échoue, la modification est annulée — une justification changée sans qu'on sache qui l'a
 * changée ne vaut rien devant un parent qui contexte.
 */
@Service
public class AttendanceJustificationService {

    /** Longueur maximale du commentaire accompagnant une modification. */
    private static final int MAX_COMMENT_LENGTH = 500;

    private final AttendanceRepository attendanceRepository;
    private final AttendanceJustificationAuditRepository auditRepository;
    private final ReadOnlyYearGuard readOnlyYearGuard;
    private final AuditorAware<String> auditorAware;

    public AttendanceJustificationService(AttendanceRepository attendanceRepository,
                                         AttendanceJustificationAuditRepository auditRepository,
                                         ReadOnlyYearGuard readOnlyYearGuard,
                                         AuditorAware<String> auditorAware) {
        this.attendanceRepository = attendanceRepository;
        this.auditRepository = auditRepository;
        this.readOnlyYearGuard = readOnlyYearGuard;
        this.auditorAware = auditorAware;
    }

    /**
     * Applique la valeur demandée à la justification d'une absence, et consigne le changement.
     *
     * <p>Ouvre une transaction <strong>nouvelle</strong> à chaque appel
     * ({@code REQUIRES_NEW}) : le rejeu piloté par {@code JustificationRetryTemplate} doit pouvoir
     * réessayer sur une transaction saine. Réessayer dans une transaction déjà marquée pour
     * annulation ne réessaierait rien.</p>
     *
     * @throws CustomServiceException 404 si la présence est introuvable (exigence 4.5)
     * @throws CustomServiceException 400 si la présence est marquée présente (4.6), désactivée
     *         (4.12), ou si le commentaire dépasse la longueur autorisée (4.8)
     * @throws com.school.management.service.exception.ReadOnlySchoolYearException 409 si l'année
     *         scolaire de la séance est close (4.13)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public JustificationUpdateResult updateJustification(Long attendanceId,
                                                         boolean justified,
                                                         String comment) {
        // L'ordre des contrôles est fixé et testé : existence, nature de la présence, activité,
        // année scolaire, puis format du commentaire. Il va du plus structurant au plus cosmétique,
        // afin que le message d'erreur nomme la cause la plus utile à corriger.
        AttendanceEntity attendance = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new CustomServiceException(
                        "Présence introuvable pour l'identifiant : " + attendanceId,
                        HttpStatus.NOT_FOUND));

        if (Boolean.TRUE.equals(attendance.getIsPresent())) {
            throw new CustomServiceException(
                    "La justification ne s'applique qu'à une absence : cette présence est marquée "
                            + "présent.",
                    HttpStatus.BAD_REQUEST);
        }

        if (!attendance.isActive()) {
            throw new CustomServiceException(
                    "Cette présence est désactivée : sa justification n'est plus modifiable.",
                    HttpStatus.BAD_REQUEST);
        }

        // Aligné sur le garde-fou d'année scolaire déjà en place ailleurs : une année close est
        // consultable mais non modifiable. Aucune autre borne d'ancienneté n'est appliquée, un
        // justificatif pouvant être remis tardivement dans l'année (exigence 4.14).
        readOnlyYearGuard.assertSessionMutable(attendance.getSession());

        String normalizedComment = normalizedComment(comment);

        Boolean currentValue = attendance.getIsJustified();
        if (currentValue != null && currentValue == justified) {
            // Exigence 4.3 : succès sans écriture ni entrée d'audit.
            return new JustificationUpdateResult(attendanceId, currentValue, false);
        }

        attendance.setIsJustified(justified);
        attendanceRepository.save(attendance);

        auditRepository.save(AttendanceJustificationAuditEntity.builder()
                .attendanceId(attendanceId)
                .oldValue(currentValue)
                .newValue(justified)
                .performedBy(currentAuditor())
                .performedAt(LocalDateTime.now())
                // Rang croissant par présence : départage deux entrées de même horodatage, ce qui
                // rend « la plus récente » déterministe (exigences 5.7, 5.8).
                .sequenceRank(auditRepository.findMaxSequenceRank(attendanceId) + 1)
                .comment(normalizedComment)
                .build());

        return new JustificationUpdateResult(attendanceId, justified, true);
    }

    /**
     * Piste d'audit d'une présence, de la plus récente à la plus ancienne (exigence 5.7).
     *
     * <p>Ouverte en lecture aux deux rôles : constater qui a modifié quoi n'est pas une écriture.
     * Restitue une collection vide lorsque la présence n'a jamais été modifiée, et les entrées
     * survivent à la désactivation comme à la suppression de la présence (exigence 5.11).</p>
     */
    @Transactional(readOnly = true)
    public List<JustificationAuditDTO> auditTrail(Long attendanceId) {
        return auditRepository.findTrail(attendanceId).stream()
                .map(entry -> new JustificationAuditDTO(
                        entry.getId(),
                        entry.getAttendanceId(),
                        entry.getOldValue(),
                        entry.getNewValue(),
                        entry.getPerformedBy(),
                        entry.getPerformedAt(),
                        entry.getComment()))
                .toList();
    }

    /** Commentaire nettoyé, ou {@code null} s'il est absent ou vide. */
    private String normalizedComment(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }
        String trimmed = comment.strip();
        if (trimmed.length() > MAX_COMMENT_LENGTH) {
            throw new CustomServiceException(String.format(
                    "Le commentaire ne doit pas dépasser %d caractères (reçu : %d).",
                    MAX_COMMENT_LENGTH, trimmed.length()),
                    HttpStatus.BAD_REQUEST);
        }
        return trimmed;
    }

    /** Auteur courant, avec repli {@code system} en l'absence d'utilisateur authentifié. */
    private String currentAuditor() {
        return auditorAware.getCurrentAuditor().orElse("system");
    }
}

package com.school.management.service.correction;

import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.AttendanceJustificationAuditEntity;
import com.school.management.repository.AttendanceJustificationAuditRepository;
import com.school.management.repository.AttendanceRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Adaptateur de {@code attendance_justification_audit} (D8) : « Séance du 14/01/2030 (Math 1ère A)
 * : absence non justifiée → justifiée ».
 *
 * <p>La table ne garde que la présence : l'élève, le jour et le groupe se lisent sur elle. Une
 * présence n'est jamais effacée, seulement retirée ; ses traces restent donc nommables.</p>
 *
 * <p>Aucun effet sur le dû : la justification est documentaire (décision du propriétaire
 * produit).</p>
 */
@Component
class JustificationJournalSource implements JournalSource {

    private final AttendanceRepository attendanceRepository;
    private final AttendanceJustificationAuditRepository auditRepository;

    JustificationJournalSource(AttendanceRepository attendanceRepository,
                               AttendanceJustificationAuditRepository auditRepository) {
        this.attendanceRepository = attendanceRepository;
        this.auditRepository = auditRepository;
    }

    @Override
    public List<Item> itemsOf(Long studentId) {
        Map<Long, AttendanceEntity> attendances = attendanceRepository.findByStudentId(studentId).stream()
                .collect(Collectors.toMap(AttendanceEntity::getId, Function.identity()));
        return auditRepository.findByAttendanceIdIn(attendances.keySet()).stream()
                .map(audit -> item(audit, attendances.get(audit.getAttendanceId())))
                .toList();
    }

    private static Item item(AttendanceJustificationAuditEntity audit, AttendanceEntity attendance) {
        String description = "Séance du " + JournalWording.dayAndGroup(attendance.getSession()) + " : absence "
                + label(audit.getOldValue()) + " → " + label(audit.getNewValue());
        return new Item(new JournalEntry(audit.getPerformedAt(), JournalCategory.JUSTIFICATION, description, null,
                null, audit.getComment(), audit.getPerformedBy()), audit.getId());
    }

    /** Jamais renseignée vaut non justifiée : c'est ce que l'écran affichait. */
    private static String label(Boolean justified) {
        return Boolean.TRUE.equals(justified) ? "justifiée" : "non justifiée";
    }
}
